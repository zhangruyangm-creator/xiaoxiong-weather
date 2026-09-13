package com.xiaoxiong.weather.data

import com.xiaoxiong.weather.data.remote.MetNorwayResponse
import com.xiaoxiong.weather.data.remote.MetNorwayTimeseriesEntry
import com.xiaoxiong.weather.data.remote.OpenMeteoHttp
import com.xiaoxiong.weather.util.WeatherCodes
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToInt

/**
 * MET Norway Locationforecast 2.0（compact）降级数据源：Open-Meteo 主站不可用时兜底。
 *
 * - 免密钥、全球覆盖；官方要求可识别 User-Agent（见 [MET_USER_AGENT]）
 * - 缺失字段（空气质量/体感/阵风/露点/能见度/日出日落/紫外线）保持 null，
 *   由展示层按「无数据」处理，相关预警自然不生成
 * - 响应时间为 UTC，按城市时区换算为本地墙钟时间（仅主源故障时使用）；
 *   时区优先取 [City.timezone]（地理编码回填），中国城市无时区时固定用 Asia/Shanghai，
 *   其余退回按经度近似
 */
class MetNorwayFetcher(
    private val client: OkHttpClient,
    private val json: Json,
    private val baseUrl: String = DEFAULT_BASE_URL,
) {
    suspend fun fetchWeather(
        city: City,
        now: Instant = Instant.now(),
    ): WeatherSnapshot =
        withContext(Dispatchers.IO) {
            val lat = String.format(Locale.US, "%.4f", city.latitude)
            val lon = String.format(Locale.US, "%.4f", city.longitude)
            val url =
                baseUrl
                    .toHttpUrl()
                    .newBuilder()
                    .addPathSegment("weatherapi")
                    .addPathSegment("locationforecast")
                    .addPathSegment("2.0")
                    .addPathSegment("compact")
                    .addQueryParameter("lat", lat)
                    .addQueryParameter("lon", lon)
                    .build()
            val body = OpenMeteoHttp.execute(url.toString(), client, MET_USER_AGENT)
            val response =
                try {
                    json.decodeFromString(MetNorwayResponse.serializer(), body)
                } catch (e: SerializationException) {
                    throw AppError.DataFormat(e)
                }
            mapToSnapshot(response, city, now)
        }

    internal fun mapToSnapshot(
        response: MetNorwayResponse,
        city: City,
        now: Instant,
    ): WeatherSnapshot {
        val zone = resolveZone(city)
        val points =
            response.properties
                ?.timeseries
                .orEmpty()
                .mapNotNull { entry ->
                    val instant = runCatching { Instant.parse(entry.time) }.getOrNull()
                    val details = entry.data?.instant?.details
                    if (instant == null || details == null) {
                        null
                    } else {
                        MetPoint(
                            instant = instant,
                            temperatureC = details.airTemperature,
                            humidityPercent = details.relativeHumidity?.roundToInt(),
                            windKmh = details.windSpeed?.times(MPS_TO_KMH),
                            windDirectionDeg = details.windFromDirection?.roundToInt(),
                            cloudCoverPercent = details.cloudAreaFraction?.roundToInt(),
                            pressureHpa = details.airPressureAtSeaLevel,
                            weatherCode = WeatherCodes.normalizeCode(weatherCodeForSymbol(symbolOf(entry))),
                            precipitationAmountMm = precipitationAmountForPeriod(entry),
                            precipitationRateMmPerHour = precipitationForHour(entry),
                            precipProbability = probabilityForHour(entry)?.roundToInt(),
                        )
                    }
                }.sortedBy { it.instant }
        if (points.isEmpty()) {
            throw AppError.DataFormat(IllegalStateException("MET Norway timeseries empty"))
        }

        val currentPoint = points.lastOrNull { !it.instant.isAfter(now) } ?: points.first()
        val localHour = currentPoint.instant.atZone(zone).hour

        val hourly =
            points
                .filter {
                    !it.instant.isBefore(currentPoint.instant) &&
                        it.instant.isBefore(currentPoint.instant.plusSeconds(HOURLY_WINDOW_HOURS * 3600L))
                }.map {
                    HourlyWeather(
                        time = formatLocal(it.instant, zone),
                        temperatureC = it.temperatureC,
                        humidityPercent = it.humidityPercent,
                        weatherCode = it.weatherCode,
                        precipitationMm = it.precipitationRateMmPerHour,
                        precipProbability = it.precipProbability,
                        windSpeedKmh = it.windKmh,
                    )
                }

        val daily =
            points
                .groupBy { it.instant.atZone(zone).toLocalDate() }
                .entries
                .sortedBy { it.key }
                .take(DAILY_WINDOW_DAYS)
                .map { (date, dayPoints) ->
                    DailyWeather(
                        date = date.toString(),
                        weatherCode = dayPoints.mapNotNull { it.weatherCode }.maxByOrNull { severityOf(it) },
                        tempMaxC = dayPoints.mapNotNull { it.temperatureC }.maxOrNull(),
                        tempMinC = dayPoints.mapNotNull { it.temperatureC }.minOrNull(),
                        precipSumMm = dayPoints.mapNotNull { it.precipitationAmountMm }.takeIf { it.isNotEmpty() }?.sum(),
                        precipProbabilityMax = dayPoints.mapNotNull { it.precipProbability }.maxOrNull(),
                        windMaxKmh = dayPoints.mapNotNull { it.windKmh }.maxOrNull(),
                    )
                }

        return WeatherSnapshot(
            city = city,
            current =
                CurrentWeather(
                    time = formatLocal(currentPoint.instant, zone),
                    temperatureC = currentPoint.temperatureC,
                    humidityPercent = currentPoint.humidityPercent,
                    weatherCode = currentPoint.weatherCode,
                    windSpeedKmh = currentPoint.windKmh,
                    windDirectionDeg = currentPoint.windDirectionDeg,
                    precipitationMm = currentPoint.precipitationRateMmPerHour,
                    precipitationIntervalSeconds = 3600,
                    precipitationRateMmPerHour = currentPoint.precipitationRateMmPerHour,
                    cloudCoverPercent = currentPoint.cloudCoverPercent,
                    pressureHpa = currentPoint.pressureHpa,
                    isDay = localHour in DAY_START_HOUR..DAY_END_HOUR,
                ),
            hourly = hourly,
            daily = daily,
            airQuality = null,
            alerts = emptyList(),
            timezone = zone.id,
        )
    }

    private data class MetPoint(
        val instant: Instant,
        val temperatureC: Double?,
        val humidityPercent: Int?,
        val windKmh: Double?,
        val windDirectionDeg: Int?,
        val cloudCoverPercent: Int?,
        val pressureHpa: Double?,
        val weatherCode: Int?,
        val precipitationAmountMm: Double?,
        val precipitationRateMmPerHour: Double?,
        val precipProbability: Int?,
    )

    companion object {
        const val DEFAULT_BASE_URL = "https://api.met.no"

        /**
         * MET Norway 要求 User-Agent 能识别应用并附联系方式，否则可能被限流/拒绝。
         * https://api.met.no/doc/TermsOfService
         */
        const val MET_USER_AGENT =
            "XiaoxiongWeather (Android; +https://github.com/zhangruyangm-creator/xiaoxiong-weather)"

        internal const val MPS_TO_KMH = 3.6
        private const val HOURLY_WINDOW_HOURS = 28
        private const val DAILY_WINDOW_DAYS = 7
        private const val DAY_START_HOUR = 6
        private const val DAY_END_HOUR = 18
    }
}

private val metLocalFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm")

internal fun formatLocal(
    instant: Instant,
    zone: ZoneId,
): String = instant.atZone(zone).toLocalDateTime().format(metLocalFormatter)

/**
 * 按经度近似时区（每 15° 一小时）。降级路径拿不到城市 IANA 时区，
 * 近似值足以保证小时序列与 [WeatherSnapshot.timezone] 自洽；夏令时地区会有偏差。
 */
internal fun approximateZone(longitude: Double): ZoneId {
    val offsetHours = (longitude / 15.0).roundToInt().coerceIn(-14, 14)
    return ZoneId.ofOffset("UTC", ZoneOffset.ofHours(offsetHours))
}

/**
 * 城市时区解析优先级：
 * 1. [City.timezone]（Open-Meteo 地理编码返回的 IANA 时区）；
 * 2. 中国城市固定 Asia/Shanghai——中国横跨五个地理时区但统一使用北京时间，
 *    按经度近似会让西部（如乌鲁木齐）偏 2 小时；
 * 3. 其余按经度近似（每 15° 一小时）。
 */
internal fun resolveZone(city: City): ZoneId {
    city.timezone
        ?.trim()
        ?.takeIf { it.isNotEmpty() }
        ?.let { id -> runCatching { ZoneId.of(id) }.getOrNull() }
        ?.let { return it }
    if (isChinaCity(city)) return CHINA_ZONE
    return approximateZone(city.longitude)
}

/** 按国家名判断是否中国城市（地理编码 `language=zh` 返回「中国」，兼容英文/代码写法）。 */
internal fun isChinaCity(city: City): Boolean {
    val country = city.country?.trim().orEmpty()
    return country.equals("中国", ignoreCase = true) ||
        country.equals("China", ignoreCase = true) ||
        country.equals("CN", ignoreCase = true) ||
        country.equals("CHN", ignoreCase = true)
}

private val CHINA_ZONE: ZoneId = ZoneId.of("Asia/Shanghai")

/**
 * MET Norway symbol_code → WMO 天气码。先去掉 `_day/_night/_polartwilight` 后缀，
 * 未知代码返回 null（展示层按无数据/阴天处理）。
 */
internal fun weatherCodeForSymbol(symbol: String?): Int? {
    val base = symbol?.substringBefore('_') ?: return null
    return when (base) {
        "clearsky" -> 0
        "fair" -> 1
        "partlycloudy" -> 2
        "cloudy" -> 3
        "fog" -> 45
        "lightrain" -> 61
        "rain" -> 63
        "heavyrain" -> 65
        "lightrainshowers" -> 80
        "rainshowers" -> 81
        "heavyrainshowers" -> 82
        "lightsleet", "sleet", "lightsleetshowers", "sleetshowers" -> 68
        "heavysleet", "heavysleetshowers" -> 69
        "lightrainandthunder", "rainandthunder", "heavyrainandthunder",
        "lightrainshowersandthunder", "rainshowersandthunder", "heavyrainshowersandthunder",
        "sleetandthunder", "sleetshowersandthunder", "heavysleetshowersandthunder",
        "lightsleetandthunder", "heavysleetandthunder",
        // MET 实际返回值：官方文档标注 misspelled 的 lights sleetshowers/ lightssnowshowers，
        // 同时兼容正确拼写变体
        "lightssleetshowersandthunder", "lightsleetshowersandthunder",
        "lightsnowandthunder", "snowandthunder", "heavysnowandthunder",
        "snowshowersandthunder", "heavysnowshowersandthunder",
        "lightssnowshowersandthunder", "lightsnowshowersandthunder",
        -> 95
        "lightsnow" -> 71
        "snow" -> 73
        "heavysnow" -> 75
        "lightsnowshowers", "snowshowers" -> 85
        "heavysnowshowers" -> 86
        else -> null
    }
}

internal fun severityOf(weatherCode: Int): Int =
    when (weatherCode) {
        in 95..99 -> 10
        67, 65, 82 -> 9
        63, 81 -> 8
        56, 57, 66, 61, 80 -> 7
        75 -> 6
        73 -> 5
        71, 85, 86 -> 4
        45, 48 -> 3
        51, 53, 55 -> 2
        1, 2, 3 -> 1
        else -> 0
    }

private fun symbolOf(entry: MetNorwayTimeseriesEntry): String? =
    entry.data
        ?.next1Hours
        ?.summary
        ?.symbolCode
        ?: entry.data
            ?.next6Hours
            ?.summary
            ?.symbolCode
        ?: entry.data
            ?.next12Hours
            ?.summary
            ?.symbolCode

private fun precipitationAmountForPeriod(entry: MetNorwayTimeseriesEntry): Double? =
    entry.data
        ?.next1Hours
        ?.details
        ?.precipitationAmount
        ?: entry.data
            ?.next6Hours
            ?.details
            ?.precipitationAmount
        ?: entry.data
            ?.next12Hours
            ?.details
            ?.precipitationAmount

private fun precipitationForHour(entry: MetNorwayTimeseriesEntry): Double? =
    entry.data
        ?.next1Hours
        ?.details
        ?.precipitationAmount
        ?: entry.data
            ?.next6Hours
            ?.details
            ?.precipitationAmount
            ?.div(6.0)
        ?: entry.data
            ?.next12Hours
            ?.details
            ?.precipitationAmount
            ?.div(12.0)

private fun probabilityForHour(entry: MetNorwayTimeseriesEntry): Double? =
    entry.data
        ?.next1Hours
        ?.details
        ?.probabilityOfPrecipitation
        ?: entry.data
            ?.next6Hours
            ?.details
            ?.probabilityOfPrecipitation
        // 长步长序列只有 next_12_hours：概率是时段值（百分比），不随时长折算
        ?: entry.data
            ?.next12Hours
            ?.details
            ?.probabilityOfPrecipitation

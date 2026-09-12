package com.xiaoxiong.weather.data

import android.content.res.Resources
import com.xiaoxiong.weather.R
import com.xiaoxiong.weather.data.remote.AirQualityResponse
import com.xiaoxiong.weather.data.remote.ForecastResponse
import com.xiaoxiong.weather.data.remote.OpenMeteoHttp
import com.xiaoxiong.weather.util.WeatherCodes
import com.xiaoxiong.weather.util.indexOfCurrentHour
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient

/** 天气与空气质量：Open-Meteo forecast / air-quality，并生成预警/建议/生活指数。 */
class ForecastFetcher(
    private val resources: Resources,
    private val client: OkHttpClient,
    private val json: Json,
    private val forecastBase: String,
    private val airQualityBase: String,
    /**
     * 主站的备用可达入口。主 forecast host 网络不可达时按序重试，
     * 缓解区域性 BGP 路由问题（见 OpenMeteoHttp 文档）。
     */
    private val forecastBaseFallbacks: List<String> = DEFAULT_FORECAST_FALLBACKS,
    /**
     * MET Norway 降级数据源 base URL（免密钥）。null = 关闭降级；
     * 仅在 Open-Meteo 主站（含备用入口）全失败时启用，见 [fetchForecastWithFallback]。
     */
    metNorwayBase: String? = DEFAULT_MET_NORWAY_BASE,
) {
    private val metNorway: MetNorwayFetcher? =
        metNorwayBase?.let { MetNorwayFetcher(client, json, it) }

    suspend fun fetchWeather(city: City): WeatherSnapshot =
        withContext(Dispatchers.IO) {
            coroutineScope {
                val forecastDeferred = async { fetchForecastWithFallback(city) }
                // 空气质量是辅助数据，失败不应阻塞主天气加载；显式透传 CancellationException
                // 避免 runCatching 吞掉取消信号导致挂后台后协程变"僵尸"继续跑。
                // 降级数据源不提供空气质量，此时 airQuality 为 null，展示层按无数据处理
                val airDeferred =
                    async {
                        try {
                            fetchAirQuality(city)
                        } catch (e: kotlinx.coroutines.CancellationException) {
                            throw e
                        } catch (e: Exception) {
                            null
                        }
                    }

                val forecast = forecastDeferred.await()
                val airQuality = airDeferred.await()
                val alerts =
                    WeatherAlertGenerator.generate(
                        resources = resources,
                        current = forecast.current,
                        hourly = forecast.hourly,
                        daily = forecast.daily,
                        airQuality = airQuality,
                    )
                val advice =
                    WeatherAdviceGenerator.generate(
                        resources = resources,
                        current = forecast.current,
                        hourly = forecast.hourly,
                        daily = forecast.daily,
                        airQuality = airQuality,
                    )
                val lifeIndices =
                    LifeIndexGenerator.generate(
                        resources = resources,
                        current = forecast.current,
                        hourly = forecast.hourly,
                        daily = forecast.daily,
                        airQuality = airQuality,
                    )

                forecast.copy(
                    airQuality = airQuality,
                    alerts = alerts,
                    advice = advice,
                    lifeIndices = lifeIndices,
                )
            }
        }

    /**
     * 主预报失败时先按 [forecastBaseFallbacks] 换 base URL（同格式镜像），
     * 仍失败且错误属于「服务端/网络/数据格式」范畴时，再切 MET Norway 完全不同的数据源。
     * 4xx（请求本身问题）不降级；降级源也失败时抛出主源错误，保持错误分类不变。
     */
    private suspend fun fetchForecastWithFallback(city: City): WeatherSnapshot =
        try {
            fetchForecast(city)
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            val fallback = metNorway?.takeIf { isMetNorwayFallbackWorthy(e) } ?: throw e
            try {
                fallback.fetchWeather(city)
            } catch (e2: kotlinx.coroutines.CancellationException) {
                throw e2
            } catch (e2: Exception) {
                throw e
            }
        }

    private fun isMetNorwayFallbackWorthy(e: Exception): Boolean =
        when (e) {
            is AppError.Http -> e.code in 500..599
            is AppError.NetworkUnavailable -> true
            is AppError.DataFormat -> true
            else -> e is java.io.IOException
        }

    private suspend fun fetchForecast(city: City): WeatherSnapshot {
        val url =
            forecastBase
                .toHttpUrl()
                .newBuilder()
                .addPathSegment("forecast")
                .addQueryParameter("latitude", city.latitude.toString())
                .addQueryParameter("longitude", city.longitude.toString())
                .addQueryParameter(
                    "current",
                    listOf(
                        "temperature_2m",
                        "relative_humidity_2m",
                        "apparent_temperature",
                        "weather_code",
                        "wind_speed_10m",
                        "wind_gusts_10m",
                        "wind_direction_10m",
                        "visibility",
                        "precipitation",
                        "cloud_cover",
                        "pressure_msl",
                        "dew_point_2m",
                        "is_day",
                    ).joinToString(","),
                ).addQueryParameter(
                    "hourly",
                    listOf(
                        "temperature_2m",
                        "relative_humidity_2m",
                        "visibility",
                        "weather_code",
                        "precipitation",
                        "precipitation_probability",
                        "wind_speed_10m",
                    ).joinToString(","),
                ).addQueryParameter(
                    "daily",
                    listOf(
                        "weather_code",
                        "temperature_2m_max",
                        "temperature_2m_min",
                        "precipitation_sum",
                        "precipitation_probability_max",
                        "sunrise",
                        "sunset",
                        "uv_index_max",
                        "wind_speed_10m_max",
                    ).joinToString(","),
                ).addQueryParameter("past_hours", "24")
                // 未来 28 小时：强降雨/降雪预警需看未来 24h 整点（upcoming.drop(1).take(24)），
                // 18 小时会让「未来 24h」窗口实际只有 18 小时，超窗强的降雨漏报
                .addQueryParameter("forecast_hours", "28")
                .addQueryParameter("forecast_days", "7")
                .addQueryParameter("wind_speed_unit", "kmh")
                .addQueryParameter("timezone", "auto")
                .build()

        val body =
            if (forecastBaseFallbacks.isEmpty()) {
                OpenMeteoHttp.execute(url.toString(), client)
            } else {
                // 主 URL 与 fallback 入口（同参数）依序尝试，首个成功即用。
                // fallback base 自身可能带路径前缀（如自建代理 /xiaoxiong），需保留后再拼接请求路径
                val fallbackUrls =
                    forecastBaseFallbacks.mapNotNull { base ->
                        runCatching {
                            val parsed = base.toHttpUrl()
                            val prefix = parsed.encodedPath.trimEnd('/')
                            parsed
                                .newBuilder()
                                .encodedPath(prefix + url.encodedPath)
                                .query(url.encodedQuery)
                                .build()
                                .toString()
                        }.getOrNull()
                    }
                OpenMeteoHttp.executeWithFallback(listOf(url.toString()) + fallbackUrls, client)
            }
        val response = decodeForecast(body)
        val currentDto =
            response.current
                ?: throw AppError.DataFormat(
                    IllegalStateException(resources.getString(R.string.error_forecast_empty)),
                )
        val hourlyDto = response.hourly
        val dailyDto = response.daily

        val hourly =
            hourlyDto?.time.orEmpty().mapIndexed { index, time ->
                HourlyWeather(
                    time = time,
                    temperatureC = hourlyDto?.temperature?.getOrNull(index),
                    humidityPercent = hourlyDto?.humidity?.getOrNull(index),
                    visibilityMeters = hourlyDto?.visibility?.getOrNull(index),
                    weatherCode = WeatherCodes.normalizeCode(hourlyDto?.weatherCode?.getOrNull(index)),
                    precipitationMm = hourlyDto?.precipitation?.getOrNull(index),
                    precipProbability = hourlyDto?.precipProbability?.getOrNull(index),
                    windSpeedKmh = hourlyDto?.windSpeed?.getOrNull(index),
                )
            }

        val daily =
            dailyDto?.time.orEmpty().mapIndexed { index, date ->
                DailyWeather(
                    date = date,
                    weatherCode = WeatherCodes.normalizeCode(dailyDto?.weatherCode?.getOrNull(index)),
                    tempMaxC = dailyDto?.tempMax?.getOrNull(index),
                    tempMinC = dailyDto?.tempMin?.getOrNull(index),
                    precipSumMm = dailyDto?.precipSum?.getOrNull(index),
                    precipProbabilityMax = dailyDto?.precipProbabilityMax?.getOrNull(index),
                    sunrise = dailyDto?.sunrise?.getOrNull(index),
                    sunset = dailyDto?.sunset?.getOrNull(index),
                    uvIndexMax = dailyDto?.uvIndexMax?.getOrNull(index),
                    windMaxKmh = dailyDto?.windMax?.getOrNull(index),
                )
            }

        val currentHourIndex = indexOfCurrentHour(hourly.map { it.time }, currentDto.time)
        // 与小时图同一标准：当前整点小时桶（该钟点合计 mm）
        val precipRateMmPerHour = hourly.getOrNull(currentHourIndex)?.precipitationMm

        return WeatherSnapshot(
            city = city,
            current =
                CurrentWeather(
                    time = currentDto.time,
                    temperatureC = currentDto.temperature,
                    apparentTemperatureC = currentDto.apparentTemperature,
                    humidityPercent = currentDto.humidity,
                    weatherCode = WeatherCodes.normalizeCode(currentDto.weatherCode),
                    windSpeedKmh = currentDto.windSpeed,
                    windGustsKmh = currentDto.windGusts,
                    windDirectionDeg = currentDto.windDirection,
                    visibilityMeters = currentDto.visibility,
                    precipitationMm = currentDto.precipitation,
                    precipitationIntervalSeconds = currentDto.interval,
                    precipitationRateMmPerHour = precipRateMmPerHour,
                    cloudCoverPercent = currentDto.cloudCover,
                    pressureHpa = currentDto.pressure,
                    dewPointC = currentDto.dewPoint,
                    isDay = currentDto.isDay?.let { it == 1 },
                ),
            hourly = hourly,
            daily = daily,
            airQuality = null,
            alerts = emptyList(),
            elevationMeters = response.elevation,
            timezone = response.timezone,
        )
    }

    private suspend fun fetchAirQuality(city: City): AirQuality {
        val url =
            airQualityBase
                .toHttpUrl()
                .newBuilder()
                .addPathSegment("air-quality")
                .addQueryParameter("latitude", city.latitude.toString())
                .addQueryParameter("longitude", city.longitude.toString())
                .addQueryParameter(
                    "current",
                    listOf(
                        "european_aqi",
                        "us_aqi",
                        "pm2_5",
                        "pm10",
                        "dust",
                        "uv_index",
                        "nitrogen_dioxide",
                        "ozone",
                    ).joinToString(","),
                ).addQueryParameter(
                    "hourly",
                    listOf("european_aqi", "us_aqi", "pm2_5", "pm10", "dust", "uv_index")
                        .joinToString(","),
                ).addQueryParameter("timezone", "auto")
                .build()

        val response =
            json.decodeFromString(
                AirQualityResponse.serializer(),
                OpenMeteoHttp.execute(url.toString(), client),
            )

        val current = response.current
        // current 有有效指标时优先用；否则按城市时区取最近整点小时
        if (current != null &&
            (
                current.europeanAqi != null ||
                    current.usAqi != null ||
                    current.pm25 != null ||
                    current.dust != null
            )
        ) {
            return AirQuality(
                europeanAqi = current.europeanAqi,
                usAqi = current.usAqi,
                pm25 = current.pm25,
                pm10 = current.pm10,
                dustUgM3 = current.dust,
                uvIndex = current.uvIndex,
                nitrogenDioxide = current.nitrogenDioxide,
                ozone = current.ozone,
            )
        }

        val hourly = response.hourly
        val index =
            nearestHourlyIndex(
                times = hourly?.time.orEmpty(),
                timezoneId = response.timezone,
                // 若有 current.time，优先按该城市本地时刻对齐
                preferredTime = current?.time,
            )
        return AirQuality(
            europeanAqi = hourly?.europeanAqi?.getOrNull(index),
            usAqi = hourly?.usAqi?.getOrNull(index),
            pm25 = hourly?.pm25?.getOrNull(index),
            pm10 = hourly?.pm10?.getOrNull(index),
            dustUgM3 = hourly?.dust?.getOrNull(index),
            uvIndex = hourly?.uvIndex?.getOrNull(index),
            nitrogenDioxide = null,
            ozone = null,
        )
    }

    /**
     * 物候积温偏移天数（正 = 推迟，负 = 提前），失败或数据不足返回 null。
     *
     * 仅在进入「七日」页时按需调用（跨年/换城/当日未拉过才真正联网，
     * 见 WeatherViewModel.ensurePhenologyShift），不参与主天气加载。
     *
     * 用 Open-Meteo Archive API 拉取「今年至今」与「往年同期」逐日均温：
     * 今年与常年按积温（GDD）对比，把温差换算为物候偏移天数。
     */

    private fun nearestHourlyIndex(
        times: List<String>,
        timezoneId: String?,
        preferredTime: String? = null,
    ): Int {
        if (times.isEmpty()) return 0
        if (!preferredTime.isNullOrBlank()) {
            return com.xiaoxiong.weather.util
                .indexOfCurrentHour(times, preferredTime)
                .coerceAtLeast(0)
        }
        val zone =
            runCatching {
                if (!timezoneId.isNullOrBlank()) {
                    java.time.ZoneId.of(timezoneId)
                } else {
                    java.time.ZoneId.systemDefault()
                }
            }.getOrDefault(java.time.ZoneId.systemDefault())
        val nowKey =
            java.time.LocalDateTime
                .now(zone)
                .format(
                    java.time.format.DateTimeFormatter
                        .ofPattern("yyyy-MM-dd'T'HH"),
                )
        return com.xiaoxiong.weather.util
            .indexOfCurrentHour(times, nowKey)
            .coerceAtLeast(0)
    }

    private fun decodeForecast(body: String): ForecastResponse =
        try {
            json.decodeFromString(ForecastResponse.serializer(), body)
        } catch (e: SerializationException) {
            throw AppError.DataFormat(e)
        }

    companion object {
        /**
         * 主预报站的备用入口。当前 Open-Meteo 免费 API 无官方镜像，保持为空；
         * 将来接入可验证的第二入口（如自建代理 / 其它兼容源）后在此配置，
         * ForecastFetcher 会自动在主站网络不可达时按序重试。
         */
        val DEFAULT_FORECAST_FALLBACKS: List<String> = emptyList()

        /** MET Norway Locationforecast：免密钥、全球覆盖，作为主源故障时的降级数据源。 */
        const val DEFAULT_MET_NORWAY_BASE = MetNorwayFetcher.DEFAULT_BASE_URL
    }
}

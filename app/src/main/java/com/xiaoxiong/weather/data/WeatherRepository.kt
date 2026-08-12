package com.xiaoxiong.weather.data

import android.content.res.Resources
import com.github.promeg.pinyinhelper.Pinyin
import com.xiaoxiong.weather.BuildConfig
import com.xiaoxiong.weather.R
import com.xiaoxiong.weather.data.remote.AirQualityResponse
import com.xiaoxiong.weather.data.remote.ArchiveResponse
import com.xiaoxiong.weather.data.remote.ForecastResponse
import com.xiaoxiong.weather.data.remote.GeocodingResponse
import com.xiaoxiong.weather.util.indexOfCurrentHour
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

class WeatherRepository(
    private val resources: Resources,
    private val client: OkHttpClient = defaultClient(),
    private val searchClient: OkHttpClient = defaultSearchClient(),
    private val json: Json = defaultJson(),
    private val geocodingBase: String = DEFAULT_GEOCODING_BASE,
    private val forecastBase: String = DEFAULT_FORECAST_BASE,
    private val airQualityBase: String = DEFAULT_AIR_QUALITY_BASE,
    private val archiveBase: String = DEFAULT_ARCHIVE_BASE,
) : WeatherFetcher {
    suspend fun searchCities(query: String): List<City> =
        withContext(Dispatchers.IO) {
            val trimmed = query.trim()
            if (trimmed.length < 2) return@withContext emptyList()

            val normalized = normalizePlaceQuery(trimmed)
            val pinyinQuery = if (containsHan(normalized)) toPinyinQuery(normalized) else ""
            // Open-Meteo 对「纽约」等中文世界城市常搜不到，用英文别名补查
            val englishAlias = ForeignCityAliases.get(resources)[normalized]
            coroutineScope {
                // 主查询错误应抛出（让 UI 显示网络异常）；拼音/英文别名是补充，失败静默
                val primaryDeferred = async { searchOpenMeteo(normalized) }
                val pinyinDeferred =
                    async {
                        if (pinyinQuery.length >= 2) {
                            runCatching { searchOpenMeteo(pinyinQuery) }.getOrDefault(emptyList())
                        } else {
                            emptyList()
                        }
                    }
                val englishDeferred =
                    async {
                        if (!englishAlias.isNullOrBlank()) {
                            runCatching { searchOpenMeteo(englishAlias) }.getOrDefault(emptyList())
                        } else {
                            emptyList()
                        }
                    }
                val primary = mergeCityResults(primaryDeferred.await(), pinyinDeferred.await())
                val english = englishDeferred.await()
                // 有中文→英文别名时优先英文结果：避免「高雄/花莲」等命中大陆同名点
                if (english.isNotEmpty() && !englishAlias.isNullOrBlank()) {
                    mergeCityResults(english, primary)
                } else {
                    mergeCityResults(primary, english)
                }
            }
        }

    private fun searchOpenMeteo(name: String): List<City> {
        val url =
            geocodingBase
                .toHttpUrl()
                .newBuilder()
                .addPathSegment("search")
                .addQueryParameter("name", name)
                .addQueryParameter("count", "6")
                .addQueryParameter("language", "zh")
                .addQueryParameter("format", "json")
                .build()

        val response = decodeGeocoding(execute(url.toString(), timeoutClient = searchClient))

        return response.results.orEmpty().map { result ->
            City(
                id = result.id,
                name = result.name,
                latitude = result.latitude,
                longitude = result.longitude,
                country = result.country,
                admin1 = result.admin1,
                admin2 = result.admin2,
            )
        }
    }

    companion object {
        private const val DEFAULT_GEOCODING_BASE = "https://geocoding-api.open-meteo.com/v1"
        private const val DEFAULT_FORECAST_BASE = "https://api.open-meteo.com/v1"
        private const val DEFAULT_AIR_QUALITY_BASE = "https://air-quality-api.open-meteo.com/v1"
        private const val DEFAULT_ARCHIVE_BASE = "https://archive-api.open-meteo.com/v1"

        /** 常年对比窗口年数（含今年共 N 年），N-1 年用于多年平均。 */
        private const val PHENOLOGY_YEARS = 6

        private val PLACE_SUFFIX =
            Regex("(特别行政区|自治州|自治区|自治县|地区|盟|市|县|区|旗|林区)$")

        fun normalizePlaceQuery(query: String): String {
            var current = query.trim()
            repeat(2) {
                val stripped = PLACE_SUFFIX.replace(current, "")
                if (stripped.length < 2) return current
                current = stripped
            }
            return current
        }

        fun containsHan(text: String): Boolean = text.any { Character.UnicodeScript.of(it.code) == Character.UnicodeScript.HAN }

        fun toPinyinQuery(text: String): String = Pinyin.toPinyin(text, "").lowercase().filter { it.isLetter() }

        fun nearSamePlace(
            a: City,
            b: City,
        ): Boolean =
            kotlin.math.abs(a.latitude - b.latitude) < 0.08 &&
                kotlin.math.abs(a.longitude - b.longitude) < 0.08

        fun mergeCityResults(
            preferred: List<City>,
            extras: List<City>,
        ): List<City> {
            val merged = preferred.toMutableList()
            extras.forEach { candidate ->
                if (merged.none { nearSamePlace(it, candidate) }) {
                    merged += candidate
                }
            }
            return merged.take(12)
        }

        fun rankCityResults(
            query: String,
            cities: List<City>,
        ): List<City> =
            cities
                .map { ChinaCityCatalog.rewriteDisplayName(it, query) }
                .distinctBy {
                    "${"%.2f".format(java.util.Locale.US, it.latitude)}," +
                        "%.2f".format(java.util.Locale.US, it.longitude)
                }.sortedByDescending { ChinaCityCatalog.relevanceScore(it, query) }
                .take(12)

        fun createDefaultClient(): OkHttpClient =
            OkHttpClient
                .Builder()
                .connectTimeout(20, TimeUnit.SECONDS)
                .readTimeout(20, TimeUnit.SECONDS)
                .build()

        fun createSearchClient(): OkHttpClient =
            OkHttpClient
                .Builder()
                .connectTimeout(5, TimeUnit.SECONDS)
                .readTimeout(5, TimeUnit.SECONDS)
                .callTimeout(6, TimeUnit.SECONDS)
                .build()

        fun createJson(): Json =
            Json {
                ignoreUnknownKeys = true
                isLenient = true
            }

        private fun defaultClient(): OkHttpClient = createDefaultClient()

        private fun defaultSearchClient(): OkHttpClient = createSearchClient()

        private fun defaultJson(): Json = createJson()
    }

    override suspend fun fetchWeather(city: City): WeatherSnapshot =
        withContext(Dispatchers.IO) {
            coroutineScope {
                val forecastDeferred = async { fetchForecast(city) }
                val airDeferred = async { runCatching { fetchAirQuality(city) }.getOrNull() }

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

    private fun fetchForecast(city: City): WeatherSnapshot {
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
                .addQueryParameter("forecast_hours", "18")
                .addQueryParameter("forecast_days", "7")
                .addQueryParameter("wind_speed_unit", "kmh")
                .addQueryParameter("timezone", "auto")
                .build()

        val response = decodeForecast(execute(url.toString()))
        val currentDto =
            response.current
                ?: error(resources.getString(R.string.error_forecast_empty))
        val hourlyDto = response.hourly
        val dailyDto = response.daily

        val hourly =
            hourlyDto?.time.orEmpty().mapIndexed { index, time ->
                HourlyWeather(
                    time = time,
                    temperatureC = hourlyDto?.temperature?.getOrNull(index),
                    humidityPercent = hourlyDto?.humidity?.getOrNull(index),
                    visibilityMeters = hourlyDto?.visibility?.getOrNull(index),
                    weatherCode = hourlyDto?.weatherCode?.getOrNull(index),
                    precipitationMm = hourlyDto?.precipitation?.getOrNull(index),
                    precipProbability = hourlyDto?.precipProbability?.getOrNull(index),
                    windSpeedKmh = hourlyDto?.windSpeed?.getOrNull(index),
                )
            }

        val daily =
            dailyDto?.time.orEmpty().mapIndexed { index, date ->
                DailyWeather(
                    date = date,
                    weatherCode = dailyDto?.weatherCode?.getOrNull(index),
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
                    weatherCode = currentDto.weatherCode,
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

    private fun fetchAirQuality(city: City): AirQuality {
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
                execute(url.toString()),
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
    override suspend fun fetchPhenologyShiftDays(
        city: City,
        today: java.time.LocalDate,
    ): Int? =
        withContext(Dispatchers.IO) {
            val startYear = today.year - PHENOLOGY_YEARS
            // ERA5 再分析数据延迟约 1 天，end_date 用昨天，否则 archive 接口报
            // "end_date out of allowed range" 直接失败（物候偏移一天延迟无感）。
            val endDate = today.minusDays(1)
            val url =
                archiveBase
                    .toHttpUrl()
                    .newBuilder()
                    .addPathSegment("archive")
                    .addQueryParameter("latitude", city.latitude.toString())
                    .addQueryParameter("longitude", city.longitude.toString())
                    .addQueryParameter("start_date", "$startYear-01-01")
                    .addQueryParameter("end_date", endDate.toString())
                    .addQueryParameter(
                        "daily",
                        "temperature_2m_mean",
                    ).addQueryParameter("timezone", "auto")
                    .build()

            val response =
                try {
                    decodeArchive(execute(url.toString()))
                } catch (e: kotlinx.coroutines.CancellationException) {
                    throw e
                } catch (e: Exception) {
                    return@withContext null
                }
            val daily = response.daily ?: return@withContext null

            val records =
                daily.time.mapIndexedNotNull { index, time ->
                    val date = runCatching { java.time.LocalDate.parse(time) }.getOrNull()
                    val mean = daily.temperatureMean.getOrNull(index)
                    if (date == null || mean == null) null else date to mean
                }

            val thisYear = today.year
            val thisYearTemps =
                records.filter { it.first.year == thisYear }.map { it.second }
            if (thisYearTemps.size < GrowingDegreeDays.MIN_DAYS) return@withContext null

            val normalByDay = GrowingDegreeDays.averageByDayOfYear(records.filter { it.first.year < thisYear })
            val normalTemps =
                GrowingDegreeDays.normalTempsUntil(normalByDay, today, java.time.LocalDate.of(thisYear, 1, 1))
            if (normalTemps.size < GrowingDegreeDays.MIN_DAYS) return@withContext null

            GrowingDegreeDays.shiftDays(thisYearTemps, normalTemps)
        }

    /**
     * 查询某天的历史天气（Archive / ERA5），失败或无数据返回 null。
     *
     * 用户主动查询单日，不参与主天气加载；date 通常为昨天或更早
     * （ERA5 延迟约 1 天）。单日请求极小，ViewModel 层再按内存缓存去重。
     */
    override suspend fun fetchHistoryDay(
        city: City,
        date: java.time.LocalDate,
    ): HistoryWeather? =
        withContext(Dispatchers.IO) {
            val url =
                archiveBase
                    .toHttpUrl()
                    .newBuilder()
                    .addPathSegment("archive")
                    .addQueryParameter("latitude", city.latitude.toString())
                    .addQueryParameter("longitude", city.longitude.toString())
                    .addQueryParameter("start_date", date.toString())
                    .addQueryParameter("end_date", date.toString())
                    .addQueryParameter(
                        "daily",
                        listOf(
                            "weather_code",
                            "temperature_2m_max",
                            "temperature_2m_min",
                            "temperature_2m_mean",
                            "precipitation_sum",
                            "relative_humidity_2m_mean",
                            "wind_speed_10m_max",
                            "sunrise",
                            "sunset",
                        ).joinToString(","),
                    ).addQueryParameter("timezone", "auto")
                    .build()

            val response =
                try {
                    decodeArchive(execute(url.toString()))
                } catch (e: kotlinx.coroutines.CancellationException) {
                    throw e
                } catch (e: Exception) {
                    return@withContext null
                }
            val daily = response.daily ?: return@withContext null
            val day = daily.time.firstOrNull() ?: return@withContext null
            val parsed = runCatching { java.time.LocalDate.parse(day) }.getOrNull() ?: return@withContext null
            if (parsed != date) return@withContext null

            HistoryWeather(
                date = day,
                weatherCode = daily.weatherCode.getOrNull(0),
                tempMaxC = daily.tempMax.getOrNull(0),
                tempMinC = daily.tempMin.getOrNull(0),
                tempMeanC = daily.temperatureMean.getOrNull(0),
                precipSumMm = daily.precipSum.getOrNull(0),
                humidityMeanPercent = daily.humidityMean.getOrNull(0),
                windMaxKmh = daily.windMax.getOrNull(0),
                sunrise = daily.sunrise.getOrNull(0),
                sunset = daily.sunset.getOrNull(0),
            )
        }

    /**
     * 查询一段日期范围的历史天气（Archive / ERA5）并本地聚合三个极值：
     * 最高温（temperature_2m_max 最大）、最低温（temperature_2m_min 最小）、
     * 最多降水（precipitation_sum 最大）。逐日列表也一并返回供 UI 展示。
     */
    override suspend fun fetchHistoryRange(
        city: City,
        start: java.time.LocalDate,
        end: java.time.LocalDate,
    ): HistoryRangeSummary? {
        if (end.isBefore(start)) return null
        return withContext(Dispatchers.IO) {
            val url =
                archiveBase
                    .toHttpUrl()
                    .newBuilder()
                    .addPathSegment("archive")
                    .addQueryParameter("latitude", city.latitude.toString())
                    .addQueryParameter("longitude", city.longitude.toString())
                    .addQueryParameter("start_date", start.toString())
                    .addQueryParameter("end_date", end.toString())
                    .addQueryParameter(
                        "daily",
                        listOf(
                            "weather_code",
                            "temperature_2m_max",
                            "temperature_2m_min",
                            "temperature_2m_mean",
                            "precipitation_sum",
                            "relative_humidity_2m_mean",
                            "wind_speed_10m_max",
                            "sunrise",
                            "sunset",
                        ).joinToString(","),
                    ).addQueryParameter("timezone", "auto")
                    .build()

            val daily =
                try {
                    decodeArchive(execute(url.toString())).daily
                } catch (e: kotlinx.coroutines.CancellationException) {
                    throw e
                } catch (e: Exception) {
                    null
                } ?: return@withContext null

            val days =
                daily.time.mapIndexedNotNull { index, day ->
                    val parsed = runCatching { java.time.LocalDate.parse(day) }.getOrNull() ?: return@mapIndexedNotNull null
                    if (parsed < start || parsed > end) return@mapIndexedNotNull null
                    HistoryWeather(
                        date = day,
                        weatherCode = daily.weatherCode.getOrNull(index),
                        tempMaxC = daily.tempMax.getOrNull(index),
                        tempMinC = daily.tempMin.getOrNull(index),
                        tempMeanC = daily.temperatureMean.getOrNull(index),
                        precipSumMm = daily.precipSum.getOrNull(index),
                        humidityMeanPercent = daily.humidityMean.getOrNull(index),
                        windMaxKmh = daily.windMax.getOrNull(index),
                        sunrise = daily.sunrise.getOrNull(index),
                        sunset = daily.sunset.getOrNull(index),
                    )
                }
            if (days.isEmpty()) return@withContext null

            fun extreme(
                select: (HistoryWeather) -> Double?,
                preferMax: Boolean,
            ): HistoryExtreme? {
                val best =
                    days.fold(null as HistoryWeather?) { acc, day ->
                        val v = select(day) ?: return@fold acc
                        if (acc == null) {
                            day
                        } else {
                            val accV = select(acc) ?: return@fold acc
                            if (if (preferMax) v > accV else v < accV) day else acc
                        }
                    } ?: return null
                return HistoryExtreme(
                    value = select(best) ?: return null,
                    date = best.date,
                    weatherCode = best.weatherCode,
                )
            }

            HistoryRangeSummary(
                startDate = start.toString(),
                endDate = end.toString(),
                days = days,
                hottest = extreme({ it.tempMaxC }, preferMax = true),
                coldest = extreme({ it.tempMinC }, preferMax = false),
                wettest = extreme({ it.precipSumMm }, preferMax = true),
            )
        }
    }

    /**
     * 小时序列来自 timezone=auto，是城市本地墙钟时间；
     * 不能用设备 [ZoneId.systemDefault]，否则跨时区会偏一小时甚至整天。
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

    private fun decodeGeocoding(body: String): GeocodingResponse =
        try {
            json.decodeFromString(GeocodingResponse.serializer(), body)
        } catch (e: SerializationException) {
            throw AppError.DataFormat(e)
        }

    private fun decodeArchive(body: String): ArchiveResponse =
        try {
            json.decodeFromString(ArchiveResponse.serializer(), body)
        } catch (e: SerializationException) {
            throw AppError.DataFormat(e)
        }

    private fun execute(
        url: String,
        timeoutClient: OkHttpClient = client,
    ): String {
        val request =
            Request
                .Builder()
                .url(url)
                .header("Accept", "application/json")
                .header("User-Agent", "XiaoxiongWeather/${BuildConfig.VERSION_NAME} (Android)")
                .get()
                .build()

        try {
            timeoutClient.newCall(request).execute().use { response ->
                val body = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    throw AppError.Http(response.code)
                }
                return body
            }
        } catch (e: AppError) {
            throw e
        } catch (e: java.io.IOException) {
            throw AppError.NetworkUnavailable(e)
        }
    }
}

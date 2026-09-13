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
    /**
     * 主源近期故障标记的 TTL：命中期间跳过主源直接走备用/MET，见 [PrimarySourceHealth]。
     */
    primaryFailureTtlMs: Long = PrimarySourceHealth.DEFAULT_FAILURE_TTL_MS,
    /**
     * 当前设置温度单位（预警文案用）。默认读进程级注入的 provider；
     * 未注入时保持摄氏，保证单测与未装配场景行为不变。
     */
    private val temperatureUnitProvider: suspend () -> TemperatureUnit = { installedTemperatureUnitProvider() },
) {
    private val metNorway: MetNorwayFetcher? =
        metNorwayBase?.let { MetNorwayFetcher(client, json, it) }

    private val primaryHealth = PrimarySourceHealth(primaryFailureTtlMs)

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
                val temperatureUnit = temperatureUnitProvider()
                val alerts =
                    WeatherAlertGenerator.generate(
                        resources = resources,
                        current = forecast.current,
                        hourly = forecast.hourly,
                        daily = forecast.daily,
                        airQuality = airQuality,
                        temperatureUnit = temperatureUnit,
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
     * 仍失败且错误属于「服务端/网络/数据格式/限流」范畴时，再切 MET Norway 完全不同的数据源。
     * 其余 4xx（请求本身问题）不降级；降级源也失败时抛出主源错误，保持错误分类不变。
     *
     * 主源近期失败标记命中时跳过主源直接走 MET（避免每次先等主源超时）；
     * 若 MET 也失败再尝试一次主源，给主源恢复机会（同一轮不重复请求 MET）。
     * 主源成功会清零标记。
     */
    private suspend fun fetchForecastWithFallback(city: City): WeatherSnapshot {
        val met = metNorway
        if (met != null && primaryHealth.shouldSkipPrimary(System.currentTimeMillis())) {
            try {
                return met.fetchWeather(city)
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                // 备用源也失败：继续尝试主源一次（fallback=null，不再重复打 MET）
                return fetchFromPrimary(city, fallback = null)
            }
        }
        return fetchFromPrimary(city, fallback = met)
    }

    private suspend fun fetchFromPrimary(
        city: City,
        fallback: MetNorwayFetcher?,
    ): WeatherSnapshot =
        try {
            val snapshot = fetchForecast(city)
            primaryHealth.recordSuccess()
            snapshot
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            if (isTransientSourceFailure(e)) primaryHealth.recordFailure(System.currentTimeMillis())
            val met = fallback?.takeIf { isMetNorwayFallbackWorthy(e) } ?: throw e
            try {
                met.fetchWeather(city)
            } catch (e2: kotlinx.coroutines.CancellationException) {
                throw e2
            } catch (e2: Exception) {
                throw e
            }
        }

    private fun isMetNorwayFallbackWorthy(e: Exception): Boolean = isTransientSourceFailure(e)

    /**
     * 是否属于「换源有意义」的瞬时故障：5xx 服务端故障、429 限流、408 超时、425 过早、
     * 网络不可达、数据格式异常。其余 4xx 是请求本身问题，换源也不会成功。
     */
    private fun isTransientSourceFailure(e: Exception): Boolean =
        when (e) {
            is AppError.Http -> e.code in 500..599 || e.code == 429 || e.code == 408 || e.code == 425
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
                        // precipitation 生产不用（降水展示统一取 hourly 的整点桶），
                        // 不再请求；DTO/快照字段保留以兼容调试与旧响应
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

    /**
     * 当前空气质量：只请求 `current`（多天 `hourly` 序列生产不用，去掉可显著缩小响应体积）。
     * `current` 全部指标缺失时返回 null，展示层按无 AQ 处理。
     */
    private suspend fun fetchAirQuality(city: City): AirQuality? {
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
                ).addQueryParameter("timezone", "auto")
                .build()

        val response =
            json.decodeFromString(
                AirQualityResponse.serializer(),
                OpenMeteoHttp.execute(url.toString(), client),
            )

        val current = response.current ?: return null
        val hasAnyMetric =
            current.europeanAqi != null ||
                current.usAqi != null ||
                current.pm25 != null ||
                current.pm10 != null ||
                current.dust != null ||
                current.uvIndex != null ||
                current.nitrogenDioxide != null ||
                current.ozone != null
        if (!hasAnyMetric) return null

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

    private fun decodeForecast(body: String): ForecastResponse =
        try {
            json.decodeFromString(ForecastResponse.serializer(), body)
        } catch (e: SerializationException) {
            throw AppError.DataFormat(e)
        }

    companion object {
        /**
         * 进程级温度单位 provider：App 装配层/DebugWeatherStore 在拿到 Context 后注入设置读取，
         * 使前台快照里的预警文案跟随「设置 → 温度单位」。默认摄氏。
         */
        @Volatile
        private var installedTemperatureUnitProvider: suspend () -> TemperatureUnit = { TemperatureUnit.CELSIUS }

        fun installTemperatureUnitProvider(provider: suspend () -> TemperatureUnit) {
            installedTemperatureUnitProvider = provider
        }

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

/**
 * 主源短期故障标记（进程内、不持久化）。
 *
 * 主源出现瞬时故障（网络/超时/限流/5xx/数据格式）后，在一个短 TTL 内标记为「不健康」：
 * 期间刷新直接走备用/MET，避免每次都先等主源超时再降级；主源成功一次即清零。
 * 纯逻辑、显式传入 now，便于单测覆盖 TTL 边界与时钟回拨。
 */
internal class PrimarySourceHealth(
    private val failureTtlMs: Long = DEFAULT_FAILURE_TTL_MS,
) {
    @Volatile
    private var lastFailureAtEpochMs: Long? = null

    /** 是否处于主源熔断期（[nowEpochMs] 距上次失败不超过 TTL）。 */
    fun shouldSkipPrimary(nowEpochMs: Long): Boolean {
        val failedAt = lastFailureAtEpochMs ?: return false
        val age = nowEpochMs - failedAt
        if (age !in 0 until failureTtlMs) {
            // TTL 到期或时钟回拨：清标记，给主源恢复机会
            lastFailureAtEpochMs = null
            return false
        }
        return true
    }

    fun recordFailure(nowEpochMs: Long) {
        lastFailureAtEpochMs = nowEpochMs
    }

    fun recordSuccess() {
        lastFailureAtEpochMs = null
    }

    companion object {
        /** 默认熔断时长：5 分钟。 */
        const val DEFAULT_FAILURE_TTL_MS = 5L * 60 * 1000
    }
}

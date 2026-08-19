package com.xiaoxiong.weather.data

import android.content.Context
import android.content.res.Resources
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.xiaoxiong.weather.util.indexOfCurrentHour
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

private val Context.debugWeatherDataStore by preferencesDataStore("debug_weather_v1")

/** 调试覆盖字段；null 表示沿用实况。 */
@Serializable
data class DebugWeatherOverride(
    val temperatureC: Double? = null,
    val apparentTemperatureC: Double? = null,
    val humidityPercent: Int? = null,
    val weatherCode: Int? = null,
    val precipitationRateMmPerHour: Double? = null,
    val windSpeedKmh: Double? = null,
    val windGustsKmh: Double? = null,
    val visibilityMeters: Double? = null,
    val uvIndex: Double? = null,
    val europeanAqi: Int? = null,
    val dustUgM3: Double? = null,
    /** 未来逐日最高温覆盖（从今天起），可短于 daily；null = 不改 daily。 */
    val dailyTempMaxC: List<Double>? = null,
    /** 未来逐日最低温覆盖（从今天起）。 */
    val dailyTempMinC: List<Double>? = null,
) {
    fun hasAny(): Boolean =
        temperatureC != null ||
            apparentTemperatureC != null ||
            humidityPercent != null ||
            weatherCode != null ||
            precipitationRateMmPerHour != null ||
            windSpeedKmh != null ||
            windGustsKmh != null ||
            visibilityMeters != null ||
            uvIndex != null ||
            europeanAqi != null ||
            dustUgM3 != null ||
            dailyTempMaxC != null ||
            dailyTempMinC != null
}

data class DebugWeatherState(
    val enabled: Boolean = false,
    val override: DebugWeatherOverride = DebugWeatherOverride(),
)

/**
 * 隐藏调试：启用后对当前天气快照叠加字段，并重算预警 / 生活指数。
 */
class DebugWeatherStore(
    private val context: Context,
) : DebugWeatherSource {
    private val enabledKey = booleanPreferencesKey("enabled")
    private val overrideKey = stringPreferencesKey("override_json")
    private val json = Json { ignoreUnknownKeys = true }

    override val state: Flow<DebugWeatherState> =
        context.debugWeatherDataStore.data.map { prefs ->
            val override =
                prefs[overrideKey]?.let { raw ->
                    runCatching {
                        json.decodeFromString(DebugWeatherOverride.serializer(), raw)
                    }.getOrNull()
                } ?: DebugWeatherOverride()
            DebugWeatherState(
                enabled = prefs[enabledKey] == true,
                override = override,
            )
        }

    override suspend fun getOnce(): DebugWeatherState = state.first()

    override suspend fun setEnabled(enabled: Boolean) {
        context.debugWeatherDataStore.edit { prefs ->
            prefs[enabledKey] = enabled
            if (!enabled) {
                prefs.remove(overrideKey)
            }
        }
    }

    override suspend fun saveOverride(override: DebugWeatherOverride) {
        context.debugWeatherDataStore.edit { prefs ->
            prefs[overrideKey] =
                json.encodeToString(
                    DebugWeatherOverride.serializer(),
                    override,
                )
            prefs[enabledKey] = true
        }
    }

    override suspend fun clearOverride() {
        context.debugWeatherDataStore.edit { prefs ->
            prefs.remove(overrideKey)
        }
    }

    companion object {
        const val MAGIC_QUERY_EN = "__debug__"
        const val MAGIC_QUERY_ZH = "#熊调试"
        const val ABOUT_TAP_COUNT = 7

        fun isMagicQuery(query: String): Boolean {
            val t = query.trim()
            return t.equals(MAGIC_QUERY_EN, ignoreCase = true) || t == MAGIC_QUERY_ZH
        }

        fun apply(
            base: WeatherSnapshot,
            override: DebugWeatherOverride,
            resources: Resources,
        ): WeatherSnapshot {
            if (!override.hasAny()) return base
            // 只设实际温度时体感温度自动跟随，避免预警仍按旧体感判断导致测试失真
            val effectiveFeelsC =
                when {
                    override.temperatureC != null && override.apparentTemperatureC == null ->
                        override.temperatureC
                    else -> override.apparentTemperatureC
                }
            val current =
                base.current.copy(
                    temperatureC = override.temperatureC ?: base.current.temperatureC,
                    apparentTemperatureC = effectiveFeelsC ?: base.current.apparentTemperatureC,
                    humidityPercent = override.humidityPercent ?: base.current.humidityPercent,
                    weatherCode = override.weatherCode ?: base.current.weatherCode,
                    precipitationRateMmPerHour =
                        override.precipitationRateMmPerHour
                            ?: base.current.precipitationRateMmPerHour,
                    precipitationMm =
                        override.precipitationRateMmPerHour
                            ?: base.current.precipitationMm,
                    windSpeedKmh = override.windSpeedKmh ?: base.current.windSpeedKmh,
                    windGustsKmh = override.windGustsKmh ?: base.current.windGustsKmh,
                    visibilityMeters = override.visibilityMeters ?: base.current.visibilityMeters,
                )
            val hourly = base.hourly.toMutableList()
            if (hourly.isNotEmpty() &&
                (override.precipitationRateMmPerHour != null || override.weatherCode != null)
            ) {
                // hourly[0] 是 24 小时前的桶（ForecastFetcher 带 past_hours=24）；需覆盖当前小时桶
                // 才能作用于预警 / 临近降水逻辑
                val idx = indexOfCurrentHour(hourly.map { it.time }, base.current.time).coerceAtLeast(0)
                val target = hourly[idx]
                hourly[idx] =
                    target.copy(
                        precipitationMm =
                            override.precipitationRateMmPerHour
                                ?: target.precipitationMm,
                        weatherCode = override.weatherCode ?: target.weatherCode,
                    )
            }
            // daily 温度覆盖：支持模拟连续高温/寒潮（长度不足处保留实况）
            val daily =
                if (override.dailyTempMaxC != null || override.dailyTempMinC != null) {
                    base.daily.mapIndexed { i, d ->
                        val max = override.dailyTempMaxC?.getOrNull(i)
                        val min = override.dailyTempMinC?.getOrNull(i)
                        if (max != null || min != null) {
                            d.copy(
                                tempMaxC = max ?: d.tempMaxC,
                                tempMinC = min ?: d.tempMinC,
                            )
                        } else {
                            d
                        }
                    }
                } else {
                    base.daily
                }
            val air =
                (base.airQuality ?: AirQuality()).copy(
                    uvIndex = override.uvIndex ?: base.airQuality?.uvIndex,
                    europeanAqi = override.europeanAqi ?: base.airQuality?.europeanAqi,
                    dustUgM3 = override.dustUgM3 ?: base.airQuality?.dustUgM3,
                )
            val alerts =
                WeatherAlertGenerator.generate(
                    resources,
                    current,
                    hourly,
                    daily,
                    air,
                )
            val lifeIndices =
                LifeIndexGenerator.generate(
                    resources,
                    current,
                    hourly,
                    daily,
                    air,
                )
            val advice =
                WeatherAdviceGenerator.generate(
                    resources,
                    current,
                    hourly,
                    daily,
                    air,
                )
            return base.copy(
                current = current,
                hourly = hourly,
                daily = daily,
                airQuality = air,
                alerts = alerts,
                lifeIndices = lifeIndices,
                advice = advice,
            )
        }
    }
}

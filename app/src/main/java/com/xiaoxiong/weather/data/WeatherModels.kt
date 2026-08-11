package com.xiaoxiong.weather.data

import kotlinx.serialization.Serializable

@Serializable
data class City(
    val id: Long,
    val name: String,
    val latitude: Double,
    val longitude: Double,
    val country: String? = null,
    val admin1: String? = null,
    val admin2: String? = null,
    val isCurrentLocation: Boolean = false,
) {
    val displayName: String
        get() =
            listOfNotNull(name, admin2, admin1, country)
                .distinct()
                .joinToString(", ")
}

@Serializable
data class CurrentWeather(
    val time: String,
    val temperatureC: Double? = null,
    val apparentTemperatureC: Double? = null,
    val humidityPercent: Int? = null,
    val weatherCode: Int? = null,
    val windSpeedKmh: Double? = null,
    val windGustsKmh: Double? = null,
    val windDirectionDeg: Int? = null,
    val visibilityMeters: Double? = null,
    /**
     * Open-Meteo current.precipitation：短窗口累计 mm（仅原始字段保留，分档不用）。
     */
    val precipitationMm: Double? = null,
    /** current.interval，秒；常见 900。 */
    val precipitationIntervalSeconds: Int? = null,
    /**
     * 分档/提醒/「降水」指标用的雨量，与小时图同一套标准：
     * 当前整点小时桶 precipitation（该钟点合计 mm，量纲同 mm/h）。
     */
    val precipitationRateMmPerHour: Double? = null,
    val cloudCoverPercent: Int? = null,
    val pressureHpa: Double? = null,
    val dewPointC: Double? = null,
    val isDay: Boolean? = null,
)

@Serializable
data class HourlyWeather(
    val time: String,
    val temperatureC: Double? = null,
    val humidityPercent: Int? = null,
    val visibilityMeters: Double? = null,
    val weatherCode: Int? = null,
    val precipitationMm: Double? = null,
    val precipProbability: Int? = null,
    val windSpeedKmh: Double? = null,
)

@Serializable
data class DailyWeather(
    val date: String,
    val weatherCode: Int? = null,
    val tempMaxC: Double? = null,
    val tempMinC: Double? = null,
    val precipSumMm: Double? = null,
    val precipProbabilityMax: Int? = null,
    val sunrise: String? = null,
    val sunset: String? = null,
    val uvIndexMax: Double? = null,
    val windMaxKmh: Double? = null,
)

@Serializable
data class AirQuality(
    val europeanAqi: Int? = null,
    val usAqi: Int? = null,
    val pm25: Double? = null,
    val pm10: Double? = null,
    /** Open-Meteo / CAMS 近地面沙尘，μg/m³ */
    val dustUgM3: Double? = null,
    val uvIndex: Double? = null,
    val nitrogenDioxide: Double? = null,
    val ozone: Double? = null,
)

@Serializable
enum class AlertSeverity { INFO, WATCH, WARNING }

/** 预警类型：用于匹配专属小熊图标。 */
@Serializable
enum class AlertKind {
    STORM,
    HAIL,
    HEAVY_RAIN,
    PONDING,
    SNOW,

    /** 未来数小时强降雪 / 暴雪预报。 */
    SNOW_SOON,
    SNOWY_ROAD,
    FREEZING_RAIN,
    FOG,
    WIND,
    HEAT,

    /** 连续多日高温预报（非当前体感高温）。 */
    HEAT_WAVE,
    COLD,

    /** 日际剧烈降温，但降温后仍偏暖（添衣，非冰雪寒潮）。 */
    COOLDOWN,

    /** 日际剧烈降温且降温后偏冷（寒潮感）。 */
    COLD_WAVE,
    LOW_VISIBILITY,

    /** 当前正在降雨（未达强降雨阈值时的出行提醒）。 */
    RAINING,
    RAIN_SOON,

    /** 当前正在降雪（未达强降雪阈值时的出行提醒）。 */
    SNOWING,
    UV,
    AIR_QUALITY,
    DUST,
    CLEAR,
}

@Serializable
data class WeatherAlert(
    val title: String,
    val message: String,
    val severity: AlertSeverity,
    val kind: AlertKind = AlertKind.CLEAR,
    /** 未来降雨提醒等：用峰值小时雨量选伞色小熊。 */
    val precipMmPerHour: Double? = null,
)

@Serializable
enum class LifeIndexKind {
    COMFORT,
    CLOTHING,
    UMBRELLA,
    UV,
    CAR_WASH,
    LAUNDRY,
    SPORT,
    COLD,
}

@Serializable
enum class LifeIndexLevel {
    GOOD,
    FAIR,
    POOR,
}

@Serializable
data class LifeIndex(
    val kind: LifeIndexKind,
    val level: LifeIndexLevel,
    val title: String,
    val summary: String,
    /** 若有则覆盖默认「适宜/不宜」类标签（穿衣等不适用「不宜」时使用） */
    val levelLabel: String? = null,
)

@Serializable
data class WeatherSnapshot(
    val city: City,
    val current: CurrentWeather,
    val hourly: List<HourlyWeather> = emptyList(),
    val daily: List<DailyWeather> = emptyList(),
    val airQuality: AirQuality? = null,
    val alerts: List<WeatherAlert> = emptyList(),
    val advice: String? = null,
    val lifeIndices: List<LifeIndex> = emptyList(),
    /** 测站/网格海拔（米），来自 Open-Meteo elevation。 */
    val elevationMeters: Double? = null,
    /** 城市 IANA 时区（如 Asia/Shanghai），来自 Open-Meteo timezone=auto；用于雷达帧等 UTC 时刻的本地化展示。 */
    val timezone: String? = null,
)

/**
 * 历史某一天的天气（Open-Meteo Archive / ERA5 再分析数据）。
 * 用户主动查询单日，值可空表示该字段无数据。
 */
@Serializable
data class HistoryWeather(
    val date: String,
    val weatherCode: Int? = null,
    val tempMaxC: Double? = null,
    val tempMinC: Double? = null,
    val tempMeanC: Double? = null,
    val precipSumMm: Double? = null,
    val humidityMeanPercent: Int? = null,
    val windMaxKmh: Double? = null,
    val sunrise: String? = null,
    val sunset: String? = null,
)

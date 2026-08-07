package com.xiaoxiong.weather.util

import android.content.Context
import android.content.res.Resources
import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.xiaoxiong.weather.R
import com.xiaoxiong.weather.data.CurrentWeather
import com.xiaoxiong.weather.data.TemperatureUnit
import java.util.Locale

object WeatherCodes {
    /**
     * 小时雨强阈值（mm/h），偏英 Met Office / 体感友好：
     * 小雨 ≤1；中雨 ≤4；大雨 &gt;4；暴雨仍对齐国标小时 ≥16；猛烈对齐英式 &gt;32。
     */
    const val PRECIP_LIGHT_MAX_MM_H = 1.0
    const val PRECIP_MODERATE_MM_H = 1.0
    /** 中雨上限 / 大雨起点（&gt; 此值进入大雨） */
    const val PRECIP_HEAVY_MM_H = 4.0
    /** 中国气象局：小时降雨量 ≥16 mm 可称暴雨 */
    const val PRECIP_TORRENTIAL_MM_H = 16.0
    /**
     * 未来强降雨升为红色「预警」的峰值门槛（mm/小时）。
     * 高于大雨起点、贴近暴雨量级，表示短时雨势很猛。
     */
    const val PRECIP_HEAVY_SOON_WARNING_MM_H = 20.0
    /** 英 Met Office 等：violent / 猛烈 &gt; 32 mm/h */
    const val PRECIP_VIOLENT_MM_H = 32.0
    /** 日累计暴雨量级（mm/24h，中国常用门槛） */
    const val PRECIP_DAILY_HEAVY_MM = 50.0
    /**
     * 剧烈降温 / 寒潮：看「今天→明天」最高温或最低温的日际降温（°C），
     * 避免把常见昼夜温差误判成寒潮。
     */
    const val COLD_WAVE_WATCH_DROP_C = 10.0
    const val COLD_WAVE_WARNING_DROP_C = 14.0
    /**
     * 降温后仍偏暖则用「添衣降温」而不是雪天寒潮图标/文案。
     * 明日最高温 ≤ 此值，或最低温 ≤ [COLD_WAVE_WINTRY_MIN_C]，才视为偏冷寒潮场景。
     */
    const val COLD_WAVE_WINTRY_MAX_C = 16.0
    const val COLD_WAVE_WINTRY_MIN_C = 8.0
    /** 连续高温：日最高温 ≥35°C 计为高温日（中国高温日常用门槛）。 */
    const val HEAT_WAVE_DAY_MAX_C = 35.0
    /** 连续高温日中，日最高温 ≥37°C 视为更强高温日。 */
    const val HEAT_WAVE_SEVERE_DAY_MAX_C = 37.0
    /** 从今天起连续高温日数达到此值 → 提醒。 */
    const val HEAT_WAVE_WATCH_DAYS = 3
    /** 连续 ≥5 天 ≥35°C，或连续 ≥3 天且天天 ≥37°C → 红色预警。 */
    const val HEAT_WAVE_WARNING_DAYS = 5
    /** CAMS 近地面沙尘（μg/m³）：≥50 提醒，≥150 预警。 */
    const val DUST_WATCH_UG_M3 = 50.0
    const val DUST_WARNING_UG_M3 = 150.0
    /**
     * 积水提醒：近几小时累计雨量门槛（城市内涝经验阈值，非官方警报）。
     * 短时强降水看 3h，持续降雨看 6h/12h。
     */
    const val PONDING_3H_MM = 25.0
    const val PONDING_6H_MM = 40.0
    const val PONDING_12H_MM = 60.0
    /** 达此累计时升为 WARNING 级别积水提醒 */
    const val PONDING_WARNING_3H_MM = 40.0
    const val PONDING_WARNING_6H_MM = 50.0
    /** 达此 12h 累计时升为 WARNING 级别积水提醒（须高于 [PONDING_12H_MM] 触发档） */
    const val PONDING_WARNING_12H_MM = 75.0
    /** 雨停后仍提示积水的小时数 */
    const val PONDING_HOURS_AFTER_RAIN = 2
    /**
     * 毛毛雨小时雨强上限（mm/h）。毛毛雨以雨滴直径为主（&lt;0.5mm），
     * 典型雨强多 &lt;0.5 mm/h；达到小雨档（≤1）前就应升为小雨展示，避免与小雨同上限。
     */
    const val PRECIP_DRIZZLE_MAX_MM_H = 0.5

    /**
     * 降雪强度（液态水当量 mm/h）。国标小/中/大/暴雪以 24h 累计为主；
     * 实时图标用小时 LWE，并参考航空常用分级与天气代码。
     * 24h：小雪 0.1–2.4，中雪 2.5–4.9，大雪 5.0–9.9，暴雪 ≥10。
     */
    const val SNOW_LIGHT_MAX_MM_H = 1.0
    const val SNOW_MODERATE_MAX_MM_H = 2.5
    const val SNOW_HEAVY_MAX_MM_H = 5.0
    /** 日累计暴雪量级（mm/24h，中国气象局） */
    const val SNOW_DAILY_BLIZZARD_MM = 10.0
    /** 日累计大雪量级下限（mm/24h） */
    const val SNOW_DAILY_HEAVY_MM = 5.0
    /**
     * 未来强降雪升为红色「暴雪预警」的峰值门槛（液态水当量 mm/小时）。
     * 对齐小时暴雪量级起点。
     */
    const val SNOW_HEAVY_SOON_WARNING_MM_H = 5.0

    /** 雪后路滑提醒持续时间（小时）：小雪短、中雪中、大雪/暴雪更长。 */
    const val SNOWY_ROAD_HOURS_AFTER_LIGHT = 3
    const val SNOWY_ROAD_HOURS_AFTER_MODERATE = 6
    const val SNOWY_ROAD_HOURS_AFTER_HEAVY = 12
    const val SNOWY_ROAD_HOURS_AFTER_BLIZZARD = 18
    /** 路面积雪仍易打滑的体感/气温上限（°C） */
    const val SNOWY_ROAD_MAX_TEMP_C = 2.0

    enum class RainIntensity {
        NONE, LIGHT, MODERATE, HEAVY, TORRENTIAL, VIOLENT
    }

    enum class SnowIntensity {
        NONE, LIGHT, MODERATE, HEAVY, BLIZZARD
    }

    fun snowyRoadAlertDurationHours(intensity: SnowIntensity): Int = when (intensity) {
        SnowIntensity.NONE -> 0
        SnowIntensity.LIGHT -> SNOWY_ROAD_HOURS_AFTER_LIGHT
        SnowIntensity.MODERATE -> SNOWY_ROAD_HOURS_AFTER_MODERATE
        SnowIntensity.HEAVY -> SNOWY_ROAD_HOURS_AFTER_HEAVY
        SnowIntensity.BLIZZARD -> SNOWY_ROAD_HOURS_AFTER_BLIZZARD
    }

    private val keepCodeAsIs = setOf(
        56, 57, 66, 67, 95, 96, 99
    )
    private val softRainCodes = setOf(51, 53, 55, 61, 63, 80, 81)
    /** 晴/云/雾等「非降水码」：有实测雨量时应升为雨码，避免文案写小雨、emoji 仍是阴天。 */
    private val drySkyCodes = setOf(0, 1, 2, 3, 45, 48)
    private val snowCodes = setOf(71, 73, 75, 77, 85, 86)
    private val drizzleCodes = setOf(51, 53, 55)

    fun isSnowCode(code: Int?): Boolean = code in snowCodes

    fun isDrizzleCode(code: Int?): Boolean = code in drizzleCodes

    /** 天气码为毛毛雨，且小时雨量仍在毛毛雨量级内（含未知/极弱降水）。 */
    fun isStillDrizzle(weatherCode: Int?, precipMmPerHour: Double?): Boolean {
        if (!isDrizzleCode(weatherCode)) return false
        val mm = precipMmPerHour ?: return true
        return mm < PRECIP_DRIZZLE_MAX_MM_H
    }

    /**
     * 分档/图标/提醒用的降水强度：与小时降雨图同一标准——当前整点小时桶 mm。
     * 拉取时写入 [CurrentWeather.precipitationRateMmPerHour]；不再用 15 分钟短窗口换算。
     */
    fun intensityPrecipMmPerHour(current: CurrentWeather): Double? =
        current.precipitationRateMmPerHour

    fun rainIntensity(precipMmPerHour: Double?): RainIntensity {
        val mm = precipMmPerHour ?: return RainIntensity.NONE
        return when {
            mm < 0.1 -> RainIntensity.NONE
            mm <= PRECIP_LIGHT_MAX_MM_H -> RainIntensity.LIGHT
            mm <= PRECIP_HEAVY_MM_H -> RainIntensity.MODERATE
            mm < PRECIP_TORRENTIAL_MM_H -> RainIntensity.HEAVY
            mm <= PRECIP_VIOLENT_MM_H -> RainIntensity.TORRENTIAL
            else -> RainIntensity.VIOLENT
        }
    }

    /**
     * 降雪档位：天气代码与小时雪量取较高档；日累计达暴雪且仍在下雪时升为暴雪。
     */
    fun snowIntensity(
        weatherCode: Int?,
        precipMmPerHour: Double? = null,
        dailyPrecipSumMm: Double = 0.0
    ): SnowIntensity {
        if (!isSnowCode(weatherCode)) return SnowIntensity.NONE
        val byCode = when (weatherCode) {
            86 -> SnowIntensity.BLIZZARD
            75 -> SnowIntensity.HEAVY
            73 -> SnowIntensity.MODERATE
            else -> SnowIntensity.LIGHT // 71, 77, 85
        }
        val byPrecip = when (val mm = precipMmPerHour) {
            null -> SnowIntensity.NONE
            else -> when {
                mm < 0.1 -> SnowIntensity.NONE
                mm < SNOW_LIGHT_MAX_MM_H -> SnowIntensity.LIGHT
                mm < SNOW_MODERATE_MAX_MM_H -> SnowIntensity.MODERATE
                mm < SNOW_HEAVY_MAX_MM_H -> SnowIntensity.HEAVY
                else -> SnowIntensity.BLIZZARD
            }
        }
        val combined = if (byPrecip == SnowIntensity.NONE) {
            byCode
        } else {
            maxOf(byCode, byPrecip)
        }
        val stillSnowing = (precipMmPerHour ?: 0.0) >= 0.1 || byCode != SnowIntensity.NONE
        return if (dailyPrecipSumMm >= SNOW_DAILY_BLIZZARD_MM && stillSnowing) {
            maxOf(combined, SnowIntensity.BLIZZARD)
        } else {
            combined
        }
    }

    /**
     * 当雨量/雪量与天气码不一致时，提升为更贴切的展示代码。
     * 不降级雷暴、冰雹、冻雨等特殊天气。
     */
    fun displayCode(code: Int?, precipMmPerHour: Double?): Int? {
        if (isSnowCode(code)) {
            return when (snowIntensity(code, precipMmPerHour)) {
                SnowIntensity.BLIZZARD -> 86
                SnowIntensity.HEAVY -> 75
                SnowIntensity.MODERATE -> 73
                SnowIntensity.LIGHT -> if (code == 77 || code == 85) code else 71
                SnowIntensity.NONE -> code
            }
        }
        val mm = precipMmPerHour ?: return code
        if (code in keepCodeAsIs) return code
        if (code == 65 || code == 82) return code
        return when (rainIntensity(mm)) {
            RainIntensity.VIOLENT, RainIntensity.TORRENTIAL, RainIntensity.HEAVY -> 65
            RainIntensity.MODERATE -> when {
                code == null || code in softRainCodes || code in drySkyCodes || code == 65 -> 63
                else -> code
            }
            RainIntensity.LIGHT -> when {
                // 毛毛雨码但雨量已超毛毛雨量级 → 升为小雨
                isDrizzleCode(code) && mm >= PRECIP_DRIZZLE_MAX_MM_H -> 61
                isDrizzleCode(code) -> code
                // 阴/晴等干码却在下雨 → 按小雨展示（emoji/氛围与文案一致）
                code == null || code in drySkyCodes -> 61
                else -> code
            }
            RainIntensity.NONE -> code
        }
    }

    /**
     * 当前是否已达强降雨量级（大雨：小时雨量 &gt;4 mm，对齐英式 heavy 起点）。
     * 不以大雨天气码单独判定——码常滞后，0 mm/h 时不应写「当前雨势较强」。
     */
    @Suppress("UNUSED_PARAMETER")
    fun isHeavyRainIntensity(
        weatherCode: Int?,
        currentPrecipMm: Double?
    ): Boolean = (currentPrecipMm ?: 0.0) > PRECIP_HEAVY_MM_H

    /** 当前雨势达暴雨量级（≥16 mm/小时）→ 红色「强降雨预警」。 */
    fun isHeavyRainWarningIntensity(currentPrecipMm: Double?): Boolean =
        (currentPrecipMm ?: 0.0) >= PRECIP_TORRENTIAL_MM_H

    /**
     * 今日截至当前已下落雨量达暴雨量级，且仍在下雨（含小雨）。
     * 用于「今日雨量已大」提醒，不表示此刻雨势很强。
     */
    fun isHeavyRainDailyAccumulation(
        currentPrecipMm: Double?,
        dailyPrecipFallenMm: Double
    ): Boolean {
        val current = currentPrecipMm ?: 0.0
        return dailyPrecipFallenMm >= PRECIP_DAILY_HEAVY_MM && current >= 0.5
    }

    /** 未来小时雨量峰值是否达到强降雨量级（用于预报提醒，非实况）。 */
    fun isHeavyRainForecastPeak(peakMmPerHour: Double?): Boolean =
        (peakMmPerHour ?: 0.0) > PRECIP_HEAVY_MM_H

    /** 未来峰值是否升为红色「强降雨预警」（默认 ≥20 mm/小时）。 */
    fun isHeavyRainForecastWarning(peakMmPerHour: Double?): Boolean =
        (peakMmPerHour ?: 0.0) >= PRECIP_HEAVY_SOON_WARNING_MM_H

    /** 当前是否达大雪及以上（小时 LWE 或天气码），用于强降雪提醒。 */
    fun isHeavySnowIntensity(
        weatherCode: Int?,
        currentPrecipMm: Double?,
        dailyPrecipSumMm: Double = 0.0
    ): Boolean {
        val intensity = snowIntensity(weatherCode, currentPrecipMm, dailyPrecipSumMm)
        return intensity == SnowIntensity.HEAVY || intensity == SnowIntensity.BLIZZARD
    }

    /** 当前是否达暴雪量级 → 红色「暴雪预警」。 */
    fun isBlizzardWarningIntensity(
        weatherCode: Int?,
        currentPrecipMm: Double?,
        dailyPrecipSumMm: Double = 0.0
    ): Boolean =
        snowIntensity(weatherCode, currentPrecipMm, dailyPrecipSumMm) == SnowIntensity.BLIZZARD

    /**
     * 今日累计已达大雪/暴雪量级，且仍在下雪。
     * 用于「今日雪量已大」提醒，不表示此刻雪势一定很强。
     */
    fun isHeavySnowDailyAccumulation(
        weatherCode: Int?,
        currentPrecipMm: Double?,
        dailyPrecipFallenMm: Double
    ): Boolean {
        if (!isSnowCode(weatherCode)) return false
        val current = currentPrecipMm ?: 0.0
        return dailyPrecipFallenMm >= SNOW_DAILY_HEAVY_MM && current >= 0.1
    }

    /** 未来小时雪量峰值是否达大雪量级（仅雪码小时）。 */
    fun isHeavySnowForecastPeak(peakMmPerHour: Double?, weatherCode: Int?): Boolean {
        if (!isSnowCode(weatherCode) && (peakMmPerHour ?: 0.0) <= 0.0) return false
        if (isSnowCode(weatherCode)) {
            return isHeavySnowIntensity(weatherCode, peakMmPerHour)
        }
        // 无码时仅按液态水当量：≥ 中雪上限进入大雪
        return (peakMmPerHour ?: 0.0) >= SNOW_MODERATE_MAX_MM_H
    }

    /** 未来峰值是否升为红色「暴雪预警」。 */
    fun isBlizzardForecastWarning(peakMmPerHour: Double?, weatherCode: Int?): Boolean {
        if (isSnowCode(weatherCode)) {
            return isBlizzardWarningIntensity(weatherCode, peakMmPerHour)
        }
        return (peakMmPerHour ?: 0.0) >= SNOW_HEAVY_SOON_WARNING_MM_H
    }

    /** 是否正在降水（以雨强为准，避免天气码粘滞误报「正在下雨」）。 */
    fun isPrecipitatingNow(precipMmPerHour: Double?): Boolean =
        (precipMmPerHour ?: 0.0) >= 0.1

    @StringRes
    fun descriptionRes(code: Int?): Int = when (code) {
        0 -> R.string.wx_clear
        1 -> R.string.wx_mainly_clear
        2 -> R.string.wx_partly_cloudy
        3 -> R.string.wx_overcast
        45, 48 -> R.string.wx_fog
        51, 53, 55 -> R.string.wx_drizzle
        56, 57 -> R.string.wx_freezing_drizzle
        61 -> R.string.wx_light_rain
        63 -> R.string.wx_moderate_rain
        65 -> R.string.wx_heavy_rain
        66, 67 -> R.string.wx_freezing_rain
        71 -> R.string.wx_light_snow
        73 -> R.string.wx_moderate_snow
        75 -> R.string.wx_heavy_snow
        77 -> R.string.wx_snow_grains
        80, 81 -> R.string.wx_rain_showers
        82 -> R.string.wx_heavy_rain_showers
        85 -> R.string.wx_snow_showers
        86 -> R.string.wx_blizzard
        95 -> R.string.wx_thunderstorm
        96, 99 -> R.string.wx_hail
        null -> R.string.label_unknown
        else -> R.string.wx_code_fallback
    }

    /**
     * 有可靠雨/雪量时优先按强度展示；文案与 [displayCode] 对齐，避免「小雨 + 阴天 emoji」。
     * 暴雨/猛烈超过大雨码表达范围时仍用强度专文案。
     */
    @StringRes
    fun descriptionRes(code: Int?, precipMmPerHour: Double?): Int {
        if (isSnowCode(code)) {
            return when (snowIntensity(code, precipMmPerHour)) {
                SnowIntensity.BLIZZARD -> R.string.wx_blizzard
                SnowIntensity.HEAVY -> R.string.wx_heavy_snow
                SnowIntensity.MODERATE -> R.string.wx_moderate_snow
                SnowIntensity.LIGHT -> R.string.wx_light_snow
                SnowIntensity.NONE -> descriptionRes(code)
            }
        }
        if (code in keepCodeAsIs) return descriptionRes(code)
        return when (rainIntensity(precipMmPerHour)) {
            RainIntensity.VIOLENT -> R.string.wx_extreme_rain
            RainIntensity.TORRENTIAL -> R.string.wx_violent_rain
            // 小/中/大雨与毛毛雨：跟展示码同一套，保证文案↔emoji↔氛围一致
            RainIntensity.HEAVY,
            RainIntensity.MODERATE,
            RainIntensity.LIGHT,
            RainIntensity.NONE -> descriptionRes(displayCode(code, precipMmPerHour))
        }
    }

    fun description(resources: Resources, code: Int?): String {
        val res = descriptionRes(code)
        return if (code != null && res == R.string.wx_code_fallback) {
            resources.getString(res, code)
        } else {
            resources.getString(res)
        }
    }

    fun description(resources: Resources, code: Int?, precipMmPerHour: Double?): String {
        val res = descriptionRes(code, precipMmPerHour)
        return if (code != null && res == R.string.wx_code_fallback) {
            resources.getString(res, code)
        } else {
            resources.getString(res)
        }
    }

    fun description(context: Context, code: Int?): String =
        description(context.resources, code)

    fun description(context: Context, code: Int?, precipMmPerHour: Double?): String =
        description(context.resources, code, precipMmPerHour)

    fun emoji(code: Int?): String = when (code) {
        0, 1 -> if (code == 1) "🌤️" else "☀️"
        2 -> "⛅"
        3 -> "☁️"
        45, 48 -> "🌫️"
        51, 53, 56, 80 -> "🌦️"
        55, 57, 61, 63, 66, 67, 81 -> "🌧️"
        65, 82 -> "🌧️"
        71, 77, 85 -> "❄️"
        73 -> "🌨️"
        75, 86 -> "🌨️"
        95 -> "⛈️"
        96, 99 -> "🧊"
        else -> "🌡️"
    }
}

@Composable
fun weatherCodeDescription(code: Int?): String {
    val res = WeatherCodes.descriptionRes(code)
    return if (code != null && res == R.string.wx_code_fallback) {
        stringResource(res, code)
    } else {
        stringResource(res)
    }
}

@Composable
fun weatherCodeDescription(code: Int?, precipMmPerHour: Double?): String {
    val res = WeatherCodes.descriptionRes(code, precipMmPerHour)
    return if (code != null && res == R.string.wx_code_fallback) {
        stringResource(res, code)
    } else {
        stringResource(res)
    }
}

fun formatVisibility(meters: Double?): String {
    if (meters == null) return "--"
    return if (meters >= 1000) {
        String.format(Locale.US, "%.1f km", meters / 1000.0)
    } else {
        String.format(Locale.US, "%.0f m", meters)
    }
}

fun celsiusToFahrenheit(celsius: Double): Double = celsius * 9.0 / 5.0 + 32.0

fun formatTemperature(
    value: Double?,
    unit: TemperatureUnit = TemperatureUnit.CELSIUS
): String {
    if (value == null) return "--"
    val display = when (unit) {
        TemperatureUnit.CELSIUS -> value
        TemperatureUnit.FAHRENHEIT -> celsiusToFahrenheit(value)
    }
    val suffix = when (unit) {
        TemperatureUnit.CELSIUS -> "°"
        TemperatureUnit.FAHRENHEIT -> "°F"
    }
    return String.format(Locale.US, "%.1f%s", display, suffix)
}

fun convertCelsiusForDisplay(
    value: Double?,
    unit: TemperatureUnit
): Double? {
    if (value == null) return null
    return when (unit) {
        TemperatureUnit.CELSIUS -> value
        TemperatureUnit.FAHRENHEIT -> celsiusToFahrenheit(value)
    }
}

fun formatHourLabel(isoTime: String): String =
    isoTime.substringAfter('T', isoTime).take(5)

fun formatDateLabel(isoDate: String): String {
    val parts = isoDate.split("-")
    return if (parts.size >= 3) "${parts[1]}/${parts[2]}" else isoDate
}

private val WIND_DIR_RES = intArrayOf(
    R.string.wind_n,
    R.string.wind_ne,
    R.string.wind_e,
    R.string.wind_se,
    R.string.wind_s,
    R.string.wind_sw,
    R.string.wind_w,
    R.string.wind_nw
)

@StringRes
fun windDirectionRes(deg: Int?): Int? {
    if (deg == null) return null
    val normalized = ((deg % 360) + 360) % 360
    val index = (((normalized + 22.5) / 45.0).toInt()) % 8
    return WIND_DIR_RES[index]
}

fun formatWindDirection(resources: Resources, deg: Int?): String {
    val res = windDirectionRes(deg) ?: return "--"
    return resources.getString(res)
}

@Composable
fun windDirectionText(deg: Int?): String {
    val res = windDirectionRes(deg) ?: return "--"
    return stringResource(res)
}

@StringRes
fun aqiLabelRes(europeanAqi: Int?): Int = when {
    europeanAqi == null -> R.string.label_unknown
    europeanAqi <= 20 -> R.string.aqi_good
    europeanAqi <= 40 -> R.string.aqi_fair
    europeanAqi <= 60 -> R.string.aqi_moderate
    europeanAqi <= 80 -> R.string.aqi_poor
    europeanAqi <= 100 -> R.string.aqi_very_poor
    else -> R.string.aqi_extremely_poor
}

fun aqiLabel(resources: Resources, europeanAqi: Int?): String =
    resources.getString(aqiLabelRes(europeanAqi))

@Composable
fun aqiLabelText(europeanAqi: Int?): String =
    stringResource(aqiLabelRes(europeanAqi))

@StringRes
fun uvLabelRes(uv: Double?): Int = when {
    uv == null -> R.string.label_unknown
    uv < 3 -> R.string.uv_low
    uv < 6 -> R.string.uv_moderate
    uv < 8 -> R.string.uv_high
    uv < 11 -> R.string.uv_very_high
    else -> R.string.uv_extreme
}

fun uvLabel(resources: Resources, uv: Double?): String =
    resources.getString(uvLabelRes(uv))

@Composable
fun uvLabelText(uv: Double?): String =
    stringResource(uvLabelRes(uv))

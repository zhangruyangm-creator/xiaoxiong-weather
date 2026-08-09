package com.xiaoxiong.weather.data

import android.content.res.Resources
import androidx.annotation.StringRes
import com.xiaoxiong.weather.R
import com.xiaoxiong.weather.util.WeatherCodes
import com.xiaoxiong.weather.util.hoursBetween
import com.xiaoxiong.weather.util.precipFallenTodayMm
import com.xiaoxiong.weather.util.recentHoursUpToCurrent
import com.xiaoxiong.weather.util.upcomingFromCurrent

/**
 * Open-Meteo 暂无官方预警接口，这里基于实况/预报阈值生成智能提醒。
 */
object WeatherAlertGenerator {
    data class Spec(
        val kind: AlertKind,
        val severity: AlertSeverity,
        @StringRes val titleRes: Int,
        @StringRes val messageRes: Int,
        val messageArg: Any? = null,
        /** 写入 [WeatherAlert.precipMmPerHour]，供未来降雨伞色分档。 */
        val precipIconMm: Double? = null,
    )

    fun specs(
        current: CurrentWeather,
        hourly: List<HourlyWeather>,
        daily: List<DailyWeather>,
        airQuality: AirQuality?,
    ): List<Spec> {
        val alerts = mutableListOf<Spec>()
        // 取当前整点 + 未来 24 小时，用于「未来强降雨/降雪」预报窗口
        val upcoming = upcomingFromCurrent(hourly, { it.time }, current.time, count = 25)
        val futureHours = upcoming.drop(1).take(24)
        val today = daily.firstOrNull()
        // 与小时图同一标准：当前整点小时桶（拉取时已写入 precipitationRateMmPerHour）
        val rateNow =
            upcoming.firstOrNull()?.precipitationMm
                ?: WeatherCodes.intensityPrecipMmPerHour(current)
                ?: 0.0
        val fallenTodayMm =
            precipFallenTodayMm(
                hourly,
                { it.time },
                { it.precipitationMm },
                current.time,
            )
        val heavyRainNow =
            !WeatherCodes.isSnowCode(current.weatherCode) &&
                WeatherCodes.isHeavyRainIntensity(
                    weatherCode = current.weatherCode,
                    currentPrecipMm = rateNow,
                )
        val nowRainWarning = WeatherCodes.isHeavyRainWarningIntensity(rateNow)
        val heavyRainDaily =
            !heavyRainNow &&
                !WeatherCodes.isSnowCode(current.weatherCode) &&
                WeatherCodes.isHeavyRainDailyAccumulation(
                    currentPrecipMm = rateNow,
                    dailyPrecipFallenMm = fallenTodayMm,
                )
        // 未来 24h 强降雨：当前已在大雨时，仅当未来峰值升到 WARNING 且当前未达 WARNING 才并报
        val futurePeakMm = futureHours.maxOfOrNull { it.precipitationMm ?: 0.0 } ?: 0.0
        val soonRainWarning = WeatherCodes.isHeavyRainForecastWarning(futurePeakMm)
        val heavyRainSoon =
            !heavyRainDaily &&
                !WeatherCodes.isSnowCode(current.weatherCode) &&
                WeatherCodes.isHeavyRainForecastPeak(futurePeakMm) &&
                (!heavyRainNow || (soonRainWarning && !nowRainWarning))
        val precipitatingNow = WeatherCodes.isPrecipitatingNow(rateNow)

        val snowIntensityNow =
            WeatherCodes.snowIntensity(
                current.weatherCode,
                rateNow,
                fallenTodayMm,
            )
        val heavySnowNow =
            WeatherCodes.isHeavySnowIntensity(
                current.weatherCode,
                rateNow,
                fallenTodayMm,
            )
        val blizzardNow =
            WeatherCodes.isBlizzardWarningIntensity(
                current.weatherCode,
                rateNow,
                fallenTodayMm,
            )
        val heavySnowDaily =
            !heavySnowNow &&
                WeatherCodes.isHeavySnowDailyAccumulation(
                    weatherCode = current.weatherCode,
                    currentPrecipMm = rateNow,
                    dailyPrecipFallenMm = fallenTodayMm,
                )
        val futureSnowHour =
            futureHours
                .filter { WeatherCodes.isSnowCode(it.weatherCode) }
                .maxByOrNull { it.precipitationMm ?: 0.0 }
        val futureSnowPeakMm = futureSnowHour?.precipitationMm ?: 0.0
        val futureSnowCode = futureSnowHour?.weatherCode
        val soonSnowWarning =
            WeatherCodes.isBlizzardForecastWarning(
                futureSnowPeakMm,
                futureSnowCode,
            )
        val heavySnowSoon =
            !heavySnowDaily &&
                WeatherCodes.isHeavySnowForecastPeak(futureSnowPeakMm, futureSnowCode) &&
                (!heavySnowNow || (soonSnowWarning && !blizzardNow))

        when (current.weatherCode) {
            95 ->
                alerts +=
                    Spec(
                        AlertKind.STORM,
                        AlertSeverity.WARNING,
                        R.string.alert_storm_title,
                        R.string.alert_storm_msg,
                    )
            96, 99 ->
                alerts +=
                    Spec(
                        AlertKind.HAIL,
                        AlertSeverity.WARNING,
                        R.string.alert_hail_title,
                        R.string.alert_hail_msg,
                    )
            56, 57, 66, 67 ->
                alerts +=
                    Spec(
                        AlertKind.FREEZING_RAIN,
                        AlertSeverity.WARNING,
                        R.string.alert_freezing_rain_title,
                        R.string.alert_freezing_rain_msg,
                    )
            // 45/48 大雾码：能见度分档在下方统一处理，避免与浓雾 WARNING 重复
        }

        if (heavyRainNow) {
            alerts +=
                Spec(
                    AlertKind.HEAVY_RAIN,
                    if (nowRainWarning) AlertSeverity.WARNING else AlertSeverity.WATCH,
                    if (nowRainWarning) {
                        R.string.alert_heavy_rain_warning_title
                    } else {
                        R.string.alert_heavy_rain_title
                    },
                    if (nowRainWarning) {
                        R.string.alert_heavy_rain_warning_msg
                    } else {
                        R.string.alert_heavy_rain_msg
                    },
                    precipIconMm = rateNow,
                )
        } else if (heavyRainDaily) {
            alerts +=
                Spec(
                    AlertKind.HEAVY_RAIN,
                    AlertSeverity.WATCH,
                    R.string.alert_heavy_rain_daily_title,
                    R.string.alert_heavy_rain_daily_msg,
                    fallenTodayMm,
                )
        }
        if (heavyRainSoon) {
            alerts +=
                Spec(
                    AlertKind.RAIN_SOON,
                    if (soonRainWarning) AlertSeverity.WARNING else AlertSeverity.WATCH,
                    if (soonRainWarning) {
                        R.string.alert_heavy_rain_soon_warning_title
                    } else {
                        R.string.alert_heavy_rain_soon_title
                    },
                    if (soonRainWarning) {
                        R.string.alert_heavy_rain_soon_warning_msg
                    } else {
                        R.string.alert_heavy_rain_soon_msg
                    },
                    futurePeakMm,
                    precipIconMm = futurePeakMm,
                )
        } else if (
            !heavyRainNow &&
            !heavyRainDaily &&
            precipitatingNow &&
            !WeatherCodes.isSnowCode(current.weatherCode) &&
            current.weatherCode !in listOf(95, 96, 99, 56, 57, 66, 67)
        ) {
            // 小到中雨等：尚不够强降雨阈值，但不宜再显示「暂无预警 / 适宜出行」
            alerts +=
                Spec(
                    AlertKind.RAINING,
                    AlertSeverity.WATCH,
                    R.string.alert_raining_title,
                    R.string.alert_raining_msg,
                    precipIconMm = rateNow,
                )
        }

        if (heavySnowNow) {
            alerts +=
                Spec(
                    AlertKind.SNOW,
                    if (blizzardNow) AlertSeverity.WARNING else AlertSeverity.WATCH,
                    if (blizzardNow) {
                        R.string.alert_blizzard_warning_title
                    } else {
                        R.string.alert_heavy_snow_title
                    },
                    if (blizzardNow) {
                        R.string.alert_blizzard_warning_msg
                    } else {
                        R.string.alert_heavy_snow_msg
                    },
                    precipIconMm = rateNow,
                )
        } else if (heavySnowDaily) {
            val dailyBlizzard = fallenTodayMm >= WeatherCodes.SNOW_DAILY_BLIZZARD_MM
            alerts +=
                Spec(
                    AlertKind.SNOW,
                    if (dailyBlizzard) AlertSeverity.WARNING else AlertSeverity.WATCH,
                    if (dailyBlizzard) {
                        R.string.alert_blizzard_daily_title
                    } else {
                        R.string.alert_heavy_snow_daily_title
                    },
                    if (dailyBlizzard) {
                        R.string.alert_blizzard_daily_msg
                    } else {
                        R.string.alert_heavy_snow_daily_msg
                    },
                    fallenTodayMm,
                )
        }
        if (heavySnowSoon) {
            alerts +=
                Spec(
                    AlertKind.SNOW_SOON,
                    if (soonSnowWarning) AlertSeverity.WARNING else AlertSeverity.WATCH,
                    if (soonSnowWarning) {
                        R.string.alert_blizzard_soon_warning_title
                    } else {
                        R.string.alert_heavy_snow_soon_title
                    },
                    if (soonSnowWarning) {
                        R.string.alert_blizzard_soon_warning_msg
                    } else {
                        R.string.alert_heavy_snow_soon_msg
                    },
                    futureSnowPeakMm,
                    precipIconMm = futureSnowPeakMm,
                )
        } else if (
            !heavySnowNow &&
            !heavySnowDaily &&
            precipitatingNow &&
            WeatherCodes.isSnowCode(current.weatherCode) &&
            snowIntensityNow != WeatherCodes.SnowIntensity.NONE
        ) {
            // 小到中雪：有降雪但未达强降雪阈值
            alerts +=
                Spec(
                    AlertKind.SNOWING,
                    AlertSeverity.WATCH,
                    R.string.alert_snowing_title,
                    R.string.alert_snowing_msg,
                    precipIconMm = rateNow,
                )
        }

        pondingSpec(current, hourly)?.let { alerts += it }

        if (shouldAlertSnowyRoad(current, hourly)) {
            alerts +=
                Spec(
                    AlertKind.SNOWY_ROAD,
                    AlertSeverity.WATCH,
                    R.string.alert_snowy_road_title,
                    R.string.alert_snowy_road_msg,
                )
        }

        val wind = current.windGustsKmh ?: current.windSpeedKmh
        if (wind != null && wind >= 62) {
            alerts +=
                Spec(
                    AlertKind.WIND,
                    AlertSeverity.WARNING,
                    R.string.alert_wind_warning_title,
                    R.string.alert_wind_warning_msg,
                    wind,
                )
        } else if (wind != null && wind >= 40) {
            alerts +=
                Spec(
                    AlertKind.WIND,
                    AlertSeverity.WATCH,
                    R.string.alert_wind_watch_title,
                    R.string.alert_wind_watch_msg,
                    wind,
                )
        }

        val temp = current.apparentTemperatureC ?: current.temperatureC
        if (temp != null && temp >= 37) {
            alerts +=
                Spec(
                    AlertKind.HEAT,
                    AlertSeverity.WARNING,
                    R.string.alert_heat_title,
                    R.string.alert_heat_msg,
                    temp,
                )
        } else if (temp != null && temp <= WeatherCodes.COLD_WARNING_APPARENT_C) {
            alerts +=
                Spec(
                    AlertKind.COLD,
                    AlertSeverity.WARNING,
                    R.string.alert_cold_title,
                    R.string.alert_cold_msg,
                    temp,
                )
        }

        coldWaveSpec(current, hourly, daily)?.let { alerts += it }
        heatWaveSpec(daily)?.let { alerts += it }

        // 大雾 / 浓雾 / 低能见度：能见度 <200m 为浓雾 WARNING；雾码或 <1000m 为提醒
        val visibility = current.visibilityMeters
        val fogCode = current.weatherCode == 45 || current.weatherCode == 48
        when {
            visibility != null && visibility < WeatherCodes.FOG_DENSE_VISIBILITY_M ->
                alerts +=
                    Spec(
                        AlertKind.FOG,
                        AlertSeverity.WARNING,
                        R.string.alert_dense_fog_title,
                        R.string.alert_dense_fog_msg,
                    )
            fogCode ->
                alerts +=
                    Spec(
                        AlertKind.FOG,
                        AlertSeverity.WATCH,
                        R.string.alert_fog_title,
                        R.string.alert_fog_msg,
                    )
            visibility != null && visibility < WeatherCodes.FOG_WATCH_VISIBILITY_M ->
                alerts +=
                    Spec(
                        AlertKind.LOW_VISIBILITY,
                        AlertSeverity.WATCH,
                        R.string.alert_low_vis_title,
                        R.string.alert_low_vis_msg,
                    )
        }

        // 未来有雨：已在下雨/下雪或已有强降水提醒则不再报「未来降雨」；只看后续小时
        val rainSoon =
            !heavyRainNow &&
                !heavyRainDaily &&
                !heavyRainSoon &&
                !heavySnowNow &&
                !heavySnowDaily &&
                !heavySnowSoon &&
                !precipitatingNow &&
                futureHours.any {
                    !WeatherCodes.isSnowCode(it.weatherCode) &&
                        ((it.precipitationMm ?: 0.0) >= 1.0 || (it.precipProbability ?: 0) >= 70)
                }
        if (rainSoon && current.weatherCode !in listOf(95, 96, 99)) {
            val soonPeakMm =
                futureHours
                    .filter { !WeatherCodes.isSnowCode(it.weatherCode) }
                    .maxOfOrNull { it.precipitationMm ?: 0.0 } ?: 0.0
            alerts +=
                Spec(
                    AlertKind.RAIN_SOON,
                    AlertSeverity.INFO,
                    R.string.alert_rain_soon_title,
                    R.string.alert_rain_soon_msg,
                    precipIconMm = soonPeakMm,
                )
        }

        // 未来有雪（未达强降雪）：小到中雪预报
        val snowSoon =
            !heavySnowNow &&
                !heavySnowDaily &&
                !heavySnowSoon &&
                !(precipitatingNow && WeatherCodes.isSnowCode(current.weatherCode)) &&
                futureHours.any {
                    WeatherCodes.isSnowCode(it.weatherCode) &&
                        ((it.precipitationMm ?: 0.0) >= 0.5 || (it.precipProbability ?: 0) >= 70)
                }
        if (snowSoon) {
            val soonPeakMm =
                futureHours
                    .filter { WeatherCodes.isSnowCode(it.weatherCode) }
                    .maxOfOrNull { it.precipitationMm ?: 0.0 } ?: 0.0
            alerts +=
                Spec(
                    AlertKind.SNOW_SOON,
                    AlertSeverity.INFO,
                    R.string.alert_snow_soon_title,
                    R.string.alert_snow_soon_msg,
                    precipIconMm = soonPeakMm,
                )
        }

        val uv =
            when {
                current.isDay == false -> airQuality?.uvIndex // 夜间不用今日 uvIndexMax 冒充当前
                else -> airQuality?.uvIndex ?: today?.uvIndexMax
            }
        if (uv != null && uv >= 8) {
            alerts +=
                Spec(
                    AlertKind.UV,
                    if (uv >= 11) AlertSeverity.WARNING else AlertSeverity.WATCH,
                    R.string.alert_uv_title,
                    R.string.alert_uv_msg,
                    uv,
                )
        }

        val aqi = airQuality?.europeanAqi
        if (aqi != null && aqi > 60) {
            alerts +=
                Spec(
                    AlertKind.AIR_QUALITY,
                    if (aqi > 80) AlertSeverity.WARNING else AlertSeverity.WATCH,
                    R.string.alert_aqi_title,
                    R.string.alert_aqi_msg,
                    aqi,
                )
        }

        val dust = airQuality?.dustUgM3
        if (dust != null && dust >= WeatherCodes.DUST_WATCH_UG_M3) {
            alerts +=
                Spec(
                    AlertKind.DUST,
                    if (dust >= WeatherCodes.DUST_WARNING_UG_M3) {
                        AlertSeverity.WARNING
                    } else {
                        AlertSeverity.WATCH
                    },
                    R.string.alert_dust_title,
                    R.string.alert_dust_msg,
                    dust,
                )
        }

        if (alerts.isEmpty()) {
            alerts +=
                Spec(
                    AlertKind.CLEAR,
                    AlertSeverity.INFO,
                    R.string.alert_clear_title,
                    R.string.alert_clear_msg,
                )
        }

        return alerts.distinctBy { it.titleRes }
    }

    fun generate(
        resources: Resources,
        current: CurrentWeather,
        hourly: List<HourlyWeather>,
        daily: List<DailyWeather>,
        airQuality: AirQuality?,
    ): List<WeatherAlert> =
        specs(current, hourly, daily, airQuality).map { spec ->
            WeatherAlert(
                title = resources.getString(spec.titleRes),
                message =
                    if (spec.messageArg != null) {
                        resources.getString(spec.messageRes, spec.messageArg)
                    } else {
                        resources.getString(spec.messageRes)
                    },
                severity = spec.severity,
                kind = spec.kind,
                precipMmPerHour = spec.precipIconMm,
            )
        }

    /**
     * 连续多日高温：从今天起，日最高温连续 ≥35°C 的天数。
     * ≥3 天提醒；≥5 天，或 ≥3 天且天天 ≥37°C → 红色预警。
     */
    internal fun heatWaveSpec(daily: List<DailyWeather>): Spec? {
        val streak = consecutiveHotDaysFromStart(daily) ?: return null
        if (streak.days < WeatherCodes.HEAT_WAVE_WATCH_DAYS) return null
        val warning =
            streak.days >= WeatherCodes.HEAT_WAVE_WARNING_DAYS ||
                (streak.days >= WeatherCodes.HEAT_WAVE_WATCH_DAYS && streak.allSevere)
        return Spec(
            AlertKind.HEAT_WAVE,
            if (warning) AlertSeverity.WARNING else AlertSeverity.WATCH,
            if (warning) R.string.alert_heat_wave_warning_title else R.string.alert_heat_wave_title,
            if (warning) R.string.alert_heat_wave_warning_msg else R.string.alert_heat_wave_msg,
            streak.days,
        )
    }

    internal data class HotDayStreak(
        val days: Int,
        val allSevere: Boolean,
    )

    /** 从 daily[0]（今天）起连续高温日数；中间断开则只计前缀。 */
    internal fun consecutiveHotDaysFromStart(daily: List<DailyWeather>): HotDayStreak? {
        if (daily.isEmpty()) return null
        var days = 0
        var allSevere = true
        for (day in daily) {
            val max = day.tempMaxC ?: break
            if (max < WeatherCodes.HEAT_WAVE_DAY_MAX_C) break
            days++
            if (max < WeatherCodes.HEAT_WAVE_SEVERE_DAY_MAX_C) allSevere = false
        }
        if (days == 0) return null
        return HotDayStreak(days = days, allSevere = allSevere)
    }

    /**
     * 剧烈降温 / 寒潮：优先用今日与明日最高温、最低温的日际降幅；
     * 无逐日数据时，回退为「约 24 小时后同时段」相对当前的降温（仍避开整夜最低温）。
     * ≥10°C 提醒；≥14°C 且降温后偏冷才升「寒潮」并使用雪天小熊，否则仍是添衣降温。
     */
    internal fun coldWaveSpec(
        current: CurrentWeather,
        hourly: List<HourlyWeather>,
        daily: List<DailyWeather>,
    ): Spec? {
        val assessment = coldWaveAssessment(current, hourly, daily) ?: return null
        if (assessment.dropC < WeatherCodes.COLD_WAVE_WATCH_DROP_C) return null
        val largeDrop = assessment.dropC >= WeatherCodes.COLD_WAVE_WARNING_DROP_C
        return if (assessment.wintry) {
            Spec(
                AlertKind.COLD_WAVE,
                if (largeDrop) AlertSeverity.WARNING else AlertSeverity.WATCH,
                if (largeDrop) {
                    R.string.alert_cold_wave_warning_title
                } else {
                    R.string.alert_cold_wave_title
                },
                if (largeDrop) {
                    R.string.alert_cold_wave_warning_msg
                } else {
                    R.string.alert_cold_wave_msg
                },
                assessment.dropC,
            )
        } else {
            // 如 37→26：降幅大也只报剧烈降温 + 添衣小熊，不用雪天寒潮形象
            Spec(
                AlertKind.COOLDOWN,
                if (largeDrop) AlertSeverity.WARNING else AlertSeverity.WATCH,
                R.string.alert_cold_wave_title,
                R.string.alert_cold_wave_msg,
                assessment.dropC,
            )
        }
    }

    internal data class ColdWaveAssessment(
        val dropC: Double,
        val wintry: Boolean,
    )

    /** 日际降温优先；否则用约 24h 后同时段气温相对当前的降幅。 */
    internal fun coldWaveAssessment(
        current: CurrentWeather,
        hourly: List<HourlyWeather>,
        daily: List<DailyWeather>,
    ): ColdWaveAssessment? {
        val today = daily.getOrNull(0)
        val tomorrow = daily.getOrNull(1)
        val maxDrop =
            today?.tempMaxC?.let { maxToday ->
                tomorrow?.tempMaxC?.let { maxToday - it }
            }
        val minDrop =
            today?.tempMinC?.let { minToday ->
                tomorrow?.tempMinC?.let { minToday - it }
            }
        val dayOverDay = listOfNotNull(maxDrop, minDrop).maxOrNull()
        if (dayOverDay != null) {
            val wintry =
                isWintryColdWaveDestination(
                    tomorrowMaxC = tomorrow?.tempMaxC,
                    tomorrowMinC = tomorrow?.tempMinC,
                )
            return ColdWaveAssessment(dayOverDay, wintry)
        }

        val nowTemp = current.temperatureC ?: current.apparentTemperatureC ?: return null
        val upcoming = upcomingFromCurrent(hourly, { it.time }, current.time, count = 30)
        // 取约 22–26 小时后的点，近似「明天同一时段」，而非未来整夜最低温
        val samePeriod = upcoming.drop(22).take(5).mapNotNull { it.temperatureC }
        val later = samePeriod.minOrNull() ?: return null
        return ColdWaveAssessment(
            dropC = nowTemp - later,
            wintry = later <= WeatherCodes.COLD_WAVE_WINTRY_MAX_C,
        )
    }

    /** 降温后是否已进入偏冷场景（才配雪天寒潮小熊 /「寒潮」文案）。 */
    internal fun isWintryColdWaveDestination(
        tomorrowMaxC: Double?,
        tomorrowMinC: Double?,
    ): Boolean {
        val max = tomorrowMaxC
        val min = tomorrowMinC
        if (max == null && min == null) return false
        return (max != null && max <= WeatherCodes.COLD_WAVE_WINTRY_MAX_C) ||
            (min != null && min <= WeatherCodes.COLD_WAVE_WINTRY_MIN_C)
    }

    /**
     * 近几小时累计降雨达阈值时提醒积水风险（与「当前雨势强」互补）。
     * 雨停后仍保留约 [WeatherCodes.PONDING_HOURS_AFTER_RAIN] 小时。
     */
    internal fun pondingSpec(
        current: CurrentWeather,
        hourly: List<HourlyWeather>,
    ): Spec? {
        val recent =
            recentHoursUpToCurrent(
                hourly,
                { it.time },
                current.time,
                count = 12,
            )
        if (recent.isEmpty()) return null

        fun sumLast(n: Int): Double = recent.takeLast(n.coerceAtMost(recent.size)).sumOf { it.precipitationMm ?: 0.0 }

        val sum3 = sumLast(3)
        val sum6 = sumLast(6)
        val sum12 = sumLast(12)
        val hit =
            sum3 >= WeatherCodes.PONDING_3H_MM ||
                sum6 >= WeatherCodes.PONDING_6H_MM ||
                sum12 >= WeatherCodes.PONDING_12H_MM
        if (!hit) return null

        // 仍在下雨，或近 2～3 小时内刚下过较明显的雨
        val rateNow = WeatherCodes.intensityPrecipMmPerHour(current) ?: 0.0
        val recentlyWet =
            rateNow >= 0.1 ||
                recent
                    .takeLast(WeatherCodes.PONDING_HOURS_AFTER_RAIN + 1)
                    .any { (it.precipitationMm ?: 0.0) >= 0.5 }
        if (!recentlyWet) return null

        val reportMm = maxOf(sum3, sum6, sum12)
        val warning =
            sum3 >= WeatherCodes.PONDING_WARNING_3H_MM ||
                sum6 >= WeatherCodes.PONDING_WARNING_6H_MM ||
                sum12 >= WeatherCodes.PONDING_WARNING_12H_MM

        return Spec(
            AlertKind.PONDING,
            if (warning) AlertSeverity.WARNING else AlertSeverity.WATCH,
            R.string.alert_ponding_title,
            R.string.alert_ponding_msg,
            reportMm,
        )
    }

    /**
     * 回顾近 24 小时降雪：按期间最强雪档决定「雪后路滑」持续时长。
     * 小雪约 3h、中雪约 6h、大雪约 12h、暴雪约 18h；气温偏暖则不提醒。
     */
    internal fun shouldAlertSnowyRoad(
        current: CurrentWeather,
        hourly: List<HourlyWeather>,
    ): Boolean {
        val recent =
            recentHoursUpToCurrent(
                hourly,
                { it.time },
                current.time,
                count = 24,
            )
        val snowHours = recent.filter { WeatherCodes.isSnowCode(it.weatherCode) }
        if (snowHours.isEmpty() && !WeatherCodes.isSnowCode(current.weatherCode)) {
            return false
        }

        val intensities =
            buildList {
                snowHours.forEach {
                    add(WeatherCodes.snowIntensity(it.weatherCode, it.precipitationMm))
                }
                if (WeatherCodes.isSnowCode(current.weatherCode)) {
                    add(
                        WeatherCodes.snowIntensity(
                            current.weatherCode,
                            WeatherCodes.intensityPrecipMmPerHour(current),
                        ),
                    )
                }
            }.filter { it != WeatherCodes.SnowIntensity.NONE }
        if (intensities.isEmpty()) return false

        val peak = intensities.max()
        val durationHours = WeatherCodes.snowyRoadAlertDurationHours(peak)
        if (durationHours <= 0) return false

        val lastSnowTime =
            snowHours.lastOrNull()?.time
                ?: current.time
        val hoursSinceSnow = hoursBetween(lastSnowTime, current.time)
        if (hoursSinceSnow > durationHours) return false

        val temp = current.apparentTemperatureC ?: current.temperatureC
        if (temp != null && temp > WeatherCodes.SNOWY_ROAD_MAX_TEMP_C) return false

        return true
    }
}

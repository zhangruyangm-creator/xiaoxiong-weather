package com.xiaoxiong.weather.util

import androidx.annotation.DrawableRes
import com.xiaoxiong.weather.R
import com.xiaoxiong.weather.data.AlertKind
import com.xiaoxiong.weather.data.AlertSeverity

/**
 * 预警类型 → 小熊插画资源。
 * 高温/连续高温/严寒/剧烈降温/寒潮/紫外线/大风/雷暴/冰雹/冻雨/强降雨/积水/大雪/雪天路滑/空气质量使用专属姿态；
 * 强降雨：提醒黄伞、预警红伞；未来降雨按峰值小时雨量换伞色；其余复用桌面天气小熊。
 */
object AlertBearIcon {
    @DrawableRes
    fun drawableRes(
        kind: AlertKind,
        precipMmPerHour: Double? = null,
        severity: AlertSeverity? = null
    ): Int = when (kind) {
        AlertKind.HEAT -> R.drawable.ic_alert_bear_hot
        AlertKind.HEAT_WAVE -> R.drawable.ic_alert_bear_heat_wave
        AlertKind.COLD -> R.drawable.ic_alert_bear_cold
        AlertKind.COOLDOWN -> R.drawable.ic_alert_bear_cooldown
        AlertKind.COLD_WAVE -> R.drawable.ic_alert_bear_cold_wave
        AlertKind.UV -> R.drawable.ic_alert_bear_uv
        AlertKind.WIND -> R.drawable.ic_alert_bear_wind
        AlertKind.STORM -> R.drawable.ic_alert_bear_storm
        AlertKind.HAIL -> R.drawable.ic_alert_bear_hail
        AlertKind.FREEZING_RAIN -> R.drawable.ic_alert_bear_freezing_rain
        AlertKind.AIR_QUALITY -> R.drawable.ic_alert_bear_aqi
        AlertKind.DUST -> R.drawable.ic_alert_bear_dust
        AlertKind.HEAVY_RAIN -> heavyRainUmbrellaRes(precipMmPerHour, severity)
        AlertKind.PONDING -> R.drawable.ic_alert_bear_ponding
        AlertKind.SNOW, AlertKind.SNOW_SOON -> snowBearRes(precipMmPerHour, severity)
        AlertKind.SNOWING -> R.drawable.ic_launcher_bear_snowy
        AlertKind.SNOWY_ROAD -> R.drawable.ic_alert_bear_snowy_road
        AlertKind.FOG, AlertKind.LOW_VISIBILITY -> R.drawable.ic_launcher_bear_foggy
        AlertKind.RAINING, AlertKind.RAIN_SOON -> rainSoonUmbrellaRes(precipMmPerHour)
        AlertKind.CLEAR -> R.drawable.ic_launcher_bear_sunny
    }

    /** 强降雪：预警或小时雪量达暴雪量级用暴雪小熊，其余用大雪小熊。 */
    @DrawableRes
    fun snowBearRes(
        precipMmPerHour: Double? = null,
        severity: AlertSeverity? = null
    ): Int {
        val blizzard = severity == AlertSeverity.WARNING ||
            (precipMmPerHour ?: 0.0) >= WeatherCodes.SNOW_HEAVY_MAX_MM_H
        return if (blizzard) {
            R.drawable.ic_launcher_bear_blizzard
        } else {
            R.drawable.ic_alert_bear_snow
        }
    }

    /** 强降雨：预警 / 暴雨量级用红伞，其余黄伞。 */
    @DrawableRes
    fun heavyRainUmbrellaRes(
        precipMmPerHour: Double? = null,
        severity: AlertSeverity? = null
    ): Int {
        val warning = severity == AlertSeverity.WARNING ||
            WeatherCodes.isHeavyRainWarningIntensity(precipMmPerHour)
        return if (warning) {
            R.drawable.ic_alert_bear_heavy_rain_warning
        } else {
            R.drawable.ic_alert_bear_heavy_rain
        }
    }

    /**
     * 未来降雨伞色：蓝 → 青绿 → 黄 → 红 → 黑（&gt;50 mm/小时）。
     */
    @DrawableRes
    fun rainSoonUmbrellaRes(peakMmPerHour: Double?): Int {
        val mm = peakMmPerHour ?: 0.0
        return when {
            mm > 50.0 -> R.drawable.ic_launcher_bear_extreme_rain
            mm > 16.0 -> R.drawable.ic_launcher_bear_violent_rain
            mm > 4.0 -> R.drawable.ic_launcher_bear_heavy_rain
            mm > 1.0 -> R.drawable.ic_launcher_bear_moderate_rain
            else -> R.drawable.ic_launcher_bear_rainy
        }
    }
}

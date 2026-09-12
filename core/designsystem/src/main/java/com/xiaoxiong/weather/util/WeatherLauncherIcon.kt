package com.xiaoxiong.weather.util

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.util.Log
import androidx.annotation.DrawableRes
import androidx.core.content.edit
import com.xiaoxiong.weather.R

/**
 * 按天气切换桌面启动图标（activity-alias）。
 *
 * 前台立刻切换 alias 会在部分机型杀进程；前台只排队，退后台再改。
 * applicationId 与 namespace 均为 com.xiaoxiong.weather。
 */
object WeatherLauncherIcon {
    private const val TAG = "WeatherLauncherIcon"
    private const val PREFS = "weather_launcher_icon"
    private const val KEY_ALIAS = "active_alias"
    private const val KEY_PENDING = "pending_alias"

    enum class Style(
        val aliasSimpleName: String,
    ) {
        SUNNY("Sunny"),

        /** 大体晴 / 少云（WMO code 1） */
        FEW_CLOUDS("FewClouds"),

        /** 少云夜间（WMO code 1） */
        FEW_CLOUDS_NIGHT("FewCloudsNight"),
        CLEAR_NIGHT("ClearNight"),
        PARTLY_CLOUDY("PartlyCloudy"),
        PARTLY_CLOUDY_NIGHT("PartlyCloudyNight"),
        CLOUDY("Cloudy"),

        /** 毛毛雨（无伞细雨） */
        DRIZZLE("Drizzle"),
        RAINY("Rainy"),
        MODERATE_RAIN("ModerateRain"),
        HEAVY_RAIN("HeavyRain"),

        /** 暴雨（≥16 mm/h，红伞） */
        VIOLENT_RAIN("ViolentRain"),

        /** 猛烈阵雨（&gt;32 mm/h，黑伞） */
        EXTREME_RAIN("ExtremeRain"),
        FREEZING_RAIN("FreezingRain"),

        /** 小雪 */
        SNOWY("Snowy"),

        /** 中雪 */
        MODERATE_SNOW("ModerateSnow"),

        /** 大雪 */
        HEAVY_SNOW("HeavySnow"),

        /** 暴雪 */
        BLIZZARD("Blizzard"),
        STORM("Storm"),
        FOGGY("Foggy"),
        HAIL("Hail"),
        ;

        fun componentName(context: Context): ComponentName =
            ComponentName(
                context.packageName,
                "${context.packageName}.launcher.$aliasSimpleName",
            )

        companion object {
            fun fromAlias(alias: String?): Style = entries.firstOrNull { it.aliasSimpleName == alias } ?: SUNNY
        }
    }

    /**
     * 仅按天气代码映射（兼容旧调用，默认白天）。
     * 降雨/降雪/昼夜请优先用 [fromWeather]。
     */
    fun fromWeatherCode(
        code: Int?,
        isDay: Boolean? = true,
    ): Style =
        when (code) {
            0 -> if (isDay == false) Style.CLEAR_NIGHT else Style.SUNNY
            1 -> if (isDay == false) Style.FEW_CLOUDS_NIGHT else Style.FEW_CLOUDS
            2 -> if (isDay == false) Style.PARTLY_CLOUDY_NIGHT else Style.PARTLY_CLOUDY
            3 -> Style.CLOUDY
            45, 48 -> Style.FOGGY
            51, 53, 55 -> Style.DRIZZLE
            61, 80 -> Style.RAINY
            63, 81 -> Style.MODERATE_RAIN
            65, 82 -> Style.HEAVY_RAIN
            56, 57, 66, 67 -> Style.FREEZING_RAIN
            71, 77, 85 -> Style.SNOWY
            73 -> Style.MODERATE_SNOW
            75 -> Style.HEAVY_SNOW
            86 -> Style.BLIZZARD
            95, 96 -> Style.STORM
            99 -> Style.HAIL
            else -> Style.CLOUDY
        }

    /**
     * 结合天气代码、昼夜与近 1 小时降水选图标。
     * 晴/多云夜间用月亮；毛毛雨单独一档；雨雪按强度分档；
     * 冰雹/冻雨优先；雷暴常见，但红伞/黑伞暴雨量级更稀少，雨强达标时盖过雷暴。
     */
    fun fromWeather(
        code: Int?,
        precipMmPerHour: Double? = null,
        isDay: Boolean? = true,
    ): Style {
        when (code) {
            99 -> return Style.HAIL
            56, 57, 66, 67 -> return Style.FREEZING_RAIN
            95, 96 -> {
                // 雷暴码：仅当雨强未达暴雨/猛烈时用雷暴图标，否则让红/黑伞胜出
                return when (WeatherCodes.rainIntensity(precipMmPerHour)) {
                    WeatherCodes.RainIntensity.TORRENTIAL -> Style.VIOLENT_RAIN
                    WeatherCodes.RainIntensity.VIOLENT -> Style.EXTREME_RAIN
                    else -> Style.STORM
                }
            }
        }
        if (WeatherCodes.isSnowCode(code)) {
            return when (WeatherCodes.snowIntensity(code, precipMmPerHour)) {
                WeatherCodes.SnowIntensity.BLIZZARD -> Style.BLIZZARD
                WeatherCodes.SnowIntensity.HEAVY -> Style.HEAVY_SNOW
                WeatherCodes.SnowIntensity.MODERATE -> Style.MODERATE_SNOW
                WeatherCodes.SnowIntensity.LIGHT,
                WeatherCodes.SnowIntensity.NONE,
                -> Style.SNOWY
            }
        }
        // 毛毛雨码且雨量仍弱：用无伞细雨图标；雨量上来后按雨强走
        if (WeatherCodes.isStillDrizzle(code, precipMmPerHour)) {
            return Style.DRIZZLE
        }
        // 先看雨强：阴/晴/雾码但正在下雨时不能提前 return 干图标
        return when (WeatherCodes.rainIntensity(precipMmPerHour)) {
            WeatherCodes.RainIntensity.VIOLENT -> Style.EXTREME_RAIN
            WeatherCodes.RainIntensity.TORRENTIAL -> Style.VIOLENT_RAIN
            WeatherCodes.RainIntensity.HEAVY -> Style.HEAVY_RAIN
            WeatherCodes.RainIntensity.MODERATE -> Style.MODERATE_RAIN
            WeatherCodes.RainIntensity.LIGHT -> Style.RAINY
            WeatherCodes.RainIntensity.NONE ->
                when (code) {
                    45, 48 -> Style.FOGGY
                    0 -> if (isDay == false) Style.CLEAR_NIGHT else Style.SUNNY
                    1 -> if (isDay == false) Style.FEW_CLOUDS_NIGHT else Style.FEW_CLOUDS
                    2 -> if (isDay == false) Style.PARTLY_CLOUDY_NIGHT else Style.PARTLY_CLOUDY
                    3 -> Style.CLOUDY
                    else -> fromWeatherCode(WeatherCodes.displayCode(code, precipMmPerHour), isDay)
                }
        }
    }

    @DrawableRes
    fun drawableRes(
        code: Int?,
        precipMmPerHour: Double? = null,
        isDay: Boolean? = true,
    ): Int =
        when (fromWeather(code, precipMmPerHour, isDay)) {
            Style.SUNNY -> R.drawable.ic_launcher_bear_sunny
            Style.FEW_CLOUDS -> R.drawable.ic_launcher_bear_few_clouds
            Style.FEW_CLOUDS_NIGHT -> R.drawable.ic_launcher_bear_few_clouds_night
            Style.CLEAR_NIGHT -> R.drawable.ic_launcher_bear_clear_night
            Style.PARTLY_CLOUDY -> R.drawable.ic_launcher_bear_partly_cloudy
            Style.PARTLY_CLOUDY_NIGHT -> R.drawable.ic_launcher_bear_partly_cloudy_night
            Style.CLOUDY -> R.drawable.ic_launcher_bear_cloudy
            Style.DRIZZLE -> R.drawable.ic_launcher_bear_drizzle
            Style.RAINY -> R.drawable.ic_launcher_bear_rainy
            Style.MODERATE_RAIN -> R.drawable.ic_launcher_bear_moderate_rain
            Style.HEAVY_RAIN -> R.drawable.ic_launcher_bear_heavy_rain
            Style.VIOLENT_RAIN -> R.drawable.ic_launcher_bear_violent_rain
            Style.EXTREME_RAIN -> R.drawable.ic_launcher_bear_extreme_rain
            Style.FREEZING_RAIN -> R.drawable.ic_launcher_bear_freezing_rain
            Style.SNOWY -> R.drawable.ic_launcher_bear_snowy
            Style.MODERATE_SNOW -> R.drawable.ic_launcher_bear_moderate_snow
            Style.HEAVY_SNOW -> R.drawable.ic_launcher_bear_heavy_snow
            Style.BLIZZARD -> R.drawable.ic_launcher_bear_blizzard
            Style.STORM -> R.drawable.ic_launcher_bear_storm
            Style.FOGGY -> R.drawable.ic_launcher_bear_foggy
            Style.HAIL -> R.drawable.ic_launcher_bear_hail
        }

    /** 前台调用：只排队，不立刻改组件。 */
    fun scheduleForWeatherCode(
        context: Context,
        weatherCode: Int?,
        precipMmPerHour: Double? = null,
        isDay: Boolean? = true,
    ) {
        val app = context.applicationContext
        val style = fromWeather(weatherCode, precipMmPerHour, isDay)
        val prefs = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val current = prefs.getString(KEY_ALIAS, Style.SUNNY.aliasSimpleName)
        if (current == style.aliasSimpleName) {
            prefs.edit { remove(KEY_PENDING) }
            return
        }
        prefs.edit { putString(KEY_PENDING, style.aliasSimpleName) }
        Log.d(TAG, "queued launcher icon -> ${style.aliasSimpleName}")
    }

    /** Activity onStop / 后台 Worker：执行排队中的图标切换。 */
    fun flushPending(context: Context) {
        val app = context.applicationContext
        val prefs = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val pending = prefs.getString(KEY_PENDING, null) ?: return
        val ok = apply(app, Style.fromAlias(pending))
        if (ok) {
            prefs.edit { remove(KEY_PENDING) }
        } else {
            Log.w(TAG, "keep pending alias=$pending after failed apply")
        }
    }

    /** 后台刷新等场景可直接切换（进程本就不在前台交互）。 */
    fun applyForWeatherCode(
        context: Context,
        weatherCode: Int?,
        precipMmPerHour: Double? = null,
        isDay: Boolean? = true,
    ) {
        apply(context, fromWeather(weatherCode, precipMmPerHour, isDay))
    }

    /** @return 是否已成功切到目标（或本就已是目标） */
    fun apply(
        context: Context,
        style: Style,
    ): Boolean {
        val app = context.applicationContext
        val prefs = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val previous = prefs.getString(KEY_ALIAS, Style.SUNNY.aliasSimpleName)
        if (previous == style.aliasSimpleName) {
            prefs.edit { remove(KEY_PENDING) }
            return true
        }

        val pm = app.packageManager
        return try {
            // 先 enable 目标再 disable 旧 alias，避免中间态「全部禁用」导致桌面图标短暂消失。
            // 短暂双启用可接受；异常恢复路径仍走全量校正。
            val previousKnown = Style.entries.any { it.aliasSimpleName == previous }
            pm.setComponentEnabledSetting(
                style.componentName(app),
                PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
                PackageManager.DONT_KILL_APP,
            )
            if (previousKnown) {
                val previousStyle = Style.fromAlias(previous)
                if (previousStyle != style) {
                    pm.setComponentEnabledSetting(
                        previousStyle.componentName(app),
                        PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                        PackageManager.DONT_KILL_APP,
                    )
                }
            } else {
                // 预置丢失/被清空：无法确定当前启用的 alias，全量校正，
                // 确保任何时刻只有一个 alias 启用（只在异常恢复时走慢路径）。
                Style.entries.forEach { candidate ->
                    if (candidate != style) {
                        pm.setComponentEnabledSetting(
                            candidate.componentName(app),
                            PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                            PackageManager.DONT_KILL_APP,
                        )
                    }
                }
            }
            // KEY_ALIAS 必须同步落盘，否则进程被杀后重启会与 PackageManager 的实际启用状态不一致
            prefs
                .edit()
                .putString(KEY_ALIAS, style.aliasSimpleName)
                .remove(KEY_PENDING)
                .commit()
            Log.d(TAG, "launcher icon -> ${style.aliasSimpleName}")
            true
        } catch (e: Exception) {
            Log.w(TAG, "failed to switch launcher icon to ${style.aliasSimpleName}", e)
            // 尽量恢复：至少保证默认晴天 alias 可用
            runCatching {
                pm.setComponentEnabledSetting(
                    Style.SUNNY.componentName(app),
                    PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
                    PackageManager.DONT_KILL_APP,
                )
            }
            false
        }
    }
}

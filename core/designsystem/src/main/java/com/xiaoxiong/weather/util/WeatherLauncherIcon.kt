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
 * 前台立刻切换 alias 会在部分机型杀进程：前台 [scheduleForWeatherCode] 只排队，
 * 退后台（MainActivity.onStop → [flushPending]）或后台 Worker（[applyForWeatherCode]）再应用。
 * applicationId 与 namespace 均为 com.xiaoxiong.weather。
 */
object WeatherLauncherIcon {
    private const val TAG = "WeatherLauncherIcon"
    private const val PREFS = "weather_launcher_icon"
    private const val KEY_ALIAS = "active_alias"
    private const val KEY_PENDING = "pending_alias"

    /** 只保护 pending/alias 的 prefs 读写（短锁），主线程 schedule 不阻塞在 PM 操作上。 */
    private val pendingLock = Any()

    /** 只保护 PackageManager 应用与全量校正（长锁）；与 [pendingLock] 分开避免主线程被拖住。 */
    private val pmLock = Any()

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
            68, 69 -> Style.SNOWY
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

    /** 前台调用：短锁排队 pending，不做 PackageManager 操作；退后台/Worker 再应用（见 [flushPending]）。 */
    fun scheduleForWeatherCode(
        context: Context,
        weatherCode: Int?,
        precipMmPerHour: Double? = null,
        isDay: Boolean? = true,
    ) {
        val app = context.applicationContext
        val style = fromWeather(weatherCode, precipMmPerHour, isDay)
        synchronized(pendingLock) {
            val prefs = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            val current = prefs.getString(KEY_ALIAS, Style.SUNNY.aliasSimpleName)
            if (current == style.aliasSimpleName) {
                prefs.edit { remove(KEY_PENDING) }
            } else {
                prefs.edit { putString(KEY_PENDING, style.aliasSimpleName) }
                Log.d(TAG, "queued launcher icon -> ${style.aliasSimpleName}")
            }
        }
    }

    /** Activity onStop / 后台 Worker：执行排队中的图标切换。 */
    fun flushPending(context: Context) {
        val app = context.applicationContext
        val pending =
            synchronized(pendingLock) {
                val prefs = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                val value = prefs.getString(KEY_PENDING, null) ?: return
                // 先读出并清除：PM 全程持长锁，期间主线程仍可短锁排队新 pending
                prefs.edit { remove(KEY_PENDING) }
                Style.fromAlias(value)
            }
        if (!synchronized(pmLock) { applyLocked(app, pending) }) {
            synchronized(pendingLock) {
                val prefs = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                // 已有更新排队时不回滚旧目标，避免覆盖主线程刚写入的 pending
                if (prefs.getString(KEY_PENDING, null) == null) {
                    prefs.edit { putString(KEY_PENDING, pending.aliasSimpleName) }
                }
            }
            Log.w(TAG, "keep pending alias=${pending.aliasSimpleName} after failed apply")
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

    /**
     * 启动时以 PackageManager 实际启用的 alias 为准对账：
     * 消除进程被杀导致 `apply()` 未落盘、或历史异常路径残留多个 enabled alias 的问题。
     * 只读 20 次组件状态，仅在需要时做全量校正（调用方应放在后台线程）。
     */
    fun reconcile(context: Context) {
        val app = context.applicationContext
        synchronized(pmLock) {
            val prefs = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            val stored = Style.fromAlias(prefs.getString(KEY_ALIAS, null))
            val pm = app.packageManager
            val enabled = Style.entries.filter { isEnabled(pm, it.componentName(app)) }
            val target = reconcileTarget(stored, enabled)
            val alreadyCorrect = enabled.size == 1 && enabled.first() == target && stored == target
            val corrected = alreadyCorrect || runCatching { correctAll(app, pm, target) }.isSuccess
            if (corrected) {
                writeAlias(app, target)
            }
            Log.d(
                TAG,
                "reconciled launcher icon -> ${target.aliasSimpleName} " +
                    "(enabled=$enabled, stored=${stored.aliasSimpleName}, corrected=$corrected)",
            )
        }
    }

    /** @return 是否已成功切到目标 */
    fun apply(
        context: Context,
        style: Style,
    ): Boolean {
        val app = context.applicationContext
        return synchronized(pmLock) { applyLocked(app, style) }
    }

    /**
     * 全量校正：先 enable 目标，再 disable 其余全部 [Style.entries]。
     * 顺序保证中间态不会「全部禁用」导致桌面图标消失；结束后最多只剩目标启用。
     */
    private fun applyLocked(
        app: Context,
        style: Style,
    ): Boolean {
        val pm = app.packageManager
        return try {
            correctAll(app, pm, style)
            // apply()：主线程不做 commit 磁盘等待；进程内由锁串行，
            // 跨进程被杀后由启动时 reconcile 用 PackageManager 实际状态对账
            writeAlias(app, style)
            Log.d(TAG, "launcher icon -> ${style.aliasSimpleName}")
            true
        } catch (e: Exception) {
            Log.w(TAG, "failed to switch launcher icon to ${style.aliasSimpleName}", e)
            // 失败恢复同样走全量校正：只留 SUNNY，避免残留多个 enabled alias
            val recovered = runCatching { correctAll(app, pm, Style.SUNNY) }.isSuccess
            if (recovered) {
                writeAlias(app, Style.SUNNY)
            }
            false
        }
    }

    /** 写入生效 alias（apply 语义）；只清除与本次目标一致的 pending，避免误删新排队目标。 */
    private fun writeAlias(
        app: Context,
        style: Style,
    ) {
        synchronized(pendingLock) {
            val prefs = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            prefs.edit {
                putString(KEY_ALIAS, style.aliasSimpleName)
                if (shouldClearPending(prefs.getString(KEY_PENDING, null), style)) {
                    remove(KEY_PENDING)
                }
            }
        }
    }

    /** enable [target]，disable 其余全部 alias；任一步失败抛异常由调用方处理。 */
    private fun correctAll(
        app: Context,
        pm: PackageManager,
        target: Style,
    ) {
        pm.setComponentEnabledSetting(
            target.componentName(app),
            PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
            PackageManager.DONT_KILL_APP,
        )
        Style.entries.forEach { candidate ->
            if (candidate != target) {
                pm.setComponentEnabledSetting(
                    candidate.componentName(app),
                    PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                    PackageManager.DONT_KILL_APP,
                )
            }
        }
    }

    private fun isEnabled(
        pm: PackageManager,
        component: ComponentName,
    ): Boolean =
        when (pm.getComponentEnabledSetting(component)) {
            PackageManager.COMPONENT_ENABLED_STATE_ENABLED -> true
            PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
            PackageManager.COMPONENT_ENABLED_STATE_DISABLED_USER,
            PackageManager.COMPONENT_ENABLED_STATE_DISABLED_UNTIL_USED,
            -> false
            else ->
                runCatching { pm.getActivityInfo(component, 0).enabled }
                    .getOrDefault(true)
        }
}

/** 生效 alias 写入后是否应清除 pending：只有 pending 与已生效目标一致时才清，避免误删新排队目标。 */
internal fun shouldClearPending(
    pendingAlias: String?,
    applied: WeatherLauncherIcon.Style,
): Boolean = pendingAlias != null && pendingAlias == applied.aliasSimpleName

/**
 * 启动对账时选目标 alias：PackageManager 实际状态优先于 prefs；
 * 多个残留 enabled 时优先保留 prefs 记录的那个，保证全量校正后与用户预期一致。
 */
internal fun reconcileTarget(
    stored: WeatherLauncherIcon.Style,
    enabled: List<WeatherLauncherIcon.Style>,
): WeatherLauncherIcon.Style =
    when {
        enabled.isEmpty() -> stored
        enabled.size == 1 -> enabled.first()
        stored in enabled -> stored
        else -> enabled.first()
    }

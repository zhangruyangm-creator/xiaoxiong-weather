package com.xiaoxiong.weather.widget

import android.content.Context
import androidx.annotation.DrawableRes
import com.xiaoxiong.weather.R
import com.xiaoxiong.weather.data.City
import com.xiaoxiong.weather.data.LifeIndexLevel
import com.xiaoxiong.weather.data.SelectedCityStore
import com.xiaoxiong.weather.data.TemperatureUnit
import com.xiaoxiong.weather.data.WeatherRefreshPolicy
import com.xiaoxiong.weather.data.WeatherSnapshot
import com.xiaoxiong.weather.di.AppContainer
import com.xiaoxiong.weather.di.appContainer
import com.xiaoxiong.weather.util.WeatherCodes
import com.xiaoxiong.weather.util.WeatherLauncherIcon
import com.xiaoxiong.weather.util.formatHourLabel
import com.xiaoxiong.weather.util.formatTemperature
import com.xiaoxiong.weather.util.upcomingFromCurrent
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.abs

object WeatherWidgetData {
    /** 多个 widget 并发 provideGlance 时的内存去重窗口：窗口内只允许一次真正读取/联网。 */
    private const val LOAD_DEDUP_TTL_MS = 30_000L

    private val loadGate = WidgetLoadGate(ttlMs = LOAD_DEDUP_TTL_MS)

    /**
     * 数据源刚刷新后调用：清掉 30s 去重缓存，保证随后的 `updateAll`
     * 不会把旧渲染复用出去。三类 widget 共享同一份新鲜结果。
     */
    suspend fun invalidateLoadCache() {
        loadGate.invalidate()
    }

    suspend fun load(context: Context): WeatherWidgetUi? =
        try {
            loadOrNull(context)
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            // DataStore 损坏/IO 异常若从 provideGlance 抛出会直接崩溃小组件，降级为空态
            null
        }

    private suspend fun loadOrNull(context: Context): WeatherWidgetUi? {
        val container = context.appContainer()
        // 同一次 load 只读一次设置，避免多次 getOnce() 的重复 IO
        val settings = container.settingsStore.getOnce()
        val selection = container.selectedCityStore.getOnce() ?: return null
        if (!settings.privacyAccepted) {
            // 隐私未同意：不联网、不定位，尽量展示已有缓存；无缓存则空白占位
            val cached = container.weatherCacheStore.get(selection.city) ?: return null
            return toUi(
                context = context,
                snapshot = cached.snapshot,
                savedAtEpochMs = cached.savedAtEpochMs,
                offline = true,
                unit = settings.temperatureUnit,
            )
        }
        // 只按「位置模式 + 选中城市」做键：30s 内定位漂移不值当再联网，WIDGET_STALE_MS 会兜底
        val cityKey = "${selection.useLocation}|${selection.city.name}"
        return loadGate.load(cityKey) {
            loadAccepted(context, container, selection, settings.temperatureUnit)
        }
    }

    private suspend fun loadAccepted(
        context: Context,
        container: AppContainer,
        selection: SelectedCityStore.PersistedSelection,
        unit: TemperatureUnit,
    ): WeatherWidgetUi? {
        val cacheStore = container.weatherCacheStore
        val locationHelper = container.locationHelper

        val city =
            if (selection.useLocation && locationHelper.hasLocationPermission()) {
                runCatching { locationHelper.resolveCurrentCityReuseLast() }.getOrDefault(selection.city)
            } else {
                selection.city
            }

        val cached = cacheStore.get(city)
        val now = System.currentTimeMillis()
        val moved = selection.useLocation && !roughlySameCity(selection.city, city)
        val stale =
            cached == null ||
                now - cached.savedAtEpochMs >= WeatherRefreshPolicy.WIDGET_STALE_MS ||
                moved

        if (stale) {
            // 显式透传 CancellationException：Glance 取消 widget update 时 runCatching
            // 会吞掉取消信号，导致挂后台的 widget 协程"僵尸"继续跑、且最终走 cached 分支
            // 返回旧数据，看起来"在线但不刷新"
            val refreshed =
                try {
                    val snapshot = container.weatherRepository.fetchWeather(city)
                    cacheStore.save(snapshot)
                    if (selection.useLocation) {
                        container.selectedCityStore.saveIfUnchanged(selection, city, useLocation = true)
                    }
                    snapshot to System.currentTimeMillis()
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    null
                }

            if (refreshed != null) {
                return toUi(context, refreshed.first, refreshed.second, offline = false, unit = unit)
            }
        }

        val entry = cached ?: return null
        return toUi(
            context = context,
            snapshot = entry.snapshot,
            savedAtEpochMs = entry.savedAtEpochMs,
            offline = stale,
            unit = unit,
        )
    }

    private fun roughlySameCity(
        a: City,
        b: City,
    ): Boolean = abs(a.latitude - b.latitude) < 0.01 && abs(a.longitude - b.longitude) < 0.01

    private fun toUi(
        context: Context,
        snapshot: WeatherSnapshot,
        savedAtEpochMs: Long,
        offline: Boolean,
        unit: TemperatureUnit,
    ): WeatherWidgetUi {
        val current = snapshot.current
        val today = snapshot.daily.firstOrNull()
        val tomorrow = snapshot.daily.getOrNull(1)
        val highLow =
            buildString {
                append(formatTemperature(today?.tempMinC, unit))
                append(" / ")
                append(formatTemperature(today?.tempMaxC, unit))
            }
        val tomorrowHighLow =
            if (tomorrow != null) {
                buildString {
                    append(formatTemperature(tomorrow.tempMinC, unit))
                    append(" / ")
                    append(formatTemperature(tomorrow.tempMaxC, unit))
                }
            } else {
                ""
            }
        val hours =
            upcomingFromCurrent(
                snapshot.hourly,
                { it.time },
                current.time,
                count = 8,
            ).map { hour ->
                WeatherWidgetHourUi(
                    label = formatHourLabel(hour.time),
                    tempText = formatTemperature(hour.temperatureC, unit),
                    emoji = widgetEmoji(hour.weatherCode, isWidgetDaytime(hour.time)),
                )
            }
        val precipRate = WeatherCodes.intensityPrecipMmPerHour(current)
        val displayCode = WeatherCodes.displayCode(current.weatherCode, precipRate)
        val tip =
            snapshot.lifeIndices
                .firstOrNull { it.level != LifeIndexLevel.GOOD }
                ?.let { "${it.title}·${it.summary}" }
                ?: snapshot.lifeIndices.firstOrNull()?.let { "${it.title}·${it.summary}" }
                ?: snapshot.advice.orEmpty()

        return WeatherWidgetUi(
            cityName = snapshot.city.name,
            emoji = widgetEmoji(displayCode, current.isDay != false),
            bearRes =
                WeatherLauncherIcon.drawableRes(
                    current.weatherCode,
                    precipRate,
                    current.isDay,
                ),
            temperatureText = formatTemperature(current.temperatureC, unit),
            description =
                WeatherCodes.description(
                    context,
                    displayCode,
                    precipRate,
                ),
            highLowText = highLow,
            updatedText = formatUpdated(context, savedAtEpochMs),
            isOfflineHint = offline,
            tomorrowLabel = context.getString(R.string.widget_tomorrow),
            tomorrowHighLowText = tomorrowHighLow,
            // 逐日预报只有全天概况、没有具体时刻：固定用白天 emoji
            tomorrowEmoji = widgetEmoji(tomorrow?.weatherCode, isDay = true),
            tomorrowDescription = WeatherCodes.description(context, tomorrow?.weatherCode),
            hours = hours,
            tipText = tip,
            bgRes = widgetBgRes(displayCode, current.isDay),
            feelsLikeText = current.apparentTemperatureC?.let { formatTemperature(it, unit) } ?: "",
            humidityText = current.humidityPercent?.let { "$it%" } ?: "--",
            windText = current.windSpeedKmh?.let { "${"%.0f".format(it)} km/h" } ?: "--",
            pressureText = current.pressureHpa?.let { "${"%.0f".format(it)} hPa" } ?: "--",
        )
    }

    /**
     * 与主 App 天气场景渐变保持一致：按天气码和昼夜挑选背景。
     * 浅色雪景白字不可读，故雪天使用深色版本。
     */
    @DrawableRes
    private fun widgetBgRes(
        code: Int?,
        isDay: Boolean?,
    ): Int =
        when {
            WeatherCodes.isSnowCode(code) -> R.drawable.widget_bg_snow
            code == 3 || code == 45 || code == 48 -> R.drawable.widget_bg_overcast
            code == 2 -> R.drawable.widget_bg_cloudy
            code == 0 || code == 1 || code == null ->
                if (isDay != false) R.drawable.widget_bg_sunny else R.drawable.widget_bg_night
            else -> R.drawable.widget_bg_rain
        }

    private fun formatUpdated(
        context: Context,
        epochMs: Long,
    ): String {
        val time =
            Instant
                .ofEpochMilli(epochMs)
                .atZone(ZoneId.systemDefault())
                .format(DateTimeFormatter.ofPattern("HH:mm"))
        return context.getString(R.string.widget_updated, time)
    }
}

/**
 * 小组件并发加载闸门：同一时刻只允许一个 [load] 真正执行，其余挂起等待后复用结果。
 * 结果含 `lastUi`/`lastAt`/`lastCityKey`，TTL 与城市键任一变化都会触发重新加载，
 * 从而让三类 widget 的并发 `provideGlance` 不会各联一次网。
 */
internal class WidgetLoadGate(
    private val ttlMs: Long,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private val mutex = Mutex()
    private var lastUi: WeatherWidgetUi? = null
    private var lastAt = 0L
    private var lastCityKey: String? = null
    private var hasResult = false

    suspend fun load(
        cityKey: String,
        fetch: suspend () -> WeatherWidgetUi?,
    ): WeatherWidgetUi? =
        mutex.withLock {
            val now = clock()
            if (hasResult && lastCityKey == cityKey && now - lastAt < ttlMs) {
                return lastUi
            }
            val ui = fetch()
            lastUi = ui
            lastAt = clock()
            lastCityKey = cityKey
            hasResult = true
            ui
        }

    suspend fun invalidate() {
        mutex.withLock { hasResult = false }
    }
}

/** 小时/当前天气 emoji：直接复用 core:common 的昼夜口径（夜间晴/云 → 月亮）。 */
internal fun widgetEmoji(
    code: Int?,
    isDay: Boolean,
): String = WeatherCodes.emoji(code, isDay)

/** 本地 6:00–18:59 视为白天，与常见天气 App 的昼夜展示一致。 */
internal fun isWidgetDaytime(isoTime: String?): Boolean {
    val timePart =
        isoTime?.substringAfter('T')?.takeUnless { it == isoTime }
            ?: isoTime?.substringAfter(' ')
    val hour = timePart?.substringBefore(':')?.toIntOrNull() ?: return true
    return hour in 6..18
}

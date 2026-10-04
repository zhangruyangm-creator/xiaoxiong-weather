package com.xiaoxiong.weather.widget

import android.content.Context
import android.util.Log
import androidx.annotation.DrawableRes
import com.xiaoxiong.weather.R
import com.xiaoxiong.weather.data.LifeIndexLevel
import com.xiaoxiong.weather.data.SelectedCityStore
import com.xiaoxiong.weather.data.SelectedCityStore.PersistedSelection
import com.xiaoxiong.weather.data.TemperatureUnit
import com.xiaoxiong.weather.data.WeatherRefreshPolicy
import com.xiaoxiong.weather.data.WeatherSnapshot
import com.xiaoxiong.weather.data.isSamePlace
import com.xiaoxiong.weather.data.presentationStampOf
import com.xiaoxiong.weather.di.AppContainer
import com.xiaoxiong.weather.di.appContainer
import com.xiaoxiong.weather.util.CrashReporting
import com.xiaoxiong.weather.util.WeatherCodes
import com.xiaoxiong.weather.util.WeatherLauncherIcon
import com.xiaoxiong.weather.util.formatHourLabel
import com.xiaoxiong.weather.util.formatTemperature
import com.xiaoxiong.weather.util.runCatchingCancellable
import com.xiaoxiong.weather.util.upcomingFromCurrent
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.round

/** 线程安全的「更新时间」格式：DateTimeFormatter 不可变，避免每次渲染重复创建。 */
private val WIDGET_UPDATED_TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm", Locale.US)

object WeatherWidgetData {
    private const val TAG = "WeatherWidgetData"

    /** 多个 widget 并发 provideGlance 时的内存去重窗口：窗口内只允许一次真正读取/联网。 */
    private const val LOAD_DEDUP_TTL_MS = 30_000L

    private val loadGate = WidgetLoadGate(ttlMs = LOAD_DEDUP_TTL_MS)

    /**
     * 30s 去重窗口的缓存键。
     *
     * 必须含坐标而不只是地名：多个城市可同名（多个"南京"），定位移动后地名也可能
     * 不变（"当前位置"）。只用 `useLocation + 城市名` 会让不同位置互相命中缓存，
     * 短时间显示另一城市或旧位置的天气。坐标量化到约 100m，与城市去重同一口径。
     */
    internal fun widgetLoadKey(selection: PersistedSelection): String =
        "${selection.useLocation}|${selection.city.name}|" +
            "${round(selection.city.latitude * 1000.0).toLong()}," +
            round(selection.city.longitude * 1000.0).toLong()

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
        } catch (e: Exception) {
            // DataStore 损坏/IO 异常若从 provideGlance 抛出会直接崩溃小组件，降级为空态
            CrashReporting.record(e)
            null
        }

    private suspend fun loadOrNull(context: Context): WeatherWidgetUi? {
        val container = context.appContainer()
        // 同一次 load 只读一次设置，避免多次 getOnce() 的重复 IO
        val settings = container.settingsStore.getOnce()
        val selection = container.selectedCityStore.getOnce() ?: return null
        if (!settings.privacyAccepted) {
            // 隐私未同意：不联网、不定位，尽量展示已有缓存；无缓存则空白占位
            val stamp = presentationStampOf(context.resources.configuration.locales[0] ?: Locale.getDefault(), settings.temperatureUnit)
            val cached = container.weatherCacheStore.get(selection.city, stamp) ?: return null
            return toUi(
                context = context,
                snapshot = cached.snapshot,
                savedAtEpochMs = cached.savedAtEpochMs,
                offline = true,
                unit = settings.temperatureUnit,
            )
        }
        // 键必须区分「不同城市但同名」与「定位漂移」：原先只用 useLocation + 城市名，
        // 于是同名城市（多个"南京"）会互相命中 30s 缓存，定位移动但地名不变时
        // 也会显示旧位置的天气。
        return loadGate.load(widgetLoadKey(selection)) {
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
                // 透传取消：Glance 取消 widget update 时不能把取消吞成「定位失败回退选中城市」
                runCatchingCancellable {
                    locationHelper.resolveCurrentCityReuseLast()
                }.getOrDefault(selection.city)
            } else {
                selection.city
            }

        val stamp = presentationStampOf(context.resources.configuration.locales[0] ?: Locale.getDefault(), unit)
        val cached = cacheStore.get(city, stamp)
        val now = System.currentTimeMillis()
        val moved = selection.useLocation && !selection.city.isSamePlace(city)
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
                    val fetchStartedAt = System.currentTimeMillis()
                    val snapshot = container.weatherRepository.fetchWeather(city)
                    // 新数据已到手：落盘/写选择失败只记日志，不能把内存快照一起丢掉
                    try {
                        cacheStore.save(snapshot, stamp, fetchStartedAt)
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        CrashReporting.record(e)
                        Log.w(TAG, "failed to save weather cache", e)
                    }
                    if (selection.useLocation) {
                        try {
                            container.selectedCityStore.saveIfUnchanged(selection, city, useLocation = true)
                        } catch (e: CancellationException) {
                            throw e
                        } catch (e: Exception) {
                            CrashReporting.record(e)
                            Log.w(TAG, "failed to persist selected city", e)
                        }
                    }
                    snapshot to System.currentTimeMillis()
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    CrashReporting.record(e)
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
            windText = current.windSpeedKmh?.let { "${String.format(Locale.US, "%.0f", it)} km/h" } ?: "--",
            pressureText = current.pressureHpa?.let { "${String.format(Locale.US, "%.0f", it)} hPa" } ?: "--",
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
                .format(WIDGET_UPDATED_TIME_FORMATTER)
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

/** 本地 6:00–17:59 视为白天，与 [com.xiaoxiong.weather.util.isDaytimeHour] 口径一致。 */
internal fun isWidgetDaytime(isoTime: String?): Boolean {
    val timePart =
        isoTime?.substringAfter('T')?.takeUnless { it == isoTime }
            ?: isoTime?.substringAfter(' ')
    val hour = timePart?.substringBefore(':')?.toIntOrNull() ?: return true
    return hour in 6 until 18
}

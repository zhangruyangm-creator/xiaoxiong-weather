package com.xiaoxiong.weather.widget

import android.content.Context
import androidx.annotation.DrawableRes
import com.xiaoxiong.weather.R
import com.xiaoxiong.weather.data.City
import com.xiaoxiong.weather.data.LifeIndexLevel
import com.xiaoxiong.weather.data.TemperatureUnit
import com.xiaoxiong.weather.data.WeatherRefreshPolicy
import com.xiaoxiong.weather.data.WeatherSnapshot
import com.xiaoxiong.weather.di.appContainer
import com.xiaoxiong.weather.util.WeatherCodes
import com.xiaoxiong.weather.util.WeatherLauncherIcon
import com.xiaoxiong.weather.util.formatHourLabel
import com.xiaoxiong.weather.util.formatTemperature
import com.xiaoxiong.weather.util.upcomingFromCurrent
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.abs

object WeatherWidgetData {
    /**
     * 定位模式先解析当前位置；缓存未过期且位置未明显变化时直接展示，
     * 否则联网刷新。
     */
    suspend fun load(context: Context): WeatherWidgetUi? {
        val container = context.appContainer()
        // 隐私未同意：不联网、不定位，尽量展示已有缓存；无缓存则空白占位
        if (!container.settingsStore.getOnce().privacyAccepted) {
            val selection = container.selectedCityStore.getOnce() ?: return null
            val unit = container.settingsStore.getOnce().temperatureUnit
            val cached = container.weatherCacheStore.get(selection.city) ?: return null
            return toUi(
                context = context,
                snapshot = cached.snapshot,
                savedAtEpochMs = cached.savedAtEpochMs,
                offline = true,
                unit = unit,
            )
        }
        val selection = container.selectedCityStore.getOnce() ?: return null
        val unit = container.settingsStore.getOnce().temperatureUnit
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
                        container.selectedCityStore.save(city, useLocation = true)
                    }
                    snapshot to System.currentTimeMillis()
                } catch (e: kotlinx.coroutines.CancellationException) {
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
                    emoji = WeatherCodes.emoji(hour.weatherCode),
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
            emoji = WeatherCodes.emoji(displayCode),
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
            tomorrowEmoji = WeatherCodes.emoji(tomorrow?.weatherCode),
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

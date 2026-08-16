package com.xiaoxiong.weather.data

import android.content.Context
import com.xiaoxiong.weather.util.WeatherLauncherIcon
import com.xiaoxiong.weather.widget.WeatherWidgetUpdater

/**
 * 正式呈现副作用实现：随 :app 层驻留，绑定启动器图标（designsystem）与小组件刷新（widget）。
 * 供 [WeatherViewModelFactory] 注入到 feature:weather 的 ViewModel。
 */
object DefaultWeatherPresentationEffects : WeatherPresentationEffects {
    override fun scheduleLauncherIcon(
        context: Context,
        weatherCode: Int?,
        precipMmPerHour: Double?,
        isDay: Boolean?,
    ) {
        WeatherLauncherIcon.scheduleForWeatherCode(
            context,
            weatherCode,
            precipMmPerHour,
            isDay,
        )
    }

    override suspend fun refreshWidgets(context: Context) {
        WeatherWidgetUpdater.updateAll(context)
    }
}

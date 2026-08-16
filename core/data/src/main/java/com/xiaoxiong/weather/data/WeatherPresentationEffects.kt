package com.xiaoxiong.weather.data

import android.content.Context

/**
 * 呈现副作用端口：启动器图标切换、小组件刷新。
 * 接口与 NoOp 供上层（feature/app）注入，避免 :core:data 反向依赖 widget/图标实现。
 */
interface WeatherPresentationEffects {
    fun scheduleLauncherIcon(
        context: Context,
        weatherCode: Int?,
        precipMmPerHour: Double? = null,
        isDay: Boolean? = true,
    )

    suspend fun refreshWidgets(context: Context)
}

/** 空实现：测试或未挂载副作用时使用。 */
object NoOpWeatherPresentationEffects : WeatherPresentationEffects {
    override fun scheduleLauncherIcon(
        context: Context,
        weatherCode: Int?,
        precipMmPerHour: Double?,
        isDay: Boolean?,
    ) = Unit

    override suspend fun refreshWidgets(context: Context) = Unit
}

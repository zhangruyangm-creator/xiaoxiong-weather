package com.xiaoxiong.weather.widget

import androidx.annotation.DrawableRes

data class WeatherWidgetHourUi(
    val label: String,
    val tempText: String,
    val emoji: String,
)

data class WeatherWidgetUi(
    val cityName: String,
    val emoji: String,
    @get:DrawableRes val bearRes: Int,
    val temperatureText: String,
    val description: String,
    val highLowText: String,
    val updatedText: String,
    val isOfflineHint: Boolean = false,
    val tomorrowLabel: String = "",
    val tomorrowHighLowText: String = "",
    val tomorrowEmoji: String = "",
    val tomorrowDescription: String = "",
    val hours: List<WeatherWidgetHourUi> = emptyList(),
    val tipText: String = "",
    /** 按当前天气场景选择的渐变背景 drawable。 */
    @get:DrawableRes val bgRes: Int = 0,
    /** 大尺寸卡片补充指标。 */
    val feelsLikeText: String = "",
    val humidityText: String = "",
    val windText: String = "",
    val pressureText: String = "",
)

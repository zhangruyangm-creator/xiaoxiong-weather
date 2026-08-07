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
)

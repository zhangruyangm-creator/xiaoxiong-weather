package com.xiaoxiong.weather.widget

import android.content.Context
import androidx.glance.appwidget.updateAll

object WeatherWidgetUpdater {
    suspend fun updateAll(context: Context) {
        WeatherAppWidget().updateAll(context.applicationContext)
    }
}

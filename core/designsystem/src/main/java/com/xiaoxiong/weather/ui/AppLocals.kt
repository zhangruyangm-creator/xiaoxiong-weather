package com.xiaoxiong.weather.ui

import androidx.compose.runtime.staticCompositionLocalOf
import com.xiaoxiong.weather.data.TemperatureUnit

val LocalTemperatureUnit = staticCompositionLocalOf { TemperatureUnit.CELSIUS }

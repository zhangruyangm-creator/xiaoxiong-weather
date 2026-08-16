package com.xiaoxiong.weather.data

import com.xiaoxiong.weather.R
import com.xiaoxiong.weather.fixtures.WeatherFixtures
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class WeatherAdviceGeneratorTest {
    @Test
    fun clothing_byFeelsLikeBands() {
        assertEquals(R.string.advice_clothing_hot, WeatherAdviceGenerator.clothingRes(33.0))
        assertEquals(R.string.advice_clothing_warm, WeatherAdviceGenerator.clothingRes(27.0))
        assertEquals(R.string.advice_clothing_mild, WeatherAdviceGenerator.clothingRes(20.0))
        assertEquals(R.string.advice_clothing_cool, WeatherAdviceGenerator.clothingRes(12.0))
        assertEquals(R.string.advice_clothing_cold, WeatherAdviceGenerator.clothingRes(5.0))
        assertEquals(R.string.advice_clothing_freezing, WeatherAdviceGenerator.clothingRes(-3.0))
        assertEquals(R.string.advice_clothing_unknown, WeatherAdviceGenerator.clothingRes(null))
    }

    @Test
    fun rainTip_nowUpcomingAndSnow() {
        assertEquals(
            R.string.advice_rain_now,
            WeatherAdviceGenerator.rainTipRes(61, 1.0, upcomingPrecip = false),
        )
        assertNull(
            WeatherAdviceGenerator.rainTipRes(61, 0.0, upcomingPrecip = false),
        )
        assertEquals(
            R.string.advice_rain_snow,
            WeatherAdviceGenerator.rainTipRes(71, 0.0, upcomingPrecip = false),
        )
        assertEquals(
            R.string.advice_rain_soon,
            WeatherAdviceGenerator.rainTipRes(0, 0.0, upcomingPrecip = true),
        )
        assertNull(WeatherAdviceGenerator.rainTipRes(0, 0.0, upcomingPrecip = false))
    }

    @Test
    fun uvAndWindTips() {
        assertEquals(R.string.advice_uv_strong, WeatherAdviceGenerator.uvTipRes(8.0))
        assertEquals(R.string.advice_uv_moderate, WeatherAdviceGenerator.uvTipRes(5.5))
        assertNull(WeatherAdviceGenerator.uvTipRes(3.0))
        assertNull(WeatherAdviceGenerator.uvTipRes(9.0, isDay = false))
        assertEquals(R.string.advice_wind_strong, WeatherAdviceGenerator.windTipRes(45.0))
        assertNull(WeatherAdviceGenerator.windTipRes(20.0))
    }

    @Test
    fun calmClearDay_onlyClothing() {
        val current = WeatherFixtures.current(weatherCode = 0, temperatureC = 22.0)
        assertEquals(
            R.string.advice_clothing_mild,
            WeatherAdviceGenerator.clothingRes(current.apparentTemperatureC ?: current.temperatureC),
        )
        assertNull(
            WeatherAdviceGenerator.rainTipRes(
                current.weatherCode,
                current.precipitationMm,
                upcomingPrecip = false,
            ),
        )
        assertNull(WeatherAdviceGenerator.uvTipRes(null))
        assertNull(WeatherAdviceGenerator.windTipRes(current.windSpeedKmh))
    }
}

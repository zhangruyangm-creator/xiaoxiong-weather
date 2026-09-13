package com.xiaoxiong.weather.data

import com.xiaoxiong.weather.R
import com.xiaoxiong.weather.fixtures.WeatherFixtures
import com.xiaoxiong.weather.util.WeatherCodes
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
        // 冻雨码与雪码共用「雨夹雪/结冰」提示（集合统一到 WeatherCodes）
        assertEquals(
            R.string.advice_rain_snow,
            WeatherAdviceGenerator.rainTipRes(66, 0.0, upcomingPrecip = false),
        )
        assertEquals(
            R.string.advice_rain_soon,
            WeatherAdviceGenerator.rainTipRes(0, 0.0, upcomingPrecip = true),
        )
        assertNull(WeatherAdviceGenerator.rainTipRes(0, 0.0, upcomingPrecip = false))
    }

    @Test
    fun uvAndWindTips() {
        // 阈值统一到 WeatherCodes.UV_HIGH(6)/UV_VERY_HIGH(8)
        assertEquals(R.string.advice_uv_strong, WeatherAdviceGenerator.uvTipRes(WeatherCodes.UV_VERY_HIGH))
        assertEquals(R.string.advice_uv_moderate, WeatherAdviceGenerator.uvTipRes(WeatherCodes.UV_HIGH))
        assertNull(WeatherAdviceGenerator.uvTipRes(WeatherCodes.UV_WATCH))
        assertNull(WeatherAdviceGenerator.uvTipRes(5.9))
        assertNull(WeatherAdviceGenerator.uvTipRes(9.0, isDay = false))
        // 昼夜未知（无 is_day 数据）同样不提示
        assertNull(WeatherAdviceGenerator.uvTipRes(9.0, isDay = null))
        assertEquals(R.string.advice_wind_strong, WeatherAdviceGenerator.windTipRes(WeatherCodes.WIND_WATCH_KMH))
        assertNull(WeatherAdviceGenerator.windTipRes(WeatherCodes.WIND_WATCH_KMH - 0.1))
    }

    @Test
    fun clothingAdvice_usesSameEffectiveFeelsAsLifeIndex() {
        // 12°C + 90% 湿度且无体感温度：生活指数按湿冷等效体感 9°C（cold 档），
        // 建议也必须跟随等效体感，不能再用原始 12°C（cool 档）产生同屏矛盾
        val current =
            CurrentWeather(
                time = "2026-08-02T12:00",
                temperatureC = 12.0,
                humidityPercent = 90,
            )
        val effective =
            LifeIndexGenerator.effectiveClothingFeels(
                12.0,
                90,
                isApparentTemperature = false,
            )
        assertEquals(9.0, effective!!, 1e-9)
        assertEquals(
            WeatherAdviceGenerator.clothingRes(effective),
            WeatherAdviceGenerator.clothingResFor(current),
        )
        assertEquals(R.string.advice_clothing_cold, WeatherAdviceGenerator.clothingResFor(current))
        assertEquals(R.string.advice_clothing_cool, WeatherAdviceGenerator.clothingRes(12.0))
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

package com.xiaoxiong.weather.ui.weather

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class WeatherSceneTest {
    @Test
    fun drizzleCodes_stayDrizzle() {
        // 51/53/55 均为 WMO 毛毛雨码
        assertEquals(WeatherScene.DRIZZLE, weatherSceneOf(51, true))
        assertEquals(WeatherScene.DRIZZLE, weatherSceneOf(53, true))
        assertEquals(WeatherScene.DRIZZLE, weatherSceneOf(55, true))
    }

    @Test
    fun showerCodes_mapToRain() {
        // 80/81 阵雨 → RAIN（不再与毛毛雨同档）；82 强阵雨 → HEAVY_RAIN
        assertEquals(WeatherScene.RAIN, weatherSceneOf(80, true))
        assertEquals(WeatherScene.RAIN, weatherSceneOf(81, true))
        assertEquals(WeatherScene.HEAVY_RAIN, weatherSceneOf(82, true))
    }

    @Test
    fun steadyRainCodes_mapToRain() {
        assertEquals(WeatherScene.RAIN, weatherSceneOf(61, true))
        assertEquals(WeatherScene.RAIN, weatherSceneOf(63, true))
        assertEquals(WeatherScene.HEAVY_RAIN, weatherSceneOf(65, true))
    }

    @Test
    fun sleetCodes_mapToSnow_notUnknown() {
        // 68/69 雨夹雪：无独立 SLEET 场景时归 SNOW，避免落到温度计兜底
        assertEquals(WeatherScene.SNOW, weatherSceneOf(68, true))
        assertEquals(WeatherScene.SNOW, weatherSceneOf(69, true))
        assertNotEquals(WeatherScene.UNKNOWN, weatherSceneOf(68, true))
        assertNotEquals(WeatherScene.UNKNOWN, weatherSceneOf(69, true))
    }

    @Test
    fun clearCloudAndUnknownScenes() {
        assertEquals(WeatherScene.CLEAR, weatherSceneOf(0, true))
        assertEquals(WeatherScene.CLEAR, weatherSceneOf(1, true))
        assertEquals(WeatherScene.PARTLY_CLOUDY, weatherSceneOf(2, true))
        assertEquals(WeatherScene.OVERCAST, weatherSceneOf(3, true))
        assertEquals(WeatherScene.FOG, weatherSceneOf(45, true))
        assertEquals(WeatherScene.FOG, weatherSceneOf(48, true))
        assertEquals(WeatherScene.FREEZING_RAIN, weatherSceneOf(56, true))
        assertEquals(WeatherScene.FREEZING_RAIN, weatherSceneOf(67, true))
        assertEquals(WeatherScene.THUNDER, weatherSceneOf(95, true))
        assertEquals(WeatherScene.HAIL, weatherSceneOf(99, true))
        assertEquals(WeatherScene.SNOW, weatherSceneOf(71, true))
        assertEquals(WeatherScene.SNOW, weatherSceneOf(85, true))
        assertEquals(WeatherScene.HEAVY_SNOW, weatherSceneOf(86, true))
        // 场景分类与昼夜无关
        assertEquals(weatherSceneOf(0, true), weatherSceneOf(0, false))
        assertEquals(WeatherScene.UNKNOWN, weatherSceneOf(999, true))
        assertEquals(WeatherScene.UNKNOWN, weatherSceneOf(null, null))
    }

    @Test
    fun partlyCloudyNight_usesDarkGradientLikeClearNight() {
        val clearNight = weatherSceneStyle(0, isDay = false)
        val partlyNight = weatherSceneStyle(2, isDay = false)
        val partlyDay = weatherSceneStyle(2, isDay = true)
        // 夜间少云与夜间晴朗同色，避免夜里仍是白天蓝
        assertEquals(clearNight.gradient, partlyNight.gradient)
        assertEquals(clearNight.contentColor, partlyNight.contentColor)
        assertNotEquals(partlyDay.gradient, partlyNight.gradient)
        // 白天少云不受影响
        assertEquals(
            listOf(Color(0xFF89CFF0), Color(0xFF7F9CF5), Color(0xFFB8C6FF)),
            partlyDay.gradient,
        )
    }
}

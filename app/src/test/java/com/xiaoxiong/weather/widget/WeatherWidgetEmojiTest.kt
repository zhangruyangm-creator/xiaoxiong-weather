package com.xiaoxiong.weather.widget

import com.xiaoxiong.weather.util.WeatherCodes
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WeatherWidgetEmojiTest {
    @Test
    fun clearAndCloudyAtNight_useMoon() {
        assertEquals("🌙", widgetEmoji(0, isDay = false))
        assertEquals("🌙", widgetEmoji(1, isDay = false))
        assertEquals("☁️", widgetEmoji(2, isDay = false))
    }

    @Test
    fun daytimeAndPrecipUnchanged() {
        assertEquals(WeatherCodes.emoji(0), widgetEmoji(0, isDay = true))
        assertEquals(WeatherCodes.emoji(2), widgetEmoji(2, isDay = true))
        // 雨雪雾等本来就无昼夜区分，夜间沿用同一 emoji
        assertEquals(WeatherCodes.emoji(61), widgetEmoji(61, isDay = false))
        assertEquals(WeatherCodes.emoji(71), widgetEmoji(71, isDay = false))
        assertEquals(WeatherCodes.emoji(45), widgetEmoji(45, isDay = false))
    }

    @Test
    fun isWidgetDaytime_parsesHourBoundaries() {
        assertFalse(isWidgetDaytime("2026-08-02T05:59"))
        assertTrue(isWidgetDaytime("2026-08-02T06:00"))
        assertTrue(isWidgetDaytime("2026-08-02T18:59"))
        assertFalse(isWidgetDaytime("2026-08-02T19:00"))
        assertFalse(isWidgetDaytime("2026-08-02 03:00"))
        assertTrue(isWidgetDaytime("2026-08-02 12:00"))
        // 解析不了时按白天处理，保证不出现异常
        assertTrue(isWidgetDaytime(null))
        assertTrue(isWidgetDaytime("not-a-time"))
    }
}

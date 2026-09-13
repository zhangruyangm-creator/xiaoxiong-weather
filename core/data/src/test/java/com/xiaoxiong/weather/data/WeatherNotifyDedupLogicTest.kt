package com.xiaoxiong.weather.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WeatherNotifyDedupLogicTest {
    private val now = 1_000_000_000_000L
    private val window = 6 * 60 * 60 * 1000L

    @Test
    fun weatherChange_firstEverOrChangedTriggers() {
        assertTrue(shouldNotifyWeatherChange("青岛", 0, 20.0, null, null, null, 0L, now))
        assertTrue(shouldNotifyWeatherChange("青岛", 0, 20.0, "北京", 0, 20.0, now - 1_000, now))
        assertTrue(shouldNotifyWeatherChange("青岛", 61, 20.0, "青岛", 0, 20.0, now - 1_000, now))
        assertTrue(shouldNotifyWeatherChange("青岛", 0, 22.0, "青岛", 0, 20.0, now - 1_000, now))
    }

    @Test
    fun weatherChange_sameStateWithinWindowStaysSilent() {
        assertFalse(shouldNotifyWeatherChange("青岛", 0, 20.0, "青岛", 0, 20.0, now - 1_000, now))
        // 天气码/气温缺失不算变化，避免对坏包快照重复推送
        assertFalse(shouldNotifyWeatherChange("青岛", null, null, "青岛", 0, 20.0, now - 1_000, now))
        // 气温变化不足 2°C
        assertFalse(shouldNotifyWeatherChange("青岛", 0, 21.9, "青岛", 0, 20.0, now - 1_000, now))
    }

    @Test
    fun weatherChange_severeCodeRemindsAfterSilenceWindow() {
        assertTrue(shouldNotifyWeatherChange("青岛", 95, 20.0, "青岛", 95, 20.0, now - window, now))
        assertFalse(shouldNotifyWeatherChange("青岛", 95, 20.0, "青岛", 95, 20.0, now - window + 1, now))
    }

    @Test
    fun alertSignatures_filterSilencedAndClockSkew() {
        val remembered =
            mapOf(
                "a" to now - 1_000,
                "b" to now - window,
                // 时钟回拨产生的未来时间戳保持静默
                "c" to now + 5_000,
            )
        assertEquals(
            listOf("b", "d"),
            filterAlertSignaturesToNotify(listOf("a", "b", "c", "d"), remembered, now),
        )
    }
}

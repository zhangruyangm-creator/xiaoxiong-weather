package com.xiaoxiong.weather.ui.weather

import com.xiaoxiong.weather.data.DailyWeather
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DailyTempRangeTest {
    @Test
    fun weekBounds_fromDailyList() {
        val days =
            listOf(
                DailyWeather(date = "2026-08-01", tempMinC = 10.0, tempMaxC = 20.0),
                DailyWeather(date = "2026-08-02", tempMinC = 5.0, tempMaxC = 28.0),
                DailyWeather(date = "2026-08-03", tempMinC = 12.0, tempMaxC = 18.0),
            )
        assertEquals(5.0 to 28.0, weekTemperatureBounds(days))
    }

    @Test
    fun weekBounds_emptyWhenNoTemps() {
        assertNull(weekTemperatureBounds(listOf(DailyWeather(date = "2026-08-01"))))
    }

    @Test
    fun alignedBar_coldDaySitsLeft_hotDaySitsRight() {
        val cold = weekAlignedTempBarRange(5.0, 8.0, weekMinC = 5.0, weekMaxC = 28.0, minVisibleWidth = 0f)
        val hot = weekAlignedTempBarRange(20.0, 28.0, weekMinC = 5.0, weekMaxC = 28.0, minVisibleWidth = 0f)
        assertEquals(0f, cold.start, 0.001f)
        assertTrue(cold.end < hot.start)
        assertEquals(1f, hot.end, 0.001f)
    }

    @Test
    fun alignedBar_enforcesMinWidth() {
        val range =
            weekAlignedTempBarRange(
                dayMinC = 15.0,
                dayMaxC = 15.2,
                weekMinC = 0.0,
                weekMaxC = 30.0,
                minVisibleWidth = 0.06f,
            )
        assertTrue(range.width + 1e-4f >= 0.06f)
    }
}

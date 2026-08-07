package com.xiaoxiong.weather.ui.weather

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant
import java.time.ZoneId

class LiveRadarPaneTimeTest {

    @Test
    fun formatRadarTime_usesCityTimezone() {
        // UTC epoch 0 = 1970-01-01T00:00:00Z
        assertEquals("08:00", formatRadarTime(0L, "Asia/Shanghai"))
        assertEquals("19:00", formatRadarTime(0L, "America/New_York"))
    }

    @Test
    fun formatRadarTime_nullTimezoneFallsBackToSystem() {
        val expected = Instant.ofEpochSecond(0L)
            .atZone(ZoneId.systemDefault())
            .format(RadarTimeFormatter)
        assertEquals(expected, formatRadarTime(0L, null))
    }

    @Test
    fun formatRadarTime_invalidTimezoneFallsBackToSystem() {
        val expected = Instant.ofEpochSecond(0L)
            .atZone(ZoneId.systemDefault())
            .format(RadarTimeFormatter)
        assertEquals(expected, formatRadarTime(0L, "not/a/zone"))
    }
}

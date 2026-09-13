package com.xiaoxiong.weather.ui.weather

import com.xiaoxiong.weather.data.HourlyWeather
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WeatherHourlyPageTest {
    @Test
    fun isDayHour_sixInclusive_eighteenExclusive() {
        assertFalse(isDayHour(5))
        assertTrue(isDayHour(6))
        assertTrue(isDayHour(12))
        assertTrue(isDayHour(17))
        assertFalse(isDayHour(18))
        assertFalse(isDayHour(23))
    }

    @Test
    fun hourlyDayFlags_parsesEachHourOnce_andFallsBackToDay() {
        val hourly =
            listOf(
                HourlyWeather(time = "2026-08-11T05:00"),
                HourlyWeather(time = "2026-08-11T06:00"),
                HourlyWeather(time = "2026-08-11T17:00"),
                HourlyWeather(time = "2026-08-11T18:00"),
                HourlyWeather(time = "bad-time"),
            )

        assertEquals(
            listOf(false, true, true, false, true),
            hourlyDayFlags(hourly),
        )
    }

    @Test
    fun remapSelectedIndex_followsTimeAcrossSlidingWindow() {
        val times = listOf("2026-08-11T10:00", "2026-08-11T11:00", "2026-08-11T12:00")
        assertEquals(1, remapSelectedIndex("2026-08-11T11:00", times))

        // 整点滚窗：11:00 从下标 1 移到 0，选中点按时间重定位而不是停在旧下标
        val rolled = listOf("2026-08-11T11:00", "2026-08-11T12:00", "2026-08-11T13:00")
        assertEquals(0, remapSelectedIndex("2026-08-11T11:00", rolled))
    }

    @Test
    fun remapSelectedIndex_outOfWindowOrEmpty_isNull() {
        assertNull(remapSelectedIndex(null, listOf("2026-08-11T10:00")))
        assertNull(remapSelectedIndex("2026-08-11T09:00", listOf("2026-08-11T10:00", "2026-08-11T11:00")))
        assertNull(remapSelectedIndex("2026-08-11T10:00", emptyList()))
    }
}

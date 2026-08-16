package com.xiaoxiong.weather.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneId
import java.time.ZonedDateTime

class WeatherRefreshPolicyTest {
    private val zone = ZoneId.of("Asia/Shanghai")

    private fun at(
        hour: Int,
        minute: Int,
        second: Int = 0,
        nano: Int = 0,
    ): ZonedDateTime = ZonedDateTime.of(2026, 8, 3, hour, minute, second, nano, zone)

    @Test
    fun millisUntilNextQuarterHour_fromMidSlot() {
        val now = at(10, 7, 30)
        val delay = WeatherRefreshPolicy.millisUntilNextQuarterHour(now)
        assertEquals(7 * 60_000L + 30_000L, delay)
    }

    @Test
    fun millisUntilNextQuarterHour_justBeforeSlot() {
        val now = at(10, 14, 59)
        val delay = WeatherRefreshPolicy.millisUntilNextQuarterHour(now)
        assertEquals(1_000L, delay)
    }

    @Test
    fun millisUntilNextQuarterHour_exactlyOnSlot_waitsForNext() {
        val now = at(10, 15, 0)
        val delay = WeatherRefreshPolicy.millisUntilNextQuarterHour(now)
        assertEquals(15 * 60_000L, delay)
    }

    @Test
    fun millisUntilNextQuarterHour_crossesHour() {
        val now = at(10, 50, 0)
        val delay = WeatherRefreshPolicy.millisUntilNextQuarterHour(now)
        assertEquals(10 * 60_000L, delay)
    }

    @Test
    fun millisUntilNextQuarterHour_minimumOneSecond() {
        val now = at(10, 14, 59, nano = 999_000_000)
        val delay = WeatherRefreshPolicy.millisUntilNextQuarterHour(now)
        assertTrue(delay >= 1_000L)
    }
}

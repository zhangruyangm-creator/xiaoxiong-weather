package com.xiaoxiong.weather.ui.weather

import org.junit.Assert.assertFalse
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
}

package com.xiaoxiong.weather.data

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WeatherCacheFreshnessTest {
    @Test
    fun freshness_acceptsOnlyAgesWithinWindow() {
        val now = 1_000_000L
        assertTrue(isWithinFreshnessWindow(savedAtEpochMs = now, nowEpochMs = now, maxAgeMs = 1_000))
        assertTrue(isWithinFreshnessWindow(savedAtEpochMs = now - 1_000, nowEpochMs = now, maxAgeMs = 1_000))
        assertFalse(isWithinFreshnessWindow(savedAtEpochMs = now - 1_001, nowEpochMs = now, maxAgeMs = 1_000))
    }

    @Test
    fun freshness_rejectsFutureTimestampsAfterClockRollback() {
        val now = 1_000_000L
        assertFalse(isWithinFreshnessWindow(savedAtEpochMs = now + 1, nowEpochMs = now, maxAgeMs = 1_000))
        assertFalse(isWithinFreshnessWindow(savedAtEpochMs = now + 10_000, nowEpochMs = now, maxAgeMs = 1_000))
    }
}

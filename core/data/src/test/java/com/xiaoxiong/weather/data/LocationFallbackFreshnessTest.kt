package com.xiaoxiong.weather.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LocationFallbackFreshnessTest {
    private val now = 1_700_000_000_000L

    @Test
    fun fallback_freshLocationIsUsable() {
        assertTrue(isUsableLastLocationFallback(savedAtEpochMs = now, nowEpochMs = now))
        assertTrue(isUsableLastLocationFallback(savedAtEpochMs = now - 60_000, nowEpochMs = now))
        assertTrue(
            isUsableLastLocationFallback(
                savedAtEpochMs = now - LAST_LOCATION_FALLBACK_MAX_AGE_MS,
                nowEpochMs = now,
            ),
        )
    }

    @Test
    fun fallback_staleLocationRejectedAfterMaxAge() {
        assertFalse(
            isUsableLastLocationFallback(
                savedAtEpochMs = now - LAST_LOCATION_FALLBACK_MAX_AGE_MS - 1,
                nowEpochMs = now,
            ),
        )
        // 数月前的坐标必须视为定位失败，避免 Worker/小组件长期使用
        assertFalse(isUsableLastLocationFallback(savedAtEpochMs = now - 90L * 24 * 3600_000, nowEpochMs = now))
    }

    @Test
    fun fallback_futureTimestampAfterClockRollbackRejected() {
        assertFalse(isUsableLastLocationFallback(savedAtEpochMs = now + 1, nowEpochMs = now))
    }

    @Test
    fun fallback_customMaxAgeRespected() {
        val maxAge = 5 * 60_000L
        assertTrue(isUsableLastLocationFallback(savedAtEpochMs = now - maxAge, nowEpochMs = now, maxAgeMs = maxAge))
        assertFalse(isUsableLastLocationFallback(savedAtEpochMs = now - maxAge - 1, nowEpochMs = now, maxAgeMs = maxAge))
    }

    @Test
    fun constants_matchDocumentedPolicy() {
        assertEquals(24L * 60 * 60 * 1000, LAST_LOCATION_FALLBACK_MAX_AGE_MS)
    }
}

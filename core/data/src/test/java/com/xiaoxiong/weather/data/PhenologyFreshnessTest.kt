package com.xiaoxiong.weather.data

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PhenologyFreshnessTest {
    private val now = 1_700_000_000_000L
    private val maxAgeMs = 24L * 60 * 60 * 1000

    private fun entry(fetchedAtEpochMs: Long) =
        PhenologyCacheStore.PhenologyEntry(
            cityLat = 36.65,
            cityLon = 117.12,
            year = 2026,
            shiftDays = 3,
            fetchedAtEpochMs = fetchedAtEpochMs,
        )

    @Test
    fun fresh_withinWindow() {
        assertTrue(entry(now).isFreshToday(now))
        assertTrue(entry(now - 1).isFreshToday(now))
        assertTrue(entry(now - maxAgeMs + 1).isFreshToday(now))
    }

    @Test
    fun fresh_rejectsExpiredEntries() {
        assertFalse(entry(now - maxAgeMs - 1).isFreshToday(now))
        assertFalse(entry(now - 7L * maxAgeMs).isFreshToday(now))
    }

    @Test
    fun fresh_rejectsFutureTimestampAfterClockRollback() {
        // 旧实现按「日历天差 < 1」判断，未来时间戳被误判为当日新鲜
        assertFalse(entry(now + 1).isFreshToday(now))
        assertFalse(entry(now + 48L * 60 * 60 * 1000).isFreshToday(now))
    }

    @Test
    fun fresh_matchesWeatherCacheWindowSemantics() {
        assertEqualsBoth(now - maxAgeMs, expected = true)
        assertEqualsBoth(now - maxAgeMs - 1, expected = false)
        assertEqualsBoth(now + 1, expected = false)
    }

    private fun assertEqualsBoth(
        fetchedAtEpochMs: Long,
        expected: Boolean,
    ) {
        val actual = entry(fetchedAtEpochMs).isFreshToday(now)
        org.junit.Assert.assertEquals(
            "fetchedAt=$fetchedAtEpochMs",
            expected,
            actual,
        )
        org.junit.Assert.assertEquals(
            "fetchedAt=$fetchedAtEpochMs should match isWithinFreshnessWindow",
            isWithinFreshnessWindow(fetchedAtEpochMs, now, maxAgeMs),
            actual,
        )
    }
}

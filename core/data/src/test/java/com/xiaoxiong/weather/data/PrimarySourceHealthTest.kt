package com.xiaoxiong.weather.data

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PrimarySourceHealthTest {
    private val now = 1_700_000_000_000L

    @Test
    fun initiallyHealthy() {
        assertFalse(PrimarySourceHealth().shouldSkipPrimary(now))
    }

    @Test
    fun failureMarksUnhealthyWithinTtl() {
        val health = PrimarySourceHealth(failureTtlMs = 5 * 60_000L)
        health.recordFailure(now)
        assertTrue(health.shouldSkipPrimary(now))
        assertTrue(health.shouldSkipPrimary(now + 5 * 60_000L - 1))
    }

    @Test
    fun ttlExpiryClearsMark() {
        val health = PrimarySourceHealth(failureTtlMs = 5 * 60_000L)
        health.recordFailure(now)
        assertFalse(health.shouldSkipPrimary(now + 5 * 60_000L))
        // 过期后再问一次仍健康
        assertFalse(health.shouldSkipPrimary(now + 5 * 60_000L + 1))
    }

    @Test
    fun successClearsMark() {
        val health = PrimarySourceHealth(failureTtlMs = 5 * 60_000L)
        health.recordFailure(now)
        health.recordSuccess()
        assertFalse(health.shouldSkipPrimary(now + 1))
    }

    @Test
    fun clockRollbackDoesNotKeepStaleFailure() {
        val health = PrimarySourceHealth(failureTtlMs = 5 * 60_000L)
        health.recordFailure(now)
        // now 回拨到失败之前：负年龄视为标记失效
        assertFalse(health.shouldSkipPrimary(now - 1))
        assertFalse(health.shouldSkipPrimary(now))
    }

    @Test
    fun latestFailureRefreshesTtl() {
        val health = PrimarySourceHealth(failureTtlMs = 5 * 60_000L)
        health.recordFailure(now)
        health.recordFailure(now + 4 * 60_000L)
        assertTrue(health.shouldSkipPrimary(now + 8 * 60_000L))
    }
}

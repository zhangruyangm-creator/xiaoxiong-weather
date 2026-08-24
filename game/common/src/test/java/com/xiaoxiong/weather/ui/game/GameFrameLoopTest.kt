package com.xiaoxiong.weather.ui.game

import org.junit.Assert.assertEquals
import org.junit.Test

class GameFrameLoopTest {
    @Test
    fun `first frame returns 1-60s fallback`() {
        val dt = computeFrameDt(lastNanos = Long.MIN_VALUE, nowNanos = 123_456_789L)
        assertEquals(1f / 60f, dt, 1e-6f)
    }

    @Test
    fun `normal frame returns elapsed seconds`() {
        val last = 1_000_000_000L // 1s
        val now = 1_016_666_000L // +16.666ms
        val dt = computeFrameDt(lastNanos = last, nowNanos = now)
        assertEquals(0.016666f, dt, 1e-4f)
    }

    @Test
    fun `lagged frame clamps dt to maxDt`() {
        val last = 1_000_000_000L
        val now = 1_500_000_000L // 500ms 掉帧
        val dt = computeFrameDt(lastNanos = last, nowNanos = now, maxDtSeconds = 0.033f)
        assertEquals(0.033f, dt, 1e-6f)
    }

    @Test
    fun `clamp respects custom maxDt`() {
        val last = 1_000_000_000L
        val now = 1_100_000_000L // 100ms
        val dt = computeFrameDt(lastNanos = last, nowNanos = now, maxDtSeconds = 0.1f)
        assertEquals(0.1f, dt, 1e-6f)
    }

    @Test
    fun `dt is monotonic below clamp`() {
        var last = 1_000_000_000L
        val samples =
            listOf(
                1_016_000_000L,
                1_032_000_000L,
                1_048_000_000L,
                1_064_000_000L,
            )
        val dts =
            samples.map { now ->
                computeFrameDt(lastNanos = last, nowNanos = now).also { last = now }
            }
        dts.forEach { assertEquals(0.016f, it, 2e-3f) }
    }
}

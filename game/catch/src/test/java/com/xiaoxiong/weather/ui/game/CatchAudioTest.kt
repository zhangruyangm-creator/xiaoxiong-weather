package com.xiaoxiong.weather.ui.game

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** 接住音效软门控单测。 */
class CatchAudioTest {
    @Test
    fun cueAllowed_rejectsWithinWindow_acceptsAtBoundary() {
        val last = 1_000L
        assertFalse(catchCueAllowed(nowMs = 1_054L, lastPlayedMs = last, minIntervalMs = 55L))
        assertTrue(catchCueAllowed(nowMs = 1_055L, lastPlayedMs = last, minIntervalMs = 55L))
        assertTrue(catchCueAllowed(nowMs = 2_000L, lastPlayedMs = last, minIntervalMs = 55L))
    }

    @Test
    fun cueAllowed_neverPlayed_isAlwaysAllowed() {
        assertTrue(catchCueAllowed(nowMs = 0L, lastPlayedMs = Long.MIN_VALUE))
    }

    @Test
    fun gate_throttlesCatchAt55ms() {
        val gate = CatchCueGate()
        assertTrue(gate.allowCatch(0L))
        assertFalse(gate.allowCatch(54L))
        assertTrue(gate.allowCatch(55L))
    }

    @Test
    fun gate_reset_allowsFirstCueImmediately() {
        val gate = CatchCueGate()
        assertTrue(gate.allowCatch(7L))
        assertFalse(gate.allowCatch(7L))
        gate.reset()
        assertTrue(gate.allowCatch(1L))
    }
}

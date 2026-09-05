package com.xiaoxiong.weather.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class MoonPhaseTest {
    @Test
    fun illumination_fullCycleIsFullyLit() {
        val info = MoonPhaseCalculator.of(LocalDate.of(2000, 1, 6))
        // 已知新月锚点附近
        assertTrue(info.illumination < 0.08)
        assertEquals(MoonPhase.NEW, info.phase)

        assertEquals(MoonPhase.FULL, MoonPhaseCalculator.phaseForCycle(0.5))
        val halfAge = MoonPhaseCalculator.SYNODIC_DAYS / 2.0
        val illum = (1.0 - kotlin.math.cos(2.0 * kotlin.math.PI * (halfAge / MoonPhaseCalculator.SYNODIC_DAYS))) / 2.0
        assertEquals(1.0, illum, 1e-9)
    }

    @Test
    fun knownFullMoon_highIllumination() {
        // 2025-02-12 附近满月
        val info = MoonPhaseCalculator.of(LocalDate.of(2025, 2, 12))
        assertTrue("illumination=${info.illumination}", info.illumination > 0.92)
        assertEquals(MoonPhase.FULL, info.phase)
    }

    @Test
    fun knownNewMoon_lowIllumination() {
        // 2025-01-29 附近新月
        val info = MoonPhaseCalculator.of(LocalDate.of(2025, 1, 29))
        assertTrue("illumination=${info.illumination}", info.illumination < 0.08)
        assertEquals(MoonPhase.NEW, info.phase)
    }

    @Test
    fun phaseForCycle_eightBuckets() {
        assertEquals(MoonPhase.NEW, MoonPhaseCalculator.phaseForCycle(0.0))
        assertEquals(MoonPhase.WAXING_CRESCENT, MoonPhaseCalculator.phaseForCycle(0.12))
        assertEquals(MoonPhase.FIRST_QUARTER, MoonPhaseCalculator.phaseForCycle(0.25))
        assertEquals(MoonPhase.WAXING_GIBBOUS, MoonPhaseCalculator.phaseForCycle(0.38))
        assertEquals(MoonPhase.FULL, MoonPhaseCalculator.phaseForCycle(0.5))
        assertEquals(MoonPhase.WANING_GIBBOUS, MoonPhaseCalculator.phaseForCycle(0.63))
        assertEquals(MoonPhase.LAST_QUARTER, MoonPhaseCalculator.phaseForCycle(0.75))
        assertEquals(MoonPhase.WANING_CRESCENT, MoonPhaseCalculator.phaseForCycle(0.88))
        assertEquals(MoonPhase.NEW, MoonPhaseCalculator.phaseForCycle(0.99))
    }

    @Test
    fun julianDay_knownEpoch() {
        // 2000-01-01 12:00 UTC = JD 2451545.0
        assertEquals(2451545.0, MoonPhaseCalculator.julianDay(LocalDate.of(2000, 1, 1)), 1e-6)
    }
}

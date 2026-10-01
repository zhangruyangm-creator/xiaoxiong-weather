package com.xiaoxiong.weather.ui.game

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaneMetaTest {
    @Test
    fun shipTints_unlockByBestWave() {
        assertTrue(planeShipTintUnlocked(PlaneShipTint.CLASSIC, 0))
        assertFalse(planeShipTintUnlocked(PlaneShipTint.GOLD, 9))
        assertTrue(planeShipTintUnlocked(PlaneShipTint.GOLD, 10))
        assertTrue(planeShipTintUnlocked(PlaneShipTint.AURORA, 50))
        assertEquals(
            listOf(PlaneShipTint.CLASSIC, PlaneShipTint.GOLD, PlaneShipTint.MIDNIGHT),
            planeUnlockedShipTints(20),
        )
    }

    @Test
    fun shipTint_fromId_fallsBackToClassic() {
        assertEquals(PlaneShipTint.ROSE, PlaneShipTint.fromId("rose"))
        assertEquals(PlaneShipTint.CLASSIC, PlaneShipTint.fromId(null))
        assertEquals(PlaneShipTint.CLASSIC, PlaneShipTint.fromId("nope"))
    }

    @Test
    fun rollPowerUpKind_coversAllKinds() {
        val kinds = (0 until 100).map { PlaneConfig.rollPowerUpKind(it) }.toSet()
        assertEquals(PlanePowerUpKind.entries.toSet(), kinds)
        assertEquals(PlanePowerUpKind.WEAPON, PlaneConfig.rollPowerUpKind(0))
        assertEquals(PlanePowerUpKind.BOMB, PlaneConfig.rollPowerUpKind(48))
        assertEquals(PlanePowerUpKind.SHIELD, PlaneConfig.rollPowerUpKind(72))
        assertEquals(PlanePowerUpKind.LIFE, PlaneConfig.rollPowerUpKind(88))
    }
}

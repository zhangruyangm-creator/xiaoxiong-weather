package com.xiaoxiong.weather.ui.game

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RunMetaTest {
    @Test
    fun scarfTints_unlockByBestScore() {
        assertTrue(runScarfTintUnlocked(RunScarfTint.CLASSIC, 0))
        assertFalse(runScarfTintUnlocked(RunScarfTint.GOLD, 499))
        assertTrue(runScarfTintUnlocked(RunScarfTint.GOLD, 500))
        assertTrue(runScarfTintUnlocked(RunScarfTint.MIDNIGHT, 1500))
        assertTrue(runScarfTintUnlocked(RunScarfTint.ROSE, 3000))
        assertTrue(runScarfTintUnlocked(RunScarfTint.AURORA, 5000))
        assertEquals(
            listOf(RunScarfTint.CLASSIC, RunScarfTint.GOLD, RunScarfTint.MIDNIGHT),
            runUnlockedScarfTints(1500),
        )
        assertEquals(5, runUnlockedScarfTints(5000).size)
    }

    @Test
    fun scarfTint_fromId_fallsBackToClassic() {
        assertEquals(RunScarfTint.ROSE, RunScarfTint.fromId("rose"))
        assertEquals(RunScarfTint.CLASSIC, RunScarfTint.fromId(null))
        assertEquals(RunScarfTint.CLASSIC, RunScarfTint.fromId("nope"))
    }
}

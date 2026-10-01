package com.xiaoxiong.weather.ui.game

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CatchMetaTest {
    @Test
    fun gearTints_unlockByBestScore() {
        assertTrue(catchGearTintUnlocked(CatchGearTint.CLASSIC, 0))
        assertFalse(catchGearTintUnlocked(CatchGearTint.GOLD, 199))
        assertTrue(catchGearTintUnlocked(CatchGearTint.GOLD, 200))
        assertTrue(catchGearTintUnlocked(CatchGearTint.AURORA, 2000))
        assertEquals(
            listOf(CatchGearTint.CLASSIC, CatchGearTint.GOLD, CatchGearTint.MIDNIGHT),
            catchUnlockedGearTints(500),
        )
    }

    @Test
    fun gearTint_fromId_fallsBackToClassic() {
        assertEquals(CatchGearTint.ROSE, CatchGearTint.fromId("rose"))
        assertEquals(CatchGearTint.CLASSIC, CatchGearTint.fromId(null))
        assertEquals(CatchGearTint.CLASSIC, CatchGearTint.fromId("nope"))
    }

    @Test
    fun bestAndLeaderboardKeys_arePerDifficulty() {
        assertEquals("catch_best_casual", catchBestKey(BearCatchLogic.CatchDifficulty.CASUAL))
        assertEquals("catch_best_standard", catchBestKey(BearCatchLogic.CatchDifficulty.STANDARD))
        assertEquals(
            "leaderboard_catch_challenge_v1",
            catchLeaderboardKey(BearCatchLogic.CatchDifficulty.CHALLENGE),
        )
    }

    @Test
    fun scoreMul_tiersMatchSpec() {
        assertEquals(0.85f, BearCatchLogic.CatchDifficulty.CASUAL.scoreMul, 1e-4f)
        assertEquals(1.0f, BearCatchLogic.CatchDifficulty.STANDARD.scoreMul, 1e-4f)
        assertEquals(1.35f, BearCatchLogic.CatchDifficulty.CHALLENGE.scoreMul, 1e-4f)
    }
}

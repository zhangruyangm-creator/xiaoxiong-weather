package com.xiaoxiong.weather.ui.game

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StarMetaTest {
    @Test
    fun difficulty_fromId_fallsBack() {
        assertEquals(StarDifficulty.CASUAL, StarDifficulty.fromId("casual"))
        assertEquals(StarDifficulty.STANDARD, StarDifficulty.fromId(null))
        assertEquals(StarDifficulty.STANDARD, StarDifficulty.fromId("nope"))
    }

    @Test
    fun trailTints_unlockByBest() {
        assertTrue(starTrailTintUnlocked(StarTrailTint.CLASSIC, 0))
        assertFalse(starTrailTintUnlocked(StarTrailTint.GOLD, 79))
        assertTrue(starTrailTintUnlocked(StarTrailTint.GOLD, 80))
        assertEquals(
            listOf(StarTrailTint.CLASSIC, StarTrailTint.GOLD, StarTrailTint.MIDNIGHT),
            starUnlockedTrailTints(180),
        )
    }

    @Test
    fun altitudeTheme_bands() {
        assertEquals(StarAltitudeTheme.DAY, starAltitudeTheme(0.2f))
        assertEquals(StarAltitudeTheme.DUSK, starAltitudeTheme(1.0f))
        assertEquals(StarAltitudeTheme.NIGHT, starAltitudeTheme(2.0f))
        assertEquals(StarAltitudeTheme.SPACE, starAltitudeTheme(3.0f))
    }

    @Test
    fun scoreFor_appliesDifficultyAndComboGraze() {
        val base = StarJumpLogic.scoreFor(-600f, 2, 1f)
        assertEquals(60, base)
        val challenge = StarJumpLogic.scoreFor(-600f, 2, 1f, StarDifficulty.CHALLENGE)
        assertEquals(81, challenge) // 60 * 1.35
        val withJuice =
            StarJumpLogic.scoreFor(
                maxHeight = -600f,
                starsCollected = 2,
                s = 1f,
                difficulty = StarDifficulty.STANDARD,
                comboBonusTotal = 6,
                grazeCount = 2,
            )
        assertEquals(60 + 6 + 16, withJuice)
    }

    @Test
    fun bounceComboBonus_caps() {
        assertEquals(0, StarJumpLogic.bounceComboBonus(1))
        assertEquals(3, StarJumpLogic.bounceComboBonus(2))
        assertEquals(30, StarJumpLogic.bounceComboBonus(20))
    }
}

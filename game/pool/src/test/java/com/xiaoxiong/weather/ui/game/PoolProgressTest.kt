package com.xiaoxiong.weather.ui.game

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** 生涯统计/成就推进的门控：对局类成就只由八球/九球产生。 */
class PoolProgressTest {
    private fun advance(
        won: Boolean,
        aiFrames: Int,
        mode: PoolMode,
        difficulty: PoolAi.Difficulty = PoolAi.Difficulty.NORMAL,
        breakAndRun: Boolean = false,
        practiceCleared: Boolean = false,
        practiceScore: Int = 0,
    ): PoolProgress =
        PoolProgress().advance(
            won = won,
            framesPlayed = 1,
            aiFrames = aiFrames,
            potsThisMatch = 5,
            bestShotThisMatch = 2,
            breakAndRun = breakAndRun,
            mode = mode,
            difficulty = difficulty,
            practiceCleared = practiceCleared,
            practiceScore = practiceScore,
        )

    @Test
    fun practiceClear_doesNotUnlockMatchAchievements() {
        val p =
            advance(
                won = true,
                aiFrames = 0,
                mode = PoolMode.PRACTICE,
                difficulty = PoolAi.Difficulty.MASTER,
                breakAndRun = true,
                practiceCleared = true,
                practiceScore = 100,
            )
        assertFalse(PoolAchievement.FIRST_WIN.isUnlocked(p))
        assertFalse(PoolAchievement.SHUTOUT.isUnlocked(p))
        assertFalse(PoolAchievement.MASTER_WIN.isUnlocked(p))
        assertFalse(PoolAchievement.WIN_STREAK_3.isUnlocked(p))
        assertFalse(PoolAchievement.NINE_BALL_WIN.isUnlocked(p))
        assertEquals(0, p.wins)
        assertEquals(0, p.cleanSheets)
        assertEquals(0, p.breakRuns)
        assertEquals(1, p.practiceClears)
        assertEquals(100, p.bestPractice)
    }

    @Test
    fun eightBallShutout_unlocksFirstWinAndShutout() {
        val p =
            advance(
                won = true,
                aiFrames = 0,
                mode = PoolMode.EIGHT_BALL,
                difficulty = PoolAi.Difficulty.MASTER,
                breakAndRun = true,
            )
        assertTrue(PoolAchievement.FIRST_WIN.isUnlocked(p))
        assertTrue(PoolAchievement.SHUTOUT.isUnlocked(p))
        assertTrue(PoolAchievement.MASTER_WIN.isUnlocked(p))
        assertTrue(PoolAchievement.CLEAN_TABLE.isUnlocked(p))
        assertEquals(1, p.wins)
        assertEquals(1, p.cleanSheets)
        assertEquals(1, p.breakRuns)
        assertEquals(0, p.nineBallWins)
    }

    @Test
    fun nineBallWin_unlocksFirstWinAndNineBall() {
        val p = advance(won = true, aiFrames = 1, mode = PoolMode.NINE_BALL)
        assertTrue(PoolAchievement.FIRST_WIN.isUnlocked(p))
        assertTrue(PoolAchievement.NINE_BALL_WIN.isUnlocked(p))
        assertFalse(PoolAchievement.SHUTOUT.isUnlocked(p))
        assertEquals(1, p.nineBallWins)
        assertEquals(1, p.wins)
    }

    @Test
    fun customRaceToShutout_countsOncePerMatchRegardlessOfFrameCount() {
        // 先到 15 且 15:0 横扫：仍只算一次零封、一场胜利、15 局
        val p =
            PoolProgress().advance(
                won = true,
                framesPlayed = 15,
                aiFrames = 0,
                potsThisMatch = 24,
                bestShotThisMatch = 3,
                breakAndRun = false,
                mode = PoolMode.EIGHT_BALL,
                difficulty = PoolAi.Difficulty.MASTER,
                practiceCleared = false,
                practiceScore = 0,
            )
        assertEquals(1, p.games)
        assertEquals(1, p.wins)
        assertEquals(15, p.frames)
        assertEquals(1, p.cleanSheets)
        assertEquals(1, p.masterWins)
        assertTrue(PoolAchievement.SHUTOUT.isUnlocked(p))
    }

    @Test
    fun competitiveLossResetsStreak_butPracticeKeepsIt() {
        val afterLoss = advance(won = false, aiFrames = 3, mode = PoolMode.EIGHT_BALL)
        assertEquals(0, afterLoss.winStreak)
        val practice = advance(won = false, aiFrames = 0, mode = PoolMode.PRACTICE, practiceCleared = true, practiceScore = 90)
        assertEquals("练习不清零连胜", 0, practice.winStreak)
        assertEquals(0, practice.wins)
    }
}

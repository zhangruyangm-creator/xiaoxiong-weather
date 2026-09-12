package com.xiaoxiong.weather.ui.game

import androidx.core.content.edit
import com.xiaoxiong.weather.ui.game.HockeyLogic.Difficulty
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** [HockeyProgress] / [HockeyAchievement] 纯 JVM 单测：统计推进、成就解锁与持久化。 */
class HockeyCareerTest {
    @Test
    fun advance_updatesGoalsShutoutStreakAndDifficultyWins() {
        val first = HockeyProgress().advance(won = true, goalsFor = 5, goalsAgainst = 0, difficulty = Difficulty.MASTER)
        assertEquals(1, first.games)
        assertEquals(1, first.wins)
        assertEquals(0, first.losses)
        assertEquals(5, first.goalsFor)
        assertEquals(0, first.goalsAgainst)
        assertEquals(1, first.shutouts)
        assertEquals(1, first.winStreak)
        assertEquals(1, first.bestStreak)
        assertEquals(5, first.bestGoals)
        assertEquals(5, first.bestShutoutGoals)
        assertEquals(1, first.winsOn(Difficulty.MASTER))

        // 输一场清零当前连胜，但不影响最长连胜与累计
        val second = first.advance(won = false, goalsFor = 2, goalsAgainst = 3, difficulty = Difficulty.MASTER)
        assertEquals(2, second.games)
        assertEquals(1, second.wins)
        assertEquals(1, second.losses)
        assertEquals(7, second.goalsFor)
        assertEquals(3, second.goalsAgainst)
        assertEquals(1, second.shutouts)
        assertEquals(0, second.winStreak)
        assertEquals(1, second.bestStreak)
        assertEquals(1, second.winsOn(Difficulty.MASTER))
    }

    @Test
    fun advance_threeWinsUnlocksStreakAchievement() {
        val p =
            HockeyProgress()
                .advance(true, 1, 0, Difficulty.EASY)
                .advance(true, 1, 0, Difficulty.EASY)
                .advance(true, 1, 0, Difficulty.EASY)
        assertEquals(3, p.bestStreak)
        assertTrue(HockeyAchievement.WIN_STREAK_3.isUnlocked(p))
    }

    @Test
    fun achievements_unlockFromDerivedStats() {
        val all =
            HockeyProgress(
                games = 10,
                wins = 1,
                shutouts = 1,
                bestStreak = 3,
                bestGoals = 5,
                bestShutoutGoals = 5,
                winsByDifficulty = mapOf(Difficulty.HARD to 1, Difficulty.MASTER to 1),
            )
        HockeyAchievement.entries.forEach { assertTrue(it.name, it.isUnlocked(all)) }

        val empty = HockeyProgress()
        HockeyAchievement.entries.forEach { assertFalse(it.name, it.isUnlocked(empty)) }
    }

    @Test
    fun saveLoad_roundTripsStatsAndAchievements() {
        val prefs = TestPrefs()
        val p =
            HockeyProgress(
                games = 3,
                wins = 2,
                losses = 1,
                goalsFor = 9,
                goalsAgainst = 4,
                shutouts = 1,
                winStreak = 2,
                bestStreak = 2,
                bestGoals = 6,
                bestShutoutGoals = 3,
                winsByDifficulty = mapOf(Difficulty.HARD to 1, Difficulty.MASTER to 1),
                unlocked = setOf(HockeyAchievement.FIRST_WIN.id),
            )
        saveHockeyProgress(prefs, p)

        assertEquals(p, loadHockeyProgress(prefs))
    }

    @Test
    fun clearStats_restoresEmptyProgress() {
        val prefs = TestPrefs()
        saveHockeyProgress(prefs, HockeyProgress(games = 4, wins = 3, goalsFor = 12))
        prefs.edit { HOCKEY_STAT_KEYS.forEach { remove(it) } }

        assertEquals(HockeyProgress(), loadHockeyProgress(prefs))
    }
}

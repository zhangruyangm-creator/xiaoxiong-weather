package com.xiaoxiong.weather.ui.game

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 冰球排行榜口径回归：每局（含惨败）都入榜，按净胜球排序，tieBreak = 失球数，
 * 展示文本为真实比分。与台球 `poolLeaderboardEntry` 同为「净胜 + tieBreak」口径。
 */
class HockeyLeaderboardEntryTest {
    @Test
    fun winEntry_isNetGoalsWithGoalsAgainstTieBreak() {
        val entry = hockeyLeaderboardEntry(playerScore = 5, aiScore = 1, timestamp = 10L, city = "北京", weather = "晴")
        assertEquals(4, entry.score)
        assertEquals(1, entry.tieBreak)
        assertEquals("5 : 1", entry.label)
        assertEquals("北京", entry.city)
        assertEquals("晴", entry.weather)
    }

    @Test
    fun lossEntry_keepsNegativeNetAndFullGoalsAgainst() {
        val entry = hockeyLeaderboardEntry(playerScore = 2, aiScore = 5, timestamp = 10L, city = null, weather = null)
        assertEquals(-3, entry.score)
        assertEquals(5, entry.tieBreak)
        assertEquals("2 : 5", entry.label)
        // 负净胜在结算界面不展示名次（榜单仍保留记录）
        assertEquals(0, leaderboardDisplayRank(entry.score, rank = 4))
    }

    @Test
    fun shutoutWin_hasZeroTieBreakAndPositiveRank() {
        val entry = hockeyLeaderboardEntry(playerScore = 5, aiScore = 0, timestamp = 10L, city = null, weather = null)
        assertEquals(5, entry.score)
        assertEquals(0, entry.tieBreak)
        assertEquals(2, leaderboardDisplayRank(entry.score, rank = 2))
    }
}

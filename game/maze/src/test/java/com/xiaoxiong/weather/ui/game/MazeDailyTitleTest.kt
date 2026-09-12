package com.xiaoxiong.weather.ui.game

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Locale

/**
 * 关卡标题中英切换与每日迷宫确定性种子的 JVM 单测。
 */
class MazeDailyTitleTest {
    @Test
    fun displayTitle_usesChineseForZhLocale() {
        val original = Locale.getDefault()
        try {
            Locale.setDefault(Locale.SIMPLIFIED_CHINESE)
            val level = MAZE_HANDCRAFTED[0]
            assertEquals(level.titleZh, level.displayTitle())
            assertEquals("阳光小径", level.displayTitle())
        } finally {
            Locale.setDefault(original)
        }
    }

    @Test
    fun displayTitle_usesEnglishForNonZhLocale() {
        val original = Locale.getDefault()
        try {
            Locale.setDefault(Locale.ENGLISH)
            val level = MAZE_HANDCRAFTED[0]
            assertEquals(level.titleEn, level.displayTitle())
            assertEquals("Sunny Path", level.displayTitle())
        } finally {
            Locale.setDefault(original)
        }
    }

    @Test
    fun metaDisplayTitle_switchesWithLocale() {
        val original = Locale.getDefault()
        try {
            Locale.setDefault(Locale.ENGLISH)
            assertEquals("Sunny Path", mazeCampaignMeta(0).displayTitle())
            Locale.setDefault(Locale.SIMPLIFIED_CHINESE)
            assertEquals("阳光小径", mazeCampaignMeta(0).displayTitle())
        } finally {
            Locale.setDefault(original)
        }
    }

    // ── 每日迷宫 ───────────────────────────────────────────────

    @Test
    fun dailyLevel_sameDayProducesSameLayout() {
        val a = dailyMazeLevel(20_001L)
        val b = dailyMazeLevel(20_001L)
        assertEquals("同一天必须完全一致", a.map, b.map)
    }

    @Test
    fun dailyLevel_differentDayProducesDifferentLayout() {
        val a = dailyMazeLevel(20_001L)
        val b = dailyMazeLevel(20_002L)
        assertNotEquals("不同日期应生成不同布局", a.map, b.map)
    }

    @Test
    fun dailyLevel_isSolvableWithStrictBudget() {
        val level = dailyMazeLevel(20_003L)
        val solved = mazeSolve(level.map)
        assertTrue("每日迷宫应可解", solved is MazeSolveOutcome.Solved)
        solved as MazeSolveOutcome.Solved
        assertTrue(
            "每日迷宫预算应留有余量：min=${solved.minMoves} budget=${level.moveBudget}",
            solved.minMoves <= level.moveBudget,
        )
    }

    @Test
    fun startDaily_marksDailyRun() {
        val game = MazeGame().apply { startDaily(20_004L) }
        assertTrue(game.daily)
        assertEquals(20_004L, game.dailyDate)
        assertEquals(MazePhase.PLAYING, game.phase)
    }
}

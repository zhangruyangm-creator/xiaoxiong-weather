package com.xiaoxiong.weather.ui.game

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * 纯逻辑层 [MazeGameLogic] 的单元测试：不依赖 Compose / Android，纯 JVM 可跑。
 */
class MazeGameLogicTest {

    // ── 星级结算 ───────────────────────────────────────────────

    @Test
    fun starsFor_withinBudget_is3Stars() {
        assertEquals(3, MazeGameLogic.starsFor(moves = 30, budget = 40))
        assertEquals(3, MazeGameLogic.starsFor(moves = 40, budget = 40))
    }

    @Test
    fun starsFor_upTo135Percent_is2Stars() {
        // 40 * 1.35 = 54
        assertEquals(2, MazeGameLogic.starsFor(moves = 54, budget = 40))
        assertEquals(2, MazeGameLogic.starsFor(moves = 53, budget = 40))
    }

    @Test
    fun starsFor_over135Percent_is1Star() {
        assertEquals(1, MazeGameLogic.starsFor(moves = 55, budget = 40))
        assertEquals(1, MazeGameLogic.starsFor(moves = 100, budget = 40))
    }

    // ── 无尽难度 ────────────────────────────────────────────────

    @Test
    fun endlessDifficulty_rampsWithWave() {
        assertEquals(10, MazeGameLogic.endlessDifficulty(1))
        assertEquals(16, MazeGameLogic.endlessDifficulty(4))
        // (8 + 19*2) = 46 → 封顶 45
        assertEquals(45, MazeGameLogic.endlessDifficulty(19))
        assertEquals(45, MazeGameLogic.endlessDifficulty(999))
    }

    @Test
    fun endlessDifficulty_clampsMinWave() {
        assertEquals(10, MazeGameLogic.endlessDifficulty(0))
        assertEquals(10, MazeGameLogic.endlessDifficulty(-5))
    }

    // ── 步数预算 ────────────────────────────────────────────────

    @Test
    fun moveBudget_growsWithDifficultyAndHoney() {
        val low = MazeGameLogic.moveBudget(shortestPathLen = 10, honeyCount = 2, difficulty = 2)
        val high = MazeGameLogic.moveBudget(shortestPathLen = 10, honeyCount = 8, difficulty = 20)
        assert(high > low) { "预算应随难度/蜂蜜提升：low=$low high=$high" }
    }

    @Test
    fun moveBudget_clampsWithinBounds() {
        assertEquals(30, MazeGameLogic.moveBudget(shortestPathLen = 1, honeyCount = 0, difficulty = 0))
        // 足够大时应被钳到 220
        assertEquals(220, MazeGameLogic.moveBudget(shortestPathLen = 500, honeyCount = 99, difficulty = 99))
    }

    // ── 冰面滑行 ────────────────────────────────────────────────

    /** 手工棋盘：一行冰面可右滑到底撞墙。 */
    private fun level(vararg rows: String): MazeLevel =
        MazeLevel(
            id = 0,
            titleZh = "t",
            titleEn = "t",
            theme = MazeTheme.GARDEN,
            moveBudget = 40,
            map = rows.toList(),
        )

    private fun assertStop(
        level: MazeLevel,
        from: Pair<Int, Int>,
        dir: Pair<Int, Int>,
        expected: Pair<Int, Int>,
    ) {
        val got = MazeGameLogic.simulateIceStep(level, from.first, from.second, dir.first, dir.second)
        assertEquals("滑行终点", expected, got)
    }

    @Test
    fun iceSlide_rightSlidesUntilWall() {
        // 有一格冰，向右滑，碰到墙停在墙前
        val lv =
            level(
                "#######",
                "#S.II..E#", // 位置: 0# 1S 2. 3I 4I 5. 6E 7#
                "#######",
            )
        // 从起点(1,1) 向右：S(1,1)→.(1,2)→I(1,3)→I(1,4)→.(1,5)→E(1,6)，无冰则停在目标格
        // 起始方向 RIGHT；但起点(1,1)本身不是冰，所以第一步落到(1,2)，非冰 → 只走一格
        assertStop(lv, 1 to 1, 0 to 1, 1 to 2)
    }

    @Test
    fun iceSlide_fromIceSlidesEntireRunway() {
        // 从冰面(1,3)向右滑到墙前
        val lv =
            level(
                "#######",
                "#S.II..E#",
                "#######",
            )
        // 列:0# 1S 2. 3I 4I 5. 6E 7#
        // 从(1,3)向右:第一步到(1,4)(I)→滑向(1,5)(.)→停止，因(1,5)非冰面
        assertStop(lv, 1 to 3, 0 to 1, 1 to 5)
    }

    @Test
    fun iceSlide_fromIceSlidesContinuousIce() {
        // 连续三格冰：从(1,3)向右，I→I→I→. 停在(1,6)（. 非冰面即停）
        val lv =
            level(
                "#########",
                "#S.III.E.#",
                "#########",
            )
        // 列:0# 1S 2. 3I 4I 5I 6. 7E 8.
        assertStop(lv, 1 to 3, 0 to 1, 1 to 6)
    }

    @Test
    fun iceSlide_downStopsAtFloorNotIce() {
        val lv =
            level(
                "#####",
                "#S.#", // 列0# 1S 2# 3# 4#
                "#####",
            )
        // 从(1,1)向下：下一格(2,1)是墙 → null
        assertNull(MazeGameLogic.simulateIceStep(lv, 1, 1, 1, 0))
    }

    @Test
    fun iceSlide_intoWallReturnsNull() {
        val lv =
            level(
                "#####",
                "#S#.#",
                "#####",
            )
        // 从(1,1)向下到(2,1)=墙 → null
        assertNull(MazeGameLogic.simulateIceStep(lv, 1, 1, 1, 0))
    }
}

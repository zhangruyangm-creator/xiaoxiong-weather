package com.xiaoxiong.weather.ui.game

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 撤销（undo）、提示（hint）与重开（restart）的 JVM 单测。
 * 使用手写教学关 [MAZE_HANDCRAFTED] 的固定地图，不依赖 Compose / Android。
 */
class MazeUndoHintTest {
    private fun startLevel1(): MazeGame = MazeGame().apply { startLevel(0) }

    /** 走一步并立即把落地动画跑完，模拟玩家按键 → 落地。 */
    private fun MazeGame.step(dir: MazeDir) {
        assertTrue("方向 $dir 应可移动", tryMove(dir))
        tick(1f)
    }

    // ── 撤销 ───────────────────────────────────────────────────

    @Test
    fun undo_restoresPositionMovesAndConsumedHoney() {
        val game = startLevel1()
        val honeyBefore = game.honeyLeft
        // 起点(1,1) 到 (1,6) 共 5 步，途中会吃掉 (1,6) 的蜂蜜
        repeat(5) { game.step(MazeDir.RIGHT) }
        assertFalse(game.moving)
        assertEquals(honeyBefore - 1, game.honeyLeft)
        assertEquals(5, game.moves)
        assertEquals(MazeTile.FLOOR, game.grid[1][6].tile)

        assertTrue("落地后应可撤销", game.canUndo)
        assertTrue(game.undo())

        assertEquals("撤销后退回上一格", 5, game.bearC)
        assertEquals(1, game.bearR)
        assertEquals(4, game.moves)
        assertEquals("被吃掉的蜂蜜应回到棋盘", honeyBefore, game.honeyLeft)
        assertEquals(MazeTile.HONEY, game.grid[1][6].tile)
    }

    @Test
    fun undo_returnsToStartAndEmptiesHistory() {
        val game = startLevel1()
        game.step(MazeDir.RIGHT)
        assertTrue(game.undo())
        assertEquals(1, game.bearC)
        assertEquals(0, game.moves)
        assertFalse("历史耗尽后不可再撤销", game.canUndo)
        assertFalse(game.undo())
    }

    @Test
    fun undo_isRejectedDuringMoveAnimation() {
        val game = startLevel1()
        assertTrue(game.tryMove(MazeDir.RIGHT))
        assertTrue("移动动画中 moving 应为 true", game.moving)
        assertFalse("动画中不可撤销", game.canUndo)
        assertFalse(game.undo())

        game.tick(1f)
        assertFalse(game.moving)
        assertTrue(game.canUndo)
    }

    @Test
    fun undo_historyIsClearedOnRestart() {
        val game = startLevel1()
        game.step(MazeDir.RIGHT)
        game.restart()
        assertFalse("换关/重开后历史应清空", game.canUndo)
    }

    // ── 提示 ───────────────────────────────────────────────────

    @Test
    fun hint_onSolvableBoard_returnsLegalDirection() {
        val game = startLevel1()
        val result = game.hint()
        assertTrue("可解局面应给出提示", result is MazeHint.Direction)
        val dir = (result as MazeHint.Direction).dir
        val nr = game.bearR + dir.dr
        val nc = game.bearC + dir.dc
        assertTrue("提示目标应在棋盘内", nr in 0 until game.rows && nc in 0 until game.cols)
        assertTrue("提示方向不能撞墙", game.grid[nr][nc].tile != MazeTile.WALL)
    }

    @Test
    fun hint_none_whenNoSolutionFromCurrentState() {
        val game = startLevel1()
        // 把熊的上下左右全部封死，剩余蜂蜜不可达
        game.grid[1][2] = MazeCell(MazeTile.WALL)
        game.grid[2][1] = MazeCell(MazeTile.WALL)
        assertEquals("无解局面应返回 NoSolution", MazeHint.NoSolution, game.hint())
        assertEquals("无解不应计入提示次数", 0, game.hintsUsed)
    }

    @Test
    fun hint_tooLarge_whenHoneyExceedsSolveBudget() {
        val game = startLevel1()
        var placed = 0
        outer@ for (r in 0 until game.rows) {
            for (c in 0 until game.cols) {
                if (placed >= MAX_SOLVE_HONEY + 1) break@outer
                if (game.grid[r][c].tile == MazeTile.FLOOR) {
                    game.grid[r][c] = MazeCell(MazeTile.HONEY)
                    placed++
                }
            }
        }
        assertEquals(MAX_SOLVE_HONEY + 1, placed)
        assertEquals("超过蜂蜜预算应区分 TooLarge 而不是无解", MazeHint.TooLarge, game.hint())
        assertEquals("TooLarge 不应计入提示次数", 0, game.hintsUsed)
    }

    @Test
    fun hint_incrementsUsageCountOnlyWhenSolved() {
        val game = startLevel1()
        assertTrue(game.hint() is MazeHint.Direction)
        assertEquals(1, game.hintsUsed)
        assertTrue(game.hint() is MazeHint.Direction)
        assertEquals(2, game.hintsUsed)
    }

    @Test
    fun applyHint_incrementsUsageWhenSnapshotStillMatches() {
        val game = startLevel1()
        val request = game.hintRequest()
        assertNotNull("正常对局应能抓取提示请求", request)
        val outcome = mazeSolve(request!!.map, request.bearR, request.bearC, request.keys)
        assertTrue(game.applyHint(request, outcome) is MazeHint.Direction)
        assertEquals(1, game.hintsUsed)
        // applyHint 自身不去重：内容相同的快照每次成功回写各计一次。
        // UI 侧由 MazeHintSession.cachedFor 拦截同局面重复请求（见 MazeHintSessionTest），
        // 因此正常操作路径下同一局面至多计一次。
        assertTrue(game.applyHint(request, outcome) is MazeHint.Direction)
        assertEquals(2, game.hintsUsed)
    }

    @Test
    fun applyHint_discardsStaleSnapshotAfterMove() {
        val game = startLevel1()
        val request = game.hintRequest()
        assertNotNull(request)
        val outcome = mazeSolve(request!!.map, request.bearR, request.bearC, request.keys)
        game.step(MazeDir.RIGHT)
        assertNull("局面已变化，过期结果应丢弃", game.applyHint(request, outcome))
        assertEquals("过期结果不应计数", 0, game.hintsUsed)
    }

    @Test
    fun hintRequestMatches_onlyForSamePositionAndBoard() {
        val game = startLevel1()
        val request = game.hintRequest()
        assertNotNull(request)
        assertTrue("未移动时应命中缓存快照", game.hintRequestMatches(request!!))
        game.step(MazeDir.RIGHT)
        assertFalse("移动后缓存快照失效", game.hintRequestMatches(request))
    }

    @Test
    fun mazeSolveFromState_reportsMinMovesAndFirstDir() {
        // 一行直路：起点 → 蜂蜜 → 出口
        val map =
            listOf(
                "#######",
                "#S.H.E#",
                "#######",
            )
        val solved = mazeSolveFromState(map, startR = 1, startC = 1, initialKeys = 0)
        assertNotNull(solved)
        assertEquals(4, solved!!.minMoves)
        assertEquals(MazeDir.RIGHT, solved.firstDir)
    }

    @Test
    fun mazeSolveFromState_nullWhenUnsolvable() {
        val map =
            listOf(
                "#######",
                "#S.LH.#",
                "#######",
            )
        assertNull(mazeSolveFromState(map, startR = 1, startC = 1))
    }

    // ── 重开（暂停菜单「重新开始」语义） ───────────────────────

    @Test
    fun restart_onCampaign_resetsSameLevel() {
        val game = MazeGame().apply { startLevel(5) }
        val level = game.level
        game.moves = 9
        game.restart()
        assertEquals(MazePhase.PLAYING, game.phase)
        assertEquals(5, game.levelIndex)
        assertEquals(0, game.moves)
        assertEquals("同一关对象应被复用", level, game.level)
    }

    @Test
    fun restart_onEndless_keepsWaveAndLayout() {
        val game = MazeGame().apply { startEndless(3) }
        val level = game.level
        game.restart()
        assertTrue(game.endless)
        assertEquals(3, game.endlessWave)
        assertEquals(0, game.moves)
        assertEquals("重开无尽应复用同一张图", level.map, game.level.map)
    }

    @Test
    fun restart_onDaily_reusesSameDateLayout() {
        val game = MazeGame().apply { startDaily(20_000L) }
        val map = game.level.map
        game.restart()
        assertTrue(game.daily)
        assertEquals(20_000L, game.dailyDate)
        assertEquals(map, game.level.map)
    }
}

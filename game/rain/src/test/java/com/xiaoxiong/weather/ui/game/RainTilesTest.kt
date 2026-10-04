package com.xiaoxiong.weather.ui.game

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RainTilesTest {
    private fun seedWhere(
        kind: RainBlackKind,
        minRow: Int = 0,
    ): Pair<Long, Int> {
        for (row in minRow until 32) {
            for (s in 0L..10_000L) {
                if (rainBlackKind(row, s) == kind) return s to row
            }
        }
        error("no seed for $kind at row>=$minRow")
    }

    @Test
    fun blackCol_respectsColumnCount() {
        assertTrue(rainBlackCol(0, 99L, columns = 3) in 0..2)
        assertTrue(rainBlackCol(0, 99L, columns = 2) in 0..1)
    }

    @Test
    fun start_storesColumnCount() {
        val game = RainTiles()
        game.start(seed = 1L, columns = 3)
        assertEquals(3, game.columnCount)
    }

    @Test
    fun mapping_row0AtBottom_matchesPixelTap() {
        val canvasW = 400f
        val canvasH = 800f
        val scroll = (RAIN_ROWS_ON_SCREEN - 1).toFloat()
        val top = rainCellTopPx(scroll, 0, canvasH)
        assertEquals(600f, top, 0.01f)
        val ny = (top + 100f) / canvasH
        assertEquals(0, rainRowAt(scroll, ny))
        assertEquals(0, rainColAt(50f / canvasW, columns = 4))
        assertEquals(3, rainColAt(399f / canvasW, columns = 4))
        assertEquals(rainCellLeftPx(2, canvasW, columns = 4), 200f, 0.01f)
    }

    @Test
    fun mapping_neverTreatsNormalizedAsPixels() {
        val top = rainCellTopPx(scroll = 3f, row = 2, canvasH = 800f)
        assertTrue("顶边应在像素量级而不是 0..4 行空间", top > 50f)
        assertEquals(200f, top, 0.01f)
    }

    @Test
    fun plainTile_scoresOnOneTap() {
        val (seed, row) = seedWhere(RainBlackKind.PLAIN, minRow = 0)
        val game = RainTiles()
        game.start(seed = seed)
        game.testingAlignRowAtBottom(row)
        val black = rainBlackCol(row, seed)
        val nx = (black + 0.5f) / game.columnCount.toFloat()
        assertEquals(RainTapResult.HIT, game.tap(nx, 0.9f))
        assertEquals(1, game.score)
        assertTrue(game.isCleared(row))
    }

    @Test
    fun bearTile_scoresOnOneTapWithBearResult() {
        val (seed, row) = seedWhere(RainBlackKind.BEAR, minRow = 0)
        val game = RainTiles()
        game.start(seed = seed)
        game.testingAlignRowAtBottom(row)
        val black = rainBlackCol(row, seed)
        assertEquals(RainTapResult.HIT_BEAR, game.tap((black + 0.5f) / game.columnCount.toFloat(), 0.9f))
        assertEquals(1, game.score)
    }

    @Test
    fun doubleTile_needsTwoTapsOnSameCell() {
        val (seed, row) = seedWhere(RainBlackKind.DOUBLE, minRow = 8)
        val game = RainTiles()
        game.start(seed = seed)
        game.testingAlignRowAtBottom(row)
        val black = rainBlackCol(row, seed)
        val nx = (black + 0.5f) / game.columnCount.toFloat()
        assertEquals(RainTapResult.HIT_DOUBLE_PRIMED, game.tap(nx, 0.9f))
        assertEquals(0, game.score)
        assertTrue(game.isPrimed(row))
        assertEquals(RainTapResult.HIT_DOUBLE_CLEAR, game.tap(nx, 0.9f))
        assertEquals(1, game.score)
        assertTrue(game.isCleared(row))
    }

    @Test
    fun doubleTile_missIfOnlyPrimedThenScrollsAway() {
        val (seed, row) = seedWhere(RainBlackKind.DOUBLE, minRow = 8)
        val game = RainTiles()
        game.start(seed = seed)
        game.testingAlignRowAtBottom(row)
        val black = rainBlackCol(row, seed)
        game.tap((black + 0.5f) / game.columnCount.toFloat(), 0.9f)
        repeat(200) { game.tick(1f / 60f) }
        assertEquals(RainPhase.OVER, game.phase)
        assertEquals(RainFail.MISS, game.fail)
    }

    @Test
    fun tapWhite_beforeScroll_staysPlaying() {
        val game = RainTiles()
        game.start(seed = 7L)
        val cols = TILES_COLUMNS_DEFAULT
        val black = rainBlackCol(0, 7L, cols)
        val white = (black + 1) % cols
        assertFalse(game.moving)
        assertEquals(RainTapResult.WHITE_WARMUP, game.tap((white + 0.5f) / cols.toFloat(), 0.9f))
        assertEquals(RainPhase.PLAYING, game.phase)
        assertEquals(0, game.score)
    }

    @Test
    fun tapWhite_afterFirstBlack_endsRun() {
        val game = RainTiles()
        game.start(seed = 7L)
        val cols = TILES_COLUMNS_DEFAULT
        val black0 = rainBlackCol(0, 7L, cols)
        val nxBlack = (black0 + 0.5f) / cols.toFloat()
        val first = game.tap(nxBlack, 0.9f)
        assertTrue(first == RainTapResult.HIT || first == RainTapResult.HIT_BEAR)
        assertTrue(game.moving)
        val black1 = rainBlackCol(1, 7L, cols)
        val white1 = (black1 + 1) % cols
        val top1 = rainCellTopPx(game.scroll, 1, 800f)
        val ny1 = (top1 + 40f) / 800f
        assertEquals(RainTapResult.WHITE, game.tap((white1 + 0.5f) / cols.toFloat(), ny1))
        assertEquals(RainPhase.OVER, game.phase)
    }

    @Test
    fun miss_whenUntappedRowLeavesBottom() {
        val (seed, row) = seedWhere(RainBlackKind.PLAIN, minRow = 0)
        val game = RainTiles()
        game.start(seed = seed)
        game.testingAlignRowAtBottom(row)
        val black = rainBlackCol(row, seed)
        game.tap((black + 0.5f) / game.columnCount.toFloat(), 0.9f)
        repeat(180) { game.tick(1f / 60f) }
        assertEquals(RainPhase.OVER, game.phase)
        assertEquals(RainFail.MISS, game.fail)
    }

    @Test
    fun pauseEquivalent_noMoveUntilFirstHit() {
        val game = RainTiles()
        game.start(seed = 1L)
        game.tick(1f)
        assertEquals(3f, game.scroll, 0f)
        assertFalse(game.moving)
    }

    @Test
    fun clearedTile_ignoresRetap() {
        val (seed, row) = seedWhere(RainBlackKind.PLAIN, minRow = 0)
        val game = RainTiles()
        game.start(seed = seed)
        val nx = (rainBlackCol(row, seed, game.columnCount) + 0.5f) / game.columnCount.toFloat()
        game.tap(nx, 0.92f)
        assertEquals(RainTapResult.IGNORE, game.tap(nx, 0.92f))
    }

    @Test
    fun speed_warmupThenRampsAndCaps() {
        assertEquals(RainSpeed.START, RainSpeed.rowsPerSecond(0), 0f)
        assertTrue(RainSpeed.rowsPerSecond(5) < RainSpeed.START + 5 * RainSpeed.PER_HIT)
        assertTrue(RainSpeed.rowsPerSecond(25) > RainSpeed.rowsPerSecond(10))
        assertEquals(RainSpeed.MAX, RainSpeed.rowsPerSecond(10_000), 0f)
        assertTrue(RainSpeed.secondsPerScreen(0) > 2f)
        assertTrue(RainSpeed.secondsPerScreen(100) < RainSpeed.secondsPerScreen(0))
    }

    @Test
    fun blackKind_noDoubleOnFirstRows() {
        for (row in 0 until 8) {
            for (seed in 0L..200L) {
                assertTrue(rainBlackKind(row.toInt(), seed) != RainBlackKind.DOUBLE)
            }
        }
    }

    @Test
    fun blackKind_doubleDisabled_becomesPlain() {
        for (row in 8 until 80) {
            for (seed in 0L..80L) {
                if (rainBlackKind(row, seed, allowDouble = true) == RainBlackKind.DOUBLE) {
                    assertEquals(RainBlackKind.PLAIN, rainBlackKind(row, seed, allowDouble = false))
                }
            }
        }
    }

    @Test
    fun speed_capAtSixRowsPerSecond() {
        assertEquals(6f, RainSpeed.rowsPerSecond(999), 0.01f)
        assertEquals(4f / 6f, RainSpeed.secondsPerScreen(999), 0.05f)
    }
}

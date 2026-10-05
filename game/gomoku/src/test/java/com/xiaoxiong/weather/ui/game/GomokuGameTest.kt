package com.xiaoxiong.weather.ui.game

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GomokuGameTest {
    @Test
    fun isLegalHumanMove_allowsEmptyFarFromStones() {
        val game = GomokuGame()
        game.start(GomokuDifficulty.NORMAL)
        val far = gomokuIndex(0, 0)
        assertTrue(far !in gomokuCandidateMoves(game.board))
        assertTrue(game.isLegalHumanMove(far))
        assertTrue(game.playHumanMove(far))
        assertEquals(GOMOKU_BLACK, game.board[far])
    }

    @Test
    fun isLegalHumanMove_rejectsOccupied() {
        val game = GomokuGame()
        game.start(GomokuDifficulty.NORMAL)
        val center = gomokuIndex(7, 7)
        assertTrue(game.playHumanMove(center))
        game.turn = GOMOKU_BLACK
        assertFalse(game.isLegalHumanMove(center))
        assertFalse(game.playHumanMove(center))
    }

    @Test
    fun stoneCounts_trackPlacedDiscs() {
        val game = GomokuGame()
        game.start(GomokuDifficulty.NORMAL)
        assertEquals(0, game.blackCount)
        assertEquals(0, game.whiteCount)
        assertTrue(game.playHumanMove(gomokuIndex(7, 7)))
        assertEquals(1, game.blackCount)
        assertEquals(0, game.whiteCount)
    }
}

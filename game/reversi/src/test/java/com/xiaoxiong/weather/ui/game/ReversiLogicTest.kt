package com.xiaoxiong.weather.ui.game

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ReversiLogicTest {
    @Test
    fun initialBoard_hasFourDiscsAndLegalMovesForBlack() {
        val board = reversiInitialBoard()
        assertEquals(2, reversiCount(board, REVERSI_BLACK))
        assertEquals(2, reversiCount(board, REVERSI_WHITE))
        assertTrue(reversiLegalMoves(board, REVERSI_BLACK).isNotEmpty())
    }

    @Test
    fun applyMove_flipsOpponentLine() {
        val board = reversiInitialBoard()
        val move = reversiLegalMoves(board, REVERSI_BLACK).first()
        val flips = reversiApplyMove(board, move.index, REVERSI_BLACK)
        assertTrue(flips > 0)
        assertEquals(REVERSI_BLACK, board[move.index])
    }

    @Test
    fun chooseAiMove_returnsLegalWhenMovesExist() {
        val board = reversiInitialBoard()
        val blackMove = reversiLegalMoves(board, REVERSI_BLACK).first()
        reversiApplyMove(board, blackMove.index, REVERSI_BLACK)
        val idx = reversiChooseAiMove(board, ReversiDifficulty.NORMAL)
        assertTrue(idx != null)
        assertTrue(reversiFlipsIfMove(board, idx!!, REVERSI_WHITE) > 0)
    }

    @Test
    fun difficulty_fromId_mapsLegacyAndDefaults() {
        assertEquals(ReversiDifficulty.BEGINNER, ReversiDifficulty.fromId("casual"))
        assertEquals(ReversiDifficulty.HARD, ReversiDifficulty.fromId("challenge"))
        assertEquals(ReversiDifficulty.NORMAL, ReversiDifficulty.fromId(null))
        assertEquals(ReversiDifficulty.EXPERT, ReversiDifficulty.fromId("expert"))
    }

    @Test
    fun difficulty_searchDepth_increasesWithTier() {
        val depths =
            listOf(
                ReversiDifficulty.BEGINNER,
                ReversiDifficulty.EASY,
                ReversiDifficulty.NORMAL,
                ReversiDifficulty.ADVANCED,
                ReversiDifficulty.HARD,
                ReversiDifficulty.MASTER,
                ReversiDifficulty.EXPERT,
            ).map { it.searchDepth }
        assertEquals(listOf(0, 2, 3, 4, 5, 6, 7), depths)
    }
}

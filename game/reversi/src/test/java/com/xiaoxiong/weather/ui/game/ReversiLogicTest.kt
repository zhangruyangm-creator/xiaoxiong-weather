package com.xiaoxiong.weather.ui.game

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
        assertEquals(REVERSI_WHITE, reversiAdvanceTurn(board, REVERSI_BLACK).first)
    }

    @Test
    fun countAndHasLegalMove_matchListVersion() {
        val board = reversiInitialBoard()
        assertEquals(reversiLegalMoves(board, REVERSI_BLACK).size, reversiCountLegalMoves(board, REVERSI_BLACK))
        assertEquals(reversiLegalMoves(board, REVERSI_WHITE).size, reversiCountLegalMoves(board, REVERSI_WHITE))
        assertEquals(reversiLegalMoves(board, REVERSI_BLACK).isNotEmpty(), reversiHasLegalMove(board, REVERSI_BLACK))
        assertEquals(reversiLegalMoves(board, REVERSI_WHITE).isNotEmpty(), reversiHasLegalMove(board, REVERSI_WHITE))
        assertEquals(
            reversiIsGameOver(board),
            !reversiHasLegalMove(board, REVERSI_BLACK) && !reversiHasLegalMove(board, REVERSI_WHITE),
        )
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
    fun hasLegalMove_whiteCanPlayWhenBlackMustPass() {
        val board = IntArray(REVERSI_CELLS) { REVERSI_WHITE }
        board[reversiIndex(0, 0)] = REVERSI_EMPTY
        board[reversiIndex(0, 1)] = REVERSI_BLACK
        assertFalse(reversiHasLegalMove(board, REVERSI_BLACK))
        assertTrue(reversiHasLegalMove(board, REVERSI_WHITE))
        assertEquals(REVERSI_WHITE, reversiAdvanceTurn(board, REVERSI_BLACK).first)
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
        assertEquals(listOf(0, 2, 3, 4, 5, 7, 8), depths)
    }

    @Test
    fun master_usesDeeperEndgameThanLowerTiers() {
        assertEquals(12, reversiEndgameEmpties(ReversiDifficulty.HARD))
        assertEquals(16, reversiEndgameEmpties(ReversiDifficulty.MASTER))
        assertEquals(16, reversiEndgameEmpties(ReversiDifficulty.EXPERT))
    }
}

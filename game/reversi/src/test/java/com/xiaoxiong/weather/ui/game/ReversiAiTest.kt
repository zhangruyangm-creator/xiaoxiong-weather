package com.xiaoxiong.weather.ui.game

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ReversiAiTest {
    @Test
    fun search_takesAvailableCorner() {
        // (0,0) 对白方合法（(0,1) 黑、(0,2) 白）；好棋手必取角，根节点符号若反了会避开它
        val board = IntArray(REVERSI_CELLS)
        board[reversiIndex(0, 1)] = REVERSI_BLACK
        board[reversiIndex(0, 2)] = REVERSI_WHITE
        board[reversiIndex(3, 3)] = REVERSI_WHITE
        board[reversiIndex(3, 4)] = REVERSI_BLACK
        assertEquals(reversiIndex(0, 0), reversiChooseAiMove(board, ReversiDifficulty.NORMAL))
    }

    @Test
    fun search_beatsGreedyBaseline() {
        // 白=搜索(NORMAL)，黑=贪心(吃最多子)。符号正确时白应明显取胜；符号反了会惨败
        val board =
            playGame(
                whiteMove = { reversiChooseAiMove(it, ReversiDifficulty.NORMAL) },
                blackMove = { b ->
                    reversiLegalMoves(b, REVERSI_BLACK)
                        .maxByOrNull { it.flips + reversiCornerBonus(it.index) }
                        ?.index
                },
            )
        val black = reversiCount(board, REVERSI_BLACK)
        val white = reversiCount(board, REVERSI_WHITE)
        assertTrue("搜索白应胜过贪心黑，实际 黑=$black 白=$white", white > black)
    }

    @Test
    fun chooseAiMove_returnsNullWhenWhiteHasNoMoves() {
        // 仅剩黑子的局面：白方无子可下
        val board = IntArray(REVERSI_CELLS)
        board[reversiIndex(0, 0)] = REVERSI_BLACK
        assertEquals(null, reversiChooseAiMove(board, ReversiDifficulty.NORMAL))
    }

    @Test
    fun flipsIfMove_outOfRangeIndexIsNotLegal() {
        val board = reversiInitialBoard()
        assertEquals(0, reversiFlipsIfMove(board, -1, REVERSI_BLACK))
        assertEquals(0, reversiFlipsIfMove(board, REVERSI_CELLS, REVERSI_BLACK))
    }

    private fun playGame(
        whiteMove: (IntArray) -> Int?,
        blackMove: (IntArray) -> Int?,
    ): IntArray {
        val board = reversiInitialBoard()
        var turn = REVERSI_BLACK
        var guard = 0
        while (!reversiIsGameOver(board) && guard++ < 200) {
            if (reversiLegalMoves(board, turn).isEmpty()) {
                turn = reversiOpponent(turn)
                continue
            }
            val mv = if (turn == REVERSI_WHITE) whiteMove(board) else blackMove(board)
            if (mv == null) {
                turn = reversiOpponent(turn)
                continue
            }
            reversiApplyMove(board, mv, turn)
            turn = reversiOpponent(turn)
        }
        return board
    }
}

package com.xiaoxiong.weather.ui.game

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GomokuLogicTest {
    @Test
    fun win_detectsFiveInRow() {
        val board = gomokuInitialBoard()
        val row = 7
        for (c in 0 until 5) {
            board[gomokuIndex(row, c)] = GOMOKU_BLACK
        }
        assertEquals(GomokuOutcome.BLACK_WIN, gomokuOutcome(board))
    }

    @Test
    fun chooseAiMove_returnsWinWhenAvailable() {
        val board = gomokuInitialBoard()
        val row = 7
        for (c in 0 until 4) {
            board[gomokuIndex(row, c)] = GOMOKU_WHITE
        }
        board[gomokuIndex(row, 10)] = GOMOKU_BLACK
        val move = gomokuChooseAiMove(board, GomokuDifficulty.NORMAL)
        assertEquals(gomokuIndex(row, 4), move)
    }

    @Test
    fun candidateMoves_startAtCenter() {
        val board = gomokuInitialBoard()
        assertEquals(listOf(gomokuIndex(7, 7)), gomokuCandidateMoves(board))
    }

    @Test
    fun chooseAiMove_blocksOpenThree() {
        // 黑方 row7 有活三(5,6,7):白方不封堵则黑方成活四必胜。搜索需选出封堵点,
        // 覆盖不走 findImmediateWin 捷径的 minimax 分支(根节点符号回归测试)。
        val board = gomokuInitialBoard()
        board[gomokuIndex(7, 5)] = GOMOKU_BLACK
        board[gomokuIndex(7, 6)] = GOMOKU_BLACK
        board[gomokuIndex(7, 7)] = GOMOKU_BLACK
        board[gomokuIndex(6, 6)] = GOMOKU_WHITE
        board[gomokuIndex(8, 8)] = GOMOKU_WHITE
        val move = gomokuChooseAiMove(board, GomokuDifficulty.NORMAL)
        val blocks = setOf(gomokuIndex(7, 4), gomokuIndex(7, 8))
        assertTrue("AI 未封堵黑方活三,实际落子:$move", move in blocks)
    }

    @Test
    fun findImmediateWin_detectsMustBlock() {
        val board = gomokuInitialBoard()
        val row = 7
        for (c in 0 until 4) {
            board[gomokuIndex(row, c)] = GOMOKU_BLACK
        }
        val blackKill = gomokuFindImmediateWin(board, GOMOKU_BLACK)
        assertEquals(gomokuIndex(row, 4), blackKill)
    }

    @Test
    fun beginner_isDeterministicForSameBoard() {
        val board = gomokuInitialBoard()
        board[gomokuIndex(7, 7)] = GOMOKU_BLACK
        board[gomokuIndex(7, 8)] = GOMOKU_BLACK
        board[gomokuIndex(6, 7)] = GOMOKU_BLACK
        board[gomokuIndex(7, 6)] = GOMOKU_WHITE
        board[gomokuIndex(8, 7)] = GOMOKU_WHITE
        board[gomokuIndex(6, 6)] = GOMOKU_WHITE
        val first = gomokuChooseAiMove(board, GomokuDifficulty.BEGINNER)
        val second = gomokuChooseAiMove(board.copyOf(), GomokuDifficulty.BEGINNER)
        assertEquals(first, second)
        assertTrue(first != null && first in gomokuCandidateMoves(board))
    }

    @Test
    fun difficulty_strengthIsMonotonic() {
        // 高阶档位不应出现"深度/候选数更低"（否则更高档反而更弱）
        val tiers =
            listOf(
                GomokuDifficulty.HARD,
                GomokuDifficulty.MASTER,
                GomokuDifficulty.EXPERT,
            )
        assertTrue(tiers.zipWithNext().all { (a, b) -> a.searchDepth <= b.searchDepth })
        assertTrue(tiers.zipWithNext().all { (a, b) -> a.topMoves <= b.topMoves })
    }

    @Test
    fun search_beatsGreedyBaseline() {
        // 确定性强度回归（类似 pool 的 perfect-bot）：白=EXPERT(深度4)、黑=贪心 1 层。
        // 根节点符号 / 评估 / 深度任一出错都可能让白在此局落败——用于锁死回归。
        val outcome =
            playGomoku(
                whiteMove = { gomokuChooseAiMove(it, GomokuDifficulty.EXPERT) },
                blackMove = { b -> gomokuRankedMoves(b, GOMOKU_BLACK, 1).firstOrNull() },
            )
        assertEquals(GomokuOutcome.WHITE_WIN, outcome)
    }

    @Test
    fun vcf_findsForcedWinWhenOpenThreeAvailable() {
        val board = gomokuInitialBoard()
        board[gomokuIndex(7, 4)] = GOMOKU_WHITE
        board[gomokuIndex(7, 5)] = GOMOKU_WHITE
        board[gomokuIndex(7, 6)] = GOMOKU_WHITE
        val move = gomokuVcfWin(board, GOMOKU_WHITE)
        assertTrue("VCF 应找到强制取胜着", move != null)
        board[move!!] = GOMOKU_WHITE
        assertTrue("取该着后应形成活四（两个成五点）", gomokuCountImmediateWins(board, GOMOKU_WHITE) >= 2)
    }

    @Test
    fun vcf_nullWhenNoThreat() {
        val board = gomokuInitialBoard()
        board[gomokuIndex(7, 7)] = GOMOKU_WHITE
        board[gomokuIndex(7, 8)] = GOMOKU_BLACK
        assertEquals(null, gomokuVcfWin(board, GOMOKU_WHITE))
    }

    @Test
    fun vcf_nullWhenOpponentCanWinFirst() {
        val board = gomokuInitialBoard()
        for (c in 0 until 4) board[gomokuIndex(7, c)] = GOMOKU_BLACK
        board[gomokuIndex(5, 5)] = GOMOKU_WHITE
        board[gomokuIndex(5, 6)] = GOMOKU_WHITE
        board[gomokuIndex(5, 7)] = GOMOKU_WHITE
        assertEquals(null, gomokuVcfWin(board, GOMOKU_WHITE))
    }

    @Test
    fun vct_findsSoundForcedWinOnDoubleThree() {
        val board = gomokuInitialBoard()
        for ((r, c) in listOf(7 to 5, 7 to 6, 5 to 7, 6 to 7)) {
            board[gomokuIndex(r, c)] = GOMOKU_WHITE
        }
        val move = gomokuVctForcedWin(board, GOMOKU_WHITE)
        assertTrue("VCT 应找到双三杀", move != null)
        // 核验无假杀：走该着后，对黑方**全部**应手，白方都仍能一步杀
        board[move!!] = GOMOKU_WHITE
        for (i in 0 until GOMOKU_CELLS) {
            if (board[i] != GOMOKU_EMPTY) continue
            board[i] = GOMOKU_BLACK
            val blackWins = gomokuWinAt(board, i, GOMOKU_BLACK)
            val whiteStillWins = gomokuWinsInTwoMove(board, GOMOKU_WHITE) != null
            board[i] = GOMOKU_EMPTY
            assertTrue("黑方应手 $i 后白方仍应能杀", !blackWins && whiteStillWins)
        }
    }

    @Test
    fun vct_nullWhenNoThreat() {
        val board = gomokuInitialBoard()
        board[gomokuIndex(7, 7)] = GOMOKU_WHITE
        board[gomokuIndex(7, 8)] = GOMOKU_BLACK
        assertEquals(null, gomokuVctForcedWin(board, GOMOKU_WHITE))
    }

    private fun playGomoku(
        whiteMove: (IntArray) -> Int?,
        blackMove: (IntArray) -> Int?,
    ): GomokuOutcome {
        val board = gomokuInitialBoard()
        var turn = GOMOKU_BLACK
        var guard = 0
        while (guard++ < GOMOKU_CELLS) {
            val move = (if (turn == GOMOKU_WHITE) whiteMove(board) else blackMove(board)) ?: break
            board[move] = turn
            if (gomokuWinAt(board, move, turn)) {
                return if (turn == GOMOKU_BLACK) GomokuOutcome.BLACK_WIN else GomokuOutcome.WHITE_WIN
            }
            if (gomokuIsFull(board)) return GomokuOutcome.DRAW
            turn = gomokuOpponent(turn)
        }
        return GomokuOutcome.DRAW
    }
}

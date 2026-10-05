package com.xiaoxiong.weather.ui.game

import android.content.SharedPreferences
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.xiaoxiong.weather.game.stringOr

internal class GomokuGame {
    var phase by mutableStateOf(GomokuPhase.READY)
        private set
    var difficulty by mutableStateOf(GomokuDifficulty.NORMAL)
        private set
    var board by mutableStateOf(gomokuInitialBoard())
        internal set
    var turn by mutableIntStateOf(GOMOKU_BLACK)
        internal set
    var lastMove by mutableIntStateOf(-1)
        private set
    var outcome by mutableStateOf(GomokuOutcome.NONE)
        private set

    val blackCount: Int
        get() = board.count { it == GOMOKU_BLACK }
    val whiteCount: Int
        get() = board.count { it == GOMOKU_WHITE }

    fun start(difficulty: GomokuDifficulty) {
        this.difficulty = difficulty
        board = gomokuInitialBoard()
        turn = GOMOKU_BLACK
        lastMove = -1
        outcome = GomokuOutcome.NONE
        phase = GomokuPhase.PLAYING
    }

    fun isLegalHumanMove(index: Int): Boolean {
        if (phase != GomokuPhase.PLAYING || turn != GOMOKU_BLACK) return false
        // 自由五子棋：任意空点均可落子（绿色圆点仅作推荐，不限制落点）
        return index in 0 until GOMOKU_CELLS && board[index] == GOMOKU_EMPTY
    }

    fun playHumanMove(index: Int): Boolean {
        if (!isLegalHumanMove(index)) return false
        board = board.copyOf().also { it[index] = GOMOKU_BLACK }
        lastMove = index
        return afterMove(GOMOKU_BLACK, index)
    }

    fun applyAiMove(index: Int): Boolean {
        if (phase != GomokuPhase.PLAYING || turn != GOMOKU_WHITE) return false
        if (index !in 0 until GOMOKU_CELLS || board[index] != GOMOKU_EMPTY) return false
        board = board.copyOf().also { it[index] = GOMOKU_WHITE }
        lastMove = index
        return afterMove(GOMOKU_WHITE, index)
    }

    fun playAiMove(): Boolean {
        if (phase != GomokuPhase.PLAYING || turn != GOMOKU_WHITE) return false
        val index = gomokuChooseAiMove(board, difficulty) ?: return finishDrawIfFull()
        return applyAiMove(index)
    }

    private fun afterMove(
        color: Int,
        index: Int,
    ): Boolean {
        if (gomokuWinAt(board, index, color)) {
            finish(if (color == GOMOKU_BLACK) GomokuOutcome.BLACK_WIN else GomokuOutcome.WHITE_WIN)
            return true
        }
        if (gomokuIsFull(board)) {
            finish(GomokuOutcome.DRAW)
            return true
        }
        turn = gomokuOpponent(color)
        return true
    }

    private fun finishDrawIfFull(): Boolean {
        if (gomokuIsFull(board)) {
            finish(GomokuOutcome.DRAW)
            return true
        }
        return false
    }

    private fun finish(result: GomokuOutcome) {
        phase = GomokuPhase.OVER
        outcome = result
    }

    fun legalHintMoves(): List<Int> =
        if (phase == GomokuPhase.PLAYING && turn == GOMOKU_BLACK) {
            gomokuCandidateMoves(board)
        } else {
            emptyList()
        }
}

internal fun gomokuDifficultyFromPrefs(prefs: SharedPreferences): GomokuDifficulty =
    GomokuDifficulty.fromId(prefs.stringOr(GOMOKU_PREF_DIFF))

internal fun gomokuPlayerScore(outcome: GomokuOutcome): Int =
    when (outcome) {
        GomokuOutcome.BLACK_WIN -> 100
        GomokuOutcome.DRAW -> 50
        GomokuOutcome.WHITE_WIN -> 0
        GomokuOutcome.NONE -> 0
    }

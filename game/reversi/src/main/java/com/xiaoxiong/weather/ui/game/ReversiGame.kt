package com.xiaoxiong.weather.ui.game

import android.content.SharedPreferences
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.xiaoxiong.weather.game.stringOr

internal class ReversiGame {
    var phase by mutableStateOf(ReversiPhase.READY)
        private set
    var difficulty by mutableStateOf(ReversiDifficulty.NORMAL)
        private set
    var board by mutableStateOf(reversiInitialBoard())
        private set
    var turn by mutableIntStateOf(REVERSI_BLACK)
        private set
    var lastMove by mutableIntStateOf(-1)
        private set
    var outcome by mutableStateOf(ReversiOutcome.NONE)
        private set
    var passNotice by mutableStateOf(false)
        private set

    val blackCount: Int get() = reversiCount(board, REVERSI_BLACK)
    val whiteCount: Int get() = reversiCount(board, REVERSI_WHITE)

    fun legalMovesForTurn(): List<Int> = reversiLegalMoves(board, turn).map { it.index }

    fun isLegal(index: Int): Boolean = reversiFlipsIfMove(board, index, turn) > 0

    fun start(difficulty: ReversiDifficulty) {
        this.difficulty = difficulty
        board = reversiInitialBoard()
        turn = REVERSI_BLACK
        lastMove = -1
        outcome = ReversiOutcome.NONE
        passNotice = false
        phase = ReversiPhase.PLAYING
    }

    fun playHumanMove(index: Int): Boolean {
        if (phase != ReversiPhase.PLAYING || turn != REVERSI_BLACK) return false
        if (!isLegal(index)) return false
        val next = board.copyOf()
        reversiApplyMove(next, index, REVERSI_BLACK)
        board = next
        lastMove = index
        passNotice = false
        return afterMove()
    }

    fun playAiMove(): Boolean {
        if (phase != ReversiPhase.PLAYING || turn != REVERSI_WHITE) return false
        val index = reversiChooseAiMove(board, difficulty) ?: return endIfStuck()
        val next = board.copyOf()
        reversiApplyMove(next, index, REVERSI_WHITE)
        board = next
        lastMove = index
        passNotice = false
        return afterMove()
    }

    private fun afterMove(): Boolean {
        if (reversiIsGameOver(board)) {
            finish()
            return true
        }
        val (nextTurn, skipped) = reversiAdvanceTurn(board, turn)
        turn = nextTurn
        passNotice = skipped
        if (reversiIsGameOver(board)) {
            finish()
        }
        return true
    }

    private fun endIfStuck(): Boolean {
        if (reversiIsGameOver(board)) {
            finish()
            return true
        }
        val (nextTurn, skipped) = reversiAdvanceTurn(board, turn)
        turn = nextTurn
        passNotice = skipped
        if (reversiIsGameOver(board)) finish()
        return true
    }

    private fun finish() {
        phase = ReversiPhase.OVER
        outcome = reversiOutcome(board)
    }
}

internal fun reversiDifficultyFromPrefs(prefs: SharedPreferences): ReversiDifficulty =
    ReversiDifficulty.fromId(prefs.stringOr(REVERSI_PREF_DIFF))

internal fun reversiPlayerScore(
    outcome: ReversiOutcome,
    blackCount: Int,
): Int =
    when (outcome) {
        ReversiOutcome.BLACK_WIN -> blackCount
        ReversiOutcome.TIE -> 32
        ReversiOutcome.WHITE_WIN -> blackCount.coerceAtMost(32)
        ReversiOutcome.NONE -> 0
    }

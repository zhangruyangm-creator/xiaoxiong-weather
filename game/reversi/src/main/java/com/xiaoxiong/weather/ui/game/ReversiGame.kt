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
        internal set
    var turn by mutableIntStateOf(REVERSI_BLACK)
        internal set
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

    /** 应用外部（后台线程）算好的 AI 落子；越权或非法时返回 false。 */
    fun applyAiMove(index: Int): Boolean {
        if (phase != ReversiPhase.PLAYING || turn != REVERSI_WHITE) return false
        if (!isLegal(index)) return false
        val next = board.copyOf()
        reversiApplyMove(next, index, REVERSI_WHITE)
        board = next
        lastMove = index
        passNotice = false
        return afterMove()
    }

    fun playAiMove(): Boolean {
        if (phase != ReversiPhase.PLAYING || turn != REVERSI_WHITE) return false
        val index = reversiChooseAiMove(board, difficulty) ?: return passTurnIfNoLegalMove()
        return applyAiMove(index)
    }

    /** 当前方无子可下时过手；返回 true 表示局面有变化（含终局）。 */
    fun passTurnIfNoLegalMove(): Boolean {
        if (phase != ReversiPhase.PLAYING) return false
        if (reversiHasLegalMove(board, turn)) {
            passNotice = false
            return false
        }
        if (reversiIsGameOver(board)) {
            finish()
            return true
        }
        val (nextTurn, _) = reversiAdvanceTurn(board, turn)
        turn = nextTurn
        // 当前方无子可下而对手有：这就是过手，必须提示（skipped 表示的是「对手被跳过、己方续走」）
        passNotice = true
        if (reversiIsGameOver(board)) {
            finish()
        }
        return true
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

    private fun finish() {
        phase = ReversiPhase.OVER
        outcome = reversiOutcome(board)
    }
}

internal fun reversiDifficultyFromPrefs(prefs: SharedPreferences): ReversiDifficulty =
    ReversiDifficulty.fromId(prefs.stringOr(REVERSI_PREF_DIFF))

internal fun reversiOutcomeCue(outcome: ReversiOutcome): GameCue =
    when (outcome) {
        ReversiOutcome.BLACK_WIN -> GameCue.WIN
        ReversiOutcome.WHITE_WIN -> GameCue.LOSE
        ReversiOutcome.TIE, ReversiOutcome.NONE -> GameCue.STAR
    }

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

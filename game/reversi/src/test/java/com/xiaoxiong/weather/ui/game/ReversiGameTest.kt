package com.xiaoxiong.weather.ui.game

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReversiGameTest {
    @Test
    fun passTurnIfNoLegalMove_whenCurrentSideBlocked_advancesTurn() {
        val game = ReversiGame()
        game.start(ReversiDifficulty.NORMAL)
        val board = IntArray(REVERSI_CELLS) { REVERSI_WHITE }
        board[reversiIndex(0, 0)] = REVERSI_EMPTY
        board[reversiIndex(0, 1)] = REVERSI_BLACK
        game.board = board
        game.turn = REVERSI_BLACK
        assertTrue(game.passTurnIfNoLegalMove())
        assertEquals(REVERSI_WHITE, game.turn)
        assertTrue(game.passNotice)
        assertEquals(ReversiPhase.PLAYING, game.phase)
    }

    @Test
    fun passTurnIfNoLegalMove_whenMovesExist_doesNothing() {
        val game = ReversiGame()
        game.start(ReversiDifficulty.NORMAL)
        assertFalse(game.passTurnIfNoLegalMove())
        assertEquals(REVERSI_BLACK, game.turn)
        assertFalse(game.passNotice)
    }

    @Test
    fun playHumanMove_rejectsIllegalCell() {
        val game = ReversiGame()
        game.start(ReversiDifficulty.NORMAL)
        assertFalse(game.playHumanMove(0))
        assertEquals(REVERSI_BLACK, game.turn)
    }

    @Test
    fun outcomeCue_drawUsesStarNotLose() {
        assertEquals(GameCue.STAR, reversiOutcomeCue(ReversiOutcome.TIE))
        assertEquals(GameCue.WIN, reversiOutcomeCue(ReversiOutcome.BLACK_WIN))
        assertEquals(GameCue.LOSE, reversiOutcomeCue(ReversiOutcome.WHITE_WIN))
    }
}

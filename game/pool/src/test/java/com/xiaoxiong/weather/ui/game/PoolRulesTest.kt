package com.xiaoxiong.weather.ui.game

import com.xiaoxiong.weather.ui.game.PoolBallKind.EIGHT
import com.xiaoxiong.weather.ui.game.PoolBallKind.SOLID
import com.xiaoxiong.weather.ui.game.PoolBallKind.STRIPE
import com.xiaoxiong.weather.ui.game.PoolGroup.SOLIDS
import com.xiaoxiong.weather.ui.game.PoolGroup.STRIPES
import com.xiaoxiong.weather.ui.game.PoolMessage.BREAK_RERACK
import com.xiaoxiong.weather.ui.game.PoolMessage.CONTINUE
import com.xiaoxiong.weather.ui.game.PoolMessage.EIGHT_LOSE
import com.xiaoxiong.weather.ui.game.PoolMessage.EIGHT_WIN
import com.xiaoxiong.weather.ui.game.PoolMessage.FOUL_CUE
import com.xiaoxiong.weather.ui.game.PoolMessage.FOUL_MISS
import com.xiaoxiong.weather.ui.game.PoolMessage.FOUL_NO_RAIL
import com.xiaoxiong.weather.ui.game.PoolMessage.FOUL_WRONG_BALL
import com.xiaoxiong.weather.ui.game.PoolMessage.GROUP_ASSIGNED
import com.xiaoxiong.weather.ui.game.PoolMessage.TURN_PASS
import com.xiaoxiong.weather.ui.game.PoolSide.AI
import com.xiaoxiong.weather.ui.game.PoolSide.PLAYER
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PoolRulesTest {
    private val noRemaining = PoolRules.RemainingBalls(solids = 3, stripes = 3, eight = 1)

    private fun outcome(
        first: PoolBallKind? = SOLID,
        rail: Boolean = true,
        potted: List<PoolBallKind> = emptyList(),
        cuePotted: Boolean = false,
        clearedBefore: Boolean = false,
    ) = PoolShotOutcome(first, rail, potted, cuePotted, clearedBefore)

    @Test
    fun openTableFirstPot_assignsGroupsAndKeepsTurn() {
        val update =
            PoolRules.resolve(
                PoolMatchState(openTable = true, turn = PLAYER),
                outcome(potted = listOf(SOLID)),
                noRemaining,
                isBreak = false,
            )
        assertFalse(update.foul)
        assertEquals(SOLIDS, update.state.playerGroup)
        assertEquals(STRIPES, update.state.aiGroup)
        assertEquals(PLAYER, update.state.turn)
        assertEquals(GROUP_ASSIGNED, update.message)
    }

    @Test
    fun ownPot_continuesTurn() {
        val state = PoolMatchState(openTable = false, playerGroup = SOLIDS, aiGroup = STRIPES, turn = PLAYER)
        val update = PoolRules.resolve(state, outcome(first = SOLID, potted = listOf(SOLID)), noRemaining, isBreak = false)
        assertFalse(update.foul)
        assertEquals(PLAYER, update.state.turn)
        assertEquals(CONTINUE, update.message)
    }

    @Test
    fun noPot_legalHit_passesTurn() {
        val state = PoolMatchState(openTable = false, playerGroup = SOLIDS, aiGroup = STRIPES, turn = PLAYER)
        val update = PoolRules.resolve(state, outcome(first = SOLID, potted = emptyList()), noRemaining, isBreak = false)
        assertFalse(update.foul)
        assertEquals(AI, update.state.turn)
        assertEquals(TURN_PASS, update.message)
    }

    @Test
    fun cuePotted_isFoulAndBallInHand() {
        val update = PoolRules.resolve(PoolMatchState(turn = PLAYER), outcome(cuePotted = true), noRemaining, isBreak = false)
        assertTrue(update.foul)
        assertTrue(update.ballInHand)
        assertEquals(AI, update.state.turn)
        assertEquals(FOUL_CUE, update.message)
    }

    @Test
    fun wrongBallFirst_isFoul() {
        val state = PoolMatchState(openTable = false, playerGroup = SOLIDS, aiGroup = STRIPES, turn = PLAYER)
        val update = PoolRules.resolve(state, outcome(first = STRIPE), noRemaining, isBreak = false)
        assertTrue(update.foul)
        assertEquals(FOUL_WRONG_BALL, update.message)
    }

    @Test
    fun noRailAfterContact_isFoul() {
        val state = PoolMatchState(openTable = false, playerGroup = SOLIDS, aiGroup = STRIPES, turn = PLAYER)
        val update = PoolRules.resolve(state, outcome(first = SOLID, rail = false), noRemaining, isBreak = false)
        assertTrue(update.foul)
        assertEquals(FOUL_NO_RAIL, update.message)
    }

    @Test
    fun completeMiss_isFoul() {
        val state = PoolMatchState(openTable = false, playerGroup = SOLIDS, aiGroup = STRIPES, turn = PLAYER)
        val update = PoolRules.resolve(state, outcome(first = null, rail = false), noRemaining, isBreak = false)
        assertTrue(update.foul)
        assertEquals(FOUL_MISS, update.message)
    }

    @Test
    fun legalEightAfterClear_winsFrame() {
        val state = PoolMatchState(openTable = false, playerGroup = SOLIDS, aiGroup = STRIPES, turn = PLAYER)
        val update =
            PoolRules.resolve(
                state,
                outcome(first = EIGHT, potted = listOf(EIGHT), clearedBefore = true),
                PoolRules.RemainingBalls(solids = 0, stripes = 2, eight = 1),
                isBreak = false,
            )
        assertEquals(PLAYER, update.frameWinner)
        assertEquals(1, update.state.playerFrames)
        assertEquals(EIGHT_WIN, update.message)
    }

    @Test
    fun earlyEight_losesFrame() {
        val state = PoolMatchState(openTable = false, playerGroup = SOLIDS, aiGroup = STRIPES, turn = PLAYER)
        val update =
            PoolRules.resolve(
                state,
                outcome(first = EIGHT, potted = listOf(EIGHT), clearedBefore = false),
                noRemaining,
                isBreak = false,
            )
        assertEquals(AI, update.frameWinner)
        assertEquals(1, update.state.aiFrames)
        assertEquals(EIGHT_LOSE, update.message)
    }

    @Test
    fun eightOnBreak_reracks() {
        val update = PoolRules.resolve(PoolMatchState(turn = PLAYER), outcome(potted = listOf(EIGHT)), noRemaining, isBreak = true)
        assertTrue(update.rerack)
        assertEquals(BREAK_RERACK, update.message)
        assertNull(update.frameWinner)
        assertEquals(0, update.state.playerFrames + update.state.aiFrames)
    }

    @Test
    fun targetKind_openTableNull_groupThenEight() {
        val open = PoolMatchState(openTable = true)
        assertNull(PoolRules.targetKindFor(open, noRemaining))
        val solids = PoolMatchState(openTable = false, playerGroup = SOLIDS, aiGroup = STRIPES)
        assertEquals(SOLID, PoolRules.targetKindFor(solids, noRemaining))
        assertEquals(
            EIGHT,
            PoolRules.targetKindFor(solids, PoolRules.RemainingBalls(solids = 0, stripes = 3, eight = 1)),
        )
    }

    @Test
    fun remaining_countsOnlyOnTable() {
        val table = PoolTable.standard()
        val balls = PoolRack.rack(table, PoolRack.standardOrder())
        balls.first { it.kind == SOLID }.onTable = false
        val r = PoolRules.remaining(balls)
        assertEquals(6, r.solids)
        assertEquals(7, r.stripes)
        assertEquals(1, r.eight)
    }

    @Test
    fun winnerFrameIncrementsOnlyWinner() {
        val state = PoolMatchState(openTable = false, playerGroup = SOLIDS, aiGroup = STRIPES, turn = AI, playerFrames = 1)
        val update =
            PoolRules.resolve(
                state,
                outcome(first = EIGHT, potted = listOf(EIGHT), clearedBefore = true),
                PoolRules.RemainingBalls(solids = 2, stripes = 0, eight = 1),
                isBreak = false,
            )
        assertEquals(AI, update.frameWinner)
        assertEquals(1, update.state.playerFrames)
        assertEquals(1, update.state.aiFrames)
    }

    // ---------------------------------------------------------------------
    // 九球
    // ---------------------------------------------------------------------

    private fun nineState(
        turn: PoolSide = PLAYER,
        playerFrames: Int = 0,
        aiFrames: Int = 0,
    ) = PoolMatchState(turn = turn, playerFrames = playerFrames, aiFrames = aiFrames, mode = PoolMode.NINE_BALL)

    @Test
    fun nineBall_legalLowestFirst_anyPot_continues() {
        val update =
            PoolRules.resolve(
                nineState(),
                PoolShotOutcome(
                    firstContact = SOLID,
                    railAfterContact = true,
                    potted = listOf(SOLID),
                    cuePotted = false,
                    shooterGroupClearedBefore = false,
                    firstContactNumber = 1,
                    pottedNumbers = listOf(1),
                    lowestBefore = 1,
                ),
                PoolRules.RemainingBalls(solids = 4, stripes = 3, eight = 1),
                isBreak = false,
            )
        assertFalse(update.foul)
        assertEquals(PLAYER, update.state.turn)
        assertEquals(CONTINUE, update.message)
    }

    @Test
    fun nineBall_wrongFirstContact_isFoul() {
        val update =
            PoolRules.resolve(
                nineState(),
                PoolShotOutcome(
                    firstContact = STRIPE,
                    railAfterContact = true,
                    potted = emptyList(),
                    cuePotted = false,
                    shooterGroupClearedBefore = false,
                    firstContactNumber = 3,
                    lowestBefore = 1,
                ),
                PoolRules.RemainingBalls(solids = 4, stripes = 3, eight = 1),
                isBreak = false,
            )
        assertTrue(update.foul)
        assertTrue(update.ballInHand)
        assertEquals(AI, update.state.turn)
        assertEquals(FOUL_WRONG_BALL, update.message)
    }

    @Test
    fun nineBall_legalNine_win() {
        val update =
            PoolRules.resolve(
                nineState(),
                PoolShotOutcome(
                    firstContact = STRIPE,
                    railAfterContact = true,
                    potted = listOf(STRIPE),
                    cuePotted = false,
                    shooterGroupClearedBefore = false,
                    firstContactNumber = 9,
                    pottedNumbers = listOf(9),
                    lowestBefore = 9,
                ),
                PoolRules.RemainingBalls(solids = 0, stripes = 0, eight = 0),
                isBreak = false,
            )
        assertEquals(PLAYER, update.frameWinner)
        assertEquals(1, update.state.playerFrames)
        assertEquals(PoolMessage.NINE_WIN, update.message)
    }

    @Test
    fun nineBall_earlyNine_legalFirst_win() {
        // 组合球：最低号是 1，但 9 号合法落袋 → 赢
        val update =
            PoolRules.resolve(
                nineState(),
                PoolShotOutcome(
                    firstContact = SOLID,
                    railAfterContact = true,
                    potted = listOf(STRIPE),
                    cuePotted = false,
                    shooterGroupClearedBefore = false,
                    firstContactNumber = 1,
                    pottedNumbers = listOf(9),
                    lowestBefore = 1,
                ),
                PoolRules.RemainingBalls(solids = 4, stripes = 3, eight = 1),
                isBreak = false,
            )
        assertEquals(PLAYER, update.frameWinner)
        assertEquals(PoolMessage.NINE_WIN, update.message)
    }

    @Test
    fun nineBall_illegalNine_lose() {
        val update =
            PoolRules.resolve(
                nineState(),
                PoolShotOutcome(
                    firstContact = SOLID,
                    railAfterContact = true,
                    potted = listOf(STRIPE),
                    cuePotted = false,
                    shooterGroupClearedBefore = false,
                    firstContactNumber = 2,
                    pottedNumbers = listOf(9),
                    lowestBefore = 1,
                ),
                PoolRules.RemainingBalls(solids = 4, stripes = 3, eight = 0),
                isBreak = false,
            )
        assertEquals(AI, update.frameWinner)
        assertEquals(1, update.state.aiFrames)
        assertEquals(PoolMessage.NINE_LOSE, update.message)
    }

    @Test
    fun nineBall_cuePotted_isFoulBallInHand() {
        val update =
            PoolRules.resolve(
                nineState(),
                PoolShotOutcome(
                    firstContact = SOLID,
                    railAfterContact = true,
                    potted = emptyList(),
                    cuePotted = true,
                    shooterGroupClearedBefore = false,
                    firstContactNumber = 1,
                    lowestBefore = 1,
                ),
                PoolRules.RemainingBalls(solids = 4, stripes = 3, eight = 1),
                isBreak = false,
            )
        assertTrue(update.foul)
        assertTrue(update.ballInHand)
        assertEquals(AI, update.state.turn)
        assertEquals(FOUL_CUE, update.message)
    }

    @Test
    fun lowestBall_returnsSmallestNumberOnTable() {
        val table = PoolTable.standard()
        val balls = PoolRack.rack(table, PoolRack.standardOrder())
        assertEquals(1, PoolRules.lowestBall(balls)?.number)
        balls.first { it.number == 1 }.onTable = false
        assertEquals(2, PoolRules.lowestBall(balls)?.number)
    }

    @Test
    fun targetBallFor_nineBall_isLowest() {
        val table = PoolTable.standard()
        val balls = PoolRack.rack(table, PoolRack.standardOrder())
        val state = PoolMatchState(mode = PoolMode.NINE_BALL)
        assertEquals(1, PoolRules.targetBallFor(state, balls)?.number)
    }

    // ---------------------------------------------------------------------
    // 练习
    // ---------------------------------------------------------------------

    @Test
    fun practice_noTurnChange_noFoul() {
        val update =
            PoolRules.resolve(
                PoolMatchState(turn = PLAYER, mode = PoolMode.PRACTICE),
                outcome(first = SOLID, potted = listOf(SOLID)),
                PoolRules.RemainingBalls(solids = 4, stripes = 3, eight = 1),
                isBreak = false,
            )
        assertFalse(update.foul)
        assertEquals(PLAYER, update.state.turn)
        assertEquals(CONTINUE, update.message)
    }

    @Test
    fun practice_cuePotted_notFoul() {
        val update =
            PoolRules.resolve(
                PoolMatchState(turn = PLAYER, mode = PoolMode.PRACTICE),
                outcome(first = null, rail = false, cuePotted = true),
                PoolRules.RemainingBalls(solids = 4, stripes = 3, eight = 1),
                isBreak = false,
            )
        assertFalse(update.foul)
        assertFalse(update.ballInHand)
        assertEquals(PLAYER, update.state.turn)
    }

    @Test
    fun practice_clearEndsFrame() {
        val update =
            PoolRules.resolve(
                PoolMatchState(turn = PLAYER, mode = PoolMode.PRACTICE),
                outcome(first = EIGHT, potted = listOf(EIGHT)),
                PoolRules.RemainingBalls(solids = 0, stripes = 0, eight = 0),
                isBreak = false,
            )
        assertEquals(PLAYER, update.frameWinner)
        assertEquals(1, update.state.playerFrames)
        assertEquals(PoolMessage.PRACTICE_CLEAR, update.message)
    }

    @Test
    fun challenge_usesPracticeRulesAndClearEndsFrame() {
        val state = PoolMatchState(turn = PLAYER, mode = PoolMode.CHALLENGE)
        val cuePotted =
            PoolRules.resolve(
                state,
                outcome(first = null, rail = false, cuePotted = true),
                PoolRules.RemainingBalls(solids = 1, stripes = 1, eight = 0),
                isBreak = false,
            )
        assertFalse(cuePotted.foul)
        assertEquals(PLAYER, cuePotted.state.turn)

        val cleared =
            PoolRules.resolve(
                state,
                outcome(first = SOLID, potted = listOf(SOLID)),
                PoolRules.RemainingBalls(solids = 0, stripes = 0, eight = 0),
                isBreak = false,
            )
        assertEquals(PLAYER, cleared.frameWinner)
        assertEquals(PoolMessage.PRACTICE_CLEAR, cleared.message)
    }

    // ---------------------------------------------------------------------
    // 首碰合法性（瞄准辅助提示）
    // ---------------------------------------------------------------------

    private fun contactBall(
        id: Int,
        kind: PoolBallKind,
    ) = PoolBall(id, kind, 0.6f, 1.2f)

    @Test
    fun isLegalFirstContact_openTable_eightIsIllegal_anyOtherBallLegal() {
        val state = PoolMatchState(openTable = true, turn = PLAYER)
        val solid = contactBall(1, SOLID)
        val stripe = contactBall(9, STRIPE)
        val eight = contactBall(8, EIGHT)
        val balls = listOf(solid, stripe, eight)
        assertTrue(PoolRules.isLegalFirstContact(state, balls, solid))
        assertTrue(PoolRules.isLegalFirstContact(state, balls, stripe))
        assertFalse(PoolRules.isLegalFirstContact(state, balls, eight))
    }

    @Test
    fun isLegalFirstContact_ownGroup_onlyOwnGroupLegal() {
        val state = PoolMatchState(openTable = false, playerGroup = SOLIDS, aiGroup = STRIPES, turn = PLAYER)
        val solid = contactBall(1, SOLID)
        val stripe = contactBall(9, STRIPE)
        val balls = listOf(solid, stripe)
        assertTrue(PoolRules.isLegalFirstContact(state, balls, solid))
        assertFalse(PoolRules.isLegalFirstContact(state, balls, stripe))
    }

    @Test
    fun isLegalFirstContact_groupCleared_onlyEightLegal() {
        val state = PoolMatchState(openTable = false, playerGroup = SOLIDS, aiGroup = STRIPES, turn = PLAYER)
        val eight = contactBall(8, EIGHT)
        val stripe = contactBall(9, STRIPE)
        // 台面无全色 → 清台，必须首碰 8
        val balls = listOf(eight, stripe)
        assertTrue(PoolRules.isLegalFirstContact(state, balls, eight))
        assertFalse(PoolRules.isLegalFirstContact(state, balls, stripe))
    }

    @Test
    fun isLegalFirstContact_nineBall_onlyLowestNumberLegal() {
        val state = PoolMatchState(turn = PLAYER, mode = PoolMode.NINE_BALL)
        val one = contactBall(1, SOLID)
        val two = contactBall(2, SOLID)
        val nine = contactBall(9, STRIPE)
        val balls = listOf(one, two, nine)
        assertTrue(PoolRules.isLegalFirstContact(state, balls, one))
        assertFalse(PoolRules.isLegalFirstContact(state, balls, two))
        assertFalse(PoolRules.isLegalFirstContact(state, balls, nine))
    }

    @Test
    fun isLegalFirstContact_practiceAndChallenge_neverFoul() {
        val eight = contactBall(8, EIGHT)
        val stripe = contactBall(9, STRIPE)
        val balls = listOf(eight, stripe)
        assertTrue(PoolRules.isLegalFirstContact(PoolMatchState(turn = PLAYER, mode = PoolMode.PRACTICE), balls, eight))
        assertTrue(PoolRules.isLegalFirstContact(PoolMatchState(turn = PLAYER, mode = PoolMode.CHALLENGE), balls, stripe))
    }

    @Test
    fun isLegalFirstContact_cueOrNull_isIllegal() {
        val state = PoolMatchState(openTable = true, turn = PLAYER)
        val cue = PoolBall(0, PoolBallKind.CUE, 0.6f, 0.8f)
        val solid = contactBall(1, SOLID)
        val balls = listOf(cue, solid)
        assertFalse(PoolRules.isLegalFirstContact(state, balls, null))
        assertFalse(PoolRules.isLegalFirstContact(state, balls, cue))
    }
}

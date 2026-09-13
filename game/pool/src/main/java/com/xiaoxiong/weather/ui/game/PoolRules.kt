package com.xiaoxiong.weather.ui.game

/*
 * 八球规则引擎（纯函数，便于单测）。
 *
 * 采用简化但完整的「美式八球」：
 *  - 开球后球台处于「开放」状态，首个合法进球确定花色（全色 1–7 / 花色 9–15）；
 *  - 合法击球：白球先碰到本方球（开放时先碰非 8），且接触后有球碰库或有球落袋；
 *  - 犯规（白球落袋 / 先碰错球 / 空杆 / 接触后无库无球落袋）→ 对手自由球；
 *  - 清完本方球后方可打 8：合法打进 8 获胜；提前打进或犯规打进 8 判负。
 */

/** 球组归属。 */
enum class PoolGroup { SOLIDS, STRIPES }

/** 对局双方。 */
enum class PoolSide { PLAYER, AI }

/** 玩法模式。 */
enum class PoolMode { EIGHT_BALL, NINE_BALL, PRACTICE, CHALLENGE }

/** 一杆结束后的客观事实（由 Game 依据物理结果收集）。 */
data class PoolShotOutcome(
    /** 白球第一个碰到的球类型；空杆为 null。 */
    val firstContact: PoolBallKind?,
    /** 接触之后是否有球（任意）碰库。 */
    val railAfterContact: Boolean,
    /** 本杆落袋的目标球类型（不含白球），按落袋先后。 */
    val potted: List<PoolBallKind>,
    /** 白球是否落袋。 */
    val cuePotted: Boolean,
    /** 击球方本杆前本方花色是否已清空（可以打 8）。 */
    val shooterGroupClearedBefore: Boolean,
    /** 白球第一个碰到的球号（不含白球）；空杆为 null。九球判定最低号首碰用。 */
    val firstContactNumber: Int? = null,
    /** 本杆落袋的球号（不含白球），按落袋先后。 */
    val pottedNumbers: List<Int> = emptyList(),
    /** 击球前台面最低号球（九球目标），无则 null。 */
    val lowestBefore: Int? = null,
)

/** 需要向玩家展示的提示类型。 */
enum class PoolMessage {
    NONE,
    OPEN_TABLE,
    GROUP_ASSIGNED,
    CONTINUE,
    TURN_PASS,
    FOUL_CUE,
    FOUL_WRONG_BALL,
    FOUL_NO_RAIL,
    FOUL_MISS,
    EIGHT_WIN,
    EIGHT_LOSE,
    BREAK_RERACK,
    NINE_WIN,
    NINE_LOSE,
    PRACTICE_CLEAR,
}

/** 规则结算结果。 */
data class PoolMatchUpdate(
    val state: PoolMatchState,
    val foul: Boolean,
    val ballInHand: Boolean,
    val frameWinner: PoolSide?,
    val message: PoolMessage,
    /** 是否重摆（开球误进 8）。 */
    val rerack: Boolean = false,
)

/** 对局状态（跨杆）。 */
data class PoolMatchState(
    val openTable: Boolean = true,
    val playerGroup: PoolGroup? = null,
    val aiGroup: PoolGroup? = null,
    val turn: PoolSide = PoolSide.PLAYER,
    val playerFrames: Int = 0,
    val aiFrames: Int = 0,
    val mode: PoolMode = PoolMode.EIGHT_BALL,
) {
    fun groupOf(side: PoolSide): PoolGroup? = if (side == PoolSide.PLAYER) playerGroup else aiGroup

    fun opponent(): PoolSide = if (turn == PoolSide.PLAYER) PoolSide.AI else PoolSide.PLAYER
}

object PoolRules {
    fun groupOf(kind: PoolBallKind?): PoolGroup? =
        when (kind) {
            PoolBallKind.SOLID -> PoolGroup.SOLIDS
            PoolBallKind.STRIPE -> PoolGroup.STRIPES
            else -> null
        }

    /**
     * 结算一杆，按 [PoolMatchState.mode] 分派到对应规则。
     *
     * @param remainingAfter 本杆结算后，台面上各类型的剩余数量（用于判断某方是否已清台）。
     */
    fun resolve(
        state: PoolMatchState,
        outcome: PoolShotOutcome,
        remainingAfter: RemainingBalls,
        isBreak: Boolean,
    ): PoolMatchUpdate =
        when (state.mode) {
            PoolMode.EIGHT_BALL -> resolveEightBall(state, outcome, remainingAfter, isBreak)
            PoolMode.NINE_BALL -> resolveNineBall(state, outcome, remainingAfter, isBreak)
            PoolMode.PRACTICE -> resolvePractice(state, outcome, remainingAfter)
            // 关卡与练习一致：不换人、无犯规、白球落袋自动回台，清台即结束
            PoolMode.CHALLENGE -> resolvePractice(state, outcome, remainingAfter)
        }

    // ---------------------------------------------------------------------
    // 八球
    // ---------------------------------------------------------------------

    private fun resolveEightBall(
        state: PoolMatchState,
        outcome: PoolShotOutcome,
        remainingAfter: RemainingBalls,
        isBreak: Boolean,
    ): PoolMatchUpdate {
        val shooter = state.turn
        val opponent = state.opponent()
        val shooterGroup = state.groupOf(shooter)

        // 开球误进 8：重摆，不计犯规
        if (isBreak && PoolBallKind.EIGHT in outcome.potted && !outcome.cuePotted) {
            return PoolMatchUpdate(
                state = state,
                foul = false,
                ballInHand = false,
                frameWinner = null,
                message = PoolMessage.BREAK_RERACK,
                rerack = true,
            )
        }

        // 8 号落袋 → 直接判定本局胜负
        if (PoolBallKind.EIGHT in outcome.potted) {
            val legalEight =
                outcome.firstContact == PoolBallKind.EIGHT &&
                    outcome.shooterGroupClearedBefore &&
                    !outcome.cuePotted
            return if (legalEight) {
                PoolMatchUpdate(
                    state =
                        state.copy(
                            playerFrames =
                                state.playerFrames +
                                    if (shooter ==
                                        PoolSide.PLAYER
                                    ) {
                                        1
                                    } else {
                                        0
                                    },
                            aiFrames = state.aiFrames + if (shooter == PoolSide.AI) 1 else 0,
                        ),
                    foul = false,
                    ballInHand = false,
                    frameWinner = shooter,
                    message = PoolMessage.EIGHT_WIN,
                )
            } else {
                PoolMatchUpdate(
                    state =
                        state.copy(
                            playerFrames =
                                state.playerFrames +
                                    if (opponent ==
                                        PoolSide.PLAYER
                                    ) {
                                        1
                                    } else {
                                        0
                                    },
                            aiFrames = state.aiFrames + if (opponent == PoolSide.AI) 1 else 0,
                        ),
                    foul = false,
                    ballInHand = false,
                    frameWinner = opponent,
                    message = PoolMessage.EIGHT_LOSE,
                )
            }
        }

        // 合法首碰判定
        val legalFirst =
            when {
                outcome.firstContact == null -> false
                outcome.firstContact == PoolBallKind.CUE -> false
                state.openTable -> outcome.firstContact != PoolBallKind.EIGHT
                shooterGroup == null -> true
                outcome.shooterGroupClearedBefore -> outcome.firstContact == PoolBallKind.EIGHT
                else -> groupOf(outcome.firstContact) == shooterGroup
            }
        val railOk = outcome.railAfterContact || outcome.potted.isNotEmpty()
        val cuePotted = outcome.cuePotted
        val foul = cuePotted || !legalFirst || !railOk

        if (foul) {
            val message =
                when {
                    cuePotted -> PoolMessage.FOUL_CUE
                    // 首碰不合法（先碰错花色/开放台先碰 8）统一提示「先碰错球」
                    !legalFirst && outcome.firstContact != null -> PoolMessage.FOUL_WRONG_BALL
                    outcome.firstContact == null -> PoolMessage.FOUL_MISS
                    else -> PoolMessage.FOUL_NO_RAIL
                }
            return PoolMatchUpdate(
                state = state.copy(turn = opponent),
                foul = true,
                ballInHand = true,
                frameWinner = null,
                message = message,
            )
        }

        // 合法进球 → 可能确定花色
        var newState = state
        var message = PoolMessage.TURN_PASS
        var continueTurn = false

        val ownPotted =
            if (state.openTable) {
                outcome.potted.filter { it != PoolBallKind.EIGHT }
            } else {
                outcome.potted.filter { shooterGroup != null && groupOf(it) == shooterGroup }
            }

        if (state.openTable && ownPotted.isNotEmpty()) {
            // 以第一颗落袋的非 8 球确定花色
            val assigned = groupOf(ownPotted.first()) ?: PoolGroup.SOLIDS
            val playerGroup = if (shooter == PoolSide.PLAYER) assigned else other(assigned)
            val aiGroup = if (shooter == PoolSide.AI) assigned else other(assigned)
            newState = state.copy(openTable = false, playerGroup = playerGroup, aiGroup = aiGroup)
            continueTurn = true
            message = PoolMessage.GROUP_ASSIGNED
        } else if (ownPotted.isNotEmpty()) {
            continueTurn = true
            message = PoolMessage.CONTINUE
        } else {
            message = PoolMessage.TURN_PASS
        }

        if (!continueTurn) {
            newState = newState.copy(turn = opponent)
        }
        return PoolMatchUpdate(
            state = newState,
            foul = false,
            ballInHand = false,
            frameWinner = null,
            message = message,
        )
    }

    // ---------------------------------------------------------------------
    // 九球
    // ---------------------------------------------------------------------

    /**
     * 九球规则：
     *  - 必须先碰台面剩余编号最小的球（首碰合法）；
     *  - 接触后需有球碰库或有球落袋，否则犯规；
     *  - 合法击球后任意球落袋都继续回合（含组合球）；合法打进 9 号直接赢；
     *  - 9 号在首碰不合法/白球落袋时落袋 → 判负（简化处理）；
     *  - 白球落袋/空杆 → 犯规，对手自由球。
     */
    private fun resolveNineBall(
        state: PoolMatchState,
        outcome: PoolShotOutcome,
        remainingAfter: RemainingBalls,
        isBreak: Boolean,
    ): PoolMatchUpdate {
        val shooter = state.turn
        val opponent = state.opponent()
        val lowest = outcome.lowestBefore
        val legalFirst =
            outcome.firstContact != null &&
                outcome.firstContact != PoolBallKind.CUE &&
                lowest != null &&
                outcome.firstContactNumber == lowest
        val railOk = outcome.railAfterContact || outcome.potted.isNotEmpty()
        val cuePotted = outcome.cuePotted

        // 9 号落袋：首碰合法且未犯规 → 赢；否则判负
        if (NINE_BALL_NUMBER in outcome.pottedNumbers) {
            return if (legalFirst && !cuePotted) {
                PoolMatchUpdate(
                    state = bumpFrames(state, shooter),
                    foul = false,
                    ballInHand = false,
                    frameWinner = shooter,
                    message = PoolMessage.NINE_WIN,
                )
            } else {
                PoolMatchUpdate(
                    state = bumpFrames(state, opponent),
                    foul = false,
                    ballInHand = false,
                    frameWinner = opponent,
                    message = PoolMessage.NINE_LOSE,
                )
            }
        }

        val foul = cuePotted || !legalFirst || !railOk
        if (foul) {
            val message =
                when {
                    cuePotted -> PoolMessage.FOUL_CUE
                    outcome.firstContact == null -> PoolMessage.FOUL_MISS
                    // 碰对了最低号但接触后无球碰库/落袋：属于「无库无袋」，不是碰错球
                    legalFirst && !railOk -> PoolMessage.FOUL_NO_RAIL
                    else -> PoolMessage.FOUL_WRONG_BALL
                }
            return PoolMatchUpdate(
                state = state.copy(turn = opponent),
                foul = true,
                ballInHand = true,
                frameWinner = null,
                message = message,
            )
        }

        return if (outcome.potted.isNotEmpty()) {
            PoolMatchUpdate(
                state = state,
                foul = false,
                ballInHand = false,
                frameWinner = null,
                message = PoolMessage.CONTINUE,
            )
        } else {
            PoolMatchUpdate(
                state = state.copy(turn = opponent),
                foul = false,
                ballInHand = false,
                frameWinner = null,
                message = PoolMessage.TURN_PASS,
            )
        }
    }

    // ---------------------------------------------------------------------
    // 练习
    // ---------------------------------------------------------------------

    /** 练习模式：不换人、无犯规；清完台面（含 8）本局结束。 */
    private fun resolvePractice(
        state: PoolMatchState,
        outcome: PoolShotOutcome,
        remainingAfter: RemainingBalls,
    ): PoolMatchUpdate {
        if (remainingAfter.total == 0) {
            return PoolMatchUpdate(
                state = state.copy(playerFrames = state.playerFrames + 1),
                foul = false,
                ballInHand = false,
                frameWinner = PoolSide.PLAYER,
                message = PoolMessage.PRACTICE_CLEAR,
            )
        }
        return PoolMatchUpdate(
            state = state,
            foul = false,
            ballInHand = false,
            frameWinner = null,
            message = if (outcome.potted.isNotEmpty()) PoolMessage.CONTINUE else PoolMessage.TURN_PASS,
        )
    }

    private fun bumpFrames(
        state: PoolMatchState,
        side: PoolSide,
    ): PoolMatchState =
        if (side == PoolSide.PLAYER) {
            state.copy(playerFrames = state.playerFrames + 1)
        } else {
            state.copy(aiFrames = state.aiFrames + 1)
        }

    fun other(group: PoolGroup): PoolGroup = if (group == PoolGroup.SOLIDS) PoolGroup.STRIPES else PoolGroup.SOLIDS

    /** 台面剩余球统计。 */
    data class RemainingBalls(
        val solids: Int,
        val stripes: Int,
        val eight: Int,
    ) {
        fun remainingFor(group: PoolGroup): Int = if (group == PoolGroup.SOLIDS) solids else stripes

        /** 台面剩余球总数（含 8，不含白球）。 */
        val total: Int get() = solids + stripes + eight
    }

    /** 根据台面球计算剩余数量。 */
    fun remaining(balls: List<PoolBall>): RemainingBalls {
        var solids = 0
        var stripes = 0
        var eight = 0
        for (b in balls) {
            if (!b.onTable) continue
            when (b.kind) {
                PoolBallKind.SOLID -> solids++
                PoolBallKind.STRIPE -> stripes++
                PoolBallKind.EIGHT -> eight++
                PoolBallKind.CUE -> Unit
            }
        }
        return RemainingBalls(solids, stripes, eight)
    }

    /** 当前击球方应该先碰的球类型（用于 AI 与瞄准提示）。 */
    fun targetKindFor(
        state: PoolMatchState,
        remaining: RemainingBalls,
    ): PoolBallKind? {
        if (state.openTable) return null // 任意非 8
        val group = state.groupOf(state.turn) ?: return null
        return if (remaining.remainingFor(group) ==
            0
        ) {
            PoolBallKind.EIGHT
        } else if (group == PoolGroup.SOLIDS) {
            PoolBallKind.SOLID
        } else {
            PoolBallKind.STRIPE
        }
    }

    /**
     * 台面剩余编号最小的球（不含白球）。九球目标。
     * 同号球不会重复，直接取 [PoolBall.number] 最小者。
     */
    fun lowestBall(balls: List<PoolBall>): PoolBall? =
        balls
            .filter { it.onTable && it.kind != PoolBallKind.CUE }
            .minByOrNull { it.number }

    /**
     * 预测首碰球是否为当前击球方的合法目标（瞄准辅助提示用，只读纯函数）。
     *  - 八球：开放台任意非 8；确定花色后为本方花色；清台后必须 8；
     *  - 九球：必须是最低号球；
     *  - 练习/挑战：不判非法（永远合法，白球除外）。
     */
    fun isLegalFirstContact(
        state: PoolMatchState,
        balls: List<PoolBall>,
        contactBall: PoolBall?,
    ): Boolean {
        if (contactBall == null || contactBall.kind == PoolBallKind.CUE) return false
        return when (state.mode) {
            PoolMode.PRACTICE, PoolMode.CHALLENGE -> true
            PoolMode.NINE_BALL -> contactBall.number == lowestBall(balls)?.number
            PoolMode.EIGHT_BALL -> {
                if (state.openTable) {
                    contactBall.kind != PoolBallKind.EIGHT
                } else {
                    val targetKind = targetKindFor(state, remaining(balls))
                    targetKind == null || contactBall.kind == targetKind
                }
            }
        }
    }

    /**
     * 当前击球方应优先击打的「目标球」。
     *  - 九球：最低号球；
     *  - 练习：无固定目标（返回 null）；
     *  - 八球：本方花色中编号最小者，清台后为 8 号；开放球台为 null。
     */
    fun targetBallFor(
        state: PoolMatchState,
        balls: List<PoolBall>,
    ): PoolBall? =
        when (state.mode) {
            PoolMode.NINE_BALL -> lowestBall(balls)
            PoolMode.PRACTICE, PoolMode.CHALLENGE -> null
            PoolMode.EIGHT_BALL -> {
                val kind = targetKindFor(state, remaining(balls)) ?: return null
                balls
                    .filter { it.onTable && it.kind == kind }
                    .minByOrNull { it.number }
            }
        }

    /** 九球 9 号球号。 */
    const val NINE_BALL_NUMBER = 9
}

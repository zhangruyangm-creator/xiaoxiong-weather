package com.xiaoxiong.weather.ui.game

import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin
import kotlin.random.Random

/**
 * 小熊台球 AI：先解析法枚举「目标球 × 袋口」的瞄点，计算切角 / 路径净空 / 距离评分，
 * 再按难度对最有希望的候选杆用真实物理内核做快速推演（克隆整桌球）验证能否进球、
 * 是否白球落袋；高难度命中率高、可识别阻挡、走位与安全球，低难度带明显瞄准误差。
 *
 * AI 的档位用 [Difficulty] 表达（5 档），并支持：
 *  - 走位意识：高难度在能进球候选中按「白球停点下一杆可打性」加权选择；
 *  - 安全球：无进攻机会时用物理推演挑一杆让对手也不好打的方案；
 *  - 解斯诺克：直接路径全被挡时尝试一库翻袋/踢球（镜像点几何）并推演验证。
 * 所有随机数都来自传入的 [rng]，保证同一 seed 复现同一杆。
 */
object PoolAi {
    /** AI 推演的固定步长：比游戏 600Hz 粗，够用且显著降低主线程开销。 */
    private const val SIM_DT = 1f / 180f

    /** 单次物理推演的最长时长（秒）。 */
    private const val SIM_MAX_SECONDS = 2f

    /** 单次推演的最大步数（[SIM_MAX_SECONDS] / [SIM_DT]）。 */
    private const val SIM_MAX_STEPS = (SIM_MAX_SECONDS / SIM_DT).toInt()

    /** 解斯诺克时最多推演的一库解球候选数。 */
    private const val KICK_MAX_SHOTS = 18

    /** 安全球最多评估的目标球数（每球 3 个偏移角）。 */
    private const val SAFETY_MAX_TARGETS = 5

    /** 安全球单次规划最多推演的候选数（给后续补验干净进球留预算）。 */
    private const val SAFETY_MAX_SIMS = 6

    /** 补验失败候选时留给安全球兜底的推演步数（约一杆轻推的量）。 */
    private const val SAFETY_FALLBACK_RESERVE = 240

    /** 安全球固定使用的力度（推演与实际出杆一致）。 */
    private const val SAFETY_POWER = 0.32f

    /**
     * 验证失败后的降力度尝试系数：原力度会跟袋或打薄时，更轻的推杆
     * 往往仍能把目标球送进袋，同时白球不易跟袋。
     */
    private val SOFTER_POWER_FACTORS = floatArrayOf(0.8f, 0.62f, 0.45f)

    enum class Difficulty(
        val aimErrorDeg: Float,
        val powerError: Float,
        val candidates: Int,
        val verifyShots: Int,
        /** 走位权重：0 表示纯进攻，越大越在意白球停点。 */
        val positionPlay: Float,
        /** 做两杆走位推演的候选数 N（0 表示不做，仅 HARD/MASTER 启用）。 */
        val positionCandidates: Int,
    ) {
        NOVICE(
            aimErrorDeg = 3.2f,
            powerError = 0.20f,
            candidates = 4,
            verifyShots = 0,
            positionPlay = 0f,
            positionCandidates = 0,
        ),
        EASY(
            aimErrorDeg = 2.2f,
            powerError = 0.14f,
            candidates = 6,
            verifyShots = 0,
            positionPlay = 0f,
            positionCandidates = 0,
        ),
        NORMAL(
            aimErrorDeg = 1.35f,
            powerError = 0.08f,
            candidates = 10,
            verifyShots = 2,
            positionPlay = 0f,
            positionCandidates = 0,
        ),
        HARD(
            aimErrorDeg = 0.5f,
            powerError = 0.045f,
            candidates = 12,
            verifyShots = 4,
            positionPlay = 0.40f,
            positionCandidates = 3,
        ),
        MASTER(
            aimErrorDeg = 0.15f,
            powerError = 0.02f,
            candidates = 16,
            verifyShots = 6,
            positionPlay = 0.70f,
            positionCandidates = 4,
        ),
    }

    /** 一杆方案：角度（弧度）、力度 0..1、杆法（spinX/spinY：-1..1）、目标球与袋口。 */
    data class ShotPlan(
        val angle: Float,
        val power: Float,
        val spinX: Float,
        val spinY: Float,
        val targetBallId: Int,
        val pocketIndex: Int,
        val safety: Boolean = false,
    )

    private data class Candidate(
        val target: PoolBall,
        val pocketIndex: Int,
        val angle: Float,
        val cutAngle: Float,
        val cueDistance: Float,
        val ballToPocket: Float,
        val score: Float,
        val spinX: Float,
        val spinY: Float,
    )

    /** 一次物理推演的结果（用克隆球桌，不影响真实棋局）。 */
    internal data class SimResult(
        val targetPotted: Boolean,
        val cueScratched: Boolean,
        val cueX: Float,
        val cueY: Float,
        val firstContactId: Int?,
        val balls: List<PoolBall>,
        /** 推演未跑完（步数上限或预算截断），结果不可当作「已完整验证」。 */
        val truncated: Boolean = false,
        /** 因阶段配额/总预算耗尽而截断：调用方应停止该阶段而不是把结果当失败。 */
        val budgetExhausted: Boolean = false,
        /** 首碰之后有球碰库或有非白球落袋：满足规则里的「接触后碰库/落袋」要求。 */
        val railAfterContact: Boolean = false,
    )

    /** AI 推演阶段：各自领取独立配额，避免一个阶段吃光整次规划的步数。 */
    internal enum class SimStage { VERIFY, KICK, SAFETY }

    /**
     * 单次 [plan] 共用的确定性物理推演步数预算：每推进一步消耗 1。
     *
     * 总预算按阶段拆成 verify / kick / safety 三段配额（默认 70% / 20% / 10%），
     * 后面的阶段只能使用前面阶段没用完的份额，因此单个阶段不会吃光整次规划；
     * 阶段配额耗尽时 [simulate] 返回 `truncated`，[plan] 据此停止该阶段而不是把
     * 「未验证」当成「没进球」，保证主线程最坏耗时可控。
     */
    internal class SimBudget(
        val maxSteps: Int,
        verifyQuota: Int = maxSteps * 7 / 10,
        kickQuota: Int = maxSteps * 2 / 10,
    ) {
        private val verifyLimit = verifyQuota.coerceIn(0, maxSteps)
        private val kickLimit = (verifyLimit + kickQuota.coerceIn(0, maxSteps - verifyLimit)).coerceIn(0, maxSteps)
        private val safetyLimit = maxSteps

        var used: Int = 0
            private set
        var verifyUsed: Int = 0
            private set
        var kickUsed: Int = 0
            private set
        var safetyUsed: Int = 0
            private set

        /** 总预算是否耗尽。 */
        val exhausted: Boolean get() = used >= maxSteps

        /** 指定阶段是否还有可用步数（阶段配额或总预算任一耗尽）。 */
        fun exhausted(stage: SimStage): Boolean = used >= limit(stage)

        /** 申请 1 步推演额度；阶段配额或总预算耗尽返回 false。 */
        fun tryConsume(stage: SimStage): Boolean {
            if (used >= limit(stage)) return false
            used++
            when (stage) {
                SimStage.VERIFY -> verifyUsed++
                SimStage.KICK -> kickUsed++
                SimStage.SAFETY -> safetyUsed++
            }
            return true
        }

        private fun limit(stage: SimStage): Int =
            when (stage) {
                SimStage.VERIFY -> verifyLimit
                SimStage.KICK -> kickLimit
                SimStage.SAFETY -> safetyLimit
            }
    }

    /**
     * 为当前击球方规划一杆。
     *
     * @param rng 确定性随机源（同一 seed 复现同一杆）。
     */
    fun plan(
        balls: List<PoolBall>,
        table: PoolTable,
        state: PoolMatchState,
        difficulty: Difficulty,
        rng: Random,
    ): ShotPlan? = plan(balls, table, state, difficulty, rng, SimBudget(simulationBudget(difficulty)))

    /** 带显式 [budget] 的规划入口（测试可观测累计推演步数）。 */
    internal fun plan(
        balls: List<PoolBall>,
        table: PoolTable,
        state: PoolMatchState,
        difficulty: Difficulty,
        rng: Random,
        budget: SimBudget,
    ): ShotPlan? {
        val cue = balls.firstOrNull { it.kind == PoolBallKind.CUE && it.onTable } ?: return null
        // 先抽定瞄准/力度误差：后续推演按「实际要打的这一杆」（含误差）验证，
        // 保证「推演干净」等于「实打干净」，避免验证后再加误差把干净杆推成落袋。
        val aimErrRad = Math.toRadians(difficulty.aimErrorDeg.toDouble()).toFloat() * (rng.nextFloat() * 2f - 1f)
        val powerErr = difficulty.powerError * (rng.nextFloat() * 2f - 1f)

        fun execAngle(c: Candidate): Float = c.angle + aimErrRad

        fun execPower(p: Float): Float = (p + powerErr).coerceIn(0.12f, 1f)
        val targets = eligibleTargets(balls, state)
        if (targets.isEmpty()) {
            return safetyPlan(cue, balls, table, state, difficulty, rng, budget)?.plan
        }

        val candidates = ArrayList<Candidate>()
        for (ball in targets) {
            for (pi in table.pockets.indices) {
                evaluateCandidate(balls, table, cue, ball, table.pockets[pi], pi)?.let { candidates += it }
            }
        }

        if (candidates.isEmpty()) {
            // 直接进攻路径全被挡：尝试一库解球（翻袋/踢球）并推演验证，失败则打安全球
            kickPlan(balls, table, state, cue, rng, budget)?.let { return it }
            return safetyPlan(cue, balls, table, state, difficulty, rng, budget)?.plan
        }

        candidates.sortByDescending { it.score }
        val shortlist = candidates.take(difficulty.candidates)

        var verified: Candidate? = null
        var planned: Candidate? = null
        var plannedValue = -Float.MAX_VALUE
        var positionChecked = 0
        var chosenPower = 0f
        val simResults = LinkedHashMap<Candidate, SimResult>()
        var verificationAttempted = false
        if (difficulty.verifyShots > 0) {
            for (c in shortlist.take(difficulty.verifyShots)) {
                if (budget.exhausted(SimStage.VERIFY)) break
                val simPower = estimatePower(c.cueDistance, c.ballToPocket, c.cutAngle)
                val res = simulate(balls, table, execAngle(c), execPower(simPower), c.target.id, budget, SimStage.VERIFY, c.spinX, c.spinY)
                // 预算耗尽后的结果是「未验证」：停止验证，不能当成「没进球」继续空跑
                if (res.budgetExhausted) break
                verificationAttempted = true
                simResults[c] = res
                if (!res.targetPotted || res.cueScratched) continue
                if (verified == null) {
                    verified = c
                    chosenPower = simPower
                }
                // 两杆走位（仅 HARD/MASTER）：把白球停点 + 剩余台面作为下一杆局面，
                // 复用 evaluateCandidate 评估「下一杆最佳可打性」，当前杆总分 = 进球分 + 权重 × 走位分。
                // 只对前 N 个已验证候选推演下一杆局面，控制成本；预算耗尽则不再走位评估。
                if (difficulty.positionCandidates > 0 &&
                    positionChecked < difficulty.positionCandidates &&
                    !budget.exhausted
                ) {
                    positionChecked++
                    val pos = nextShotQuality(res.balls, table, state)
                    val value = c.score + difficulty.positionPlay * pos
                    if (value > plannedValue) {
                        plannedValue = value
                        planned = c
                    }
                }
            }
        }
        val chosen =
            planned ?: verified ?: run {
                if (!verificationAttempted) {
                    // 没跑过推演（如预算为 0）：保留原有「几何最优」行为
                    shortlist.first()
                } else {
                    // 验证过但没有任何「进球且不落袋」的候选：不再退回未验证的几何最优。
                    // 先用剩余额度补验短名单剩余候选（原力度优先，其次降力度），
                    // 补验期间给安全球兜底留出保留额度；仍无干净进球时，
                    // 先尝试经推演过滤的安全球，再从候选缓存里挑「不落袋 / 风险最低」的一杆。
                    for (c in shortlist) {
                        if (verified != null || budget.reserveReached()) break
                        if (simResults.containsKey(c)) continue
                        val basePower = estimatePower(c.cueDistance, c.ballToPocket, c.cutAngle)
                        val res =
                            simulate(
                                balls,
                                table,
                                execAngle(c),
                                execPower(basePower),
                                c.target.id,
                                budget,
                                SimStage.SAFETY,
                                c.spinX,
                                c.spinY,
                            )
                        if (res.budgetExhausted) break
                        simResults[c] = res
                        if (res.targetPotted && !res.cueScratched) {
                            verified = c
                            chosenPower = basePower
                        }
                    }
                    if (verified == null) {
                        for (c in shortlist) {
                            if (verified != null || budget.reserveReached()) break
                            val basePower = estimatePower(c.cueDistance, c.ballToPocket, c.cutAngle)
                            for (factor in SOFTER_POWER_FACTORS) {
                                if (budget.reserveReached()) break
                                val softPower = (basePower * factor).coerceAtLeast(0.15f)
                                val res =
                                    simulate(
                                        balls,
                                        table,
                                        execAngle(c),
                                        execPower(softPower),
                                        c.target.id,
                                        budget,
                                        SimStage.SAFETY,
                                        c.spinX,
                                        c.spinY,
                                    )
                                if (res.budgetExhausted) break
                                if (res.targetPotted && !res.cueScratched) {
                                    verified = c
                                    chosenPower = softPower
                                    break
                                }
                            }
                        }
                    }
                    val clean = verified
                    if (clean != null) {
                        clean
                    } else {
                        val safety = safetyPlan(cue, balls, table, state, difficulty, rng, budget)
                        val safest = safestCandidate(candidates, simResults, balls, table, budget, aimErrRad, powerErr)
                        val safestSim = safest?.let { simResults[it] }
                        val safestSafe =
                            safest != null &&
                                (
                                    safestSim == null ||
                                        (!safestSim.cueScratched && (safestSim.targetPotted || safestSim.railAfterContact))
                                )
                        when {
                            safestSafe -> safest
                            // 候选已确认白球落袋时，宁可用未经确认的低力度安全球，也不主动落袋
                            safety != null -> return safety.plan
                            safest != null -> safest
                            else -> shortlist.first()
                        }
                    }
                }
            }
        val power =
            if (chosenPower > 0f) {
                chosenPower
            } else {
                estimatePower(chosen.cueDistance, chosen.ballToPocket, chosen.cutAngle)
            }
        return ShotPlan(
            angle = chosen.angle + aimErrRad,
            power = execPower(power),
            spinX = chosen.spinX,
            spinY = chosen.spinY,
            targetBallId = chosen.target.id,
            pocketIndex = chosen.pocketIndex,
        )
    }

    /** 当前击球方可直接进攻的球（按模式区分）。 */
    fun eligibleTargets(
        balls: List<PoolBall>,
        state: PoolMatchState,
    ): List<PoolBall> {
        val onTable = balls.filter { it.onTable && it.kind != PoolBallKind.CUE }
        return when (state.mode) {
            PoolMode.NINE_BALL -> PoolRules.lowestBall(balls)?.let { listOf(it) } ?: emptyList()
            PoolMode.PRACTICE, PoolMode.CHALLENGE -> onTable
            PoolMode.EIGHT_BALL -> {
                val targetKind = PoolRules.targetKindFor(state, PoolRules.remaining(balls))
                when {
                    targetKind == PoolBallKind.EIGHT -> onTable.filter { it.kind == PoolBallKind.EIGHT }
                    state.openTable -> onTable.filter { it.kind != PoolBallKind.EIGHT }
                    targetKind == null -> emptyList()
                    else -> onTable.filter { it.kind == targetKind }
                }
            }
        }
    }

    /** 目标球是否可直接进攻指定袋口（幽灵球位 + 切角 + 路径净空）。 */
    private fun evaluateCandidate(
        balls: List<PoolBall>,
        table: PoolTable,
        cue: PoolBall,
        ball: PoolBall,
        pocket: PoolPocket,
        pocketIndex: Int,
    ): Candidate? {
        val bx = ball.x - pocket.x
        val by = ball.y - pocket.y
        val bl = hypot(bx, by)
        if (bl < 1e-4f) return null
        val ux = bx / bl
        val uy = by / bl
        val ghostX = ball.x + ux * (ball.radius * 2f)
        val ghostY = ball.y + uy * (ball.radius * 2f)
        if (!table.contains(ghostX, ghostY, margin = -cue.radius)) return null
        val aimX = ghostX - cue.x
        val aimY = ghostY - cue.y
        val aimLen = hypot(aimX, aimY)
        if (aimLen < ball.radius * 2.2f) return null
        val cut = PoolMath.angleBetween(aimX, aimY, -ux, -uy)
        if (cut > 1.35f) return null
        if (!pathClear(balls, cue.x, cue.y, ghostX, ghostY, cue.radius * 2f, cue, ball)) return null
        if (!pathClear(balls, ball.x, ball.y, pocket.x, pocket.y, ball.radius * 2f, null, ball)) return null

        val cueDistance = aimLen
        // 距离价值按「目标球到袋口捕获圈边缘」计算：中袋/角袋捕获半径已同量级，不再额外惩罚中袋
        val ballToPocket = (bl - PoolPhysics.pocketCaptureRadius(pocket, ball.radius)).coerceAtLeast(0f)
        val cutFactor = 1f - (cut / 1.35f) * 0.7f
        val score = cutFactor / (1f + cueDistance * 1.6f + ballToPocket * 1.1f)
        val spinY = if (cueDistance < 0.25f) 0.25f else 0f
        return Candidate(ball, pocketIndex, atan2(aimY, aimX), cut, cueDistance, ballToPocket, score, 0f, spinY)
    }

    /** 线段 [x1,y1]-[x2,y2] 在宽度 [clearance] 内是否被其它球阻挡。 */
    private fun pathClear(
        balls: List<PoolBall>,
        x1: Float,
        y1: Float,
        x2: Float,
        y2: Float,
        clearance: Float,
        excludeA: PoolBall?,
        excludeB: PoolBall?,
    ): Boolean {
        val dx = x2 - x1
        val dy = y2 - y1
        val len2 = dx * dx + dy * dy
        if (len2 < 1e-8f) return true
        for (b in balls) {
            if (!b.onTable || b === excludeA || b === excludeB) continue
            val t = (((b.x - x1) * dx + (b.y - y1) * dy) / len2).coerceIn(0f, 1f)
            val px = x1 + dx * t
            val py = y1 + dy * t
            if (hypot(b.x - px, b.y - py) < clearance - 1e-4f) return false
        }
        return true
    }

    /**
     * 用真实物理内核克隆整桌推演一杆，返回落袋/走位/首碰等信息；
     * [budget]/[stage] 额度耗尽或达到 [SIM_MAX_STEPS] 仍未静止时，结果标记为
     * [SimResult.truncated]（未验证），调用方必须与「未进球」区分。
     *
     * [spinX]/[spinY] 与实际出杆 [PoolGame.applyShotToCue] 一致（含偏杆 squirt），
     * 保证「推演不落袋」等于「实打不落袋」。
     */
    internal fun simulate(
        balls: List<PoolBall>,
        table: PoolTable,
        angle: Float,
        power: Float,
        targetId: Int,
        budget: SimBudget? = null,
        stage: SimStage = SimStage.VERIFY,
        spinX: Float = 0f,
        spinY: Float = 0f,
    ): SimResult {
        val clones = balls.map { cloneBall(it) }
        val cloneCue = clones.firstOrNull { it.kind == PoolBallKind.CUE } ?: return SimResult(false, false, 0f, 0f, null, balls)
        val speed = simulationSpeed(power)
        // 侧旋「偏杆」：与 PoolGame.applyShotToCue 相同的出杆方向修正
        val squirt = (-spinX) * PoolMath.radians(0.85f) * (0.35f + 0.65f * power)
        val dir = angle + squirt
        cloneCue.vx = cos(dir) * speed
        cloneCue.vy = sin(dir) * speed
        val r = cloneCue.radius
        when {
            spinY > 0f -> {
                cloneCue.wx = -cloneCue.vy / r * spinY
                cloneCue.wy = cloneCue.vx / r * spinY
            }
            spinY < 0f -> {
                val k = -spinY
                cloneCue.wx = cloneCue.vy / r * k
                cloneCue.wy = -cloneCue.vx / r * k
            }
        }
        cloneCue.wz = spinX * (12f * power + 4f)
        var firstContactId: Int? = null
        var railAfterContact = false
        val listener =
            object : PoolPhysics.Listener {
                override fun onBallBall(
                    a: PoolBall,
                    b: PoolBall,
                ) {
                    if (firstContactId == null && (a.kind == PoolBallKind.CUE || b.kind == PoolBallKind.CUE)) {
                        firstContactId = if (a.kind == PoolBallKind.CUE) b.id else a.id
                    }
                }

                override fun onRail(ball: PoolBall) {
                    if (firstContactId != null) railAfterContact = true
                }

                override fun onPocket(ball: PoolBall) {
                    if (firstContactId != null && ball.kind != PoolBallKind.CUE) railAfterContact = true
                }
            }
        var steps = 0
        var truncated = false
        var budgetExhausted = false
        while (steps < SIM_MAX_STEPS && !PoolPhysics.settled(clones)) {
            if (budget != null && !budget.tryConsume(stage)) {
                truncated = true
                budgetExhausted = true
                break
            }
            PoolPhysics.step(clones, table, SIM_DT, listener)
            steps++
        }
        if (!truncated && steps >= SIM_MAX_STEPS && !PoolPhysics.settled(clones)) truncated = true
        val targetClone = clones.firstOrNull { it.id == targetId }
        return SimResult(
            targetPotted = targetClone != null && targetClone.potted,
            cueScratched = cloneCue.potted,
            cueX = cloneCue.x,
            cueY = cloneCue.y,
            firstContactId = firstContactId,
            balls = clones,
            truncated = truncated,
            budgetExhausted = budgetExhausted,
            railAfterContact = railAfterContact,
        )
    }

    /**
     * AI 推演用的白球初速度：与真实出杆 [PoolConfig.shotSpeedMps] 完全一致，
     * 避免推演按与实际击球不同的速度评估候选（可单测）。
     */
    internal fun simulationSpeed(power: Float): Float = PoolConfig.shotSpeedMps(power)

    /**
     * 单次 [plan] 的确定性推演预算（主线程约 20ms 内的最坏步数），
     * 所有 [simulate] 共享同一个 [SimBudget]，耗尽后停止后续验证/走位候选。
     *
     * NORMAL 及以上统一到与 MASTER 相同的上限：验证失败后的兜底要补验更多候选、
     * 尝试更轻的力度才能找到「进球且不落袋」的线路，否则只能退回安全球而错过干净机会。
     */
    private fun simulationBudget(difficulty: Difficulty): Int =
        when (difficulty) {
            Difficulty.NOVICE -> 360
            Difficulty.EASY -> 600
            Difficulty.NORMAL -> 1500
            Difficulty.HARD -> 1500
            Difficulty.MASTER -> 1500
        }

    /**
     * 单次 [plan] 的最坏物理推演步数（主线程预算，供测试回归）。
     *
     * 所有推演共用确定性 [SimBudget]，因此最坏步数就是该难度的总预算。
     */
    fun worstCaseSimulationSteps(difficulty: Difficulty): Int = simulationBudget(difficulty)

    /** 推演预算是否已触及留给安全球兜底的保留额度（[SAFETY_FALLBACK_RESERVE]）。 */
    private fun SimBudget.reserveReached(): Boolean = used + SAFETY_FALLBACK_RESERVE >= maxSteps

    private fun cloneBall(b: PoolBall): PoolBall =
        PoolBall(b.id, b.kind, b.x, b.y, b.vx, b.vy, b.wx, b.wy, b.wz, b.radius).also {
            it.qw = b.qw
            it.qx = b.qx
            it.qy = b.qy
            it.qz = b.qz
            it.onTable = b.onTable
            it.potted = b.potted
            it.pocketIndex = b.pocketIndex
            it.pocketAnim = b.pocketAnim
        }

    /**
     * 白球停点后的「下一杆可打性」粗略分：对当前击球方的合法目标，
     * 取「到目标球距离越短、切角越小」的最优值（越高越好走位）。
     */
    private fun nextShotQuality(
        balls: List<PoolBall>,
        table: PoolTable,
        state: PoolMatchState,
    ): Float {
        val cue = balls.firstOrNull { it.kind == PoolBallKind.CUE && it.onTable && !it.potted } ?: return 0f
        return shotQualityFrom(cue, balls, table, state)
    }

    /** 以 [cue] 为击球点评估当前击球方的最佳可打性（切角 / 路径净空 / 距离）。 */
    private fun shotQualityFrom(
        cue: PoolBall,
        balls: List<PoolBall>,
        table: PoolTable,
        state: PoolMatchState,
    ): Float {
        val targets = eligibleTargets(balls, state)
        if (targets.isEmpty()) return 1f
        var best = 0f
        for (t in targets) {
            for (pi in table.pockets.indices) {
                val c = evaluateCandidate(balls, table, cue, t, table.pockets[pi], pi) ?: continue
                val straight = 1f - (c.cutAngle / 1.35f) * 0.6f
                val v = straight / (1f + c.cueDistance * 1.4f + c.ballToPocket)
                if (v > best) best = v
            }
        }
        return best
    }

    /** 对手下一杆的最佳机会分（越低说明安全球做得越好）。 */
    private fun opponentOpportunity(
        balls: List<PoolBall>,
        table: PoolTable,
        state: PoolMatchState,
    ): Float {
        if (state.mode == PoolMode.PRACTICE || state.mode == PoolMode.CHALLENGE) return 0f
        return nextShotQuality(balls, table, state.copy(turn = state.opponent()))
    }

    /** 安全球结果：方案 + 是否经物理推演确认「首碰合法且白球不落袋」。 */
    private data class SafetyOutcome(
        val plan: ShotPlan,
        val noScratchVerified: Boolean,
    )

    /**
     * 无进攻机会时的安全球：对每个合法目标生成若干瞄点，低力度推演，
     * 选「对手下一杆机会最小」的一杆；低难度退化为轻推最近目标。
     *
     * 推演过的候选会过滤掉白球落袋与首碰错球；当找不到任何通过推演的方案时，
     * 退化为最近目标轻推，并用 [SafetyOutcome.noScratchVerified] 标记「未经确认」，
     * 由 [plan] 决定是否改用其它不落袋的候选。
     */
    private fun safetyPlan(
        cue: PoolBall,
        balls: List<PoolBall>,
        table: PoolTable,
        state: PoolMatchState,
        difficulty: Difficulty,
        rng: Random,
        budget: SimBudget,
    ): SafetyOutcome? {
        val targets =
            eligibleTargets(balls, state).ifEmpty {
                balls.filter { it.onTable && it.kind != PoolBallKind.CUE }
            }
        if (targets.isEmpty()) return null

        var best = nearestLightPush(cue, targets)
        var verifiedNoScratch = false
        if (difficulty.verifyShots > 0 || difficulty.positionPlay > 0f) {
            var bestOpp = Float.MAX_VALUE
            var found = false
            var sims = 0
            for (t in targets.take(SAFETY_MAX_TARGETS)) {
                if (budget.exhausted(SimStage.SAFETY) || sims >= SAFETY_MAX_SIMS) break
                val base = atan2(t.y - cue.y, t.x - cue.x)
                for (off in floatArrayOf(0f, -0.045f, 0.045f)) {
                    if (budget.exhausted(SimStage.SAFETY) || sims >= SAFETY_MAX_SIMS) break
                    sims++
                    val angle = base + off
                    val res = simulate(balls, table, angle, SAFETY_POWER, t.id, budget, SimStage.SAFETY)
                    if (res.budgetExhausted) break
                    if (res.cueScratched || res.firstContactId != t.id) continue
                    // 低力度轻推容易「碰球后无库无袋」：不满足规则要求的安全球不采用
                    if (!res.railAfterContact && !res.targetPotted) continue
                    val opp = opponentOpportunity(res.balls, table, state)
                    if (opp < bestOpp) {
                        bestOpp = opp
                        best = angle to t
                        found = true
                    }
                }
            }
            verifiedNoScratch = found
            if (!found) best = nearestLightPush(cue, targets)
        }
        val jitter = (rng.nextFloat() - 0.5f) * 0.03f
        return SafetyOutcome(
            plan =
                ShotPlan(
                    angle = best.first + jitter,
                    power = SAFETY_POWER,
                    spinX = 0f,
                    spinY = 0f,
                    targetBallId = best.second.id,
                    pocketIndex = -1,
                    safety = true,
                ),
            noScratchVerified = verifiedNoScratch,
        )
    }

    /**
     * 验证失败后的兜底选杆：先用验证阶段剩余配额补推演候选（缓存结果），
     * 再按「不落白球」优先排序——进球且不落袋 > 未进球且不落袋 > 未推演 > 已确认落袋；
     * 同为不落袋时，白球终点离袋口越远越安全。
     */
    private fun safestCandidate(
        candidates: List<Candidate>,
        simResults: MutableMap<Candidate, SimResult>,
        balls: List<PoolBall>,
        table: PoolTable,
        budget: SimBudget,
        aimErrRad: Float,
        powerErr: Float,
    ): Candidate? {
        for (c in candidates) {
            if (simResults.containsKey(c)) continue
            if (budget.exhausted(SimStage.VERIFY)) break
            val angle = c.angle + aimErrRad
            val power = (estimatePower(c.cueDistance, c.ballToPocket, c.cutAngle) + powerErr).coerceIn(0.12f, 1f)
            val res = simulate(balls, table, angle, power, c.target.id, budget, SimStage.VERIFY, c.spinX, c.spinY)
            if (res.budgetExhausted) break
            simResults[c] = res
        }
        var best: Candidate? = null
        var bestRank = -Float.MAX_VALUE
        for (c in candidates) {
            val rank = cueScratchRank(simResults[c], table)
            if (rank > bestRank) {
                bestRank = rank
                best = c
            }
        }
        return best
    }

    /** 候选的「白球不落袋」排序分（越大越安全）：已确认落袋最低，未推演次之。 */
    private fun cueScratchRank(
        sim: SimResult?,
        table: PoolTable,
    ): Float =
        when {
            sim == null -> 0f
            sim.cueScratched -> -1f
            sim.targetPotted -> 2f + cuePocketClearance(sim, table)
            // 接触后无库无袋会判「未碰库」犯规，排在正常得分的落袋线之后
            sim.railAfterContact -> 1f + cuePocketClearance(sim, table)
            else -> 0.5f + cuePocketClearance(sim, table)
        }

    /** 推演后白球停点到最近袋口捕获圈边缘的距离（越大越远离落袋风险）。 */
    private fun cuePocketClearance(
        sim: SimResult,
        table: PoolTable,
    ): Float {
        var min = Float.MAX_VALUE
        for (p in table.pockets) {
            val d = hypot(sim.cueX - p.x, sim.cueY - p.y) - PoolPhysics.pocketCaptureRadius(p, PoolConfig.BALL_RADIUS)
            if (d < min) min = d
        }
        return min.coerceAtLeast(0f)
    }

    private fun nearestLightPush(
        cue: PoolBall,
        targets: List<PoolBall>,
    ): Pair<Float, PoolBall> {
        val t = targets.minByOrNull { hypot(it.x - cue.x, it.y - cue.y) } ?: targets.first()
        return atan2(t.y - cue.y, t.x - cue.x) to t
    }

    /**
     * 解斯诺克：直接路径全被挡时，用 6 段主库（排除袋口钳口）的镜像点生成一库解球候选并物理推演，
     * 仅当能真正打进目标球才采用（否则退回安全球）。候选按「库 → 目标」轮转，
     * 避免第一个目标吃光 [KICK_MAX_SHOTS] 预算而漏掉后续目标。
     */
    internal fun kickPlan(
        balls: List<PoolBall>,
        table: PoolTable,
        state: PoolMatchState,
        cue: PoolBall,
        rng: Random,
        budget: SimBudget,
    ): ShotPlan? {
        val targets = eligibleTargets(balls, state)
        if (targets.isEmpty()) return null
        for ((angle, t) in kickCandidates(table, targets, cue).take(KICK_MAX_SHOTS)) {
            if (budget.exhausted(SimStage.KICK)) break
            val power = estimatePower(hypot(t.x - cue.x, t.y - cue.y), 0.35f, 0.4f)
            val res = simulate(balls, table, angle, power, t.id, budget, SimStage.KICK)
            // 预算耗尽后的结果是「未验证」：停止解球而不是当作解球失败继续空跑
            if (res.budgetExhausted) break
            if (res.cueScratched || !res.targetPotted || res.firstContactId != t.id) continue
            return ShotPlan(
                angle = angle + (rng.nextFloat() - 0.5f) * 0.02f,
                power = power,
                spinX = 0f,
                spinY = 0f,
                targetBallId = t.id,
                pocketIndex = -1,
            )
        }
        return null
    }

    /**
     * 一库解球候选：只对 6 段主库（台面四边整段，排除 12 段袋口钳口）生成，
     * 顺序为「库 → 目标」轮转，保证多目标时每个目标都能分到候选名额。
     */
    internal fun kickCandidates(
        table: PoolTable,
        targets: List<PoolBall>,
        cue: PoolBall,
    ): List<Pair<Float, PoolBall>> {
        val kicks = ArrayList<Pair<Float, PoolBall>>()
        for (rail in mainRails(table)) {
            for (t in targets) {
                val angle = oneCushionAngle(cue, t, rail) ?: continue
                kicks += angle to t
            }
        }
        return kicks
    }

    /** 台面四边的整段主库（排除袋口钳口斜边）。 */
    private fun mainRails(table: PoolTable): List<PoolRail> =
        table.rails.filter { rail ->
            val verticalOnBoundary =
                abs(rail.x1 - rail.x2) < 1e-6f &&
                    (abs(rail.x1 - table.left) < 1e-4f || abs(rail.x1 - table.right) < 1e-4f)
            val horizontalOnBoundary =
                abs(rail.y1 - rail.y2) < 1e-6f &&
                    (abs(rail.y1 - table.top) < 1e-4f || abs(rail.y1 - table.bottom) < 1e-4f)
            verticalOnBoundary || horizontalOnBoundary
        }

    /** 白球到目标球的一库镜像瞄角（反射点需落在该库段内），无解返回 null。 */
    private fun oneCushionAngle(
        cue: PoolBall,
        target: PoolBall,
        rail: PoolRail,
    ): Float? {
        val dx = rail.x2 - rail.x1
        val dy = rail.y2 - rail.y1
        val len2 = dx * dx + dy * dy
        if (len2 < 1e-9f) return null
        // 目标球关于库线（无限延长）的镜像点
        val tp = ((target.x - rail.x1) * dx + (target.y - rail.y1) * dy) / len2
        val projX = rail.x1 + dx * tp
        val projY = rail.y1 + dy * tp
        val mx = 2f * projX - target.x
        val my = 2f * projY - target.y
        val ex = mx - cue.x
        val ey = my - cue.y
        if (hypot(ex, ey) < 1e-4f) return null
        // 线段 cue→mirror 与库线段的交点参数 s（0..1）/ u（0..1）
        val denom = ex * dy - ey * dx
        if (abs(denom) < 1e-9f) return null
        val wx = rail.x1 - cue.x
        val wy = rail.y1 - cue.y
        val s = (wx * dy - wy * dx) / denom
        val u = (wx * ey - wy * ex) / denom
        if (s <= 0f || s >= 1f || u < 0f || u > 1f) return null
        return atan2(ey, ex)
    }

    /** 力度估计：距离越远、切角越大越用力。 */
    fun estimatePower(
        cueDistance: Float,
        ballToPocket: Float,
        cutAngle: Float,
    ): Float {
        val d = cueDistance + ballToPocket
        val base = 0.28f + d * 0.55f
        val cutPenalty = cutAngle / 1.35f * 0.22f
        return (base + cutPenalty).coerceIn(0.15f, 1f)
    }

    /**
     * 自由球时 AI 摆白球：对每个「目标球 × 袋口」的直球摆位做实际质量评估
     * （切角 / 路径净空 / 距离），取最佳位置；全被挡或非法时在台面内搜索合法空位。
     * 合法性与玩家 [PoolGame.tryPlaceCue] 共用 [PoolPhysics.isLegalCuePlacement]，
     * 不会把白球放进袋口捕获圈或与其它球重叠。
     */
    fun placeCueBall(
        balls: List<PoolBall>,
        table: PoolTable,
        state: PoolMatchState,
        rng: Random,
    ): Pair<Float, Float> {
        val cue = balls.firstOrNull { it.kind == PoolBallKind.CUE } ?: return table.centerX to table.centerY
        val targets = eligibleTargets(balls, state)
        val candidates = ArrayList<Triple<Float, Float, Float>>() // x, y, score
        for (ball in targets) {
            for (pocket in table.pockets) {
                val dx = ball.x - pocket.x
                val dy = ball.y - pocket.y
                val l = hypot(dx, dy)
                if (l < 1e-4f) continue
                val px = ball.x + dx / l * (ball.radius * 2f + 0.12f)
                val py = ball.y + dy / l * (ball.radius * 2f + 0.12f)
                if (!PoolPhysics.isLegalCuePlacement(balls, table, cue, px, py)) continue
                val score = placementQuality(balls, table, state, cue, px, py)
                candidates += Triple(px, py, score)
            }
        }
        val best = candidates.maxByOrNull { it.third }
        if (best != null) return best.first to best.second
        return findLegalCueSpot(balls, table, cue) ?: (table.centerX to table.centerY)
    }

    /** 兜底：按 [PoolPhysics.isLegalCuePlacement] 在台面内扫描第一个合法摆位。 */
    private fun findLegalCueSpot(
        balls: List<PoolBall>,
        table: PoolTable,
        cue: PoolBall,
    ): Pair<Float, Float>? {
        val margin = cue.radius * 1.5f
        val step = cue.radius * 2.5f
        var y = table.top + margin
        while (y <= table.bottom - margin) {
            var x = table.left + margin
            while (x <= table.right - margin) {
                if (PoolPhysics.isLegalCuePlacement(balls, table, cue, x, y)) return x to y
                x += step
            }
            y += step
        }
        return null
    }

    /**
     * 自由球摆位质量：把白球假想放在 ([x], [y])，评估该位置的最佳直接进攻机会
     * （切角越小、路径越净空、距离越近分越高；被挡或看不到球则接近 0）。
     */
    internal fun placementQuality(
        balls: List<PoolBall>,
        table: PoolTable,
        state: PoolMatchState,
        cue: PoolBall,
        x: Float,
        y: Float,
    ): Float {
        val probe = PoolBall(cue.id, PoolBallKind.CUE, x, y, radius = cue.radius)
        val probeBalls = balls.map { if (it === cue) probe else it }
        return shotQualityFrom(probe, probeBalls, table, state)
    }
}

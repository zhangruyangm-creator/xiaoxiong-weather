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

    /** 安全球固定使用的力度（推演与实际出杆一致）。 */
    private const val SAFETY_POWER = 0.32f

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
            aimErrorDeg = 7.5f,
            powerError = 0.20f,
            candidates = 4,
            verifyShots = 0,
            positionPlay = 0f,
            positionCandidates = 0,
        ),
        EASY(
            aimErrorDeg = 5.0f,
            powerError = 0.14f,
            candidates = 6,
            verifyShots = 0,
            positionPlay = 0f,
            positionCandidates = 0,
        ),
        NORMAL(
            aimErrorDeg = 2.4f,
            powerError = 0.08f,
            candidates = 10,
            verifyShots = 2,
            positionPlay = 0f,
            positionCandidates = 0,
        ),
        HARD(
            aimErrorDeg = 1.0f,
            powerError = 0.045f,
            candidates = 12,
            verifyShots = 4,
            positionPlay = 0.40f,
            positionCandidates = 3,
        ),
        MASTER(
            aimErrorDeg = 0.35f,
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
    private data class SimResult(
        val targetPotted: Boolean,
        val cueScratched: Boolean,
        val cueX: Float,
        val cueY: Float,
        val firstContactId: Int?,
        val balls: List<PoolBall>,
    )

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
    ): ShotPlan? {
        val cue = balls.firstOrNull { it.kind == PoolBallKind.CUE && it.onTable } ?: return null
        val targets = eligibleTargets(balls, state)
        if (targets.isEmpty()) {
            return safetyPlan(cue, balls, table, state, difficulty, rng)
        }

        val candidates = ArrayList<Candidate>()
        for (ball in targets) {
            for (pi in table.pockets.indices) {
                evaluateCandidate(balls, table, cue, ball, table.pockets[pi], pi)?.let { candidates += it }
            }
        }

        if (candidates.isEmpty()) {
            // 直接进攻路径全被挡：尝试一库解球（翻袋/踢球）并推演验证，失败则打安全球
            kickPlan(balls, table, state, cue, rng)?.let { return it }
            return safetyPlan(cue, balls, table, state, difficulty, rng)
        }

        candidates.sortByDescending { it.score }
        val shortlist = candidates.take(difficulty.candidates)

        var verified: Candidate? = null
        var planned: Candidate? = null
        var plannedValue = -Float.MAX_VALUE
        var positionChecked = 0
        if (difficulty.verifyShots > 0) {
            for (c in shortlist.take(difficulty.verifyShots)) {
                val simPower = estimatePower(c.cueDistance, c.ballToPocket, c.cutAngle)
                val res = simulate(balls, table, c.angle, simPower, c.target.id)
                if (!res.targetPotted || res.cueScratched) continue
                if (verified == null) verified = c
                // 两杆走位（仅 HARD/MASTER）：把白球停点 + 剩余台面作为下一杆局面，
                // 复用 evaluateCandidate 评估「下一杆最佳可打性」，当前杆总分 = 进球分 + 权重 × 走位分。
                // 只对前 N 个已验证候选推演下一杆局面，控制成本。
                if (difficulty.positionCandidates > 0 && positionChecked < difficulty.positionCandidates) {
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
        val chosen = planned ?: verified ?: shortlist.first()
        val aimErrRad = Math.toRadians(difficulty.aimErrorDeg.toDouble()).toFloat() * (rng.nextFloat() * 2f - 1f)
        val powerErr = difficulty.powerError * (rng.nextFloat() * 2f - 1f)
        val power = estimatePower(chosen.cueDistance, chosen.ballToPocket, chosen.cutAngle)
        return ShotPlan(
            angle = chosen.angle + aimErrRad,
            power = (power + powerErr).coerceIn(0.12f, 1f),
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
        val ballToPocket = bl
        val pocketFactor = if (pocket.corner) 1f else 0.92f
        val cutFactor = 1f - (cut / 1.35f) * 0.7f
        val score = pocketFactor * cutFactor / (1f + cueDistance * 1.6f + ballToPocket * 1.1f)
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

    /** 用真实物理内核克隆整桌推演一杆，返回落袋/走位/首碰等信息。 */
    private fun simulate(
        balls: List<PoolBall>,
        table: PoolTable,
        angle: Float,
        power: Float,
        targetId: Int,
    ): SimResult {
        val clones = balls.map { cloneBall(it) }
        val cloneCue = clones.firstOrNull { it.kind == PoolBallKind.CUE } ?: return SimResult(false, false, 0f, 0f, null, balls)
        val speed = simulationSpeed(power)
        cloneCue.vx = cos(angle) * speed
        cloneCue.vy = sin(angle) * speed
        var firstContactId: Int? = null
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
            }
        var steps = 0
        while (steps < SIM_MAX_STEPS && !PoolPhysics.settled(clones)) {
            PoolPhysics.step(clones, table, SIM_DT, listener)
            steps++
        }
        val targetClone = clones.firstOrNull { it.id == targetId }
        return SimResult(
            targetPotted = targetClone != null && targetClone.potted,
            cueScratched = cloneCue.potted,
            cueX = cloneCue.x,
            cueY = cloneCue.y,
            firstContactId = firstContactId,
            balls = clones,
        )
    }

    /**
     * AI 推演用的白球初速度：与真实出杆 [PoolConfig.shotSpeedMps] 完全一致，
     * 避免推演按与实际击球不同的速度评估候选（可单测）。
     */
    internal fun simulationSpeed(power: Float): Float = PoolConfig.shotSpeedMps(power)

    /**
     * 单次 [plan] 的最坏物理推演步数（主线程预算，供测试回归）。
     *
     * 取「有候选手动验证」与「全部被挡 → 解球 + 安全球」两条路径的较大者：
     * 后者 = [KICK_MAX_SHOTS] × [SIM_MAX_STEPS] + [SAFETY_MAX_TARGETS] × 3 × [SIM_MAX_STEPS]。
     */
    fun worstCaseSimulationSteps(difficulty: Difficulty): Int {
        val verify = difficulty.verifyShots * SIM_MAX_STEPS
        val blocked = (KICK_MAX_SHOTS + SAFETY_MAX_TARGETS * 3) * SIM_MAX_STEPS
        return maxOf(verify, blocked)
    }

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

    /**
     * 无进攻机会时的安全球：对每个合法目标生成若干瞄点，低力度推演，
     * 选「对手下一杆机会最小」的一杆；低难度退化为轻推最近目标。
     */
    private fun safetyPlan(
        cue: PoolBall,
        balls: List<PoolBall>,
        table: PoolTable,
        state: PoolMatchState,
        difficulty: Difficulty,
        rng: Random,
    ): ShotPlan? {
        val targets =
            eligibleTargets(balls, state).ifEmpty {
                balls.filter { it.onTable && it.kind != PoolBallKind.CUE }
            }
        if (targets.isEmpty()) return null

        var best = nearestLightPush(cue, targets)
        if (difficulty.verifyShots > 0 || difficulty.positionPlay > 0f) {
            var bestOpp = Float.MAX_VALUE
            var found = false
            for (t in targets.take(SAFETY_MAX_TARGETS)) {
                val base = atan2(t.y - cue.y, t.x - cue.x)
                for (off in floatArrayOf(0f, -0.045f, 0.045f)) {
                    val angle = base + off
                    val res = simulate(balls, table, angle, SAFETY_POWER, t.id)
                    if (res.cueScratched || res.firstContactId != t.id) continue
                    val opp = opponentOpportunity(res.balls, table, state)
                    if (opp < bestOpp) {
                        bestOpp = opp
                        best = angle to t
                        found = true
                    }
                }
            }
            if (!found) best = nearestLightPush(cue, targets)
        }
        val jitter = (rng.nextFloat() - 0.5f) * 0.03f
        return ShotPlan(
            angle = best.first + jitter,
            power = SAFETY_POWER,
            spinX = 0f,
            spinY = 0f,
            targetBallId = best.second.id,
            pocketIndex = -1,
            safety = true,
        )
    }

    private fun nearestLightPush(
        cue: PoolBall,
        targets: List<PoolBall>,
    ): Pair<Float, PoolBall> {
        val t = targets.minByOrNull { hypot(it.x - cue.x, it.y - cue.y) } ?: targets.first()
        return atan2(t.y - cue.y, t.x - cue.x) to t
    }

    /**
     * 解斯诺克：直接路径全被挡时，用台边镜像点生成一库解球候选并物理推演，
     * 仅当能真正打进目标球才采用（否则退回安全球）。
     */
    private fun kickPlan(
        balls: List<PoolBall>,
        table: PoolTable,
        state: PoolMatchState,
        cue: PoolBall,
        rng: Random,
    ): ShotPlan? {
        val targets = eligibleTargets(balls, state)
        if (targets.isEmpty()) return null
        val kicks = ArrayList<Pair<Float, PoolBall>>()
        for (t in targets) {
            for (rail in table.rails) {
                val angle = oneCushionAngle(cue, t, rail) ?: continue
                kicks += angle to t
            }
        }
        for ((angle, t) in kicks.take(KICK_MAX_SHOTS)) {
            val power = estimatePower(hypot(t.x - cue.x, t.y - cue.y), 0.35f, 0.4f)
            val res = simulate(balls, table, angle, power, t.id)
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
     * （切角 / 路径净空 / 距离），取最佳位置；全被挡或非法时退回台面中心附近。
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
                if (!table.contains(px, py, margin = -cue.radius)) continue
                if (!freeSpot(balls, cue, px, py)) continue
                val score = placementQuality(balls, table, state, cue, px, py)
                candidates += Triple(px, py, score)
            }
        }
        val best = candidates.maxByOrNull { it.third }
        if (best != null) return best.first to best.second
        return table.centerX to table.centerY
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

    private fun freeSpot(
        balls: List<PoolBall>,
        cue: PoolBall,
        x: Float,
        y: Float,
    ): Boolean {
        for (b in balls) {
            if (!b.onTable || b === cue) continue
            if (hypot(b.x - x, b.y - y) < b.radius + cue.radius + 1e-3f) return false
        }
        return true
    }
}

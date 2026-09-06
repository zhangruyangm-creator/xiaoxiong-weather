package com.xiaoxiong.weather.ui.game

import kotlin.math.hypot
import kotlin.math.min
import kotlin.random.Random

/**
 * 小熊冰球：纯逻辑（物理 / AI / 计分），无 Compose 依赖，便于单测。
 *
 * 坐标系：竖屏俯视，玩家在下方、AI 在上方；Y 向下为正。
 */
object HockeyLogic {
    enum class Difficulty { EASY, NORMAL, HARD, CUSTOM }

    enum class Phase { READY, SERVE, PLAYING, SCORED, MATCH_OVER }

    /** 谁进球（球进入对方球门）。 */
    enum class Scorer { PLAYER, AI }

    data class DifficultyParams(
        /** AI 最大移动速度（相对短边 / 秒）。 */
        val aiMaxSpeedMul: Float,
        /** AI 加速度（相对短边 / 秒²）。 */
        val aiAccelMul: Float,
        /** 反应延迟（秒）：延迟后才更新追踪目标。 */
        val reactionDelay: Float,
        /** 预判系数 0…1：沿球速方向提前量。 */
        val predict: Float,
        /** 守门倾向 0…1：越高越贴己方球门。 */
        val defenseBias: Float,
        /** 出击：球进入己方半场时向前压迫的比例。 */
        val chase: Float,
    )

    const val CUSTOM_LEVEL_MIN = 1
    const val CUSTOM_LEVEL_MAX = 10
    const val WIN_SCORE_MIN = 1
    const val WIN_SCORE_MAX = 99

    fun params(
        difficulty: Difficulty,
        customLevel: Int = 5,
    ): DifficultyParams =
        when (difficulty) {
            Difficulty.EASY -> PRESET_EASY
            Difficulty.NORMAL -> PRESET_NORMAL
            Difficulty.HARD -> PRESET_HARD
            Difficulty.CUSTOM ->
                lerpParams(
                    PRESET_EASY,
                    PRESET_HARD,
                    (
                        (customLevel.coerceIn(CUSTOM_LEVEL_MIN, CUSTOM_LEVEL_MAX) - 1) /
                            (CUSTOM_LEVEL_MAX - CUSTOM_LEVEL_MIN).toFloat()
                    ),
                )
        }

    private val PRESET_EASY =
        DifficultyParams(
            aiMaxSpeedMul = 0.42f,
            aiAccelMul = 2.2f,
            reactionDelay = 0.42f,
            predict = 0.05f,
            defenseBias = 0.72f,
            chase = 0.35f,
        )

    private val PRESET_NORMAL =
        DifficultyParams(
            aiMaxSpeedMul = 0.72f,
            aiAccelMul = 4.0f,
            reactionDelay = 0.16f,
            predict = 0.28f,
            defenseBias = 0.48f,
            chase = 0.62f,
        )

    private val PRESET_HARD =
        DifficultyParams(
            aiMaxSpeedMul = 1.05f,
            aiAccelMul = 6.5f,
            reactionDelay = 0.04f,
            predict = 0.55f,
            defenseBias = 0.32f,
            chase = 0.88f,
        )

    fun lerpParams(
        a: DifficultyParams,
        b: DifficultyParams,
        t: Float,
    ): DifficultyParams {
        val u = t.coerceIn(0f, 1f)
        return DifficultyParams(
            aiMaxSpeedMul = lerp(a.aiMaxSpeedMul, b.aiMaxSpeedMul, u),
            aiAccelMul = lerp(a.aiAccelMul, b.aiAccelMul, u),
            reactionDelay = lerp(a.reactionDelay, b.reactionDelay, u),
            predict = lerp(a.predict, b.predict, u),
            defenseBias = lerp(a.defenseBias, b.defenseBias, u),
            chase = lerp(a.chase, b.chase, u),
        )
    }

    data class Table(
        val left: Float,
        val top: Float,
        val right: Float,
        val bottom: Float,
        val goalWidth: Float,
        val paddleR: Float,
        val puckR: Float,
        val midY: Float = (top + bottom) * 0.5f,
        val midX: Float = (left + right) * 0.5f,
    ) {
        val width: Float get() = right - left
        val height: Float get() = bottom - top
        val shortSide: Float get() = min(width, height)
    }

    fun layoutTable(
        canvasW: Float,
        canvasH: Float,
        padH: Float,
        padV: Float,
    ): Table {
        val left = padH
        val right = canvasW - padH
        val top = padV
        val bottom = canvasH - padV
        val short = min(right - left, bottom - top)
        return Table(
            left = left,
            top = top,
            right = right,
            bottom = bottom,
            goalWidth = short * 0.36f,
            paddleR = short * 0.075f,
            puckR = short * 0.038f,
        )
    }

    data class Body(
        var x: Float,
        var y: Float,
        var vx: Float = 0f,
        var vy: Float = 0f,
        var r: Float,
    )

    data class CollisionResult(
        val hitPaddle: Boolean = false,
        val hitWall: Boolean = false,
        val scored: Scorer? = null,
    )

    fun clampPaddle(
        table: Table,
        x: Float,
        y: Float,
        isPlayer: Boolean,
    ): Pair<Float, Float> {
        val r = table.paddleR
        val minX = table.left + r
        val maxX = table.right - r
        val cx = x.coerceIn(minX, maxX)
        val cy =
            if (isPlayer) {
                y.coerceIn(table.midY + r * 0.35f, table.bottom - r)
            } else {
                y.coerceIn(table.top + r, table.midY - r * 0.35f)
            }
        return cx to cy
    }

    fun playerHome(table: Table): Pair<Float, Float> = table.midX to (table.bottom - table.height * 0.18f)

    fun aiHome(table: Table): Pair<Float, Float> = table.midX to (table.top + table.height * 0.18f)

    fun puckCenter(table: Table): Pair<Float, Float> = table.midX to table.midY

    /** 开球：略带横向分量，主方向飞向指定半场。 */
    fun serveVelocity(
        table: Table,
        towardPlayer: Boolean,
        random: Random = Random.Default,
    ): Pair<Float, Float> {
        val speed = table.shortSide * (0.55f + random.nextFloat() * 0.12f)
        val vx = (random.nextFloat() - 0.5f) * 2f * speed * 0.55f
        val vy = speed * (0.72f + random.nextFloat() * 0.2f) * if (towardPlayer) 1f else -1f
        return vx to vy
    }

    fun integratePuck(
        puck: Body,
        table: Table,
        dt: Float,
        friction: Float = 0.18f,
        minSpeedMul: Float = 0.12f,
        maxSpeedMul: Float = 2.35f,
    ): CollisionResult {
        var hitWall = false
        puck.x += puck.vx * dt
        puck.y += puck.vy * dt

        // 微摩擦
        val damp = (1f - friction * dt).coerceIn(0.85f, 1f)
        puck.vx *= damp
        puck.vy *= damp

        val maxSp = table.shortSide * maxSpeedMul
        val minSp = table.shortSide * minSpeedMul
        clampSpeed(puck, minSp, maxSp)

        // 左右墙
        if (puck.x - puck.r < table.left) {
            puck.x = table.left + puck.r
            puck.vx = kotlin.math.abs(puck.vx)
            hitWall = true
        } else if (puck.x + puck.r > table.right) {
            puck.x = table.right - puck.r
            puck.vx = -kotlin.math.abs(puck.vx)
            hitWall = true
        }

        val goalHalf = table.goalWidth * 0.5f
        val inGoalX = puck.x in (table.midX - goalHalf)..(table.midX + goalHalf)

        // 上下：球门进球，否则弹墙
        if (puck.y - puck.r < table.top) {
            if (inGoalX && puck.vy < 0f) {
                return CollisionResult(hitWall = hitWall, scored = Scorer.PLAYER)
            }
            puck.y = table.top + puck.r
            puck.vy = kotlin.math.abs(puck.vy)
            hitWall = true
        } else if (puck.y + puck.r > table.bottom) {
            if (inGoalX && puck.vy > 0f) {
                return CollisionResult(hitWall = hitWall, scored = Scorer.AI)
            }
            puck.y = table.bottom - puck.r
            puck.vy = -kotlin.math.abs(puck.vy)
            hitWall = true
        }

        return CollisionResult(hitWall = hitWall)
    }

    fun collidePaddle(
        puck: Body,
        paddle: Body,
        paddleVx: Float,
        paddleVy: Float,
        table: Table,
        impulse: Float = 1.15f,
    ): Boolean {
        val dx = puck.x - paddle.x
        val dy = puck.y - paddle.y
        val dist = hypot(dx, dy)
        val minDist = puck.r + paddle.r
        if (dist >= minDist || dist < 1e-4f) return false

        val nx = dx / dist
        val ny = dy / dist
        // 分离重叠
        val overlap = minDist - dist
        puck.x += nx * (overlap + 0.5f)
        puck.y += ny * (overlap + 0.5f)

        // 相对速度
        val rvx = puck.vx - paddleVx
        val rvy = puck.vy - paddleVy
        val vn = rvx * nx + rvy * ny
        if (vn < 0f) {
            // 弹性碰撞 + 挡板速度注入
            puck.vx -= (1.85f) * vn * nx
            puck.vy -= (1.85f) * vn * ny
        }
        puck.vx += paddleVx * impulse
        puck.vy += paddleVy * impulse

        val maxSp = table.shortSide * 2.35f
        val minSp = table.shortSide * 0.18f
        clampSpeed(puck, minSp, maxSp)
        // 防止贴死在挡板里：给一点点沿法线推开速度
        if (hypot(puck.vx, puck.vy) < minSp) {
            puck.vx = nx * minSp
            puck.vy = ny * minSp
        }
        return true
    }

    fun clampSpeed(
        body: Body,
        minSpeed: Float,
        maxSpeed: Float,
    ) {
        val sp = hypot(body.vx, body.vy)
        when {
            sp > maxSpeed && sp > 1e-4f -> {
                val s = maxSpeed / sp
                body.vx *= s
                body.vy *= s
            }
            sp in 1e-4f..<minSpeed -> {
                val s = minSpeed / sp
                body.vx *= s
                body.vy *= s
            }
        }
    }

    /**
     * AI：带延迟目标 + 预判 + 守门/出击混合。
     * [ai] 的 vx/vy 为输出速度；位置由调用方按速度积分并 clamp。
     */
    fun updateAiTarget(
        table: Table,
        puck: Body,
        params: DifficultyParams,
        reactionAge: Float,
        cachedTargetX: Float,
        cachedTargetY: Float,
    ): Triple<Float, Float, Boolean> {
        val refresh = reactionAge >= params.reactionDelay
        if (!refresh) {
            return Triple(cachedTargetX, cachedTargetY, false)
        }
        val predictT = params.predict * 0.28f
        var tx = puck.x + puck.vx * predictT
        var ty = puck.y + puck.vy * predictT
        tx = tx.coerceIn(table.left + table.paddleR, table.right - table.paddleR)

        val homeY = table.top + table.height * 0.16f
        val chaseY =
            if (puck.y < table.midY && puck.vy <= 0f) {
                // 球在己方半场且向上/静止：出击
                lerp(homeY, table.midY - table.paddleR * 1.2f, params.chase)
                    .coerceAtMost(ty + table.paddleR)
            } else {
                homeY
            }
        ty = lerp(chaseY, ty.coerceIn(table.top + table.paddleR, table.midY - table.paddleR), 1f - params.defenseBias * 0.65f)
        ty = ty.coerceIn(table.top + table.paddleR, table.midY - table.paddleR * 0.4f)

        // 球远离时稍微回中
        if (puck.y > table.midY + table.height * 0.15f) {
            tx = lerp(tx, table.midX, 0.35f + params.defenseBias * 0.25f)
            ty = lerp(ty, homeY, 0.4f)
        }
        return Triple(tx, ty, true)
    }

    fun stepAiVelocity(
        ai: Body,
        targetX: Float,
        targetY: Float,
        table: Table,
        params: DifficultyParams,
        dt: Float,
    ) {
        val maxSp = table.shortSide * params.aiMaxSpeedMul
        val accel = table.shortSide * params.aiAccelMul
        val dx = targetX - ai.x
        val dy = targetY - ai.y
        val dist = hypot(dx, dy)
        if (dist < 1f) {
            ai.vx *= (1f - 8f * dt).coerceIn(0f, 1f)
            ai.vy *= (1f - 8f * dt).coerceIn(0f, 1f)
            return
        }
        val desiredVx = dx / dist * maxSp
        val desiredVy = dy / dist * maxSp
        ai.vx = approach(ai.vx, desiredVx, accel * dt)
        ai.vy = approach(ai.vy, desiredVy, accel * dt)
        val sp = hypot(ai.vx, ai.vy)
        if (sp > maxSp) {
            ai.vx *= maxSp / sp
            ai.vy *= maxSp / sp
        }
    }

    fun matchOver(
        playerScore: Int,
        aiScore: Int,
        winScore: Int,
    ): Boolean = playerScore >= winScore || aiScore >= winScore

    fun winner(
        playerScore: Int,
        aiScore: Int,
    ): Scorer? =
        when {
            playerScore > aiScore -> Scorer.PLAYER
            aiScore > playerScore -> Scorer.AI
            else -> null
        }

    fun speed(body: Body): Float = hypot(body.vx, body.vy)

    private fun approach(
        current: Float,
        target: Float,
        maxDelta: Float,
    ): Float {
        val d = target - current
        return when {
            d > maxDelta -> current + maxDelta
            d < -maxDelta -> current - maxDelta
            else -> target
        }
    }

    private fun lerp(
        a: Float,
        b: Float,
        t: Float,
    ): Float = a + (b - a) * t.coerceIn(0f, 1f)
}

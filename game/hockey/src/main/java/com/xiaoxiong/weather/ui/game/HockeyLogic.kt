package com.xiaoxiong.weather.ui.game

import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.random.Random

/**
 * 小熊冰球：纯逻辑（物理 / AI / 计分），无 Compose 依赖，便于单测。
 *
 * 坐标系：竖屏俯视，玩家在下方、AI 在上方；Y 向下为正。
 */
object HockeyLogic {
    enum class Difficulty { BEGINNER, EASY, NORMAL, ADVANCED, HARD, MASTER, CUSTOM }

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
    const val CUSTOM_LEVEL_MAX = 15
    const val WIN_SCORE_MIN = 1
    const val WIN_SCORE_MAX = 99

    /** 挡板尺寸倍率：设置三档（标准 / 大 / 超大），读取时对旧存档做防御性钳制。 */
    const val PADDLE_SCALE_DEFAULT = 1f
    const val PADDLE_SCALE_MIN = 0.8f
    const val PADDLE_SCALE_MAX = 1.6f

    /** 球（蜜罐）尺寸倍率。 */
    const val PUCK_SCALE_DEFAULT = 1f
    const val PUCK_SCALE_MIN = 0.6f
    const val PUCK_SCALE_MAX = 1.5f

    /** 球门宽度倍率。 */
    const val GOAL_SCALE_DEFAULT = 1f
    const val GOAL_SCALE_MIN = 0.55f
    const val GOAL_SCALE_MAX = 1.45f

    /** 墙角解脱球速（相对短边/秒）：足够脱离挡板，又不至于像开球那么冲。 */
    private const val ESCAPE_SPEED_MUL = 1.15f

    /** 门柱反弹系数（1+e）：柱视为固定圆柱，略带回弹损耗。 */
    private const val POST_RESTITUTION = 1.7f

    fun params(
        difficulty: Difficulty,
        customLevel: Int = 5,
    ): DifficultyParams =
        when (difficulty) {
            Difficulty.BEGINNER -> PRESET_BEGINNER
            Difficulty.EASY -> PRESET_EASY
            Difficulty.NORMAL -> PRESET_NORMAL
            Difficulty.ADVANCED -> PRESET_ADVANCED
            Difficulty.HARD -> PRESET_HARD
            Difficulty.MASTER -> PRESET_MASTER
            Difficulty.CUSTOM ->
                lerpParams(
                    PRESET_BEGINNER,
                    PRESET_MASTER,
                    (
                        (customLevel.coerceIn(CUSTOM_LEVEL_MIN, CUSTOM_LEVEL_MAX) - 1) /
                            (CUSTOM_LEVEL_MAX - CUSTOM_LEVEL_MIN).toFloat()
                    ),
                )
        }

    private val PRESET_BEGINNER =
        DifficultyParams(
            aiMaxSpeedMul = 0.30f,
            aiAccelMul = 1.6f,
            reactionDelay = 0.55f,
            predict = 0.0f,
            defenseBias = 0.85f,
            chase = 0.20f,
        )

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
            aiMaxSpeedMul = 0.66f,
            aiAccelMul = 3.6f,
            reactionDelay = 0.20f,
            predict = 0.24f,
            defenseBias = 0.52f,
            chase = 0.56f,
        )

    private val PRESET_ADVANCED =
        DifficultyParams(
            aiMaxSpeedMul = 0.84f,
            aiAccelMul = 5.0f,
            reactionDelay = 0.10f,
            predict = 0.40f,
            defenseBias = 0.42f,
            chase = 0.72f,
        )

    private val PRESET_HARD =
        DifficultyParams(
            aiMaxSpeedMul = 1.45f,
            aiAccelMul = 11f,
            reactionDelay = 0.01f,
            predict = 0.85f,
            defenseBias = 0.22f,
            chase = 1.0f,
        )

    private val PRESET_MASTER =
        DifficultyParams(
            // 仅抬高档：更快贴线 + 零延迟；低档仍走旧跟球逻辑（见 updateAiTarget）
            aiMaxSpeedMul = 2.05f,
            aiAccelMul = 18f,
            reactionDelay = 0f,
            predict = 1.0f,
            defenseBias = 0.10f,
            chase = 1.0f,
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

    /**
     * 门柱半径（与渲染同一公式）：外框 frame 取桌边距推导（layoutTable 下即 min(padH, padV)），
     * 渲染层 HockeyRender.kt 画柱时直接调用本函数，保证碰撞与像素一致。
     */
    fun goalPostRadius(table: Table): Float {
        val short = table.shortSide
        val margin = min(table.left, table.top)
        val frame = min(short * 0.075f, margin * 0.86f).coerceAtLeast(2f)
        return max(frame * 0.92f * 0.40f, short * 0.012f)
    }

    fun clampPaddle(
        table: Table,
        x: Float,
        y: Float,
        isPlayer: Boolean,
        paddleR: Float = table.paddleR,
        aiGoalLineHug: Boolean = false,
    ): Pair<Float, Float> {
        val r = paddleR
        val minX = table.left + r
        val maxX = table.right - r
        // 半径异常大（旧存档 / 退化尺寸）时区间会反转，coerceIn 会抛异常；
        // 退回半场中轴，保证任何输入都能返回可用坐标而不是让存档静默丢失。
        val cx = if (minX <= maxX) x.coerceIn(minX, maxX) else table.midX
        // AI 默认下界 top+r；仅在门线解围（[aiGoalLineClearanceActive]）时允许压到 top+r/2：
        // 球贴门线时只有站到球心上方（y 更小）才能向下/侧向解围，否则会从下往上顶进自家门；
        // 但中心不能压到 top，否则半个挡板会穿出球门框。
        val minY =
            when {
                isPlayer -> table.midY + r * 0.35f
                aiGoalLineHug -> table.top + r * 0.5f
                else -> table.top + r
            }
        val maxY = if (isPlayer) table.bottom - r else table.midY - r * 0.35f
        val cy = if (minY <= maxY) y.coerceIn(minY, maxY) else table.midY
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
        val prevX = puck.x
        val prevY = puck.y
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

        // 门柱：半径与渲染一致，先于进球判定；正对柱心的球会被弹回（vy 反号），不会算进
        val goalHalf = table.goalWidth * 0.5f
        val postR = goalPostRadius(table)
        val leftPostX = table.midX - goalHalf
        val rightPostX = table.midX + goalHalf
        var hitPost = collideGoalPost(puck, leftPostX, table.top, postR, prevX, prevY, rinkSign = 1f)
        hitPost = collideGoalPost(puck, rightPostX, table.top, postR, prevX, prevY, rinkSign = 1f) || hitPost
        hitPost = collideGoalPost(puck, leftPostX, table.bottom, postR, prevX, prevY, rinkSign = -1f) || hitPost
        hitPost = collideGoalPost(puck, rightPostX, table.bottom, postR, prevX, prevY, rinkSign = -1f) || hitPost
        if (hitPost) {
            hitWall = true
            clampSpeed(puck, minSp, maxSp)
        }

        val inGoalX = puck.x in (table.midX - goalHalf)..(table.midX + goalHalf)

        // 上下：球门进球，否则弹墙。正对门柱的球在碰撞里已被弹回（vy 反向），
        // 擦柱内侧的球仍朝门内飞，按正常进球处理（贴柱滑入）。
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

    /**
     * 单个门柱的圆-圆碰撞：从台面侧飞来的球与柱分离并反弹（柱视为固定圆柱）；
     * 已越过门线的球不再被柱拦截，保证贴柱滑入的球能判进。
     * 快球单帧跨过柱心时用法线取移动前方向，避免把球继续往门里推。
     * 返回本帧是否触柱。
     */
    private fun collideGoalPost(
        puck: Body,
        postX: Float,
        lineY: Float,
        postR: Float,
        prevX: Float,
        prevY: Float,
        rinkSign: Float,
    ): Boolean {
        // 移动前已在门内的球不参与柱碰撞
        if ((prevY - lineY) * rinkSign < 0f) return false
        val dx = puck.x - postX
        val dy = puck.y - lineY
        val dist = hypot(dx, dy)
        val minDist = postR + puck.r
        if (dist >= minDist || dist < 1e-4f) return false
        var nx = dx / dist
        var ny = dy / dist
        if (dy * rinkSign < 0f) {
            // 这一帧跨过了门线：改用移动前位置定法线，把球弹回台面侧
            val pdx = prevX - postX
            val pdy = prevY - lineY
            val pd = hypot(pdx, pdy)
            if (pd < 1e-4f) return false
            nx = pdx / pd
            ny = pdy / pd
        }
        val overlap = minDist - dist
        puck.x += nx * overlap
        puck.y += ny * overlap
        val vn = puck.vx * nx + puck.vy * ny
        if (vn < 0f) {
            puck.vx -= POST_RESTITUTION * vn * nx
            puck.vy -= POST_RESTITUTION * vn * ny
        }
        return true
    }

    fun collidePaddle(
        puck: Body,
        paddle: Body,
        paddleVx: Float,
        paddleVy: Float,
        table: Table,
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
            // 弹性碰撞（挡板视为无限质量）：puck.v_n' = paddle.v_n − e·(puck.v_n − paddle.v_n)，
            // 公式已包含挡板沿法线速度，不能再额外注入一次（否则重复施力：
            // 球已远离、挡板仍在追时会凭空加速，甚至把球顶进自家球门）。
            puck.vx -= (1.85f) * vn * nx
            puck.vy -= (1.85f) * vn * ny
        }

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

    /**
     * 墙角解脱：球被挡板顶死在墙角时（墙面反弹与挡板碰撞互相抵消，球原地高频抖动），
     * 朝场地中心（略带随机）弹出一脚，并把球抬出挡板触及范围——
     * 否则下一帧的分离逻辑会把球重新顶回角落。
     */
    fun cornerEscapeKick(
        table: Table,
        puck: Body,
        random: Random = Random.Default,
    ) {
        val dx = table.midX - puck.x
        val dy = table.midY - puck.y
        val d = hypot(dx, dy).coerceAtLeast(1f)
        val jitter = (random.nextFloat() - 0.5f) * 0.5f
        val jCos = kotlin.math.cos(jitter)
        val jSin = kotlin.math.sin(jitter)
        val dirX = dx / d * jCos - dy / d * jSin
        val dirY = dx / d * jSin + dy / d * jCos
        puck.vx = dirX * table.shortSide * ESCAPE_SPEED_MUL
        puck.vy = dirY * table.shortSide * ESCAPE_SPEED_MUL
        // 抬离挡板触及范围（最坏情况：挡板把球完全顶进角落，球拍间距约 √2·(paddleR−puckR)）
        puck.x += dirX * table.paddleR * 2.5f
        puck.y += dirY * table.paddleR * 2.5f
        puck.x = puck.x.coerceIn(table.left + puck.r, table.right - puck.r)
        puck.y = puck.y.coerceIn(table.top + puck.r, table.bottom - puck.r)
    }

    /** 球是否贴近某个墙角（球心距最近角点 2*puckR 内）：角落挤压限能与解脱判定的区域。 */
    fun inCornerZone(
        table: Table,
        puck: Body,
    ): Boolean {
        val dx = min(puck.x - table.left, table.right - puck.x).coerceAtLeast(0f)
        val dy = min(puck.y - table.top, table.bottom - puck.y).coerceAtLeast(0f)
        return hypot(dx, dy) <= puck.r * 2f
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
     *
     * - 传入 [ai] 时启用「防乌龙走位」：球在本方半场时，AI 只会绕到球的**上方**
     *   （更靠自家球门一侧）再向下推，绝不从下往上顶球；来球威胁时优先堵射门线落点。
     * - 不传 [ai]（旧调用/测试）时保持原「跟球 + 半场出击」逻辑。
     */
    fun updateAiTarget(
        table: Table,
        puck: Body,
        params: DifficultyParams,
        reactionAge: Float,
        cachedTargetX: Float,
        cachedTargetY: Float,
        paddleR: Float = table.paddleR,
        ai: Body? = null,
    ): Triple<Float, Float, Boolean> {
        val refresh = reactionAge >= params.reactionDelay
        if (!refresh) {
            return Triple(cachedTargetX, cachedTargetY, false)
        }

        val homeY = table.top + table.height * 0.16f
        val minX = table.left + paddleR
        val maxX = table.right - paddleR
        val d = paddleR + puck.r
        val midY = table.midY
        val smartDefense = params.predict >= 0.5f
        val dangerous = puck.vy < -table.shortSide * 0.10f

        // 球贴门线且不朝自家门快速飞来时，「绕到球上方」因挡板 y 下界而不可达：
        // 改走横向扫球解围，避免从侧下方把球顶进自家门（乌龙）或停在球侧
        // 2*paddleR 处完全不触球（死锁）。
        if (ai != null && aiGoalLineClearanceActive(table, puck, paddleR)) {
            return aiGoalLineSweep(table, puck, ai, paddleR, minX, maxX, midY)
        }

        if (smartDefense && dangerous) {
            val goalY = table.top + paddleR * 0.85f
            // 传了 ai 且它还在球下方 / 同一水平带时，先按进攻分支的方式侧移绕行：
            // 从内侧接触会把原本会弹库的球蹭进自家门；到球上方（ai.y 越过
            // puck.y - d·0.35）后自动落回下面的射门线拦截。
            if (ai != null && ai.y > puck.y - d * 0.35f) {
                val side =
                    when {
                        cachedTargetX < puck.x - 1f -> -1f
                        cachedTargetX > puck.x + 1f -> 1f
                        ai.x <= puck.x -> -1f
                        else -> 1f
                    }
                val besideX = (puck.x + side * paddleR * 2f).coerceIn(minX, maxX)
                return if (kotlin.math.abs(ai.x - besideX) > paddleR * 0.5f) {
                    // 先侧移到球侧方（保持当前 y，避免上移时穿过球）
                    Triple(besideX, ai.y, true)
                } else {
                    // 再沿侧面上移到球上方
                    Triple(besideX, (puck.y - d).coerceIn(table.top + paddleR, midY - paddleR * 0.35f), true)
                }
            }
            val interceptX = goalLineInterceptX(table, puck, goalY)
            // 高难度几乎贴死落点
            val tx =
                lerp(puck.x, interceptX, 0.72f + params.predict * 0.28f)
                    .coerceIn(minX, maxX)
            // 挡板必须位于来球「上方」(更靠自家球门)，接触才会把球压回去而不是顶进去
            val ty =
                (puck.y - paddleR * 0.9f)
                    .coerceIn(table.top + paddleR, midY - paddleR * 0.35f)
            return Triple(tx, ty, true)
        }

        if (ai != null) {
            // —— 防乌龙走位：球在本方半场时，先绕到球上方再向下推 ——
            val inAiHalf = puck.y < midY
            val attacking = inAiHalf && puck.vy < table.shortSide * 0.35f
            if (attacking) {
                val aboveContact = puck.y - d * 0.55f
                // 侧面选择「锁存」：优先沿用上一帧目标所在的侧，避免目标点左右抖动导致原地打转
                val side =
                    when {
                        cachedTargetX < puck.x - 1f -> -1f
                        cachedTargetX > puck.x + 1f -> 1f
                        ai.x <= puck.x -> -1f
                        else -> 1f
                    }
                val besideX = (puck.x + side * paddleR * 2f).coerceIn(minX, maxX)
                val above = ai.y <= aboveContact
                return if (above) {
                    // 已在上方：对准球心向下推，把球送往玩家球门（正中轴线）
                    val tx = puck.x.coerceIn(minX, maxX)
                    val ty = (puck.y + d * 0.15f).coerceIn(table.top + paddleR, midY - paddleR * 0.35f)
                    Triple(tx, ty, true)
                } else if (kotlin.math.abs(ai.x - besideX) > paddleR * 0.5f) {
                    // 先侧移到球侧方（保持当前 y，避免上移时穿过球）
                    Triple(besideX, ai.y, true)
                } else {
                    // 再沿侧面上移到球上方
                    Triple(besideX, (puck.y - d).coerceIn(table.top + paddleR, midY - paddleR * 0.35f), true)
                }
            }
            // 守门/回位：挡住来球或退回本场，挡板始终在球上方
            val interceptX =
                if (dangerous) {
                    goalLineInterceptX(table, puck, table.top + paddleR * 0.85f)
                } else {
                    (puck.x + puck.vx * (params.predict * 0.28f))
                }
            val tx = lerp(puck.x, interceptX, 0.35f + params.chase * 0.4f).coerceIn(minX, maxX)
            val ty =
                if (inAiHalf) {
                    (puck.y - paddleR * 0.7f).coerceIn(table.top + paddleR, midY - paddleR * 0.35f)
                } else {
                    homeY.coerceIn(table.top + paddleR, midY - paddleR * 0.35f)
                }
            return Triple(tx, ty, true)
        }

        // —— 低/中难度：原跟球逻辑（未传 ai 的兼容路径） ——
        val predictT = params.predict * 0.28f
        var tx = puck.x + puck.vx * predictT
        var ty = puck.y + puck.vy * predictT
        tx = tx.coerceIn(minX, maxX)

        val chaseY =
            if (puck.y < table.midY && puck.vy <= 0f) {
                lerp(homeY, table.midY - paddleR * 1.2f, params.chase)
                    .coerceAtMost(ty + paddleR)
            } else {
                homeY
            }
        ty =
            lerp(
                chaseY,
                ty.coerceIn(table.top + paddleR, table.midY - paddleR),
                1f - params.defenseBias * 0.65f,
            )
        ty = ty.coerceIn(table.top + paddleR, table.midY - paddleR * 0.4f)

        if (puck.y > table.midY + table.height * 0.15f) {
            tx = lerp(tx, table.midX, 0.35f + params.defenseBias * 0.25f)
            ty = lerp(ty, homeY, 0.4f)
        }
        return Triple(tx.coerceIn(minX, maxX), ty, true)
    }

    /**
     * 门线解围条件：球贴门线（约 2 个挡板半径内）且不朝自家门快速飞来，
     * 同时（慢球 / 静止球）必须在门洞横向范围内才成立。该区域 AI 需要允许压到门线
     * （[clampPaddle] 的 `aiGoalLineHug`）并走横向扫球，否则会从侧下方顶出乌龙或停在
     * 球侧不触球（死锁）；远离门洞的顶库慢球走原跟球逻辑，避免被无谓地扫向同侧角落。
     * 正在外出的球只在门洞内卡位（否则按原跟球逻辑离远一点，避免无谓地改变整场走势）。
     */
    fun aiGoalLineClearanceActive(
        table: Table,
        puck: Body,
        paddleR: Float,
    ): Boolean {
        if (puck.y >= table.top + paddleR * 2f) return false
        if (puck.vy < -table.shortSide * 0.10f) return false
        val goalHalf = table.goalWidth * 0.5f
        val dx = kotlin.math.abs(puck.x - table.midX)
        if (puck.vy <= table.shortSide * 0.06f) {
            // 慢球 / 静止 / 缓速来球：留约 1.5 个挡板半径余量，覆盖门柱附近防死锁；
            // 更远的顶库慢球不触发，保持原有跟球逻辑（否则整条顶库都会被横向扫球接管）。
            return dx <= goalHalf + puck.r + paddleR * 1.5f
        }
        // 正在外出的球：只有还在门洞横向范围内才需要防「顶回门线」
        return dx <= goalHalf + puck.r
    }

    /**
     * 门线附近解围（球不朝自家门快速飞来时）：AI 站到球的台面内侧（y 更大的安全侧），
     * 沿 x 横向把球扫向远离门洞中心的一侧，而不是绕到球上方推击（门线附近不可达）。
     *
     * 接触法线保证不指向自家球门：
     * - 球在 AI 上方（y 更小）前只做水平位移或向下退让，绝不从下往上撞球；
     * - 扫球阶段目标点略高于球心，接触只会把球推向侧方 / 台面内侧。
     */
    private fun aiGoalLineSweep(
        table: Table,
        puck: Body,
        ai: Body,
        paddleR: Float,
        minX: Float,
        maxX: Float,
        midY: Float,
    ): Triple<Float, Float, Boolean> {
        val d = paddleR + puck.r
        val maxY = midY - paddleR * 0.35f
        // 朝远离门洞中心的方向解围：球在左半侧往左扫，右半侧往右扫
        val pushDir = if (puck.x <= table.midX) -1f else 1f
        val standX = (puck.x - pushDir * d * 1.3f).coerceIn(minX, maxX)
        val standY = puck.y.coerceIn(table.top, maxY)
        val beside = kotlin.math.abs(ai.x - standX) <= d * 0.25f
        // 球下方先退到安全距离，保证横移不会从下往上蹭到球
        val safeBelowY = (puck.y + d * 1.05f).coerceAtMost(maxY)
        // 正在外出的球只卡住侧位、不主动扫：避免把它顶回门线，也避免给球注入额外能量
        val exiting = puck.vy > table.shortSide * 0.06f
        return when {
            // 不在侧位又离球太近（在球下方）：先下沉让开通道，避免横移时从下往上蹭球
            ai.y > puck.y && !beside && ai.y < safeBelowY -> Triple(ai.x, safeBelowY, true)
            ai.y > puck.y && !beside -> Triple(standX, ai.y, true)
            // 已到侧位：上移到球的水平线（x 已错开 d 以上，不会从下方撞球）
            ai.y > puck.y -> Triple(standX, standY, true)
            !beside -> Triple(standX, standY, true)
            exiting -> Triple(standX, standY, true)
            else ->
                Triple(
                    (puck.x + pushDir * d).coerceIn(minX, maxX),
                    (puck.y - paddleR * 0.15f).coerceIn(table.top, maxY),
                    true,
                )
        }
    }

    /**
     * 估算球沿当前速度飞到 [goalY] 时的 X（左右墙镜面折叠，不含挡板）。
     * 球不朝该方向飞时退回当前 X。
     */
    fun goalLineInterceptX(
        table: Table,
        puck: Body,
        goalY: Float,
    ): Float {
        if (kotlin.math.abs(puck.vy) < 1e-3f) return puck.x
        val tHit = (goalY - puck.y) / puck.vy
        if (tHit < 0f) return puck.x
        val rawX = puck.x + puck.vx * tHit
        val min = table.left + puck.r
        val max = table.right - puck.r
        return foldIntoRange(rawX, min, max)
    }

    /** 把坐标按 [min,max] 做镜面折叠（模拟侧墙反弹后的落点）。 */
    fun foldIntoRange(
        value: Float,
        min: Float,
        max: Float,
    ): Float {
        val span = max - min
        if (span <= 1e-4f) return min
        var v = (value - min) % (2f * span)
        if (v < 0f) v += 2f * span
        return if (v <= span) min + v else max - (v - span)
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

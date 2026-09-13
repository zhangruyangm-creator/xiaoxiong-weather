package com.xiaoxiong.weather.ui.game

import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/**
 * 台球物理内核：固定步长积分的刚体模拟。
 *
 * 每一步（[PoolConfig.SIM_DT] = 600Hz）：
 *  1. 台呢摩擦：区分「打滑」与「纯滚动」，把线速度/角速度耦合更新（含高低杆、侧旋衰减）；
 *  2. 连续碰撞检测（TOI）：在剩余时间内找最早碰撞（球-球 / 球-库），推进到接触点再解算冲量，
 *     循环直到耗尽剩余时间——高速开球不会穿球/穿库；
 *  3. 解算库边/球球冲量：法向弹性 + 切向摩擦（甩球 throw）+ 旋转传递；
 *  4. 袋口捕获与静止判定。
 *
 * 纯逻辑、无 Compose 依赖；测试直接构造 [PoolTable]/[PoolBall] 断言物理不变量。
 */
object PoolPhysics {
    /** 单步统计，便于测试与 AI 搜索。 */
    data class StepStats(
        var ballBallHits: Int = 0,
        var railHits: Int = 0,
        var potted: Int = 0,
        var iterations: Int = 0,
    )

    /** 一次碰撞的形态。 */
    private enum class HitType { BALL_BALL, RAIL }

    private data class Hit(
        val time: Float,
        val type: HitType,
        val a: Int,
        val b: Int,
        val nx: Float,
        val ny: Float,
    )

    // ---------------------------------------------------------------------
    // 对外接口
    // ---------------------------------------------------------------------

    /** 物理事件监听：供游戏层收集「首碰 / 碰库 / 落袋」等事实。 */
    interface Listener {
        fun onBallBall(
            a: PoolBall,
            b: PoolBall,
        ) {}

        fun onRail(ball: PoolBall) {}

        fun onPocket(ball: PoolBall) {}
    }

    /** 推进一个固定步长。返回本步事件统计。 */
    fun step(
        balls: List<PoolBall>,
        table: PoolTable,
        dt: Float = PoolConfig.SIM_DT,
        listener: Listener? = null,
    ): StepStats {
        val stats = StepStats()
        // 速度钳制：保证单步位移远小于半径，配合 TOI 双重防穿模
        for (ball in balls) {
            if (ball.onTable) clampSpeed(ball)
        }
        // 弧线（抬杆 + 侧旋）：横向加速度 + 衰减。腾空球不产生弧线。
        for (ball in balls) {
            if (ball.onTable && !ball.airborne && ball.curve != 0f) applyCurve(ball, dt)
        }
        // 台呢摩擦（打滑/滚动/自旋）：腾空球不受台呢摩擦
        for (ball in balls) {
            if (ball.onTable && !ball.airborne && ball.isMoving()) applyClothFriction(ball, dt)
        }
        // 竖直方向：跳球抛物线，落地清零（并给一个小的前滚修正）
        val airborneThisStep = BooleanArray(balls.size)
        for (i in balls.indices) {
            val ball = balls[i]
            if (!ball.onTable) continue
            val wasAirborne = ball.airborne || ball.vz != 0f
            airborneThisStep[i] = wasAirborne
            if (wasAirborne) integrateVertical(ball, dt)
        }

        var remaining = dt
        var iterations = 0
        while (remaining > 1e-7f && iterations < PoolConfig.MAX_TOI_ITERATIONS) {
            val hit = findEarliestHit(balls, table, remaining)
            if (hit == null) {
                advance(balls, remaining)
                remaining = 0f
            } else {
                advance(balls, hit.time)
                remaining -= hit.time
                when (hit.type) {
                    HitType.BALL_BALL -> {
                        resolveBallBall(balls[hit.a], balls[hit.b], hit.nx, hit.ny)
                        listener?.onBallBall(balls[hit.a], balls[hit.b])
                        stats.ballBallHits++
                    }
                    HitType.RAIL -> {
                        resolveRail(balls[hit.a], hit.nx, hit.ny)
                        listener?.onRail(balls[hit.a])
                        stats.railHits++
                    }
                }
            }
            iterations++
        }
        // 极端情况下（迭代耗尽）仍把剩余时间走完，避免速度被吞
        if (remaining > 0f) advance(balls, remaining)
        stats.iterations = iterations

        // 袋口捕获（腾空高于球半径时从袋口上方飞过，不判落袋）
        for (ball in balls) {
            if (ball.onTable && ball.z <= ball.radius && captureInPocket(ball, table)) {
                ball.potted = true
                ball.onTable = false
                ball.stop()
                stats.potted++
                listener?.onPocket(ball)
            }
        }
        // 跳球飞出球桌边界：按丢球处理（白球即 scratch），并回调 onPocket
        val jumpMargin = (balls.firstOrNull()?.radius ?: PoolConfig.BALL_RADIUS) * 1.5f
        for (i in balls.indices) {
            val ball = balls[i]
            if (!ball.onTable || !airborneThisStep[i]) continue
            if (ball.x < table.left - jumpMargin ||
                ball.x > table.right + jumpMargin ||
                ball.y < table.top - jumpMargin ||
                ball.y > table.bottom + jumpMargin
            ) {
                ball.potted = true
                ball.onTable = false
                ball.stop()
                stats.potted++
                listener?.onPocket(ball)
            }
        }
        // 安全网：极端角度从袋口缝隙逸出台面的球按落袋处理，避免在台外无限滑行
        val escapeMargin = (balls.firstOrNull()?.radius ?: PoolConfig.BALL_RADIUS) * 4f
        for (ball in balls) {
            if (!ball.onTable) continue
            if (ball.x < table.left - escapeMargin ||
                ball.x > table.right + escapeMargin ||
                ball.y < table.top - escapeMargin ||
                ball.y > table.bottom + escapeMargin
            ) {
                ball.potted = true
                ball.onTable = false
                ball.stop()
                // 与其它落袋路径一致：标记最近袋口并回调，供游戏层判定白球落袋/进球
                ball.pocketIndex = table.pockets.indices.minByOrNull { i ->
                    val p = table.pockets[i]
                    hypot(ball.x - p.x, ball.y - p.y)
                } ?: -1
                listener?.onPocket(ball)
                stats.potted++
            }
        }
        // 静止判定
        for (ball in balls) {
            if (ball.onTable && !ball.isMoving()) ball.stop()
        }
        return stats
    }

    /** 是否所有球都已静止（可进行下一杆）。 */
    fun settled(balls: List<PoolBall>): Boolean = balls.none { it.onTable && it.isMoving() }

    /**
     * 连续模拟直到所有球静止或超过 [maxSeconds]。
     * 供测试与 AI 快速推演使用；[onEvent] 可选地在每次落袋时回调。
     */
    fun simulateUntilSettled(
        balls: List<PoolBall>,
        table: PoolTable,
        maxSeconds: Float = 25f,
        onPocketed: ((PoolBall) -> Unit)? = null,
    ): StepStats {
        val total = StepStats()
        var elapsed = 0f
        val limit = (maxSeconds / PoolConfig.SIM_DT).toInt()
        var steps = 0
        while (elapsed < maxSeconds && steps < limit && !settled(balls)) {
            val s = step(balls, table)
            total.ballBallHits += s.ballBallHits
            total.railHits += s.railHits
            total.potted += s.potted
            total.iterations += s.iterations
            if (s.potted > 0 && onPocketed != null) {
                // 找出新落袋的球（本步内 potted=true 且 pocketAnim 尚未置位）
                for (b in balls) {
                    if (b.potted && b.pocketAnim == 0f) {
                        onPocketed(b)
                    }
                }
            }
            elapsed += PoolConfig.SIM_DT
            steps++
        }
        return total
    }

    // ---------------------------------------------------------------------
    // 台呢摩擦
    // ---------------------------------------------------------------------

    /**
     * 台呢摩擦：接触点速度 u = v + ω × (-r ẑ)。
     *  |u| > 阈值 → 打滑：摩擦冲量同时改变 v 与 ω；
     *  |u| ≈ 0 → 纯滚动：滚动阻力减速，并维持 ω 与 v 的滚动关系。
     */
    private fun applyClothFriction(
        ball: PoolBall,
        dt: Float,
    ) {
        val r = ball.radius
        // 顺逆毛：台呢沿 +Y 为顺毛，按运动方向与毛向夹角对阻力做各向异性。
        val vSpeed = ball.speed
        val nap =
            if (vSpeed > 1e-4f) {
                (1f - PoolConfig.NAP_ANISOTROPY * (ball.vy / vSpeed)).coerceAtLeast(0.5f)
            } else {
                1f
            }
        // 接触点相对速度 u = v + ω × (-r ẑ)
        val ux = ball.vx - r * ball.wy
        val uy = ball.vy + r * ball.wx
        val us = hypot(ux, uy)
        val eps = 1e-4f
        val m = PoolConfig.BALL_MASS
        if (us > eps) {
            // 库仑摩擦冲量：把接触点速度一次解到零（受 μ m g dt 上限约束），
            // 避免显式欧拉在阈值附近过冲抖动、导致滚动几乎不减速。
            val nx = ux / us
            val ny = uy / us
            val jReq = (2f / 7f) * m * us // 球体切向等效质量 2/7 m
            val jMax = PoolConfig.MU_SLIDE * m * PoolConfig.GRAVITY * dt * nap
            val j = min(jReq, jMax)
            val px = -j * nx
            val py = -j * ny
            ball.vx += px / m
            ball.vy += py / m
            val invI = 1f / (0.4f * m * r * r)
            ball.wx += (r * py) * invI
            ball.wy += (-r * px) * invI
        } else {
            // 纯滚动：滚动阻力减速，并维持滚动关系
            val v = hypot(ball.vx, ball.vy)
            if (v > eps) {
                val dec = PoolConfig.MU_ROLL * PoolConfig.GRAVITY * dt * nap
                val scale = max(0f, v - dec) / v
                ball.vx *= scale
                ball.vy *= scale
            }
            ball.wx = -ball.vy / r
            ball.wy = ball.vx / r
        }
        // 竖直自旋（侧旋/跟缩）衰减
        if (abs(ball.wz) > 1e-5f) {
            val dwz = (2.5f * PoolConfig.MU_SPIN * PoolConfig.GRAVITY / ball.radius) * dt
            ball.wz = if (ball.wz > 0f) max(0f, ball.wz - dwz) else min(0f, ball.wz + dwz)
        }
    }

    /** 跳球竖直积分：vz 受重力，z 落地清零并做小幅前滚修正。 */
    private fun integrateVertical(
        ball: PoolBall,
        dt: Float,
    ) {
        ball.vz -= PoolConfig.GRAVITY * dt
        ball.z += ball.vz * dt
        if (ball.z <= 0f) {
            ball.z = 0f
            if (ball.vz < 0f) ball.vz = 0f
            // 落地冲击：少量水平动能损耗（向前滚动修正）
            ball.vx *= PoolConfig.LANDING_KEEP
            ball.vy *= PoolConfig.LANDING_KEEP
        }
    }

    /**
     * 弧线：抬杆 + 侧旋使球走弧线。用横向加速度近似（垂直于速度方向），
     * 加速度随自旋衰减，表现为先弯后直的 masse/swerve 轨迹。
     */
    private fun applyCurve(
        ball: PoolBall,
        dt: Float,
    ) {
        val s = ball.speed
        if (s > 1e-3f) {
            val ux = ball.vx / s
            val uy = ball.vy / s
            // 左法向
            ball.vx += (-uy) * ball.curve * dt
            ball.vy += (ux) * ball.curve * dt
        }
        val decay = (1f - PoolConfig.CURVE_DECAY_RATE * dt).coerceIn(0f, 1f)
        ball.curve *= decay
        if (abs(ball.curve) < 1e-3f) ball.curve = 0f
    }

    private fun clampSpeed(ball: PoolBall) {
        val s = ball.speed
        if (s > PoolConfig.MAX_SPEED) {
            val k = PoolConfig.MAX_SPEED / s
            ball.vx *= k
            ball.vy *= k
        }
        val w = hypot(ball.wx, ball.wy)
        val wMax = PoolConfig.MAX_SPEED / ball.radius
        if (w > wMax) {
            val k = wMax / w
            ball.wx *= k
            ball.wy *= k
        }
    }

    // ---------------------------------------------------------------------
    // 积分
    // ---------------------------------------------------------------------

    private fun advance(
        balls: List<PoolBall>,
        dt: Float,
    ) {
        if (dt <= 0f) return
        for (ball in balls) {
            if (!ball.onTable) continue
            ball.x += ball.vx * dt
            ball.y += ball.vy * dt
            ball.integrateOrientation(dt)
        }
    }

    // ---------------------------------------------------------------------
    // 最早碰撞（TOI）
    // ---------------------------------------------------------------------

    private fun findEarliestHit(
        balls: List<PoolBall>,
        table: PoolTable,
        dt: Float,
    ): Hit? {
        var best: Hit? = null
        // 球-球：腾空高于球半径的球既不撞人也不被撞
        for (i in balls.indices) {
            val a = balls[i]
            if (!a.onTable || a.z > a.radius) continue
            for (j in i + 1 until balls.size) {
                val b = balls[j]
                if (!b.onTable || b.z > b.radius) continue
                val t = ballBallToi(a, b, dt) ?: continue
                if (best == null || t < best.time) {
                    val nx = b.x - a.x
                    val ny = b.y - a.y
                    val d = hypot(nx, ny).coerceAtLeast(1e-6f)
                    best = Hit(t, HitType.BALL_BALL, i, j, nx / d, ny / d)
                }
            }
        }
        // 球-库：腾空飞越时不与库边碰撞
        for (i in balls.indices) {
            val ball = balls[i]
            if (!ball.onTable || ball.z > ball.radius) continue
            for (rail in table.rails) {
                val hit = sweptCircleSegment(ball, rail, dt) ?: continue
                if (best == null || hit.first < best.time) {
                    best = Hit(hit.first, HitType.RAIL, i, -1, hit.second, hit.third)
                }
            }
        }
        // 忽略近似为 0 的接触（避免原地反复解算死循环）
        if (best != null && best.time < 1e-6f) return best
        return best
    }

    private fun ballBallToi(
        a: PoolBall,
        b: PoolBall,
        dt: Float,
    ): Float? {
        val px = b.x - a.x
        val py = b.y - a.y
        val vx = b.vx - a.vx
        val vy = b.vy - a.vy
        val r = a.radius + b.radius
        val c = px * px + py * py - r * r
        val a2 = vx * vx + vy * vy
        val b2 = 2f * (px * vx + py * vy)
        // 已重叠：只有真正相互接近时才按 0 时间碰撞解算；静止相切（如三角架摆放）
        // 不能返回 0，否则会占满 TOI 迭代、让高速球在兜底推进时穿库。
        if (c <= 0f) return if (b2 < -1e-6f) 0f else null
        if (a2 < 1e-9f) return null
        if (b2 >= 0f) return null // 正在远离
        val disc = b2 * b2 - 4f * a2 * c
        if (disc < 0f) return null
        val t = (-b2 - sqrt(disc)) / (2f * a2)
        return if (t in 0f..dt) t else null
    }

    /** 扫掠圆 vs 线段：返回 (time, normalX, normalY)，法线由线段指向球心。 */
    private fun sweptCircleSegment(
        ball: PoolBall,
        rail: PoolRail,
        dt: Float,
    ): Triple<Float, Float, Float>? {
        val ax = rail.x1
        val ay = rail.y1
        val dx = rail.x2 - rail.x1
        val dy = rail.y2 - rail.y1
        val len2 = dx * dx + dy * dy
        val r = ball.radius
        var bestT = Float.MAX_VALUE
        var bestNx = 0f
        var bestNy = 0f

        fun consider(
            t: Float,
            nx: Float,
            ny: Float,
        ) {
            if (t in 0f..dt && t < bestT) {
                bestT = t
                bestNx = nx
                bestNy = ny
            }
        }

        if (len2 > 1e-9f) {
            val len = sqrt(len2)
            // 线段法线（两侧）
            val n0x = -dy / len
            val n0y = dx / len
            val d0 = (ball.x - ax) * n0x + (ball.y - ay) * n0y
            val dvn = ball.vx * n0x + ball.vy * n0y
            // 与无限直线碰撞：|d0 + dvn t| = r
            if (dvn < -1e-6f && d0 > r) {
                // 从正侧接近：法线由线段指向球（+n0）
                val t = (r - d0) / dvn
                val qx = ball.x + ball.vx * t
                val qy = ball.y + ball.vy * t
                val u = ((qx - ax) * dx + (qy - ay) * dy) / len2
                if (u in 0f..1f) consider(t, n0x, n0y)
            } else if (dvn > 1e-6f && d0 < -r) {
                val t = (-r - d0) / dvn
                val qx = ball.x + ball.vx * t
                val qy = ball.y + ball.vy * t
                val u = ((qx - ax) * dx + (qy - ay) * dy) / len2
                if (u in 0f..1f) consider(t, -n0x, -n0y)
            } else if (abs(d0) <= r) {
                // 已接触：仅在仍朝线段方向运动时受理，避免静止贴库反复 0 时间碰撞
                val approaching = (d0 >= 0f && dvn < -1e-6f) || (d0 < 0f && dvn > 1e-6f)
                if (approaching) {
                    val u = ((ball.x - ax) * dx + (ball.y - ay) * dy) / len2
                    if (u in 0f..1f) {
                        val sign = if (d0 >= 0f) 1f else -1f
                        consider(0f, sign * n0x, sign * n0y)
                    }
                }
            }
        }
        // 端点（球心到端点的距离 = r）
        considerEndpoint(ball, ax, ay, dt)?.let { consider(it.first, it.second, it.third) }
        considerEndpoint(ball, rail.x2, rail.y2, dt)?.let { consider(it.first, it.second, it.third) }

        if (bestT == Float.MAX_VALUE) return null
        return Triple(bestT, bestNx, bestNy)
    }

    private fun considerEndpoint(
        ball: PoolBall,
        ex: Float,
        ey: Float,
        dt: Float,
    ): Triple<Float, Float, Float>? {
        val px = ball.x - ex
        val py = ball.y - ey
        val vx = ball.vx
        val vy = ball.vy
        val r = ball.radius
        val a = vx * vx + vy * vy
        if (a < 1e-9f) return null
        val b = 2f * (px * vx + py * vy)
        val c = px * px + py * py - r * r
        val disc = b * b - 4f * a * c
        if (disc < 0f) return null
        val t1 = (-b - sqrt(disc)) / (2f * a)
        val t2 = (-b + sqrt(disc)) / (2f * a)
        val t =
            if (t1 in 0f..dt) {
                t1
            } else if (t2 in 0f..dt) {
                t2
            } else {
                return null
            }
        val qx = ball.x + vx * t - ex
        val qy = ball.y + vy * t - ey
        val d = hypot(qx, qy).coerceAtLeast(1e-6f)
        return Triple(t, qx / d, qy / d)
    }

    // ---------------------------------------------------------------------
    // 冲量解算
    // ---------------------------------------------------------------------

    private fun resolveBallBall(
        a: PoolBall,
        b: PoolBall,
        fallbackNx: Float,
        fallbackNy: Float,
    ) {
        // 位置纠偏（数值误差导致的少量重叠）
        val dx = b.x - a.x
        val dy = b.y - a.y
        val dist = hypot(dx, dy)
        val target = a.radius + b.radius
        if (dist < target && dist > 1e-6f) {
            val push = (target - dist) * 0.5f
            a.x -= (dx / dist) * push
            a.y -= (dy / dist) * push
            b.x += (dx / dist) * push
            b.y += (dy / dist) * push
        }
        // 法线必须取「推进到接触时刻之后」的实际球心连线：hit.n 是 TOI 前的旧方向，
        // 切球时偏差可达数十度，会让目标球按错误方向分离（瞄准线对准却打不进）。
        val nx: Float
        val ny: Float
        if (dist > 1e-6f) {
            nx = dx / dist
            ny = dy / dist
        } else {
            nx = fallbackNx
            ny = fallbackNy
        }
        val vn = (b.vx - a.vx) * nx + (b.vy - a.vy) * ny
        if (vn >= 0f) return
        val ma = PoolConfig.BALL_MASS
        val mb = PoolConfig.BALL_MASS
        val j = -(1f + PoolConfig.BALL_RESTITUTION) * vn / (1f / ma + 1f / mb)
        a.vx -= (j / ma) * nx
        a.vy -= (j / ma) * ny
        b.vx += (j / mb) * nx
        b.vy += (j / mb) * ny

        // 切向摩擦：相对表面速度（含旋转）产生「甩球」与旋转传递
        val tx = -ny
        val ty = nx
        val surfaceA = surfaceVelocity(a, nx * a.radius, ny * a.radius)
        val surfaceB = surfaceVelocity(b, -nx * b.radius, -ny * b.radius)
        val relT = (surfaceB.first - surfaceA.first) * tx + (surfaceB.second - surfaceA.second) * ty
        val maxFriction = PoolConfig.BALL_FRICTION * abs(j)
        // 等质量双球切向等效质量 m/7（含球体转动惯量 2/5 m r²）
        val jt = (-relT * (PoolConfig.BALL_MASS / 7f)).coerceIn(-maxFriction, maxFriction)
        a.vx -= (jt / ma) * tx
        a.vy -= (jt / ma) * ty
        b.vx += (jt / mb) * tx
        b.vy += (jt / mb) * ty
        // 切向冲量对旋转的影响（近似：接触点力矩）
        // a 接触点在 +r·n、受切向冲量 -jt·t，b 接触点在 -r·n、受 +jt·t，
        // 两者 r_c × J 的 z 分量同为 -r·jt，因此都对 wz 施加同一冲量。
        applyTangentialSpin(a, jt)
        applyTangentialSpin(b, jt)
    }

    private fun resolveRail(
        ball: PoolBall,
        nx: Float,
        ny: Float,
    ) {
        // 位置纠偏：确保球在库内
        val vn = ball.vx * nx + ball.vy * ny
        if (vn < 0f) {
            val e = PoolConfig.cushionRestitution(abs(vn))
            ball.vx -= (1f + e) * vn * nx
            ball.vy -= (1f + e) * vn * ny
        }
        // 切向摩擦 + 侧旋（英式塞）对反弹角的影响
        val tx = -ny
        val ty = nx
        val surface = surfaceVelocity(ball, -nx * ball.radius, -ny * ball.radius)
        val vt = surface.first * tx + surface.second * ty
        val friction = PoolConfig.CUSHION_FRICTION * abs(vn) * 0.5f
        val dv = (-vt).coerceIn(-friction, friction)
        ball.vx += dv * tx
        ball.vy += dv * ty
        applyTangentialSpin(ball, PoolConfig.BALL_MASS * dv)
    }

    /** 球体上某接触点（相对球心偏移 ox/oy，z=0）的表面速度 = v + ω × offset。 */
    private fun surfaceVelocity(
        ball: PoolBall,
        ox: Float,
        oy: Float,
    ): Pair<Float, Float> {
        // ω × (ox,oy,0) = (wy*0 - wz*oy, wz*ox - wx*0) = (-wz*oy, wz*ox)
        val sx = ball.vx - ball.wz * oy
        val sy = ball.vy + ball.wz * ox
        return sx to sy
    }

    /**
     * 水平接触点的切向冲量只改变竖直自旋 wz：τ = r_c × J，其中 r_c = -r·n、切向 t = perp(n)，
     * 于是 r_c × (j·t) = -r·j·ẑ。之前误改 wx/wy，既衰减不了侧旋又会被台呢摩擦转成线速度。
     */
    private fun applyTangentialSpin(
        ball: PoolBall,
        impulse: Float,
    ) {
        val invI = 1f / (0.4f * ball.radius * ball.radius * PoolConfig.BALL_MASS)
        ball.wz += (-ball.radius * impulse) * invI
    }

    // ---------------------------------------------------------------------
    // 袋口
    // ---------------------------------------------------------------------

    private fun captureInPocket(
        ball: PoolBall,
        table: PoolTable,
    ): Boolean {
        for (pocket in table.pockets) {
            val d = hypot(ball.x - pocket.x, ball.y - pocket.y)
            // 角袋捕获半径贴近绘制洞口；中袋同样按洞口取，
            // 让「瞄准线指向洞内」与「实际落袋」一致（贴库滚过洞口会被自然捕获）。
            if (d <= pocketCaptureRadius(pocket, ball.radius)) {
                ball.pocketIndex = table.pockets.indexOf(pocket)
                return true
            }
        }
        return false
    }

    /** 袋口捕获半径（物理与瞄准预测共用，保证辅助线「画到袋里」=「真能进」）。 */
    fun pocketCaptureRadius(
        pocket: PoolPocket,
        ballRadius: Float,
    ): Float =
        if (pocket.corner) {
            pocket.radius - ballRadius * 0.05f
        } else {
            pocket.radius * 0.85f
        }

    // ---------------------------------------------------------------------
    // 供渲染/AI 的预测
    // ---------------------------------------------------------------------

    /**
     * 预测白球沿 [angle] 以 [power] 击打后，最先碰到的球（用于瞄准辅助）。
     * 返回 (目标球索引, 碰撞点 x, 碰撞点 y, 目标球到碰撞点的距离) 或 null。
     */
    fun predictFirstContact(
        balls: List<PoolBall>,
        table: PoolTable,
        cue: PoolBall,
        angle: Float,
        maxDistance: Float = PoolConfig.PLAY_HEIGHT * 2f,
    ): FirstContact? {
        val dx = kotlin.math.cos(angle)
        val dy = kotlin.math.sin(angle)
        var bestT = maxDistance
        var target: PoolBall? = null
        for (ball in balls) {
            if (ball === cue || !ball.onTable) continue
            val px = ball.x - cue.x
            val py = ball.y - cue.y
            val proj = px * dx + py * dy
            if (proj <= 0f) continue
            val perp = abs(px * dy - py * dx)
            val r = cue.radius + ball.radius
            if (perp > r) continue
            val t = proj - sqrt(max(0f, r * r - perp * perp))
            if (t in 0f..bestT) {
                bestT = t
                target = ball
            }
        }
        // 库边遮挡：白球到目标之间是否被库挡住（简单取最短距离，超过则丢弃）
        val railT = raycastRail(cue.x, cue.y, dx, dy, table, bestT)
        if (railT != null && target != null && railT < bestT) return null
        val t = target?.let { bestT } ?: bestT
        val cx = cue.x + dx * t
        val cy = cue.y + dy * t
        return FirstContact(target, cx, cy, t)
    }

    data class FirstContact(
        val target: PoolBall?,
        val x: Float,
        val y: Float,
        val distance: Float,
    )

    /**
     * 目标球沿 [dirX]/[dirY] 出球后的路径预测：返回最早遇到的袋口、其它球或库边。
     * 袋口判定与 [pocketCaptureRadius] 完全一致，辅助线端点即真实停点。
     */
    fun predictObjectPath(
        balls: List<PoolBall>,
        table: PoolTable,
        originX: Float,
        originY: Float,
        dirX: Float,
        dirY: Float,
        radius: Float,
        exclude: PoolBall?,
        maxDistance: Float = PoolConfig.PLAY_HEIGHT * 2f,
    ): ObjectPath {
        var bestT = maxDistance
        var pocket: PoolPocket? = null
        var pocketIndex = -1
        var blocker: PoolBall? = null

        for ((index, p) in table.pockets.withIndex()) {
            val capture = pocketCaptureRadius(p, radius)
            val px = p.x - originX
            val py = p.y - originY
            val proj = px * dirX + py * dirY
            if (proj < 0f) continue
            val perp = abs(px * dirY - py * dirX)
            if (perp > capture) continue
            val t = proj - sqrt(max(0f, capture * capture - perp * perp))
            if (t in 0f..bestT) {
                bestT = t
                pocket = p
                pocketIndex = index
                blocker = null
            }
        }

        for (b in balls) {
            if (b === exclude || !b.onTable) continue
            val px = b.x - originX
            val py = b.y - originY
            val proj = px * dirX + py * dirY
            if (proj <= 0f) continue
            val perp = abs(px * dirY - py * dirX)
            val r = radius + b.radius
            if (perp > r) continue
            val t = proj - sqrt(max(0f, r * r - perp * perp))
            if (t in 0f..bestT) {
                bestT = t
                pocket = null
                blocker = b
            }
        }

        val railT = raycastRail(originX, originY, dirX, dirY, table, maxDistance)
        val railDistance = railT?.let { (it - radius).coerceAtLeast(0f) }
        if (railDistance != null && railDistance < bestT) {
            bestT = railDistance
            pocket = null
            blocker = null
        }

        return ObjectPath(
            endX = originX + dirX * bestT,
            endY = originY + dirY * bestT,
            distance = bestT,
            pocket = pocket,
            pocketIndex = pocketIndex,
            blocker = blocker,
            railDistance = railDistance,
        )
    }

    data class ObjectPath(
        val endX: Float,
        val endY: Float,
        val distance: Float,
        val pocket: PoolPocket?,
        val pocketIndex: Int,
        val blocker: PoolBall?,
        val railDistance: Float?,
    )

    /** 射线与库边线段的最近交点距离（用于瞄准线被库挡住的判断），无交点返回 null。 */
    fun raycastRail(
        x: Float,
        y: Float,
        dx: Float,
        dy: Float,
        table: PoolTable,
        maxT: Float,
    ): Float? {
        var best: Float? = null
        for (rail in table.rails) {
            val t = raySegment(x, y, dx, dy, rail.x1, rail.y1, rail.x2, rail.y2)
            if (t != null && t in 0f..maxT) {
                if (best == null || t < best!!) best = t
            }
        }
        return best
    }

    private fun raySegment(
        x: Float,
        y: Float,
        dx: Float,
        dy: Float,
        ax: Float,
        ay: Float,
        bx: Float,
        by: Float,
    ): Float? {
        val ex = bx - ax
        val ey = by - ay
        val denom = dx * ey - dy * ex
        if (abs(denom) < 1e-9f) return null
        val t = ((ax - x) * ey - (ay - y) * ex) / denom
        val u = ((ax - x) * dy - (ay - y) * dx) / denom
        return if (t >= 0f && u in 0f..1f) t else null
    }

    /** 库边内侧法线（指向台面中心），供渲染/AI 使用。 */
    fun railInwardNormal(
        rail: PoolRail,
        table: PoolTable,
    ): Pair<Float, Float> {
        val dx = rail.x2 - rail.x1
        val dy = rail.y2 - rail.y1
        val len = hypot(dx, dy).coerceAtLeast(1e-6f)
        var nx = -dy / len
        var ny = dx / len
        val midX = (rail.x1 + rail.x2) * 0.5f
        val midY = (rail.y1 + rail.y2) * 0.5f
        if ((table.centerX - midX) * nx + (table.centerY - midY) * ny < 0f) {
            nx = -nx
            ny = -ny
        }
        return nx to ny
    }
}

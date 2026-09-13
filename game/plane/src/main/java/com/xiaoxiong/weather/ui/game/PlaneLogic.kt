package com.xiaoxiong.weather.ui.game

import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

/**
 * 确定性伪随机数发生器（LCG）。
 *
 * 不依赖 wall clock / JVM Random：固定 seed 下序列完全一致，且 [state] 可直接落盘，
 * 快照恢复后从同一状态继续（子弹抖动、掉落、爆炸粒子都据此生成）。
 */
class PlaneRng(
    seed: Long = 1L,
) {
    var state: Long = seed xor SEED_MIX
        private set

    fun nextLong(): Long {
        state = state * MULTIPLIER + INCREMENT
        return state
    }

    /** 无偏 [0, bound) 整数：拒绝采样剔除取模偏斜；[bound] 必须为正。 */
    fun nextInt(bound: Int): Int {
        require(bound > 0) { "bound must be positive" }
        val range = (Long.MAX_VALUE / bound) * bound
        while (true) {
            val value = nextLong() ushr 1
            if (value < range) return (value % bound).toInt()
        }
    }

    /** [0, 1) 浮点（24 位尾数）。 */
    fun nextFloat(): Float = (nextLong() ushr 40) / 16777216f

    /** 快照恢复：直接写回状态。 */
    fun restore(newState: Long) {
        state = newState
    }

    private companion object {
        const val MULTIPLIER = 6364136223846793005L
        const val INCREMENT = 1442695040888963407L

        /** 0x9E3779B97F4A7C15 的无符号值（编译期常量，避免 ULong.toLong() 非法）。 */
        const val SEED_MIX = -7046029254386353131L
    }
}

/** 一发敌弹 / 玩家子弹的生成参数（由弹幕模式函数返回，游戏层据此建实体）。 */
data class PlaneBulletSpec(
    val x: Float,
    val y: Float,
    val vx: Float,
    val vy: Float,
    val shape: PlaneBulletShape = PlaneBulletShape.CIRCLE,
    val radius: Float = PlaneConfig.ENEMY_BULLET_RADIUS,
    val halfLength: Float = 0f,
    val angle: Float = 0f,
    val damage: Int = 1,
)

/** 武器枪口偏移与张角。 */
data class PlaneMuzzle(
    val offsetX: Float,
    val angleRad: Float,
)

/**
 * 小熊战机的纯逻辑内核：几何 / 碰撞 / 弹幕 / 武器 / 敌人行为。
 *
 * 所有函数不持有状态、不读时钟，输入相同输出必然相同，便于 JVM 单测与快照续玩。
 */
object PlaneLogic {
    /** 俯冲兵切换俯冲的延迟（tick）。 */
    const val DIVER_DIVE_AFTER_TICKS = 45

    /** 俯冲加速度（逻辑单位 / 秒²）。 */
    const val DIVER_ACCEL = 95f

    /** 俯冲最高速度相对基准速度的倍率。 */
    const val DIVER_MAX_SPEED_MUL = 3.2f

    /** 俯冲横向追踪的最大偏移（逻辑单位）。 */
    const val DIVER_TRACK_RANGE = 12f

    /** 摆渡角频率（弧度 / 秒）与振幅（逻辑单位）。 */
    const val WEAVE_OMEGA = 2.4f
    const val WEAVE_AMP = 11f

    /** Boss 停靠高度、横向扫射角频率与速度。 */
    const val BOSS_HOLD_Y = 22f
    const val BOSS_SWEEP_OMEGA = 1.1f
    const val BOSS_SWEEP_SPEED = 26f

    private val TAU = (2 * PI).toFloat()
    private val HALF_PI = (PI / 2).toFloat()

    // ---------------------------------------------------------------------
    // 碰撞几何
    // ---------------------------------------------------------------------

    /** 两圆相交判定（相切算命中）。 */
    fun circleHit(
        ax: Float,
        ay: Float,
        ar: Float,
        bx: Float,
        by: Float,
        br: Float,
    ): Boolean = hypot(ax - bx, ay - by) <= ar + br

    /** 点到线段的距离。 */
    fun pointSegmentDistance(
        px: Float,
        py: Float,
        x0: Float,
        y0: Float,
        x1: Float,
        y1: Float,
    ): Float {
        val dx = x1 - x0
        val dy = y1 - y0
        val lenSq = dx * dx + dy * dy
        if (lenSq < 1e-6f) return hypot(px - x0, py - y0)
        val t = (((px - x0) * dx + (py - y0) * dy) / lenSq).coerceIn(0f, 1f)
        return hypot(px - (x0 + dx * t), py - (y0 + dy * t))
    }

    /** 以 ([x], [y]) 为中心、沿 [angle] 方向、半长 [halfLength]、半径 [radius] 的胶囊与圆判定。 */
    fun capsuleCircleHit(
        cx: Float,
        cy: Float,
        cr: Float,
        x: Float,
        y: Float,
        angle: Float,
        halfLength: Float,
        radius: Float,
    ): Boolean {
        val dx = cos(angle) * halfLength
        val dy = sin(angle) * halfLength
        return pointSegmentDistance(cx, cy, x - dx, y - dy, x + dx, y + dy) <= cr + radius
    }

    /** 轴对齐矩形（中心 [rx]/[ry]，半宽 [halfW]，半高 [halfH]）与圆判定。 */
    fun rectCircleHit(
        cx: Float,
        cy: Float,
        cr: Float,
        rx: Float,
        ry: Float,
        halfW: Float,
        halfH: Float,
    ): Boolean {
        val nx = cx.coerceIn(rx - halfW, rx + halfW)
        val ny = cy.coerceIn(ry - halfH, ry + halfH)
        return hypot(cx - nx, cy - ny) <= cr
    }

    /**
     * 椭圆（中心 [ex]/[ey]，半轴 [semiMajor] × [semiMinor]）与圆判定。
     * 用「半轴外扩圆半径」近似 Minkowski 和：确定性强、无开方，足够弹幕判定使用。
     */
    fun ellipseCircleHit(
        cx: Float,
        cy: Float,
        cr: Float,
        ex: Float,
        ey: Float,
        semiMajor: Float,
        semiMinor: Float,
    ): Boolean {
        if (semiMajor <= 0f || semiMinor <= 0f) return false
        val nx = (cx - ex) / (semiMajor + cr)
        val ny = (cy - ey) / (semiMinor + cr)
        return nx * nx + ny * ny <= 1f
    }

    /**
     * 子弹与圆的统一判定：按 [PlaneBullet.shape] 分派到圆 / 胶囊 / 矩形。
     * RECT 用 [PlaneBullet.radius] 作半宽、[PlaneBullet.halfLength] 作半高（轴对齐）。
     */
    fun bulletHitsCircle(
        bullet: PlaneBullet,
        cx: Float,
        cy: Float,
        cr: Float,
    ): Boolean =
        when (bullet.shape) {
            PlaneBulletShape.CIRCLE -> circleHit(bullet.x, bullet.y, bullet.radius, cx, cy, cr)
            PlaneBulletShape.CAPSULE ->
                capsuleCircleHit(
                    cx = cx,
                    cy = cy,
                    cr = cr,
                    x = bullet.x,
                    y = bullet.y,
                    angle = bullet.angle,
                    halfLength = bullet.halfLength,
                    radius = bullet.radius,
                )
            PlaneBulletShape.RECT ->
                rectCircleHit(
                    cx = cx,
                    cy = cy,
                    cr = cr,
                    rx = bullet.x,
                    ry = bullet.y,
                    halfW = bullet.radius,
                    halfH = bullet.halfLength.coerceAtLeast(bullet.radius),
                )
        }

    // ---------------------------------------------------------------------
    // 武器 / 弹幕
    // ---------------------------------------------------------------------

    /** 按武器规格展开枪口：单发居中、双发平行、三向散射。 */
    fun muzzleSpecs(spec: PlaneWeaponSpec): List<PlaneMuzzle> =
        when {
            spec.count <= 1 -> listOf(PlaneMuzzle(0f, 0f))
            spec.spreadRad > 0f ->
                List(spec.count) { i -> PlaneMuzzle(0f, (i - (spec.count - 1) / 2f) * spec.spreadRad) }
            else ->
                List(spec.count) { i -> PlaneMuzzle((i - (spec.count - 1) / 2f) * spec.offsetX, 0f) }
        }

    /** 瞄准扇形：以目标方向为中轴、[count] 发等角散布。 */
    fun aimedFan(
        x: Float,
        y: Float,
        targetX: Float,
        targetY: Float,
        count: Int,
        spreadRad: Float,
        speed: Float,
        radius: Float = PlaneConfig.ENEMY_BULLET_RADIUS,
    ): List<PlaneBulletSpec> {
        val base = atan2(targetY - y, targetX - x)
        return List(count.coerceAtLeast(1)) { i ->
            val offset = if (count <= 1) 0f else (i - (count - 1) / 2f) * spreadRad
            val a = base + offset
            PlaneBulletSpec(
                x = x,
                y = y,
                vx = cos(a) * speed,
                vy = sin(a) * speed,
                radius = radius,
                angle = a,
            )
        }
    }

    /** 环形弹幕：[count] 发均匀分布，[phaseRad] 控制整体旋转。 */
    fun ring(
        x: Float,
        y: Float,
        count: Int,
        speed: Float,
        phaseRad: Float,
        radius: Float = PlaneConfig.ENEMY_BULLET_RADIUS,
    ): List<PlaneBulletSpec> =
        List(count.coerceAtLeast(1)) { i ->
            val a = phaseRad + i * (TAU / count.coerceAtLeast(1))
            PlaneBulletSpec(
                x = x,
                y = y,
                vx = cos(a) * speed,
                vy = sin(a) * speed,
                radius = radius,
                angle = a,
            )
        }

    /**
     * 敌人本次开火的弹幕。Boss 按 [PlaneEnemy.bossPhase] 切换：
     * 0 追踪扇形 / 1 扇形 + 环形 / 2 旋转环 + 密集扇形。
     */
    fun enemyBulletSpecs(
        enemy: PlaneEnemy,
        playerX: Float,
        playerY: Float,
        difficulty: PlaneDifficulty,
        spinPhaseRad: Float,
    ): List<PlaneBulletSpec> {
        if (enemy.type.baseFireIntervalTicks <= 0) return emptyList()
        val speed = enemy.type.baseBulletSpeed * difficulty.bulletSpeedMul
        return when (enemy.type) {
            PlaneEnemyType.TRANSPORT -> emptyList()
            PlaneEnemyType.BOSS -> bossBulletSpecs(enemy, playerX, playerY, speed, spinPhaseRad)
            PlaneEnemyType.GRUNT, PlaneEnemyType.DIVER ->
                aimedFan(enemy.x, enemy.y, playerX, playerY, count = 1, spreadRad = 0f, speed = speed)
            PlaneEnemyType.WEAVER ->
                aimedFan(enemy.x, enemy.y, playerX, playerY, count = 2, spreadRad = 0.20f, speed = speed)
            PlaneEnemyType.TURRET ->
                aimedFan(enemy.x, enemy.y, playerX, playerY, count = 2, spreadRad = 0.26f, speed = speed) +
                    turretBarSpec(enemy.x, enemy.y, speed)
        }
    }

    /**
     * 炮台长条弹：竖直 RECT 慢速下压封锁走位。
     * 半高不小于半宽，与 `bulletHitsCircle` / 渲染层的 RECT 约定保持一致。
     */
    fun turretBarSpec(
        x: Float,
        y: Float,
        speed: Float,
    ): PlaneBulletSpec =
        PlaneBulletSpec(
            x = x,
            y = y,
            vx = 0f,
            vy = speed * PlaneConfig.TURRET_BAR_SPEED_MUL,
            shape = PlaneBulletShape.RECT,
            radius = PlaneConfig.TURRET_BAR_HALF_WIDTH,
            halfLength = PlaneConfig.TURRET_BAR_HALF_HEIGHT,
            angle = HALF_PI,
        )

    /**
     * 擦弹判定：普通弹按弹体形状外扩圆半径，长条（RECT）弹沿弹体呈椭圆包络，
     * 使贴长条弹侧边 / 纵长方向飞过的擦弹反馈与弹型一致。
     */
    fun enemyBulletGrazes(
        bullet: PlaneBullet,
        cx: Float,
        cy: Float,
        cr: Float,
    ): Boolean =
        when (bullet.shape) {
            PlaneBulletShape.RECT ->
                ellipseCircleHit(
                    cx = cx,
                    cy = cy,
                    cr = cr,
                    ex = bullet.x,
                    ey = bullet.y,
                    semiMajor = bullet.radius,
                    semiMinor = bullet.halfLength.coerceAtLeast(bullet.radius),
                )
            else -> bulletHitsCircle(bullet, cx, cy, cr)
        }

    /** Boss 三阶段弹幕（数量确定，便于单测）。 */
    fun bossBulletSpecs(
        boss: PlaneEnemy,
        playerX: Float,
        playerY: Float,
        speed: Float,
        spinPhaseRad: Float,
    ): List<PlaneBulletSpec> =
        when (boss.bossPhase) {
            0 ->
                aimedFan(
                    boss.x,
                    boss.y,
                    playerX,
                    playerY,
                    count = 3,
                    spreadRad = 0.20f,
                    speed = speed,
                    radius = PlaneConfig.BOSS_BULLET_RADIUS,
                )
            1 ->
                aimedFan(
                    boss.x,
                    boss.y,
                    playerX,
                    playerY,
                    count = 4,
                    spreadRad = 0.32f,
                    speed = speed,
                    radius = PlaneConfig.BOSS_BULLET_RADIUS,
                ) +
                    ring(
                        boss.x,
                        boss.y,
                        count = 8,
                        speed = speed * 0.9f,
                        phaseRad = spinPhaseRad,
                        radius = PlaneConfig.BOSS_BULLET_RADIUS,
                    )
            else ->
                ring(
                    boss.x,
                    boss.y,
                    count = 11,
                    speed = speed * 0.95f,
                    phaseRad = spinPhaseRad * 1.6f,
                    radius = PlaneConfig.BOSS_BULLET_RADIUS,
                ) +
                    aimedFan(
                        boss.x,
                        boss.y,
                        playerX,
                        playerY,
                        count = 3,
                        spreadRad = 0.24f,
                        speed = speed * 1.05f,
                        radius = PlaneConfig.BOSS_BULLET_RADIUS,
                    )
        }

    // ---------------------------------------------------------------------
    // 敌人行为
    // ---------------------------------------------------------------------

    /**
     * 敌人当前是否允许开火（还须配合 [PlaneEnemy.fireTimerTicks] 计时）：
     * - 小兵按 id 奇偶交替开火，控制弹幕总量；
     * - 俯冲兵俯冲后才开火；炮台停靠后才开火。
     */
    fun enemyWantsFire(
        enemy: PlaneEnemy,
        fieldH: Float,
    ): Boolean {
        if (enemy.type.baseFireIntervalTicks <= 0) return false
        if (enemy.y < 4f || enemy.y > fieldH * 0.85f) return false
        if (enemy.x < 2f || enemy.x > PLANE_LOGICAL_WIDTH - 2f) return false
        return when (enemy.type) {
            PlaneEnemyType.GRUNT -> enemy.id % 2 == 0
            PlaneEnemyType.DIVER -> enemy.ageTicks >= DIVER_DIVE_AFTER_TICKS
            PlaneEnemyType.TURRET -> enemy.y >= enemy.targetY - 1f
            else -> true
        }
    }

    /**
     * 敌人每 tick 的速度（逻辑单位 / 秒），由类型与 [PlaneEnemy.ageTicks] 决定：
     * - 侧向入场期间返回入场时已按难度缩放的速度，入场结束后转入常规行为；
     * - 摆渡兵按正弦轨迹摆动（用速度形式表达，便于积分）；
     * - 炮台下降到 [PlaneEnemy.targetY] 后停下；
     * - Boss 下降到停靠线后左右扫射。
     */
    fun enemyVelocity(
        enemy: PlaneEnemy,
        playerX: Float,
        fieldH: Float,
        speedMul: Float,
    ): Pair<Float, Float> {
        val base = enemy.type.baseSpeed * speedMul
        if (enemy.side != 0 && enemy.ageTicks < enemy.entryTicks) {
            return enemy.vx to enemy.vy
        }
        return when (enemy.type) {
            PlaneEnemyType.GRUNT, PlaneEnemyType.TRANSPORT -> 0f to base
            PlaneEnemyType.DIVER -> {
                if (enemy.ageTicks < DIVER_DIVE_AFTER_TICKS) {
                    0f to base
                } else {
                    val t = (enemy.ageTicks - DIVER_DIVE_AFTER_TICKS) / 60f
                    val vy = (base + DIVER_ACCEL * t).coerceAtMost(base * DIVER_MAX_SPEED_MUL)
                    val track = ((playerX - enemy.x).coerceIn(-DIVER_TRACK_RANGE, DIVER_TRACK_RANGE) / DIVER_TRACK_RANGE)
                    track * base * 0.9f to vy
                }
            }
            PlaneEnemyType.WEAVER ->
                (WEAVE_AMP * WEAVE_OMEGA * cos(enemy.ageTicks * WEAVE_OMEGA / 60f)) to base * 0.75f
            PlaneEnemyType.TURRET -> if (enemy.y < enemy.targetY - 0.5f) 0f to base else 0f to 0f
            PlaneEnemyType.BOSS -> {
                val vy = if (enemy.y < BOSS_HOLD_Y) base else 0f
                (sin(enemy.ageTicks * BOSS_SWEEP_OMEGA / 60f) * BOSS_SWEEP_SPEED) to vy
            }
        }
    }
}

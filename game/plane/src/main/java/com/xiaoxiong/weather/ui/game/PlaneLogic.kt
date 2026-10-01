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

    /** 直升机悬停高度、摆动角频率 / 振幅与垂直浮动幅度。 */
    const val HELI_HOVER_Y = 36f
    const val HELI_SWAY_OMEGA = 1.5f
    const val HELI_SWAY_AMP = 15f
    const val HELI_BOB_AMP = 3.5f

    /** 直升机悬停持续时间（tick）：超时后加速脱离，避免不推进的敌人让波次永久卡住。 */
    const val HELI_HOLD_TICKS = 480

    /** 直升机脱离时的竖直速度倍率。 */
    const val HELI_EXIT_SPEED_MUL = 1.35f

    /** 装甲车地面线距场底距离、瞄准炮弹半径与直射炮弹参数。 */
    const val TANK_GROUND_MARGIN = 8f
    const val TANK_SHELL_RADIUS = 2.4f
    const val TANK_MORTAR_RADIUS = 1.7f
    const val TANK_MORTAR_HALF_LENGTH = 4.5f
    const val TANK_MORTAR_SPEED_MUL = 0.8f

    /** 直升机向下扇形射击的张角（弧度）。 */
    const val HELI_DOWN_FAN_SPREAD = 0.24f

    /** 侦察机之字形：每段时长（tick）与横向速度相对基准速度的倍率。 */
    const val SCOUT_ZIG_TICKS = 28
    const val SCOUT_ZIG_SPEED_MUL = 1.15f

    /** 轰炸机三连落弹的横向间距（逻辑单位）与相对弹速。 */
    const val BOMBER_BOMB_SPACING = 4.2f
    const val BOMBER_BOMB_SPEED_MUL = 0.72f

    /** 狙击机停驻开火窗口（从出生计时）；超时后下行脱离，避免卡波。 */
    const val SNIPER_EXIT_AFTER_TICKS = 360

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

    /** 向下扇形：以正下方为中轴、[count] 发等角散布（直升机专用）。 */
    fun downFan(
        x: Float,
        y: Float,
        count: Int,
        spreadRad: Float,
        speed: Float,
        radius: Float = PlaneConfig.ENEMY_BULLET_RADIUS,
    ): List<PlaneBulletSpec> =
        List(count.coerceAtLeast(1)) { i ->
            val offset = if (count <= 1) 0f else (i - (count - 1) / 2f) * spreadRad
            val a = HALF_PI + offset
            PlaneBulletSpec(
                x = x,
                y = y,
                vx = cos(a) * speed,
                vy = sin(a) * speed,
                radius = radius,
                angle = a,
            )
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
     * 直升机固定向下扇形；装甲车发射瞄准炮弹 + 直射跟踪弹；
     * 侦察机单发瞄准；轰炸机三连垂直落弹；狙击机高速单发瞄准。
     * [waveIndex] 参与弹速的波次成长（与 [PlaneConfig.enemySpec] 口径一致）。
     */
    fun enemyBulletSpecs(
        enemy: PlaneEnemy,
        playerX: Float,
        playerY: Float,
        difficulty: PlaneDifficulty,
        spinPhaseRad: Float,
        waveIndex: Int = 1,
    ): List<PlaneBulletSpec> {
        if (enemy.type.baseFireIntervalTicks <= 0) return emptyList()
        val speed =
            enemy.type.baseBulletSpeed *
                difficulty.bulletSpeedMul *
                PlaneConfig.waveBulletSpeedMul(waveIndex)
        return when (enemy.type) {
            PlaneEnemyType.TRANSPORT -> emptyList()
            PlaneEnemyType.BOSS -> bossBulletSpecs(enemy, playerX, playerY, speed, spinPhaseRad)
            PlaneEnemyType.GRUNT, PlaneEnemyType.DIVER, PlaneEnemyType.SCOUT, PlaneEnemyType.SNIPER ->
                aimedFan(enemy.x, enemy.y, playerX, playerY, count = 1, spreadRad = 0f, speed = speed)
            PlaneEnemyType.WEAVER ->
                aimedFan(enemy.x, enemy.y, playerX, playerY, count = 2, spreadRad = 0.20f, speed = speed)
            PlaneEnemyType.HELI ->
                downFan(
                    enemy.x,
                    enemy.y,
                    count = 3,
                    spreadRad = HELI_DOWN_FAN_SPREAD,
                    speed = speed,
                )
            PlaneEnemyType.BOMBER -> bomberBombSpecs(enemy.x, enemy.y, speed)
            PlaneEnemyType.TANK -> tankBulletSpecs(enemy, playerX, playerY, speed)
            PlaneEnemyType.TURRET ->
                aimedFan(enemy.x, enemy.y, playerX, playerY, count = 2, spreadRad = 0.26f, speed = speed) +
                    turretBarSpec(enemy.x, enemy.y, speed)
        }
    }

    /** 轰炸机落弹：机身中线两侧各一发 + 正中一发，全部竖直下落封锁走位。 */
    fun bomberBombSpecs(
        x: Float,
        y: Float,
        speed: Float,
    ): List<PlaneBulletSpec> {
        val vy = speed * BOMBER_BOMB_SPEED_MUL
        return listOf(-BOMBER_BOMB_SPACING, 0f, BOMBER_BOMB_SPACING).map { dx ->
            PlaneBulletSpec(
                x = x + dx,
                y = y,
                vx = 0f,
                vy = vy,
                radius = PlaneConfig.ENEMY_BULLET_RADIUS * 1.15f,
                angle = HALF_PI,
            )
        }
    }

    /**
     * 装甲车弹幕：一发瞄准玩家的炮弹（大圆，跟随玩家位置）+ 一发垂直上射的慢速跟踪弹
     * （CAPSULE 形状、沿弹体方向判定），逼迫玩家横向走位。
     */
    fun tankBulletSpecs(
        enemy: PlaneEnemy,
        playerX: Float,
        playerY: Float,
        speed: Float,
    ): List<PlaneBulletSpec> =
        aimedFan(
            enemy.x,
            enemy.y,
            playerX,
            playerY,
            count = 1,
            spreadRad = 0f,
            speed = speed,
            radius = TANK_SHELL_RADIUS,
        ) +
            listOf(
                PlaneBulletSpec(
                    x = enemy.x,
                    y = enemy.y,
                    vx = 0f,
                    vy = -speed * TANK_MORTAR_SPEED_MUL,
                    shape = PlaneBulletShape.CAPSULE,
                    radius = TANK_MORTAR_RADIUS,
                    halfLength = TANK_MORTAR_HALF_LENGTH,
                    angle = -HALF_PI,
                ),
            )

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

    /** Boss 三阶段弹幕（按 [PlaneEnemy.bossKind] 分支，数量确定便于单测）。 */
    fun bossBulletSpecs(
        boss: PlaneEnemy,
        playerX: Float,
        playerY: Float,
        speed: Float,
        spinPhaseRad: Float,
    ): List<PlaneBulletSpec> =
        when (boss.bossKind) {
            PlaneBossKind.STANDARD ->
                standardBossBulletSpecs(boss, playerX, playerY, speed, spinPhaseRad)
            PlaneBossKind.RINGMASTER ->
                ringmasterBossBulletSpecs(boss, playerX, playerY, speed, spinPhaseRad)
            PlaneBossKind.STRIKER ->
                strikerBossBulletSpecs(boss, playerX, playerY, speed, spinPhaseRad)
        }

    private fun standardBossBulletSpecs(
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

    /** 环幕主宰：密环封锁走位，瞄准弹更少。 */
    private fun ringmasterBossBulletSpecs(
        boss: PlaneEnemy,
        playerX: Float,
        playerY: Float,
        speed: Float,
        spinPhaseRad: Float,
    ): List<PlaneBulletSpec> =
        when (boss.bossPhase) {
            0 ->
                ring(
                    boss.x,
                    boss.y,
                    count = 8,
                    speed = speed * 0.88f,
                    phaseRad = spinPhaseRad,
                    radius = PlaneConfig.BOSS_BULLET_RADIUS,
                )
            1 ->
                ring(
                    boss.x,
                    boss.y,
                    count = 10,
                    speed = speed * 0.9f,
                    phaseRad = spinPhaseRad,
                    radius = PlaneConfig.BOSS_BULLET_RADIUS,
                ) +
                    ring(
                        boss.x,
                        boss.y,
                        count = 6,
                        speed = speed * 0.78f,
                        phaseRad = spinPhaseRad + 0.35f,
                        radius = PlaneConfig.BOSS_BULLET_RADIUS * 0.9f,
                    )
            else ->
                ring(
                    boss.x,
                    boss.y,
                    count = 14,
                    speed = speed * 0.92f,
                    phaseRad = spinPhaseRad * 1.4f,
                    radius = PlaneConfig.BOSS_BULLET_RADIUS,
                ) +
                    aimedFan(
                        boss.x,
                        boss.y,
                        playerX,
                        playerY,
                        count = 2,
                        spreadRad = 0.18f,
                        speed = speed,
                        radius = PlaneConfig.BOSS_BULLET_RADIUS,
                    )
        }

    /** 强袭战机：密瞄准扇形 + 阶段 1 两侧长条封锁。 */
    private fun strikerBossBulletSpecs(
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
                    count = 5,
                    spreadRad = 0.14f,
                    speed = speed * 1.08f,
                    radius = PlaneConfig.BOSS_BULLET_RADIUS,
                )
            1 ->
                aimedFan(
                    boss.x,
                    boss.y,
                    playerX,
                    playerY,
                    count = 3,
                    spreadRad = 0.22f,
                    speed = speed * 1.05f,
                    radius = PlaneConfig.BOSS_BULLET_RADIUS,
                ) +
                    listOf(
                        turretBarSpec(boss.x - 16f, boss.y, speed * 0.85f),
                        turretBarSpec(boss.x + 16f, boss.y, speed * 0.85f),
                    )
            else ->
                aimedFan(
                    boss.x,
                    boss.y,
                    playerX,
                    playerY,
                    count = 7,
                    spreadRad = 0.28f,
                    speed = speed * 1.1f,
                    radius = PlaneConfig.BOSS_BULLET_RADIUS,
                ) +
                    ring(
                        boss.x,
                        boss.y,
                        count = 6,
                        speed = speed * 0.9f,
                        phaseRad = spinPhaseRad,
                        radius = PlaneConfig.BOSS_BULLET_RADIUS,
                    )
        }

    // ---------------------------------------------------------------------
    // 敌人行为
    // ---------------------------------------------------------------------

    /**
     * 敌人当前是否允许开火（还须配合 [PlaneEnemy.fireTimerTicks] 计时）：
     * - 小兵按 id 奇偶交替开火，控制弹幕总量；
     * - 俯冲兵俯冲后才开火；炮台 / 狙击机停靠后才开火；直升机悬停后才开火；
     * - 狙击机脱离阶段不再开火；
     * - 装甲车贴地巡行时即可开火（地面单位不适用常规场高限制）。
     */
    fun enemyWantsFire(
        enemy: PlaneEnemy,
        fieldH: Float,
    ): Boolean {
        if (enemy.type.baseFireIntervalTicks <= 0) return false
        if (enemy.type == PlaneEnemyType.TANK) {
            // 贴地开火：只允许在地面线附近（未爬升到位或越出屏幕下方时都不开火，
            // 否则炮弹会从画面外冒出，玩家没有反应时间）。
            val groundY = fieldH - TANK_GROUND_MARGIN
            if (enemy.y < groundY - 1f || enemy.y > groundY + 1f) return false
        } else {
            if (enemy.y < 4f || enemy.y > fieldH * 0.85f) return false
        }
        if (enemy.x < 2f || enemy.x > PLANE_LOGICAL_WIDTH - 2f) return false
        return when (enemy.type) {
            PlaneEnemyType.GRUNT -> enemy.id % 2 == 0
            PlaneEnemyType.DIVER -> enemy.ageTicks >= DIVER_DIVE_AFTER_TICKS
            PlaneEnemyType.TURRET -> enemy.y >= enemy.targetY - 1f
            PlaneEnemyType.HELI -> enemy.y >= HELI_HOVER_Y - 1f
            PlaneEnemyType.SNIPER ->
                enemy.y >= enemy.targetY - 1f && enemy.ageTicks < SNIPER_EXIT_AFTER_TICKS
            else -> true
        }
    }

    /**
     * 敌人每 tick 的速度（逻辑单位 / 秒），由类型与 [PlaneEnemy.ageTicks] 决定：
     * - 侧向入场期间返回入场时已按难度缩放的速度，入场结束后转入常规行为；
     * - 摆渡兵 / 直升机按正弦轨迹摆动（用速度形式表达，便于积分）；
     * - 炮台下降到 [PlaneEnemy.targetY] 后停下；
     * - 装甲车从场底爬升到地面线后保持 [PlaneEnemy.vx] 横向巡行；
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
            PlaneEnemyType.GRUNT, PlaneEnemyType.TRANSPORT, PlaneEnemyType.BOMBER -> 0f to base
            PlaneEnemyType.SCOUT -> {
                val dir = if ((enemy.ageTicks / SCOUT_ZIG_TICKS) % 2 == 0) 1f else -1f
                dir * base * SCOUT_ZIG_SPEED_MUL to base
            }
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
            PlaneEnemyType.SNIPER ->
                when {
                    enemy.y < enemy.targetY - 0.5f -> 0f to base
                    enemy.ageTicks >= SNIPER_EXIT_AFTER_TICKS -> 0f to base * 1.4f
                    else -> 0f to 0f
                }
            PlaneEnemyType.HELI -> {
                if (enemy.y < HELI_HOVER_Y) {
                    0f to base
                } else {
                    val t = enemy.ageTicks / 60f
                    val sway = HELI_SWAY_AMP * HELI_SWAY_OMEGA * cos(t * HELI_SWAY_OMEGA)
                    val vy =
                        if (enemy.ageTicks >= HELI_HOLD_TICKS) {
                            base * HELI_EXIT_SPEED_MUL
                        } else {
                            HELI_BOB_AMP * sin(t * 1.3f)
                        }
                    sway to vy
                }
            }
            PlaneEnemyType.TANK -> {
                // 地面线两侧都朝线收敛：从下方爬升进场；resize 后场高变大时也会重新下降到新地面线，
                // 不会悬停在旧地面线上。±0.5 缓冲避免在线上低频振荡。
                val groundY = fieldH - TANK_GROUND_MARGIN
                val vy =
                    when {
                        enemy.y > groundY + 0.5f -> -base
                        enemy.y < groundY - 0.5f -> base
                        else -> 0f
                    }
                enemy.vx to vy
            }
            PlaneEnemyType.BOSS -> {
                val holdY =
                    when (enemy.bossKind) {
                        PlaneBossKind.RINGMASTER -> 26f
                        PlaneBossKind.STRIKER -> 18f
                        PlaneBossKind.STANDARD -> BOSS_HOLD_Y
                    }
                val omega =
                    when (enemy.bossKind) {
                        PlaneBossKind.STRIKER -> 1.6f
                        PlaneBossKind.RINGMASTER -> 0.7f
                        PlaneBossKind.STANDARD -> BOSS_SWEEP_OMEGA
                    }
                val sweep =
                    when (enemy.bossKind) {
                        PlaneBossKind.STRIKER -> 36f
                        PlaneBossKind.RINGMASTER -> 16f
                        PlaneBossKind.STANDARD -> BOSS_SWEEP_SPEED
                    }
                val vy = if (enemy.y < holdY) base else 0f
                // cos（而非 sin）积分后才是围绕出生点左右对称的往返扫射：
                // sin 速度的积分是单侧振荡，Boss 永远不会扫到出生点左侧，左半场站桩玩家打不到 Boss。
                (cos(enemy.ageTicks * omega / 60f) * sweep) to vy
            }
        }
    }
}

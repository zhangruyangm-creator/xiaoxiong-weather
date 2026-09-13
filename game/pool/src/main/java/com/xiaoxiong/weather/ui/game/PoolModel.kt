package com.xiaoxiong.weather.ui.game

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * 小熊台球的物理常量与球桌几何模型（纯 Kotlin，无 Compose 依赖，可裸 JVM 单测）。
 *
 * 坐标系：俯视，X 向右、Y 向下，单位用「米」量级（9 尺台面 2.54m × 1.27m）。
 * 球桌长轴沿 Y（竖屏友好）：宽 [PLAY_WIDTH]、高 [PLAY_HEIGHT]，左上角为 (0,0)。
 */
object PoolConfig {
    /** 9 尺台面（1" = 25.4mm）：长边 2.54m，短边 1.27m。 */
    const val PLAY_WIDTH = 1.27f
    const val PLAY_HEIGHT = 2.54f

    /** 标准美式台球直径 57.15mm。 */
    const val BALL_RADIUS = 0.028575f
    const val BALL_MASS = 0.17f

    /** 重力加速度（m/s²）。 */
    const val GRAVITY = 9.80665f

    /** 滑动摩擦（球在台呢上打滑阶段）。 */
    const val MU_SLIDE = 0.20f

    /** 滚动阻力（纯滚动后的减速）。取值偏游戏手感：一杆中等力度约数秒停下。 */
    const val MU_ROLL = 0.05f

    /** 自旋（高低杆/侧旋）衰减系数。 */
    const val MU_SPIN = 0.022f

    /** 库边法向弹性与切向摩擦。高速来球弹性略降（能量损失更真实）。 */
    const val CUSHION_RESTITUTION = 0.78f
    const val CUSHION_RESTITUTION_FAST_LOSS = 0.12f
    const val CUSHION_FRICTION = 0.20f

    /** 弧线（swerve/masse）横向加速度衰减速率（每秒）。 */
    const val CURVE_DECAY_RATE = 3f

    /** 由来球法向速度得到的库边弹性。 */
    fun cushionRestitution(normalSpeed: Float): Float =
        (CUSHION_RESTITUTION - CUSHION_RESTITUTION_FAST_LOSS * (normalSpeed / 6f).coerceIn(0f, 1f))
            .coerceAtLeast(0.45f)

    /** 球-球法向弹性与切向摩擦（决定「甩球」throw）。 */
    const val BALL_RESTITUTION = 0.95f
    const val BALL_FRICTION = 0.06f

    /** 物理固定步长：600Hz，配合最大球速保证单步位移远小于球半径（不穿模）。 */
    const val SIM_DT = 1f / 600f

    /** 单步内最大碰撞求解迭代次数（多库反弹/多球连碰）。 */
    const val MAX_TOI_ITERATIONS = 32

    /** 速度上限（开球约 8–9 m/s），防止数值爆炸与穿模。 */
    const val MAX_SPEED = 12.0f

    /** 最小出杆速度（力度 0 时的白球初速度，m/s）。 */
    const val MIN_SHOT_SPEED = 0.7f

    /** 力度 0..1 映射到白球初速度（m/s）。真实出杆与 AI 推演共用，保证两者一致。 */
    fun shotSpeedMps(power: Float): Float = MIN_SHOT_SPEED + power.coerceIn(0f, 1f) * (MAX_SPEED - MIN_SHOT_SPEED)

    /** 判定静止：线速度与角速度都足够小。 */
    const val SLEEP_SPEED = 0.0055f
    const val SLEEP_SPIN = 0.06f

    /** 台边到袋口中心的横向缺口（决定袋口大小）：接近真实比例（角袋约 2.2 个球直径）。 */
    const val CORNER_MOUTH = BALL_RADIUS * 3.2f
    const val SIDE_MOUTH = BALL_RADIUS * 3.4f

    /** 袋口捕获半径（球心进入该范围即落袋，与绘制洞口同量级）。 */
    const val CORNER_POCKET_R = BALL_RADIUS * 2.9f
    const val SIDE_POCKET_R = BALL_RADIUS * 2.4f

    /** 角袋钳口：库端沿 45° 向袋内斜伸的倒角分量（收口后对角净宽仍 > 2×球半径）。 */
    const val JAW_CORNER_CHAMFER = 0.007f

    /** 中袋库端向袋内（台面外）延伸的漏斗进深。 */
    const val JAW_SIDE_DEPTH = 0.018f

    /** 中袋漏斗相对库端的收口量。 */
    const val JAW_SIDE_INSET = 0.002f

    /** 判定「腾空」的高度阈值（米）。 */
    const val AIRBORNE_EPS = 1e-4f

    /** 跳球起跳：仅当抬杆/力度同时达到阈值时给白球竖直初速度。 */
    const val JUMP_ELEVATION_MIN = 0.7f
    const val JUMP_POWER_MIN = 0.7f

    /** 起跳竖直速度系数：vz = GRAVITY * 该系数 * elevation * (0.5 + power)。 */
    const val JUMP_TAKEOFF = 0.16f

    /** 落地时保留的水平速度比例（落地冲击的小幅前滚修正）。 */
    const val LANDING_KEEP = 0.985f

    /**
     * 台呢顺逆毛（nap）各向异性幅度：0 = 各向同性。
     * 约定 +Y 为顺毛，顺毛方向滚动阻力略小（乘以 1 - NAP_ANISOTROPY），
     * 逆毛方向阻力略大（乘以 1 + NAP_ANISOTROPY）。
     */
    const val NAP_ANISOTROPY = 0.15f
}

/** 球类型：白球 / 全色 1–7 / 黑 8 / 花色 9–15。 */
enum class PoolBallKind { CUE, SOLID, EIGHT, STRIPE }

/**
 * 台球刚体：位置、线速度、角速度（ωx/ωy 为滚动轴，ωz 为竖直自旋/杆法），
 * 以及用于渲染的朝向四元数（球面滚动时数字/花色会随之转动）。
 */
class PoolBall(
    val id: Int,
    val kind: PoolBallKind,
    var x: Float,
    var y: Float,
    var vx: Float = 0f,
    var vy: Float = 0f,
    var wx: Float = 0f,
    var wy: Float = 0f,
    var wz: Float = 0f,
    val radius: Float = PoolConfig.BALL_RADIUS,
) {
    /** 朝向四元数（世界系，单位元 = 数字朝上）。 */
    var qw: Float = 1f
    var qx: Float = 0f
    var qy: Float = 0f
    var qz: Float = 0f

    /** 是否还在台面上（未落袋）。 */
    var onTable: Boolean = true

    /** 是否已落袋（本局不再参与物理）。 */
    var potted: Boolean = false

    /** 落袋动画进度 0..1（渲染用）。 */
    var pocketAnim: Float = 0f

    /** 落袋时进入的袋口索引（渲染用，-1 表示无）。 */
    var pocketIndex: Int = -1

    /**
     * 弧线（swerve/masse）横向加速度 m/s²：由抬杆 + 侧旋耦合产生，随时间衰减。
     * 正值表示朝速度方向左侧偏转。白球被击打后设置，其余球恒为 0。
     */
    var curve: Float = 0f

    /** 离台面高度（米）：0 = 贴台呢；跳球时 > 0。 */
    var z: Float = 0f

    /** 竖直速度（米/秒，向上为正）。 */
    var vz: Float = 0f

    /** 是否腾空（离地）。 */
    val airborne: Boolean get() = z > PoolConfig.AIRBORNE_EPS

    /** 球号：白球 0，其余 1..15。 */
    val number: Int
        get() =
            when (kind) {
                PoolBallKind.CUE -> 0
                PoolBallKind.SOLID, PoolBallKind.EIGHT, PoolBallKind.STRIPE -> id
            }

    val speed: Float get() = hypot(vx, vy)

    val spinMagnitude: Float get() = hypot(hypot(wx, wy), wz)

    fun isMoving(): Boolean =
        onTable &&
            (
                speed > PoolConfig.SLEEP_SPEED ||
                    spinMagnitude > PoolConfig.SLEEP_SPIN ||
                    airborne ||
                    abs(vz) > PoolConfig.SLEEP_SPEED
            )

    fun stop() {
        vx = 0f
        vy = 0f
        wx = 0f
        wy = 0f
        wz = 0f
        curve = 0f
        z = 0f
        vz = 0f
    }

    /** 以角速度向量（世界系 rad/s）积分朝向四元数，dt 秒。 */
    fun integrateOrientation(dt: Float) {
        val h = 0.5f * dt
        val q0w = qw
        val q0x = qx
        val q0y = qy
        val q0z = qz
        // dq = 0.5 * (0, wx, wy, wz) * q
        val dw = -wx * q0x - wy * q0y - wz * q0z
        val dx = wx * q0w + wy * q0z - wz * q0y
        val dy = wy * q0w + wz * q0x - wx * q0z
        val dz = wz * q0w + wx * q0y - wy * q0x
        qw = q0w + h * dw
        qx = q0x + h * dx
        qy = q0y + h * dy
        qz = q0z + h * dz
        normalizeQuaternion()
    }

    private fun normalizeQuaternion() {
        val len = sqrt(qw * qw + qx * qx + qy * qy + qz * qz)
        if (len < 1e-6f) {
            qw = 1f
            qx = 0f
            qy = 0f
            qz = 0f
            return
        }
        val inv = 1f / len
        qw *= inv
        qx *= inv
        qy *= inv
        qz *= inv
    }

    /**
     * 「数字面法线」在世界系的朝向（用于渲染数字/花色在球面上的位置）。
     * 数字位于球体局部 +Z 轴（竖直向上时为正面朝上）。
     */
    fun markingNormal(): FloatArray {
        // R * (0,0,1)：四元数旋转
        val nx = 2f * (qx * qz + qw * qy)
        val ny = 2f * (qy * qz - qw * qx)
        val nz = 1f - 2f * (qx * qx + qy * qy)
        return floatArrayOf(nx, ny, nz)
    }

    /** 数字在球面投影的滚动角（2D，用于旋转贴图）。 */
    fun markingAngle(): Float {
        val n = markingNormal()
        return atan2(n[1], n[0])
    }
}

/** 袋口：圆心 + 半径，[corner] 区分角袋/中袋（外观与捕获半径不同）。 */
data class PoolPocket(
    val x: Float,
    val y: Float,
    val radius: Float,
    val corner: Boolean,
)

/** 库边线段（轴对齐或斜向皆可），法线由碰撞时按球心方向决定。 */
data class PoolRail(
    val x1: Float,
    val y1: Float,
    val x2: Float,
    val y2: Float,
)

/**
 * 球桌：台呢矩形 + 6 个袋口 + 库边线段（避开袋口）。
 * 用 [PoolTable.standard] 生成标准几何。
 */
class PoolTable(
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float,
    val ballRadius: Float,
    val pockets: List<PoolPocket>,
    val rails: List<PoolRail>,
) {
    val width: Float get() = right - left
    val height: Float get() = bottom - top
    val centerX: Float get() = (left + right) * 0.5f
    val centerY: Float get() = (top + bottom) * 0.5f

    /** 开球线（1/4 长边处，白球初始区）。 */
    val headStringY: Float get() = top + height * 0.25f

    /** 置球点（3/4 长边处，黑八/三角架顶点）。 */
    val footSpotY: Float get() = top + height * 0.75f

    /** 判断坐标是否落在台呢内（含边界容差）。 */
    fun contains(
        x: Float,
        y: Float,
        margin: Float = 0f,
    ): Boolean = x >= left - margin && x <= right + margin && y >= top - margin && y <= bottom + margin

    companion object {
        /** 标准 9 尺台几何（长轴沿 Y）。 */
        fun standard(
            ballRadius: Float = PoolConfig.BALL_RADIUS,
            width: Float = PoolConfig.PLAY_WIDTH,
            height: Float = PoolConfig.PLAY_HEIGHT,
        ): PoolTable {
            val left = 0f
            val top = 0f
            val right = width
            val bottom = height
            val cornerR = PoolConfig.CORNER_POCKET_R
            val sideR = PoolConfig.SIDE_POCKET_R
            val cornerMouth = PoolConfig.CORNER_MOUTH
            val sideMouth = PoolConfig.SIDE_MOUTH

            val pockets =
                listOf(
                    PoolPocket(left, top, cornerR, corner = true),
                    PoolPocket(right, top, cornerR, corner = true),
                    PoolPocket(left, bottom, cornerR, corner = true),
                    PoolPocket(right, bottom, cornerR, corner = true),
                    // 中袋在长库（左右）中点，而不是短库：竖版长轴沿 Y 时，标准台的中袋在左右。
                    PoolPocket(left, (top + bottom) * 0.5f, sideR, corner = false),
                    PoolPocket(right, (top + bottom) * 0.5f, sideR, corner = false),
                )

            val cy = (top + bottom) * 0.5f
            val rails =
                buildList {
                    // 上下短库（y = top / bottom）：角袋之间整段
                    add(PoolRail(left + cornerMouth, top, right - cornerMouth, top))
                    add(PoolRail(left + cornerMouth, bottom, right - cornerMouth, bottom))
                    // 左右长库（x = left / right）：中袋处断开为两段
                    add(PoolRail(left, top + cornerMouth, left, cy - sideMouth * 0.5f))
                    add(PoolRail(left, cy + sideMouth * 0.5f, left, bottom - cornerMouth))
                    add(PoolRail(right, top + cornerMouth, right, cy - sideMouth * 0.5f))
                    add(PoolRail(right, cy + sideMouth * 0.5f, right, bottom - cornerMouth))
                    // 袋口钳口：让贴袋/擦袋有更真实的反弹（rattling），并收窄入口。
                    addAll(jaws(left, top, right, bottom, cornerMouth, sideMouth))
                }
            return PoolTable(left, top, right, bottom, ballRadius, pockets, rails)
        }

        /**
         * 6 个袋口各两段短斜边钳口：
         *  - 角袋：库端沿 45° 向台内侧倒角，收口后袋口对角线净宽仍 > 2×球半径；
         *  - 中袋：库端向袋内（台面外）延伸成漏斗，不侵入台面，避免贴库滚过时误碰。
         */
        private fun jaws(
            left: Float,
            top: Float,
            right: Float,
            bottom: Float,
            cornerMouth: Float,
            sideMouth: Float,
        ): List<PoolRail> {
            val c = PoolConfig.JAW_CORNER_CHAMFER
            val depth = PoolConfig.JAW_SIDE_DEPTH
            val sideFront = sideMouth * 0.5f - PoolConfig.JAW_SIDE_INSET
            val cy = (top + bottom) * 0.5f
            return buildList {
                // 四个角袋
                for (sx in listOf(1f, -1f)) {
                    for (sy in listOf(1f, -1f)) {
                        val cornerX = if (sx > 0f) left else right
                        val cornerY = if (sy > 0f) top else bottom
                        // 水平库端的钳口：向袋内（台面外）斜伸，作为逸出兜底
                        add(
                            PoolRail(
                                cornerX + sx * cornerMouth,
                                cornerY,
                                cornerX + sx * (cornerMouth - c),
                                cornerY - sy * c,
                            ),
                        )
                        // 竖直库端的钳口
                        add(
                            PoolRail(
                                cornerX,
                                cornerY + sy * cornerMouth,
                                cornerX - sx * c,
                                cornerY + sy * (cornerMouth - c),
                            ),
                        )
                    }
                }
                // 左右中袋的袋内漏斗（沿 ±X 向外）
                for (sx in listOf(1f, -1f)) {
                    val pocketX = if (sx > 0f) left else right
                    add(PoolRail(pocketX, cy - sideMouth * 0.5f, pocketX - sx * depth, cy - sideFront))
                    add(PoolRail(pocketX, cy + sideMouth * 0.5f, pocketX - sx * depth, cy + sideFront))
                }
            }
        }
    }
}

/**
 * 标准十五球三角架。返回的球按「架内序号」排列，白球由调用方单独放于开球线。
 *
 * 规则约束：8 号位于第三排正中；底排两角一全色一花色（传统摆法）。
 */
object PoolRack {
    /** 1..15 的摆位（相对置球点的 x/y 偏移，Y 指向底袋，即 +Y）。 */
    fun triangleOffsets(diameter: Float): List<Pair<Float, Float>> {
        val rowGap = diameter * (sqrt(3f) / 2f)
        val offsets = ArrayList<Pair<Float, Float>>(15)
        for (row in 0..4) {
            for (i in 0..row) {
                val offsetX = (i - row / 2f) * diameter
                val offsetY = row * rowGap
                offsets += offsetX to offsetY
            }
        }
        return offsets
    }

    /**
     * 生成一局全部球（含白球）。位置在 [table] 上按标准三角架摆放。
     *
     * @param order 1..15 的摆位顺序（长度 15，可固定种子打乱；8 号约束由调用方保证）。
     */
    fun rack(
        table: PoolTable,
        order: List<Int>,
    ): List<PoolBall> {
        require(order.size == 15 && order.toSet() == (1..15).toSet()) {
            "order 必须是 1..15 的一个排列"
        }
        val balls = ArrayList<PoolBall>(16)
        // 白球
        balls += PoolBall(id = 0, kind = PoolBallKind.CUE, x = table.centerX, y = table.headStringY)
        val offsets = triangleOffsets(table.ballRadius * 2f)
        order.forEachIndexed { index, number ->
            val (dx, dy) = offsets[index]
            val kind =
                when (number) {
                    8 -> PoolBallKind.EIGHT
                    in 1..7 -> PoolBallKind.SOLID
                    else -> PoolBallKind.STRIPE
                }
            balls +=
                PoolBall(
                    id = number,
                    kind = kind,
                    x = table.centerX + dx,
                    y = table.footSpotY + dy,
                )
        }
        return balls
    }

    /** 标准摆法（8 居中、底排两角一实一花）。可传 seed 生成不同但合法的排列。 */
    fun standardOrder(seed: Long = 0L): List<Int> {
        if (seed == 0L) {
            // 行优先：顶点 1；第 3 排正中（下标 4）为 8；底排两角（10、14）一实一花
            return listOf(1, 9, 2, 10, 8, 11, 3, 12, 4, 13, 5, 14, 6, 7, 15)
        }
        val rng = kotlin.random.Random(seed)
        val solids = (1..7).shuffled(rng).toMutableList()
        val stripes = (9..15).shuffled(rng).toMutableList()
        val order = MutableList(15) { 0 }
        // 第 3 排正中固定 8
        order[4] = 8
        // 底排两角一实一花
        order[10] = solids.removeAt(0)
        order[14] = stripes.removeAt(0)
        val rest = (solids + stripes).shuffled(rng)
        var ri = 0
        for (i in order.indices) {
            if (order[i] == 0) order[i] = rest[ri++]
        }
        return order
    }
}

/** 常用角度工具（纯函数，便于测试）。 */
object PoolMath {
    fun normalizeAngle(angle: Float): Float {
        var a = angle
        while (a > PI.toFloat()) a -= (2f * PI.toFloat())
        while (a < -PI.toFloat()) a += (2f * PI.toFloat())
        return a
    }

    fun degrees(radians: Float): Float = radians * 180f / PI.toFloat()

    fun radians(degrees: Float): Float = degrees * PI.toFloat() / 180f

    /** 两条 2D 向量夹角（0..π）。 */
    fun angleBetween(
        ax: Float,
        ay: Float,
        bx: Float,
        by: Float,
    ): Float {
        val la = hypot(ax, ay)
        val lb = hypot(bx, by)
        if (la < 1e-6f || lb < 1e-6f) return 0f
        val c = ((ax * bx + ay * by) / (la * lb)).coerceIn(-1f, 1f)
        return kotlin.math.acos(c)
    }

    fun cosd(degrees: Float): Float = cos(radians(degrees))

    fun sind(degrees: Float): Float = sin(radians(degrees))

    fun approximately(
        a: Float,
        b: Float,
        epsilon: Float,
    ): Boolean = abs(a - b) <= epsilon
}

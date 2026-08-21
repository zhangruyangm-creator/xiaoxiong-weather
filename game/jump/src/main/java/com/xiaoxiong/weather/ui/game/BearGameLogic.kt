package com.xiaoxiong.weather.ui.game

import kotlin.math.min
import kotlin.math.sqrt

/**
 * 小熊跳跳的纯逻辑层：与 Compose / Android 完全解耦，可被普通 JUnit 单测。
 *
 * 屏幕尺寸缩放 [s]（= 屏高 / 820）在这里作为**显式参数**传入，而不是读取 Compose
 * [androidx.compose.ui.unit.IntSize]；这样同一套规则既能在 [BearGame]（Compose 状态
 * 容器/渲染适配）里跑，也能脱离渲染环境被确定性测试。
 *
 * 注意 [stepBearPhysics] 刻意**逐字复刻**原 [BearGame.tick] 的显式欧拉积分与落地判定，
 * 以保证重构前后帧级行为完全一致。
 */
object BearGameLogic {
    // ── 物理常量（尺寸缩放由调用方传入）────────────────────────

    fun gravity(s: Float): Float = 2500f * s

    fun jumpV(s: Float): Float = -960f * s

    fun jumpV2(s: Float): Float = -830f * s

    fun maxSpeed(s: Float): Float = 950f * s

    // ── 纯计算 ─────────────────────────────────────────────────

    /**
     * 速度曲线：由纯距离分数（不含金币）驱动，而不是按时间线性加速。
     * 开局 340，约 5000 分时达极速 950；前期较快、越到后期增速越缓。
     */
    fun speedAt(
        distanceScore: Float,
        s: Float,
    ): Float {
        val p = (distanceScore / 5000f).coerceIn(0f, 1f)
        return (340f + 610f * sqrt(p)) * s
    }

    fun score(
        distance: Float,
        coinCount: Int,
        s: Float,
    ): Int = if (s <= 0f) 0 else (distance / (60f * s)).toInt() + coinCount * 10

    /** 奔跑循环相位速率（rad/s）：开局从容、随速度渐快，步频封顶，避免高速抽帧。 */
    fun runPhaseRate(
        speed: Float,
        s: Float,
    ): Float = min(speed, 640f * s) * 0.031f / s

    // ── 障碍决策（冰墙几何/门槛）───────────────────────────────

    /**
     * 冰墙高度：单跳（~184·s）够不到、二段跳（~322·s）稳过；
     * 雪天比冻雨略矮，手感更宽松。
     */
    fun iceWallHeight(
        scene: RunScene,
        s: Float,
    ): Float =
        when (scene) {
            RunScene.SNOW -> 195f * s
            else -> 210f * s
        }

    /** 冰墙出现所需的最低分数：雪天 300、冻雨 200，低分段先不出（需二段跳），前松后紧。 */
    fun iceWallMinScore(scene: RunScene): Int =
        when (scene) {
            RunScene.SNOW -> 300
            else -> 200
        }

    // ── 物理推进（核心引擎，见类内 KDoc 的等价性说明）───────────

    data class BearPhysics(
        val bearY: Float,
        val bearVY: Float,
        val jumpsLeft: Int,
        val grounded: Boolean,
        /** 本帧是否从空中落到地面（用于触发挤压/扬尘等表现层副作用）。 */
        val landed: Boolean,
    )

    /**
     * 推进小熊一帧的弹跳物理。等价于原 [BearGame.tick] 中的顺序更新段：
     * `bearVY += gravity·dt` 再 `bearY += bearVY·dt`（显式欧拉），
     * 落地时夹到 [floorY]、清零垂直速度并重置二段跳。
     */
    fun stepBearPhysics(
        bearY: Float,
        bearVY: Float,
        jumpsLeft: Int,
        grounded: Boolean,
        floorY: Float,
        s: Float,
        dt: Float,
    ): BearPhysics {
        val g = gravity(s)
        var ny = bearY
        var nvy = bearVY + g * dt
        ny += nvy * dt
        var njumps = jumpsLeft
        var ngrounded = grounded
        var landed = false
        if (ny >= floorY) {
            landed = !ngrounded
            ny = floorY
            nvy = 0f
            njumps = 2
            ngrounded = true
        } else {
            ngrounded = false
        }
        return BearPhysics(ny, nvy, njumps, ngrounded, landed)
    }
}

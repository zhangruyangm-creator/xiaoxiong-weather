package com.xiaoxiong.weather.ui.game

import kotlin.math.abs
import kotlin.math.min

/**
 * 小熊摘星的纯逻辑层：与 Compose / Android 完全解耦，可被普通 JUnit 单测。
 *
 * 屏幕尺寸 [w]/[h] 与缩放 [s]（= 屏幕缩放系数）作为**显式参数**传入，而不是读取
 * [StarJumpGame] 的可变状态；保证同一套规则在 [StarJumpGame]（Compose 状态容器/渲染
 * 适配）与脱离渲染环境的确定性测试中行为一致。
 *
 * 涉及 UI 触发（弹跳音效/得分/星星粒子/震屏）与 Compose 状态的部分不在本文件。
 */
internal object StarJumpLogic {

    // ── 常量（缩放由调用方传入）────────────────────────────────

    fun gravity(s: Float): Float = 2600f * s
    fun bearSize(s: Float): Float = 58f * s
    /** 普通/碎平台反弹初速。 */
    fun bounceV(s: Float): Float = -1350f * s
    /** 弹簧平台反弹初速。 */
    fun springV(s: Float): Float = -2350f * s

    // ── 纯计算 ─────────────────────────────────────────────────

    /** 得分：按到达最高高度 + 收集星星（+25/颗）。 */
    fun scoreFor(
        maxHeight: Float,
        starsCollected: Int,
        s: Float,
    ): Int = ((-maxHeight) / (60f * s)).toInt().coerceAtLeast(0) + starsCollected * 25

    /** 相机顶对应的海拔（公里）。 */
    fun altitudeKm(
        cameraY: Float,
        s: Float,
    ): Float = (-cameraY / (2400f * s)).coerceAtLeast(0f)

    // ── 垂直物理 ───────────────────────────────────────────────

    /**
     * 一帧重力积分（显式欧拉，先加速度再加位移）。与 [StarJumpGame.tick] 顺序一致：
     * `bearVY += g·dt; bearY += bearVY·dt`。
     */
    fun stepFalling(
        bearY: Float,
        bearVY: Float,
        s: Float,
        dt: Float,
    ): Pair<Float, Float> {
        val g = gravity(s)
        val nvy = bearVY + g * dt
        val ny = bearY + nvy * dt
        return ny to nvy
    }

    // ── 平台接触判定（玩法正确性核心）──────────────────────────

    data class Contact(
        val hit: Boolean,
        /** 踩中的平台；null 表示本帧未踩中。命中后调用方可能需据其 [Platform.type] 标记破碎。 */
        val platform: Platform?,
        /** 落到台面时的熊中心 y。 */
        val landY: Float,
        /** 命中后的反弹速度（弹簧或普通/破碎）。 */
        val bounceVY: Float,
    )

    /**
     * 判定小熊是否在垂直方向踩中某个平台。
     *
     * 规则与 [StarJumpGame.tick] 逐字对齐：
     * 仅下落（[bearVY] > 0）时判；脚线（[prevFeetY]→[newFeetY]）穿越台面 y；
     * 死亡线以下（`p.y - cameraY > h + bs`）不可踩；跳过已破碎平台；
     * 水平上需 `|dx(环绕修正)| < p.w/2 + bs·0.24`。
     *
     * @param newFeetY 本帧积分后的脚线 y（= 本帧 bearY + bs/2）
     * @return 第一个命中的平台（自顶向下遍历）对应 [Contact]。
     */
    fun resolveContact(
        bearX: Float,
        prevFeetY: Float,
        newFeetY: Float,
        bearVY: Float,
        platforms: List<Platform>,
        w: Float,
        h: Float,
        s: Float,
        cameraY: Float,
    ): Contact {
        if (bearVY <= 0f) return Contact(false, null, newFeetY - bearSize(s) / 2f, 0f)
        val bs = bearSize(s)
        for (p in platforms) {
            if (p.broken) continue
            if (p.y - cameraY > h + bs) continue
            if (prevFeetY <= p.y && newFeetY >= p.y) {
                var dx = bearX - (p.x + p.w / 2f)
                if (dx > w / 2f) dx -= w + bs
                if (dx < -w / 2f) dx += w + bs
                if (abs(dx) < p.w / 2f + bs * 0.24f) {
                    val landY = p.y - bs / 2f
                    val vy =
                        if (p.type == PlatType.SPRING) springV(s) else bounceV(s)
                    return Contact(true, p, landY, vy)
                }
            }
        }
        return Contact(false, null, newFeetY - bs / 2f, 0f)
    }

    /** 单颗星星是否被熊吃到（环绕修正后的圆心距离 < 半径）。 */
    fun starPickup(
        bearX: Float,
        bearY: Float,
        starX: Float,
        starY: Float,
        w: Float,
        s: Float,
    ): Boolean {
        val bs = bearSize(s)
        var dx = bearX - starX
        if (dx > w / 2f) dx -= w + bs
        if (dx < -w / 2f) dx += w + bs
        val dy = bearY - starY
        val reach = bs * 0.62f
        return dx * dx + dy * dy < reach * reach
    }

    // ── 平台生成决策 ───────────────────────────────────────────

    /**
     * 下一块平台的**类型与宽度**决策（不含 x 与 vx——它们由调用方用独立的随机数决定，
     * 以免 type 与落点相关而改变生成分布）。规则与 [StarJumpGame.genPlatforms] 逐字对齐：
     * 早期必为普通台；随后按 roll 分布到 弹簧/碎台/移动台；移动台宽度被钳到可移动区间 55% 内。
     *
     * @return `(类型, 宽度)`；宽度已含缩放与各类钳制。
     */
    fun platformShape(
        progress: Float,
        early: Boolean,
        roll: Float,
        w: Float,
        s: Float,
    ): Pair<PlatType, Float> {
        val margin = 8f * s
        val maxTravel = (w - margin * 2f).coerceAtLeast(1f)
        var pw = (150f - 26f * progress.coerceIn(0f, 1f)) * s
        val type =
            when {
                early -> PlatType.NORMAL
                roll < 0.07f -> PlatType.SPRING
                roll < 0.17f + 0.08f * progress -> PlatType.BREAK
                roll < 0.32f + 0.18f * progress -> PlatType.MOVING
                else -> PlatType.NORMAL
            }
        pw =
            if (type == PlatType.MOVING) {
                min(pw, maxTravel * 0.55f).coerceAtLeast(48f * s)
            } else {
                min(pw, maxTravel)
            }
        return type to pw
    }
}

package com.xiaoxiong.weather.ui.game

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.IntSize
import kotlin.math.max
import kotlin.math.min

/**
 * 小熊接雨雪的 Compose 状态容器。物理量在普通字段里（每帧变化不走快照），
 * 只有阶段/最高分/心数/场景用 Compose state 驱动 HUD 重组（对齐 BearGame 写法）。
 *
 * [tick] 把当前帧交给 [BearCatchLogic]（纯引擎），拿到事件后更新分数/心数，
 * 供渲染层播放效果（飘分、红闪、震动）。
 */
class BearCatchGame {
    var phase by mutableStateOf(GamePhase.READY)
    var best by mutableIntStateOf(0)
    var score by mutableIntStateOf(0)
    var hearts by mutableIntStateOf(MAX_HEARTS)

    /** 是否拥有一次性护盾：接到 BUBBLE 后获得，被一次伤害消耗，进入冷却。 */
    var hasShield by mutableStateOf(false)

    /** 护盾冷却剩余时间（秒）；冷却结束后再次可获取护盾。 */
    var shieldCooldownT = 0f

    /** 连击：连续接住得分物不中断；断连（漏接得分物）归零。 */
    var combo by mutableIntStateOf(0)

    /** 双倍得分剩余时间（秒）；<=0 时无加成。 */
    var doubleT = 0f

    /** 最近一次是否处于风暴爆发波次（供 HUD 闪烁）。 */
    var inBurstWave by mutableStateOf(false)

    /** 当前天气场景；由 Screen 在天气变化时刷新。 */
    var scene by mutableStateOf(BearCatchLogic.CatchScene.SUNNY)

    var size = IntSize.Zero
    var time = 0f

    /** 小熊中心 x（屏幕坐标）。 */
    var catchX = 0f

    /** 生成累计时钟（跨帧保留，交给引擎继续计）。 */
    var spawnAccum = 0f

    var touchX: Float? = null

    val items = mutableListOf<BearCatchLogic.Item>()
    val particles = mutableListOf<Particle>()

    /** 接住挤压计时；< 0 表示未触发。 */
    var squashT = -1f

    /** 命中惩罚后的屏幕震动剩余时间。 */
    var shakeT = 0f

    /** 受击后无敌时间，避免同帧多重扣血瞬死。 */
    var invincibleT = 0f

    val s get() = if (size.height > 0) size.height / 820f else 1f
    val catchY get() = size.height * 0.90f
    val catchR get() = size.height * 0.085f

    fun resize(newSize: IntSize) {
        if (newSize == size || newSize.width <= 0 || newSize.height <= 0) return
        size = newSize
        catchX = (size.width / 2f).coerceIn(catchR, size.width - catchR)
        if (phase == GamePhase.RUNNING) {
            phase = GamePhase.READY
            reset()
        } else {
            reset()
        }
    }

    fun reset() {
        score = 0
        hearts = MAX_HEARTS
        hasShield = false
        shieldCooldownT = 0f
        combo = 0
        doubleT = 0f
        inBurstWave = false
        spawnAccum = 0f
        time = 0f
        squashT = -1f
        shakeT = 0f
        invincibleT = 0f
        items.clear()
        particles.clear()
        if (size.width > 0) {
            catchX = (size.width / 2f).coerceIn(catchR, size.width - catchR)
        }
    }

    fun start() {
        reset()
        phase = GamePhase.RUNNING
    }

    /** 推进一帧：委托 [BearCatchLogic] 引擎，并消费事件更新状态。 */
    fun tick(
        dt: Float,
        onCaught: (BearCatchLogic.ItemKind) -> Unit,
        onHit: (BearCatchLogic.ItemKind) -> Unit,
        onDeath: (Int) -> Unit,
    ) {
        if (size == IntSize.Zero) return
        time += dt

        if (doubleT > 0f) doubleT = max(0f, doubleT - dt)
        if (shieldCooldownT > 0f) shieldCooldownT = max(0f, shieldCooldownT - dt)

        // 粒子任何阶段都继续飞
        val iter = particles.iterator()
        while (iter.hasNext()) {
            val p = iter.next()
            p.life -= dt
            p.vy += 900f * s * dt
            p.x += p.vx * dt
            p.y += p.vy * dt
            if (p.life <= 0f) iter.remove()
        }
        if (squashT >= 0f) {
            squashT += dt
            if (squashT > 0.16f) squashT = -1f
        }
        if (shakeT > 0f) shakeT = max(0f, shakeT - dt)
        if (invincibleT > 0f) invincibleT = max(0f, invincibleT - dt)

        if (phase == GamePhase.READY) return
        if (phase == GamePhase.OVER) return

        // 小熊跟随手指水平移动（截距式：取 touchX，无触摸时惯性衰减）
        val tx = touchX
        if (tx != null) {
            val target = tx.coerceIn(catchR, size.width - catchR)
            catchX += (target - catchX) * min(1f, dt * 18f)
        }

        val ev =
            BearCatchLogic.stepCatchWorldDefault(
                items = items,
                score = score,
                scene = scene,
                s = s,
                dt = dt,
                width = size.width.toFloat(),
                height = size.height.toFloat(),
                catchX = catchX,
                catchY = catchY,
                catchR = catchR,
                spawnAccum = spawnAccum,
                time = time,
            )
        spawnAccum = ev.spawnAccum
        inBurstWave = ev.inBurstWave

        if (ev.missedGood > 0) combo = 0

        for (kind in ev.caught) {
            when (kind) {
                BearCatchLogic.ItemKind.GIFT -> {
                    score += BearCatchLogic.scoreOf(kind)
                    doubleT = BearCatchLogic.DOUBLE_TIME_SEC
                    combo++
                    squashT = 0f
                    burst(
                        cx = catchX,
                        cy = catchY - catchR * 0.5f,
                        count = 12,
                        color = Color(0xFFFF8F00),
                    )
                }
                BearCatchLogic.ItemKind.BUBBLE -> {
                    // 冷却期间拾取不再叠加；只有在没护盾且没冷却时才给一次性护盾
                    if (!hasShield && shieldCooldownT <= 0f) {
                        hasShield = true
                    }
                    squashT = 0f
                    burst(
                        cx = catchX,
                        cy = catchY - catchR * 0.5f,
                        count = 10,
                        color = Color(0xFF80DEEA),
                    )
                }
                else -> {
                    val base = BearCatchLogic.scoreOf(kind)
                    val mult = if (doubleT > 0f) 2 else 1
                    score += base * mult
                    combo++
                    squashT = 0f
                    burst(
                        cx = catchX,
                        cy = catchY - catchR * 0.5f,
                        count = 6,
                        color = burstColorOf(kind),
                    )
                }
            }
            onCaught(kind)
        }
        // 命中处理：同帧最多只生效一次伤害/护盾消耗，
        // 避免「4 块乌云同帧砸中 → 只扣 1 层护盾/扣 1 心」的吞伤问题。
        // 优先级：闪电 > 冰雹 > 乌云（越致命越靠前；闪电无视护盾直接扣血）。
        val priority =
            mapOf(
                BearCatchLogic.ItemKind.LIGHTNING to 0,
                BearCatchLogic.ItemKind.HAIL to 1,
                BearCatchLogic.ItemKind.CLOUD to 2,
            )
        val effectiveHit =
            ev.hit
                .filter { it in priority }
                .minByOrNull { priority.getValue(it) }
        if (effectiveHit != null) {
            if (invincibleT <= 0f) {
                // 闪电无视护盾：扣 2 心并打满无敌；其余物品优先用护盾挡一次
                if (effectiveHit == BearCatchLogic.ItemKind.LIGHTNING) {
                    hearts -= BearCatchLogic.damageOf(effectiveHit)
                    combo = 0
                    shakeT = 0.30f
                    invincibleT = 0.75f
                    burst(
                        cx = catchX,
                        cy = catchY - catchR * 0.6f,
                        count = 14,
                        color = Color(0xFFFFD600),
                    )
                    onHit(effectiveHit)
                } else if (hasShield) {
                    hasShield = false
                    shieldCooldownT = BearCatchLogic.SHIELD_COOLDOWN_SEC
                    shakeT = 0.12f
                    invincibleT = 0.3f
                    burst(
                        cx = catchX,
                        cy = catchY - catchR * 0.6f,
                        count = 12,
                        color = Color(0xFF80DEEA),
                    )
                    onHit(effectiveHit)
                } else {
                    hearts -= BearCatchLogic.damageOf(effectiveHit)
                    combo = 0
                    shakeT = 0.22f
                    invincibleT = 0.75f
                    burst(
                        cx = catchX,
                        cy = catchY - catchR * 0.6f,
                        count = 10,
                        color = Color(0xFF9E9E9E),
                    )
                    onHit(effectiveHit)
                }
                if (hearts <= 0) {
                    hearts = 0
                    phase = GamePhase.OVER
                    onDeath(score)
                }
            } else {
                // 已在无敌帧：本帧所有命中全部忽略；物品已在 stepCatchWorld 中移除
            }
        }
    }

    /** 接住某类掉落物的爆点颜色（纯数据，不触碰 Android 图形 API，保证可单测）。 */
    fun burstColorOf(kind: BearCatchLogic.ItemKind): Color =
        when (kind) {
            BearCatchLogic.ItemKind.COIN -> Color(0xFFFFCA28)
            BearCatchLogic.ItemKind.RAINDROP -> Color(0xFF8FC3F0)
            BearCatchLogic.ItemKind.SNOWFLAKE -> Color(0xFFE8F2FF)
            BearCatchLogic.ItemKind.PETAL -> Color(0xFFF8BBD0)
            BearCatchLogic.ItemKind.CLOUD -> Color(0xFF8A94A0)
            BearCatchLogic.ItemKind.HAIL -> Color(0xFFC4CFDA)
            BearCatchLogic.ItemKind.LIGHTNING -> Color(0xFFFFEB3B)
            BearCatchLogic.ItemKind.GIFT -> Color(0xFFFF8F00)
            BearCatchLogic.ItemKind.BUBBLE -> Color(0xFF80DEEA)
        }

    /** 接住/命中时的爆点粒子（对齐 jump 的 burst 手法）。 */
    fun burst(
        cx: Float,
        cy: Float,
        count: Int,
        color: Color,
    ) {
        val spread = 180f * s
        repeat(count) {
            val a = kotlin.random.Random.nextFloat() * (Math.PI * 2).toFloat()
            val v = (0.4f + kotlin.random.Random.nextFloat() * 0.6f) * spread
            val life = 0.4f + kotlin.random.Random.nextFloat() * 0.35f
            particles +=
                Particle(
                    x = cx,
                    y = cy,
                    vx = kotlin.math.cos(a) * v,
                    vy = kotlin.math.sin(a) * v,
                    life = life,
                    maxLife = life,
                    color = color,
                )
        }
    }

    companion object {
        const val MAX_HEARTS = 3
    }
}

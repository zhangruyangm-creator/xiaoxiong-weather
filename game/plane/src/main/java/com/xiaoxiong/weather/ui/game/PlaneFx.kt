package com.xiaoxiong.weather.ui.game

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import kotlin.math.cos
import kotlin.math.sin

/**
 * 小熊战机的**表现层状态**：全部是屏幕侧状态（闪白 / 震屏 / 拖尾 / 擦弹脉冲），
 * 不回写玩法（判定与数值语义保持冻结）。字段为普通可变字段，只在 draw / tick
 * 阶段读写，不触发重组。
 */
internal class PlaneFrameState {
    /** 视觉时钟（60Hz 渲染帧）：开新局归零，保证背景 / 粒子重置后一致。 */
    var visualTick = 0L

    /** 上一波波次（HUD 横幅用）。 */
    var lastWaveIndex = 0

    /** 炸弹全屏白闪剩余帧（含冲击波 / 震屏）。 */
    var bombTicks = 0

    /** 玩家受击红闪剩余帧。 */
    var hitTicks = 0

    /** 擦弹火花剩余帧。 */
    var grazeTicks = 0

    /** 道具拾取光剩余帧。 */
    var pickupTicks = 0

    /** 震屏剩余帧（炸弹 / Boss 死亡）。 */
    var shakeTicks = 0

    /** 震屏短门控（毫秒时间戳）：与音频节流独立，避免炸弹 / Boss 击破同帧叠加抖动。 */
    private var lastShakeMs = Long.MIN_VALUE

    /** 机身倾斜（-1..1 归一，绘制时乘角度）。 */
    var tilt = 0f

    private var prevPlayerX = Float.NaN
    private var prevGrazeCount = 0
    private var prevCombo = 0

    // 机尾拖尾环形缓冲（坐标是逻辑坐标）
    val trailX = FloatArray(TRAIL_CAPACITY)
    val trailY = FloatArray(TRAIL_CAPACITY)
    var trailHead = 0
    var trailCount = 0

    // 敌机受击闪白：id / 上次 HP / 上次 Boss 阶段 / 闪白截止 tick，定长数组避免每帧分配
    private val flashIds = IntArray(FLASH_CAPACITY)
    private val flashHp = IntArray(FLASH_CAPACITY)
    private val flashPhase = IntArray(FLASH_CAPACITY)
    private val flashUntil = LongArray(FLASH_CAPACITY)
    private var flashCount = 0

    /** 每个渲染帧调用：推进视觉时钟与所有计时器。 */
    fun onTick(game: PlaneGame) {
        visualTick++
        if (game.grazeCount > prevGrazeCount) {
            prevGrazeCount = game.grazeCount
            grazeTicks = GRAZE_FX_TICKS
        }
        if (game.combo > prevCombo && game.combo > 0 && game.combo % 5 == 0) {
            // 连杀节点轻震（不走音频门控，短于炸弹震屏）
            shakeTicks = maxOf(shakeTicks, SHAKE_FX_TICKS / 2)
        }
        prevCombo = game.combo
        if (bombTicks > 0) bombTicks--
        if (hitTicks > 0) hitTicks--
        if (grazeTicks > 0) grazeTicks--
        if (pickupTicks > 0) pickupTicks--
        if (shakeTicks > 0) shakeTicks--
        val px = game.player.x
        tilt =
            if (prevPlayerX.isNaN()) {
                0f
            } else {
                ((px - prevPlayerX).coerceIn(-2.5f, 2.5f) / 2.5f) * 0.26f
            }
        prevPlayerX = px
        if (game.phase == PlanePhase.PLAYING && game.player.alive) {
            trailHead = (trailHead + 1) % TRAIL_CAPACITY
            trailX[trailHead] = game.player.x
            trailY[trailHead] = game.player.y
            if (trailCount < TRAIL_CAPACITY) trailCount++
        }
    }

    fun onBombUsed(nowMs: Long) {
        bombTicks = BOMB_FX_TICKS
        onShake(nowMs)
    }

    fun onPlayerHit(nowMs: Long = Long.MIN_VALUE) {
        hitTicks = HIT_FX_TICKS
        if (nowMs != Long.MIN_VALUE) onShake(nowMs, strengthTicks = SHAKE_FX_TICKS / 2)
    }

    fun onPowerUp() {
        pickupTicks = PICKUP_FX_TICKS
    }

    fun onBossDown(nowMs: Long) {
        onShake(nowMs)
    }

    /** 震屏短门控：距上次触发不足 [SHAKE_GATE_MS] 时忽略，避免同帧 / 连帧重复起震。 */
    private fun onShake(
        nowMs: Long,
        strengthTicks: Int = SHAKE_FX_TICKS,
    ) {
        if (lastShakeMs != Long.MIN_VALUE && nowMs - lastShakeMs < SHAKE_GATE_MS) return
        lastShakeMs = nowMs
        shakeTicks = maxOf(shakeTicks, strengthTicks)
    }

    /** 快照恢复后对齐基线：避免把恢复瞬间的擦弹计数 / 波次误判成新事件。 */
    fun onRestore(game: PlaneGame) {
        lastWaveIndex = game.waveIndex
        prevGrazeCount = game.grazeCount
        prevCombo = game.combo
        prevPlayerX = game.player.x
        trailHead = 0
        trailCount = 0
    }

    fun reset() {
        visualTick = 0L
        lastWaveIndex = 0
        bombTicks = 0
        hitTicks = 0
        grazeTicks = 0
        pickupTicks = 0
        shakeTicks = 0
        lastShakeMs = Long.MIN_VALUE
        tilt = 0f
        prevPlayerX = Float.NaN
        prevGrazeCount = 0
        prevCombo = 0
        trailHead = 0
        trailCount = 0
        flashCount = 0
        flashUntil.fill(0L)
    }

    /** 敌机本帧闪白强度（受击 / Boss 阶段切换时短暂高亮）。 */
    fun flashAlpha(
        enemy: PlaneEnemy,
        tick: Long,
    ): Float {
        var index = -1
        for (i in 0 until flashCount) {
            if (flashIds[i] == enemy.id) {
                index = i
                break
            }
        }
        if (index < 0) {
            if (flashCount < FLASH_CAPACITY) {
                index = flashCount++
            } else {
                // 罕见溢出：整体复位后重新登记，避免误闪
                flashCount = 0
                index = 0
                flashCount = 1
            }
            flashIds[index] = enemy.id
            flashHp[index] = enemy.hp
            flashPhase[index] = enemy.bossPhase
            flashUntil[index] = 0L
        }
        // 小怪按掉血闪白；Boss 只在阶段切换时整机闪烁，避免持续开火下一直泛白
        val damaged = enemy.hp < flashHp[index]
        val phaseChanged = enemy.isBoss && enemy.bossPhase != flashPhase[index]
        if (damaged && (!enemy.isBoss || phaseChanged)) flashUntil[index] = tick + FLASH_FX_TICKS
        flashHp[index] = enemy.hp
        flashPhase[index] = enemy.bossPhase
        val remaining = flashUntil[index] - tick
        return if (remaining > 0) (remaining / FLASH_FX_TICKS.toFloat()).coerceIn(0f, 1f) * 0.92f else 0f
    }
}

internal const val BOMB_FX_TICKS = 42
internal const val HIT_FX_TICKS = 28
internal const val GRAZE_FX_TICKS = 22
internal const val PICKUP_FX_TICKS = 18
internal const val SHAKE_FX_TICKS = 28

/** 震屏短门控间隔：同一次爆炸链路内的重复触发只保留一次。 */
private const val SHAKE_GATE_MS = 120L
private const val FLASH_FX_TICKS = 7L
private const val TRAIL_CAPACITY = 12
private const val FLASH_CAPACITY = 64

// ---------------------------------------------------------------------
// 天气（雨 / 雪 / 雷电 / 冰雹 / 雾），复用 common GameWeatherFx
// ---------------------------------------------------------------------

/**
 * 天气粒子层：雨 / 冻雨 / 冰雹 / 雷电直接复用 [drawRainLayered] / [drawStormLightning]；
 * 雪与雾是弹幕射击特有的柔化版本，本地程序绘制。全部由 [time] 驱动（确定性），
 * [reduceMotion] 时只保留静态氛围。
 */
internal fun DrawScope.drawPlaneWeather(
    weather: FarmWeather,
    rain: RainStrength,
    freezingRain: Boolean,
    hail: Boolean,
    time: Float,
    reduceMotion: Boolean,
) {
    val w = size.width
    val h = size.height
    if (w <= 0f || h <= 0f) return
    if (freezingRain) {
        drawFreezingRain(time, 1f, w, h, splash = false, reduceMotion = reduceMotion)
        return
    }
    if (hail) {
        drawRainLayered(
            fgCount = 16,
            bgCount = 8,
            time = time,
            width = w,
            height = h,
            scale = 1f,
            lenMul = 1.05f,
            alpha = 0.40f,
            splash = false,
            reduceMotion = reduceMotion,
        )
        drawHailPellets(time, 1f, w, h, count = 38, reduceMotion = reduceMotion)
        drawStormLightning(time, 1f, w, h, reduceMotion = reduceMotion)
        return
    }
    when (weather) {
        FarmWeather.RAINY ->
            when (rain) {
                RainStrength.LIGHT ->
                    drawRainLayered(14, 7, time, w, h, 1f, alpha = 0.36f, splash = false, reduceMotion = reduceMotion)
                RainStrength.MODERATE ->
                    drawRainLayered(24, 12, time, w, h, 1f, alpha = 0.46f, splash = false, reduceMotion = reduceMotion)
                RainStrength.HEAVY ->
                    drawRainLayered(38, 18, time, w, h, 1f, alpha = 0.56f, splash = false, reduceMotion = reduceMotion)
                RainStrength.NONE ->
                    drawRainLayered(10, 5, time, w, h, 1f, alpha = 0.30f, splash = false, reduceMotion = reduceMotion)
            }
        FarmWeather.STORMY -> {
            drawRainLayered(38, 18, time, w, h, 1f, alpha = 0.56f, splash = false, reduceMotion = reduceMotion)
            drawStormLightning(time, 1f, w, h, reduceMotion = reduceMotion)
        }
        FarmWeather.SNOWY -> drawPlaneSnowfall(time, w, h, reduceMotion)
        FarmWeather.FOGGY -> drawPlaneFogBands(time, w, h, reduceMotion)
        FarmWeather.SUNNY, FarmWeather.CLOUDY -> Unit
    }
}

/** 雪：细密缓慢 + 横向摇摆，密度减半由 [reduceMotion] 控制。 */
internal fun DrawScope.drawPlaneSnowfall(
    time: Float,
    w: Float,
    h: Float,
    reduceMotion: Boolean,
) {
    if (reduceMotion) return
    val count = 40
    for (i in 0 until count) {
        val phase = ((i * 67) % 29) / 29f
        val speed = 26f + phase * 34f
        val x = planeMod(i * 83f + time * speed * 0.55f, w + 44f) - 22f
        val y = planeMod(i * 61f + time * speed, h + 44f) - 22f
        val sway = sin(time * 1.15f + i * 0.9f) * 7f
        val r = 1.4f + (i % 4) * 0.75f
        drawCircle(Color.White.copy(alpha = 0.22f + phase * 0.34f), r, Offset(x + sway, y))
    }
}

/** 雾：横向缓慢漂移的柔光带，低透明度不遮挡弹幕可读性。 */
internal fun DrawScope.drawPlaneFogBands(
    time: Float,
    w: Float,
    h: Float,
    reduceMotion: Boolean,
) {
    if (reduceMotion) return
    for (i in 0 until 5) {
        val y = h * (0.12f + i * 0.18f) + sin(time * 0.35f + i * 1.3f) * 10f
        val x = planeMod(i * 217f + time * 11f, w + 300f) - 150f
        drawOval(
            Color.White.copy(alpha = 0.055f),
            topLeft = Offset(x - w * 0.45f, y - h * 0.05f),
            size = Size(w * 0.90f, h * 0.10f),
        )
    }
    drawRect(Color(0xFFB7CBDD).copy(alpha = 0.06f))
}

// ---------------------------------------------------------------------
// 粒子 / 拖尾 / 擦弹
// ---------------------------------------------------------------------

/** 爆炸火星 / 烟。核心 + 加色外发光，大颗粒叠一层烟；reduceMotion 时密度减半。 */
internal fun DrawScope.drawPlaneEffects(
    game: PlaneGame,
    reduceMotion: Boolean,
) {
    val scale = game.viewScale
    val effects = game.effects
    if (effects.isEmpty() || scale <= 0f) return
    for (i in 0 until effects.size) {
        if (reduceMotion && i % 2 == 0) continue
        val effect = effects[i]
        val a = (effect.lifeTicks / 26f).coerceIn(0.04f, 1f)
        val x = game.toScreenX(effect.x)
        val y = game.toScreenY(effect.y)
        val r = (effect.size * 1.1f * scale).coerceAtLeast(1f)
        if (effect.size > 1.15f) {
            drawCircle(Color(0xFF1D2126).copy(alpha = 0.20f * a), r * 2.5f, Offset(x, y))
        }
        drawCircle(
            color = Color(effect.color).copy(alpha = 0.22f * a),
            radius = r * 2.1f,
            center = Offset(x, y),
            blendMode = BlendMode.Plus,
        )
        drawCircle(Color(effect.color).copy(alpha = 0.92f * a), r, Offset(x, y))
        drawCircle(Color.White.copy(alpha = 0.70f * a), r * 0.40f, Offset(x, y))
    }
}

/** 拖尾残影 + 引擎尾焰（加色，随视觉时钟闪烁；[reduceMotion] 时尾焰冻结静态帧）。 */
internal fun DrawScope.drawPlayerTrailAndFlame(
    game: PlaneGame,
    state: PlaneFrameState,
    playerRadius: Float,
    reduceMotion: Boolean,
) {
    if (!game.player.alive) return
    val scale = game.viewScale
    if (scale <= 0f) return
    val x = game.toScreenX(game.player.x)
    val y = game.toScreenY(game.player.y)
    val r = playerRadius * scale
    for (k in 1 until state.trailCount) {
        val idx = (state.trailHead - k + TRAIL_CAPACITY * 2) % TRAIL_CAPACITY
        val a = (1f - k / TRAIL_CAPACITY.toFloat()) * 0.16f
        if (a <= 0.01f) continue
        drawCircle(
            color = Color(0xFFFFE0B2).copy(alpha = a),
            radius = r * (0.55f - k * 0.02f).coerceAtLeast(r * 0.16f),
            center = Offset(game.toScreenX(state.trailX[idx]), game.toScreenY(state.trailY[idx])),
        )
    }
    val flick = if (reduceMotion) 1f else 0.72f + 0.28f * sin(state.visualTick * 0.85f)
    val flameH = r * (0.42f + 0.42f * flick)
    drawOval(
        color = Color(0xFF80DEEA).copy(alpha = 0.50f),
        topLeft = Offset(x - r * 0.30f, y + r * 0.52f),
        size = Size(r * 0.60f, flameH),
        blendMode = BlendMode.Plus,
    )
    drawOval(
        color = Color.White.copy(alpha = 0.55f),
        topLeft = Offset(x - r * 0.16f, y + r * 0.52f),
        size = Size(r * 0.32f, flameH * 0.62f),
        blendMode = BlendMode.Plus,
    )
}

/** 擦弹火花：玩家周围青色扩散环，短促且不遮挡机体。[reduceMotion] 时不扩散（半径固定，仅淡出）。 */
internal fun DrawScope.drawGrazeRing(
    game: PlaneGame,
    state: PlaneFrameState,
    playerRadius: Float,
    reduceMotion: Boolean = false,
) {
    if (state.grazeTicks <= 0) return
    val scale = game.viewScale
    if (scale <= 0f) return
    val p = state.grazeTicks / GRAZE_FX_TICKS.toFloat()
    // 减弱动画：扩散半径固定在起始值，只保留淡出——保留"擦弹了"的反馈但去掉位移/缩放
    val spread = if (reduceMotion) 0f else 1f - p
    val x = game.toScreenX(game.player.x)
    val y = game.toScreenY(game.player.y)
    val r = playerRadius * scale
    drawCircle(
        color = PlaneUi.accent.copy(alpha = 0.55f * p),
        radius = r * (1.15f + spread * 1.7f),
        center = Offset(x, y),
        style = PlaneStroke.of(r * 0.10f),
        blendMode = BlendMode.Plus,
    )
    drawCircle(
        color = Color.White.copy(alpha = 0.45f * p),
        radius = r * (0.95f + spread * 0.9f),
        center = Offset(x, y),
        style = PlaneStroke.of(r * 0.05f),
        blendMode = BlendMode.Plus,
    )
}

/** 道具拾取光：金色扩散环 + 八向短光刺，与擦弹环区分；[reduceMotion] 时冻结光刺朝向。 */
internal fun DrawScope.drawPickupBurst(
    game: PlaneGame,
    state: PlaneFrameState,
    playerRadius: Float,
    reduceMotion: Boolean,
) {
    if (state.pickupTicks <= 0) return
    val scale = game.viewScale
    if (scale <= 0f) return
    val p = state.pickupTicks / PICKUP_FX_TICKS.toFloat()
    val x = game.toScreenX(game.player.x)
    val y = game.toScreenY(game.player.y)
    val r = playerRadius * scale
    val gold = PlaneUi.accentWarm
    drawCircle(
        color = gold.copy(alpha = 0.55f * p),
        radius = r * (1.0f + (1f - p) * 2.2f),
        center = Offset(x, y),
        style = PlaneStroke.of(r * 0.09f),
        blendMode = BlendMode.Plus,
    )
    val outer = r * (1.1f + (1f - p) * 1.9f)
    val spin = if (reduceMotion) 0f else state.visualTick * 0.03f
    for (k in 0 until 8) {
        val a = k * 0.7854f + spin
        val ca = cos(a)
        val sa = sin(a)
        drawLine(
            color = Color.White.copy(alpha = 0.55f * p),
            start = Offset(x + ca * outer * 0.65f, y + sa * outer * 0.65f),
            end = Offset(x + ca * outer, y + sa * outer),
            strokeWidth = r * 0.05f,
            cap = StrokeCap.Round,
            blendMode = BlendMode.Plus,
        )
    }
}

// ---------------------------------------------------------------------
// 全屏特效：炸弹 / 受击 / Boss 预警 / 结算彩屑 / 暗角
// ---------------------------------------------------------------------

/** 炸弹：全屏白闪 + 两道冲击波；[reduceMotion] 时整段跳过。 */
internal fun DrawScope.drawBombBlast(
    state: PlaneFrameState,
    reduceMotion: Boolean,
) {
    if (reduceMotion || state.bombTicks <= 0) return
    val p = 1f - state.bombTicks / BOMB_FX_TICKS.toFloat()
    val fade = (1f - p) * (1f - p)
    drawRect(Color.White.copy(alpha = 0.62f * fade))
    val cx = size.width * 0.5f
    val cy = size.height * 0.5f
    val maxR = size.maxDimension
    // 低频特效：宽度每帧连续变化，直接用普通 Stroke，避免打穿 PlaneStroke 缓存（0.1px 步进）
    drawCircle(
        color = Color(0xFFFFF59D).copy(alpha = 0.55f * fade),
        radius = maxR * 0.08f + p * maxR * 0.52f,
        center = Offset(cx, cy),
        style = Stroke((maxR * 0.022f * fade).coerceAtLeast(2f)),
        blendMode = BlendMode.Plus,
    )
    val p2 = (p - 0.18f).coerceAtLeast(0f)
    if (p2 > 0f) {
        val fade2 = (1f - p2) * (1f - p2)
        drawCircle(
            color = Color(0xFF80DEEA).copy(alpha = 0.42f * fade2),
            radius = maxR * 0.06f + p2 * maxR * 0.42f,
            center = Offset(cx, cy),
            style = Stroke((maxR * 0.016f * fade2).coerceAtLeast(2f)),
            blendMode = BlendMode.Plus,
        )
    }
}

/** 玩家受击：红色边缘警示（中心保持透明，不遮挡观察弹幕）。 */
internal fun DrawScope.drawHitFlash(
    state: PlaneFrameState,
    reduceMotion: Boolean,
) {
    if (reduceMotion || state.hitTicks <= 0) return
    val p = state.hitTicks / HIT_FX_TICKS.toFloat()
    drawRect(PlaneUi.danger.copy(alpha = 0.16f * p))
    drawRect(
        color = Color(0xFFFF1744).copy(alpha = 0.42f * p),
        topLeft = Offset.Zero,
        size = Size(size.width, size.height * 0.12f),
    )
    drawRect(
        color = Color(0xFFFF1744).copy(alpha = 0.42f * p),
        topLeft = Offset(0f, size.height * 0.88f),
        size = Size(size.width, size.height * 0.12f),
    )
    // 左右侧边也闪一下，受击感更「包住」屏幕
    drawRect(
        color = Color(0xFFFF1744).copy(alpha = 0.22f * p),
        topLeft = Offset.Zero,
        size = Size(size.width * 0.06f, size.height),
    )
    drawRect(
        color = Color(0xFFFF1744).copy(alpha = 0.22f * p),
        topLeft = Offset(size.width * 0.94f, 0f),
        size = Size(size.width * 0.06f, size.height),
    )
}

/** Boss 预警：红色呼吸闪烁 + 上下警示带（横幅文案由 HUD 卡片承担）。 */
internal fun DrawScope.drawBossWarningFlash(
    warningTicks: Int,
    totalTicks: Int,
    visualTick: Long,
    reduceMotion: Boolean,
) {
    if (warningTicks <= 0 || totalTicks <= 0) return
    if (reduceMotion) {
        drawRect(Color(0xFFFF1744).copy(alpha = 0.06f))
        return
    }
    val pulse = 0.5f + 0.5f * sin(visualTick * 0.42f)
    drawRect(Color(0xFFFF1744).copy(alpha = 0.05f + 0.10f * pulse))
    val band = size.height * 0.045f
    drawRect(Color(0xFFFF1744).copy(alpha = 0.22f + 0.16f * pulse), Offset.Zero, Size(size.width, band))
    drawRect(
        Color(0xFFFF1744).copy(alpha = 0.22f + 0.16f * pulse),
        Offset(0f, size.height - band),
        Size(size.width, band),
    )
    for (i in 0 until 12) {
        val x = (i - 6f) * size.width * 0.09f + (visualTick % 90L) * 0.6f
        val bx = planeMod(x, size.width + 60f) - 30f
        drawLine(
            Color(0x66FFFFFF),
            Offset(bx, 0f),
            Offset(bx - band * 0.7f, band),
            strokeWidth = band * 0.16f,
            cap = StrokeCap.Square,
        )
        drawLine(
            Color(0x66FFFFFF),
            Offset(bx, size.height - band),
            Offset(bx - band * 0.7f, size.height),
            strokeWidth = band * 0.16f,
            cap = StrokeCap.Square,
        )
    }
}

/** 结算彩屑：胜利时从顶部飘落的确定性彩片。 */
internal fun DrawScope.drawPlaneConfetti(
    visualTick: Long,
    reduceMotion: Boolean,
) {
    if (reduceMotion) return
    val w = size.width
    val h = size.height
    val span = h + 60f
    for (i in 0 until 30) {
        val h1 = planeHash01(i, 301)
        val h2 = planeHash01(i, 507)
        val speed = h1 * 0.10f + h2 * 0.16f + 40f
        val y = planeMod(visualTick * speed * 0.35f + h2 * span, span) - 30f
        val x = h1 * w + sin(visualTick * 0.03f + i) * 18f
        val side = 4f + h2 * 7f
        drawRect(
            color = Color(planeConfettiColors[i % planeConfettiColors.size]).copy(alpha = 0.45f + 0.45f * h2),
            topLeft = Offset(x, y),
            size = Size(side, side * 1.6f),
        )
    }
}

private val planeConfettiColors =
    intArrayOf(
        0xFFFFC46B.toInt(),
        0xFF7FE3F2.toInt(),
        0xFFFFFFFF.toInt(),
        0xFFFF8A65.toInt(),
    )

/** 顶部 / 底部暗角笔刷缓存（尺寸不变时复用，避免每帧 new Brush + list）。 */
private class PlaneVignetteCache {
    private var w = -1f
    private var h = -1f
    private var top: Brush? = null
    private var bottom: Brush? = null

    fun top(
        w: Float,
        h: Float,
    ): Brush {
        ensure(w, h)
        return top!!
    }

    fun bottom(
        w: Float,
        h: Float,
    ): Brush {
        ensure(w, h)
        return bottom!!
    }

    private fun ensure(
        w: Float,
        h: Float,
    ) {
        if (this.w == w && this.h == h && top != null) return
        this.w = w
        this.h = h
        val band = (h * 0.20f).coerceAtLeast(1f)
        top =
            Brush.verticalGradient(
                colors = listOf(Color(0x8C03070D), Color(0x0003070D)),
                startY = 0f,
                endY = band,
            )
        bottom =
            Brush.verticalGradient(
                colors = listOf(Color(0x0003070D), Color(0x8C03070D)),
                startY = h - band,
                endY = h,
            )
    }
}

private val planeVignetteCache = PlaneVignetteCache()

/** 上下暗角：压住天空高亮区，保证 HUD 与弹幕边缘可读。 */
internal fun DrawScope.drawPlaneVignette() {
    val w = size.width
    val h = size.height
    if (w <= 0f || h <= 0f) return
    val band = (h * 0.20f).coerceAtLeast(1f)
    drawRect(planeVignetteCache.top(w, h), Offset.Zero, Size(w, band))
    drawRect(planeVignetteCache.bottom(w, h), Offset(0f, h - band), Size(w, band))
}

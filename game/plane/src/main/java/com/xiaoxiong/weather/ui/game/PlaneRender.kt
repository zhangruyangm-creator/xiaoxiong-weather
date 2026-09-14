package com.xiaoxiong.weather.ui.game

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import com.xiaoxiong.weather.ui.weather.WeatherScene
import com.xiaoxiong.weather.ui.weather.WeatherSceneStyle
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

/**
 * 小熊战机世界渲染入口。
 *
 * 分层：
 *  1. 天空静态层（渐变 + 星空 / 日月 + 远山 + 海面 + 地平线辉光）→ 整屏烘焙 [ImageBitmap]，
 *     键 = 画布尺寸 + 场景样式 + 昼夜，2 槽 LRU，先淘汰再分配 + runCatching 降级；
 *  2. 视差层：中景岛屿 / 城市 / 云带（中速）、近景薄云 / 飞鸟（快速），全部程序绘制，
 *     由 [PlaneFrameState.visualTick] 驱动（开新局归零，重置后一致）；
 *  3. 实体层：玩家机 / 敌机 / Boss / 道具（烘焙精灵 + 动态螺旋桨 / 炮塔 / 核心辉光）；
 *  4. 弹幕层：玩家子弹（圆 / 胶囊激光）与敌弹（圆 / 胶囊 / 矩形，按类型配色 + 边缘高光）；
 *  5. 特效层：爆炸粒子、擦弹火花、炸弹白闪冲击波、受击红闪、Boss 预警、结算彩屑、暗角；
 *  6. 天气层：复用 common GameWeatherFx（雨 / 冻雨 / 冰雹 / 雷电）与本地雪 / 雾。
 *
 * 热路径只用 drawCircle / drawLine / drawRect / drawImage，不 new Path / Brush / Paint / list。
 */
internal fun DrawScope.drawPlaneWorld(
    game: PlaneGame,
    state: PlaneFrameState,
    style: WeatherSceneStyle,
    isDay: Boolean,
    weather: FarmWeather,
    rain: RainStrength,
    freezingRain: Boolean,
    hail: Boolean,
    reduceMotion: Boolean,
) {
    if (size.width <= 0f || size.height <= 0f) return
    val scale = game.viewScale
    if (scale <= 0f) return
    val time = state.visualTick / 60f
    val tick = state.visualTick

    // 炸弹 / Boss 死亡的震屏：只抖世界，HUD 保持稳定；reduceMotion 关闭
    val shakeAmp =
        if (reduceMotion || state.shakeTicks <= 0) {
            0f
        } else {
            (state.shakeTicks / SHAKE_FX_TICKS.toFloat()) * size.minDimension * 0.014f
        }
    val shakeX = sin(state.visualTick * 2.7f) * shakeAmp
    val shakeY = cos(state.visualTick * 3.3f) * shakeAmp

    withTransform({ translate(shakeX, shakeY) }) {
        // 1. 静态天空
        val layer = PlaneSkyCache.bitmap(style, isDay, size, this, layoutDirection)
        if (layer != null) {
            drawImage(
                image = layer.bitmap,
                srcOffset = IntOffset.Zero,
                srcSize = IntSize(layer.key.width, layer.key.height),
                dstOffset = IntOffset.Zero,
                dstSize = IntSize(layer.key.width, layer.key.height),
                filterQuality = FilterQuality.Low,
            )
        } else {
            drawPlaneSkyStatic(style, isDay)
        }
        // 保证白字 / 弹幕可读的 scrim（浅色场景由 WeatherSceneStyle.scrimAlpha 提供）
        if (style.scrimAlpha > 0.001f) {
            drawRect(Color.Black.copy(alpha = style.scrimAlpha))
        }

        // 2. 视差层
        drawPlaneMidLayer(time, scale, isDay, weather, reduceMotion)
        drawPlaneNearLayer(time, scale, isDay, weather, reduceMotion)

        // 3. 实体 + 4. 弹幕
        drawPowerUps(game, isDay, tick, reduceMotion)
        drawEnemies(game, state, isDay, tick, reduceMotion)
        drawPlayerTrailAndFlame(game, state, PLANE_PLAYER_VISUAL_RADIUS, reduceMotion)
        drawPlayer(game, state, isDay, reduceMotion)
        drawPlayerBullets(game)
        drawEnemyBullets(game, tick)

        // 5. 粒子与天气
        drawPlaneEffects(game, reduceMotion)
        drawGrazeRing(game, state, PLANE_PLAYER_VISUAL_RADIUS)
        drawPickupBurst(game, state, PLANE_PLAYER_VISUAL_RADIUS, reduceMotion)
        drawPlaneWeather(weather, rain, freezingRain, hail, time, reduceMotion)
        drawBombBlast(state, reduceMotion)
        drawHitFlash(state, reduceMotion)
        drawBossWarningFlash(
            warningTicks = game.bossWarningTicks,
            totalTicks = PlaneConfig.BOSS_WARNING_TICKS,
            visualTick = tick,
            reduceMotion = reduceMotion,
        )
        if (game.phase == PlanePhase.SETTLED && game.player.alive) {
            drawPlaneConfetti(tick, reduceMotion)
        }
        drawPlaneVignette()
    }
}

/** 释放全屏静态层与实体精灵缓存；退出游戏时调用，二次进入会按需重建。 */
internal fun clearPlaneRenderCaches() {
    PlaneSkyCache.clear()
    PlaneSprites.clear()
}

// ---------------------------------------------------------------------
// 静态天空层
// ---------------------------------------------------------------------

internal data class PlaneSkyKey(
    val width: Int,
    val height: Int,
    val style: WeatherSceneStyle,
    val isDay: Boolean,
)

private class PlaneSkyLayer(
    val bitmap: ImageBitmap,
    val key: PlaneSkyKey,
)

private object PlaneSkyCache {
    private const val MAX_ENTRIES = 2
    private val entries = ArrayList<PlaneSkyLayer>(MAX_ENTRIES)

    fun bitmap(
        style: WeatherSceneStyle,
        isDay: Boolean,
        size: Size,
        density: Density,
        layoutDirection: LayoutDirection,
    ): PlaneSkyLayer? {
        val w = ceil(size.width).toInt()
        val h = ceil(size.height).toInt()
        if (w <= 0 || h <= 0) return null
        val key = PlaneSkyKey(w, h, style, isDay)
        var i = entries.size - 1
        while (i >= 0) {
            val e = entries[i]
            if (e.key == key) {
                entries.removeAt(i)
                entries.add(e)
                return e
            }
            i--
        }
        // 先淘汰最旧再分配：限制常驻位图数量；分配 / 绘制失败返回 null 走直接绘制降级
        if (entries.size >= MAX_ENTRIES) entries.removeAt(0)
        val bitmap = runCatching { ImageBitmap(w, h) }.getOrNull() ?: return null
        runCatching {
            CanvasDrawScope().draw(density, layoutDirection, Canvas(bitmap), size) {
                drawPlaneSkyStatic(style, isDay)
            }
        }.onFailure { return null }
        val layer = PlaneSkyLayer(bitmap, key)
        entries.add(layer)
        return layer
    }

    fun clear() {
        entries.clear()
    }
}

/** 静态天空：天气渐变 + 星空 / 日月 + 远山 / 海面 + 地平线辉光 + 静态云带（只在缓存未命中时执行）。 */
private fun DrawScope.drawPlaneSkyStatic(
    style: WeatherSceneStyle,
    isDay: Boolean,
) {
    val w = size.width
    val h = size.height
    val backdrop = planeBackdrop(style)
    drawRect(Brush.verticalGradient(backdrop))

    val horizon = h * 0.70f
    // 星空（夜晚）
    if (!isDay) {
        for (i in 0 until 64) {
            val hx = planeHash01(i, 11) * w
            val hy = planeHash01(i, 23) * horizon * 0.9f
            val alpha = 0.10f + planeHash01(i, 37) * 0.55f
            val r = 0.8f + planeHash01(i, 53) * 1.3f
            drawCircle(Color.White.copy(alpha = alpha), r, Offset(hx, hy))
        }
    }
    // 日月（雨 / 雾 / 雷暴天压暗或隐藏）
    val obscuring =
        when (style.scene) {
            WeatherScene.RAIN,
            WeatherScene.HEAVY_RAIN,
            WeatherScene.THUNDER,
            WeatherScene.FOG,
            -> true
            else -> false
        }
    if (!obscuring) {
        val cx = w * 0.78f
        val cy = h * 0.13f
        val glowColor = if (isDay) Color(0xFFFFE082) else Color(0xFFDDE7FF)
        for (i in 0 until 5) {
            val r = w * (0.30f - i * 0.05f)
            drawCircle(glowColor.copy(alpha = if (i == 4) 0.16f else 0.055f), r, Offset(cx, cy))
        }
        drawCircle(glowColor.copy(alpha = if (isDay) 0.95f else 0.90f), w * 0.055f, Offset(cx, cy))
        if (!isDay) {
            drawCircle(
                Color(0xFFC3CEE6).copy(alpha = 0.55f),
                w * 0.014f,
                Offset(cx - w * 0.018f, cy - h * 0.012f),
            )
            drawCircle(
                Color(0xFFC3CEE6).copy(alpha = 0.45f),
                w * 0.010f,
                Offset(cx + w * 0.016f, cy + h * 0.010f),
            )
        }
    }
    // 静态远云带
    val cloudTone =
        if (isDay) {
            Color.White
        } else {
            Color(0xFF8FA6C8)
        }
    for (i in 0 until 5) {
        val cx = planeHash01(i, 101) * w
        val cy = h * (0.06f + planeHash01(i, 113) * 0.26f)
        val s = w * (0.10f + planeHash01(i, 127) * 0.10f)
        val alpha = 0.05f + planeHash01(i, 139) * 0.07f
        drawCircle(cloudTone.copy(alpha = alpha), s, Offset(cx, cy))
        drawCircle(cloudTone.copy(alpha = alpha), s * 0.78f, Offset(cx + s * 0.75f, cy + s * 0.16f))
        drawCircle(cloudTone.copy(alpha = alpha), s * 0.66f, Offset(cx - s * 0.80f, cy + s * 0.22f))
    }

    // 海面 / 地面：地平线以下铺一层更深的水色
    val seaTop = lerp(backdrop[2], Color(0xFF061423), 0.55f)
    drawRect(
        Brush.verticalGradient(listOf(seaTop, Color(0xFF03080F))),
        topLeft = Offset(0f, horizon),
        size = Size(w, h - horizon),
    )
    // 远山轮廓（两排，程序三角）
    drawMountainRange(
        baseY = horizon,
        height = h * 0.10f,
        color = lerp(backdrop[1], Color(0xFF101B2A), 0.45f),
        seed = 7,
    )
    drawMountainRange(
        baseY = horizon,
        height = h * 0.065f,
        color = lerp(backdrop[1], Color(0xFF1A2738), 0.20f),
        seed = 71,
    )
    // 夜晚远城灯火
    if (!isDay) {
        for (i in 0 until 16) {
            val bx = planeHash01(i, 211) * w
            val bh = h * (0.012f + planeHash01(i, 223) * 0.030f)
            drawRect(
                Color(0xFF0A1220).copy(alpha = 0.9f),
                topLeft = Offset(bx, horizon - bh),
                size = Size(w * 0.020f, bh),
            )
            drawCircle(Color(0xFFFFD54F).copy(alpha = 0.8f), w * 0.0035f, Offset(bx + w * 0.008f, horizon - bh * 0.6f))
        }
    }
    // 海面波纹
    for (i in 0 until 22) {
        val y = horizon + (h - horizon) * planeHash01(i, 307)
        val x = planeHash01(i, 311) * w
        val len = w * (0.03f + planeHash01(i, 313) * 0.08f)
        drawLine(
            Color.White.copy(alpha = 0.05f + planeHash01(i, 317) * 0.07f),
            Offset(x, y),
            Offset(x + len, y),
            strokeWidth = 1.2f,
        )
    }
    // 地平线辉光
    drawHorizonGlow(horizon, lerp(style.gradient.first(), Color.White, 0.35f), h * 0.16f)
}

private fun DrawScope.drawMountainRange(
    baseY: Float,
    height: Float,
    color: Color,
    seed: Int,
) {
    val w = size.width
    val path = Path()
    path.moveTo(0f, baseY)
    val peaks = 9
    for (i in 0..peaks) {
        val x = w * i / peaks
        val peak = baseY - height * (0.35f + planeHash01(seed + i, 19) * 0.65f)
        path.lineTo(x - w / (peaks * 2f), peak)
        path.lineTo(x, baseY)
    }
    path.lineTo(w, baseY)
    path.lineTo(w, baseY + height)
    path.lineTo(0f, baseY + height)
    path.close()
    drawPath(path, color)
}

// ---------------------------------------------------------------------
// 视差层（中景 / 近景）
// ---------------------------------------------------------------------

private fun DrawScope.drawPlaneMidLayer(
    time: Float,
    scale: Float,
    isDay: Boolean,
    weather: FarmWeather,
    reduceMotion: Boolean,
) {
    val w = size.width
    val h = size.height
    val speed = if (reduceMotion) 0f else 5.5f * scale
    val period = h + 320f * scale
    val offset = time * speed
    val night = !isDay
    for (i in 0 until 3) {
        val x = planeHash01(i, 401) * (w + 240f * scale) - 120f * scale
        val y = planeMod(planeHash01(i, 409) * period + offset, period) - 160f * scale
        when (weather) {
            FarmWeather.SNOWY -> drawSnowIsland(x, y, scale, night)
            FarmWeather.FOGGY -> drawFogBank(x, y, scale)
            FarmWeather.RAINY, FarmWeather.STORMY -> drawStormBank(x, y, scale, night)
            FarmWeather.SUNNY, FarmWeather.CLOUDY -> drawGreenIsland(x, y, scale, night)
        }
    }
    if (night && weather != FarmWeather.SNOWY && weather != FarmWeather.FOGGY) {
        drawNightCity(time, scale, reduceMotion)
    }
}

private fun DrawScope.drawGreenIsland(
    x: Float,
    y: Float,
    scale: Float,
    night: Boolean,
) {
    val s = 26f * scale
    val sand = if (night) Color(0xFF6D6142) else Color(0xFFC9B87C)
    val green = if (night) Color(0xFF1F3A2C) else Color(0xFF3F7A4C)
    drawOval(sand.copy(alpha = 0.85f), topLeft = Offset(x - s * 1.4f, y - s * 0.45f), size = Size(s * 2.8f, s * 0.9f))
    drawOval(green, topLeft = Offset(x - s * 1.1f, y - s * 0.8f), size = Size(s * 2.2f, s * 0.9f))
    // 椰树
    drawLine(Color(0xFF4E3A22), Offset(x, y - s * 0.5f), Offset(x + s * 0.12f, y - s * 1.25f), strokeWidth = s * 0.10f)
    for (k in -1..1) {
        drawLine(
            green,
            Offset(x + s * 0.12f, y - s * 1.25f),
            Offset(x + s * 0.12f + k * s * 0.48f, y - s * (1.45f - abs(k) * 0.12f)),
            strokeWidth = s * 0.09f,
            cap = StrokeCap.Round,
        )
    }
}

private fun DrawScope.drawSnowIsland(
    x: Float,
    y: Float,
    scale: Float,
    night: Boolean,
) {
    val s = 30f * scale
    val iceTop = if (night) Color(0xFF3E5A6E) else Color(0xFFE3F2FD)
    val iceSide = if (night) Color(0xFF22384A) else Color(0xFF9CC3DC)
    drawOval(iceSide.copy(alpha = 0.9f), topLeft = Offset(x - s * 1.5f, y - s * 0.35f), size = Size(s * 3.0f, s * 0.8f))
    drawOval(iceTop, topLeft = Offset(x - s * 1.1f, y - s * 0.75f), size = Size(s * 2.2f, s * 0.85f))
    drawLine(iceTop, Offset(x - s * 0.3f, y - s * 0.7f), Offset(x, y - s * 1.3f), strokeWidth = s * 0.16f)
    drawLine(iceTop, Offset(x + s * 0.3f, y - s * 0.7f), Offset(x, y - s * 1.3f), strokeWidth = s * 0.16f)
}

private fun DrawScope.drawFogBank(
    x: Float,
    y: Float,
    scale: Float,
) {
    val s = 42f * scale
    drawOval(Color.White.copy(alpha = 0.06f), topLeft = Offset(x - s * 1.6f, y - s * 0.4f), size = Size(s * 3.2f, s * 0.9f))
    drawOval(Color.White.copy(alpha = 0.05f), topLeft = Offset(x - s * 1.1f, y - s * 0.85f), size = Size(s * 2.4f, s * 0.85f))
}

private fun DrawScope.drawStormBank(
    x: Float,
    y: Float,
    scale: Float,
    night: Boolean,
) {
    val s = 44f * scale
    val tone = if (night) Color(0xFF20293A) else Color(0xFF38455A)
    drawOval(tone.copy(alpha = 0.80f), topLeft = Offset(x - s * 1.7f, y - s * 0.55f), size = Size(s * 3.4f, s * 1.1f))
    drawOval(tone.copy(alpha = 0.60f), topLeft = Offset(x - s * 1.1f, y - s * 1.0f), size = Size(s * 2.3f, s * 0.9f))
    // 云下细雨线
    for (i in 0 until 4) {
        val lx = x - s * 1.2f + i * s * 0.8f
        drawLine(
            Color(0xFF9CC3DC).copy(alpha = 0.18f),
            Offset(lx, y + s * 0.25f),
            Offset(lx - s * 0.06f, y + s * 0.75f),
            strokeWidth = 1.4f * scale,
        )
    }
}

private fun DrawScope.drawNightCity(
    time: Float,
    scale: Float,
    reduceMotion: Boolean,
) {
    val w = size.width
    val h = size.height
    val horizon = h * 0.70f
    // reduceMotion：冻结横向滚动，城市保持静态帧
    val speed = if (reduceMotion) 0f else 3.2f * scale
    val period = w + 420f * scale
    val offset = time * speed
    for (i in 0 until 7) {
        val bw = w * (0.055f + planeHash01(i, 601) * 0.05f)
        val bh = h * (0.035f + planeHash01(i, 607) * 0.055f)
        val x = planeMod(planeHash01(i, 613) * period + offset, period) - 210f * scale
        drawRect(
            Color(0xFF0B1524).copy(alpha = 0.92f),
            topLeft = Offset(x, horizon - bh),
            size = Size(bw, bh),
        )
        for (k in 0 until 4) {
            val lit = planeHash01(i * 7 + k, 619)
            if (lit < 0.55f) continue
            drawRect(
                Color(0xFFFFD54F).copy(alpha = 0.55f + lit * 0.35f),
                topLeft = Offset(x + bw * (0.15f + k * 0.2f), horizon - bh * 0.75f),
                size = Size(bw * 0.09f, bh * 0.18f),
            )
        }
    }
}

private fun DrawScope.drawPlaneNearLayer(
    time: Float,
    scale: Float,
    isDay: Boolean,
    weather: FarmWeather,
    reduceMotion: Boolean,
) {
    val w = size.width
    val h = size.height
    val period = h + 260f * scale
    val offset = if (reduceMotion) 0f else time * 15f * scale
    val tone = if (isDay) Color.White else Color(0xFF9FB4D8)
    for (i in 0 until 3) {
        val x = planeHash01(i, 701) * (w + 200f * scale) - 100f * scale
        val y = planeMod(planeHash01(i, 709) * period + offset, period) - 130f * scale
        val s = (30f + planeHash01(i, 719) * 22f) * scale
        drawOval(tone.copy(alpha = 0.10f), topLeft = Offset(x - s * 1.6f, y - s * 0.22f), size = Size(s * 3.2f, s * 0.44f))
        drawOval(tone.copy(alpha = 0.08f), topLeft = Offset(x - s * 1.1f, y - s * 0.5f), size = Size(s * 2.2f, s * 0.42f))
    }
    val birds = weather == FarmWeather.SUNNY || weather == FarmWeather.CLOUDY
    if (birds && !reduceMotion) {
        for (i in 0 until 3) {
            val x = planeHash01(i, 801) * (w + 240f * scale) - 120f * scale
            val y = planeMod(planeHash01(i, 809) * period + offset * 1.35f, period) - 110f * scale
            val s = (7f + planeHash01(i, 811) * 5f) * scale
            val flap = sin(time * 5.5f + i * 1.7f) * s * 0.35f
            val birdColor = if (isDay) Color(0xFF37474F).copy(alpha = 0.65f) else Color(0xFFB0BEC5).copy(alpha = 0.55f)
            drawLine(birdColor, Offset(x - s, y + flap), Offset(x, y), strokeWidth = s * 0.22f, cap = StrokeCap.Round)
            drawLine(birdColor, Offset(x, y), Offset(x + s, y + flap), strokeWidth = s * 0.22f, cap = StrokeCap.Round)
        }
    }
}

// ---------------------------------------------------------------------
// 实体：道具 / 敌机 / 玩家
// ---------------------------------------------------------------------

/** 玩家机视觉半径（逻辑单位）：约 2.75 倍判定半径，机身细节可辨。 */
internal const val PLANE_PLAYER_VISUAL_RADIUS = PlaneConfig.PLAYER_RADIUS * 2.75f

/** 敌机视觉半径（逻辑单位）：小怪放大 1.7 倍便于辨认轮廓，Boss 已很大只放大 1.15。 */
internal fun planeEnemyVisualRadius(type: PlaneEnemyType): Float = type.baseRadius * (if (type == PlaneEnemyType.BOSS) 1.15f else 1.7f)

private fun DrawScope.drawPowerUps(
    game: PlaneGame,
    isDay: Boolean,
    tick: Long,
    reduceMotion: Boolean,
) {
    if (game.powerUps.isEmpty()) return
    val scale = game.viewScale
    val radius = PlaneConfig.POWERUP_RADIUS * 1.9f
    for (i in 0 until game.powerUps.size) {
        val p = game.powerUps[i]
        val x = game.toScreenX(p.x)
        val y = game.toScreenY(p.y)
        val r = radius * scale
        drawPowerUpGlow(x, y, r, p.kind, tick, reduceMotion)
        val sprite = PlaneSprites.powerUp(p.kind, r, isDay, this, layoutDirection)
        if (sprite != null) {
            drawPlaneSprite(sprite, x, y)
        } else {
            val color = if (p.kind == PlanePowerUpKind.WEAPON) PlaneUi.ok else Color(0xFFFFD740)
            drawCircle(color.copy(alpha = 0.25f), r * 1.2f, Offset(x, y))
            drawCircle(color, r * 0.85f, Offset(x, y))
        }
    }
}

private fun DrawScope.drawEnemies(
    game: PlaneGame,
    state: PlaneFrameState,
    isDay: Boolean,
    tick: Long,
    reduceMotion: Boolean,
) {
    if (game.enemies.isEmpty()) return
    val scale = game.viewScale
    for (i in 0 until game.enemies.size) {
        val enemy = game.enemies[i]
        val x = game.toScreenX(enemy.x)
        val y = game.toScreenY(enemy.y)
        val r = planeEnemyVisualRadius(enemy.type) * scale
        val flash = state.flashAlpha(enemy, game.tickCount)
        val sprite = PlaneSprites.enemy(enemy.type, enemy.bossPhase, r, isDay, this, layoutDirection)
        if (sprite != null) {
            val wobble =
                if (reduceMotion || enemy.isBoss || enemy.type == PlaneEnemyType.TANK) {
                    // Boss / 地面装甲车不做空中悬浮摆动；reduceMotion 冻结悬浮摆动
                    0f
                } else {
                    sin(tick * 0.05f + enemy.id) * 0.7f * scale
                }
            drawPlaneSprite(sprite, x + wobble, y, flashAlpha = flash)
            if (enemy.isBoss) drawBossDynamic(enemy, x + wobble, y, r, tick, reduceMotion)
        } else {
            val palette = planeEnemyPalette(enemy.type, enemy.bossPhase)
            drawCircle(palette.dark, r, Offset(x, y))
            drawCircle(palette.body, r * 0.8f, Offset(x, y))
            if (flash > 0.01f) drawCircle(Color.White.copy(alpha = flash), r * 0.8f, Offset(x, y))
        }
    }
}

/** Boss 动态部件：旋转侧炮塔 + 核心辉光（阶段越高越红 / 越亮；[reduceMotion] 时冻结静态帧）。 */
private fun DrawScope.drawBossDynamic(
    boss: PlaneEnemy,
    x: Float,
    y: Float,
    r: Float,
    tick: Long,
    reduceMotion: Boolean,
) {
    val phase = boss.bossPhase.coerceIn(0, 2)
    val coreColor =
        when (phase) {
            0 -> Color(0xFF7FE3F2)
            1 -> Color(0xFFFFD54F)
            else -> Color(0xFFFF5252)
        }
    val pulse = if (reduceMotion) 0.5f else 0.5f + 0.5f * sin(tick * 0.11f + phase)
    // 侧炮塔（左右两侧，避免每帧创建数组 / 迭代器）；reduceMotion 冻结朝向
    val barrelAngle = if (reduceMotion) 180f else sin(tick * 0.035f) * 26f + 180f
    for (i in 0 until 2) {
        val side = if (i == 0) -1f else 1f
        val tx = x + side * r * 0.60f
        val ty = y + r * 0.34f
        withTransform({ rotate(barrelAngle, pivot = Offset(tx, ty)) }) {
            drawRoundRect(
                color = Color(0xFF3A4658),
                topLeft = Offset(tx - r * 0.05f, ty),
                size = Size(r * 0.10f, r * 0.34f),
                cornerRadius = CornerRadius(r * 0.04f),
            )
            drawCircle(Color(0xFF2A3442), r * 0.055f, Offset(tx, ty + r * 0.34f))
        }
    }
    // 核心
    val coreY = y + r * 0.24f
    drawCircle(
        color = coreColor.copy(alpha = (0.20f + 0.22f * pulse)),
        radius = r * (0.30f + 0.05f * pulse),
        center = Offset(x, coreY),
        blendMode = BlendMode.Plus,
    )
    drawCircle(
        color = Color.White.copy(alpha = 0.55f + 0.35f * pulse),
        radius = r * 0.10f,
        center = Offset(x, coreY),
        blendMode = BlendMode.Plus,
    )
    if (phase >= 1) {
        drawCircle(
            color = coreColor.copy(alpha = 0.40f + 0.25f * pulse),
            radius = r * (0.40f + 0.06f * pulse),
            center = Offset(x, coreY),
            style = PlaneStroke.of(r * 0.028f),
            blendMode = BlendMode.Plus,
        )
    }
    // 阶段 2：外露能量裂缝
    if (phase == 2) {
        for (k in 0 until 3) {
            val a = if (reduceMotion) k * 2.094f else tick * 0.06f + k * 2.094f
            drawLine(
                Color(0xFFFF5252).copy(alpha = 0.55f * pulse),
                Offset(x, coreY),
                Offset(x + cos(a) * r * 0.55f, coreY + sin(a) * r * 0.42f),
                strokeWidth = r * 0.035f,
                cap = StrokeCap.Round,
                blendMode = BlendMode.Plus,
            )
        }
    }
}

private fun DrawScope.drawPlayer(
    game: PlaneGame,
    state: PlaneFrameState,
    isDay: Boolean,
    reduceMotion: Boolean,
) {
    if (!game.player.alive) return
    val scale = game.viewScale
    val invuln = game.player.invulnTicks > 0
    val blink = invuln && !reduceMotion && (state.visualTick / 4) % 2 == 0L
    val x = game.toScreenX(game.player.x)
    val y = game.toScreenY(game.player.y)
    val r = PLANE_PLAYER_VISUAL_RADIUS * scale
    if (!blink) {
        val sprite = PlaneSprites.player(r, isDay, this, layoutDirection)
        val flash = if (state.hitTicks > 0) state.hitTicks / HIT_FX_TICKS.toFloat() else 0f
        if (sprite != null) {
            drawPlaneSprite(
                sprite = sprite,
                cx = x,
                cy = y,
                rotationDeg = state.tilt * 26f,
                scaleY = 1f - abs(state.tilt) * 0.16f,
                flashAlpha = flash * 0.85f,
            )
        } else {
            drawCircle(Color.White, r * 0.7f, Offset(x, y))
            drawCircle(Color(0xFF40C4FF), r * 0.45f, Offset(x, y))
        }
    }
    // 螺旋桨（动态）：旋转桨叶 + 运动模糊盘
    val propY = y - r * 0.78f
    drawOval(
        Color.White.copy(alpha = 0.12f),
        topLeft = Offset(x - r * 0.80f, propY - r * 0.10f),
        size = Size(r * 1.60f, r * 0.20f),
    )
    val propAngle = if (reduceMotion) 0f else (state.visualTick * 46f) % 360f
    withTransform({ rotate(propAngle, pivot = Offset(x, propY)) }) {
        drawLine(
            planePlayerBrownDark,
            Offset(x - r * 0.78f, propY),
            Offset(x + r * 0.78f, propY),
            strokeWidth = r * 0.10f,
            cap = StrokeCap.Round,
        )
    }
    // 无敌护盾
    if (invuln && !reduceMotion) {
        val pulse = 0.5f + 0.5f * sin(state.visualTick * 0.30f)
        drawCircle(
            color = PlaneUi.accent.copy(alpha = 0.28f + 0.22f * pulse),
            radius = r * (1.35f + 0.10f * pulse),
            center = Offset(x, y),
            style = PlaneStroke.of(r * 0.07f),
            blendMode = BlendMode.Plus,
        )
        drawCircle(
            color = Color.White.copy(alpha = 0.20f + 0.18f * pulse),
            radius = r * 0.95f,
            center = Offset(x, y),
            style = PlaneStroke.of(r * 0.04f),
            blendMode = BlendMode.Plus,
        )
    }
}

// ---------------------------------------------------------------------
// 弹幕
// ---------------------------------------------------------------------

private fun DrawScope.drawPlayerBullets(game: PlaneGame) {
    if (game.playerBullets.isEmpty()) return
    val scale = game.viewScale
    for (i in 0 until game.playerBullets.size) {
        val bullet = game.playerBullets[i]
        val x = game.toScreenX(bullet.x)
        val y = game.toScreenY(bullet.y)
        if (bullet.shape == PlaneBulletShape.CAPSULE) {
            drawPlayerLaser(bullet, x, y, scale)
        } else {
            val r = (bullet.radius * scale).coerceAtLeast(1.4f)
            drawCircle(
                color = Color(0xFF40C4FF).copy(alpha = 0.22f),
                radius = r * 2.6f,
                center = Offset(x, y),
                blendMode = BlendMode.Plus,
            )
            drawCircle(Color(0xFF9BE9FF), r, Offset(x, y))
            drawCircle(Color.White, r * 0.48f, Offset(x, y))
        }
    }
}

private fun DrawScope.drawPlayerLaser(
    bullet: PlaneBullet,
    x: Float,
    y: Float,
    scale: Float,
) {
    val r = (bullet.radius * scale).coerceAtLeast(1.2f)
    val dx = cos(bullet.angle) * bullet.halfLength * scale
    val dy = sin(bullet.angle) * bullet.halfLength * scale
    val start = Offset(x - dx, y - dy)
    val end = Offset(x + dx, y + dy)
    drawLine(
        color = Color(0xFF40C4FF).copy(alpha = 0.20f),
        start = start,
        end = end,
        strokeWidth = r * 4.2f,
        cap = StrokeCap.Round,
        blendMode = BlendMode.Plus,
    )
    drawLine(
        color = Color(0xFF7FE3F2).copy(alpha = 0.58f),
        start = start,
        end = end,
        strokeWidth = r * 2.2f,
        cap = StrokeCap.Round,
        blendMode = BlendMode.Plus,
    )
    drawLine(
        color = Color.White.copy(alpha = 0.92f),
        start = start,
        end = end,
        strokeWidth = r * 0.8f,
        cap = StrokeCap.Round,
    )
}

private fun DrawScope.drawEnemyBullets(
    game: PlaneGame,
    tick: Long,
) {
    if (game.enemyBullets.isEmpty()) return
    val scale = game.viewScale
    for (i in 0 until game.enemyBullets.size) {
        val bullet = game.enemyBullets[i]
        val x = game.toScreenX(bullet.x)
        val y = game.toScreenY(bullet.y)
        // 表现层分色：Boss 特殊圆弹（品红）/ 直冲玩家的瞄准弹（琥珀）/ 其余普通弹（赤红）。
        // RECT / CAPSULE（炮台长条弹等）不套 Boss 配色，用普通 / 瞄准色。
        val speed = hypot(bullet.vx, bullet.vy)
        val boss = bullet.boss && bullet.shape == PlaneBulletShape.CIRCLE
        val aimed = !boss && speed > 1f && planeBulletAimedAtPlayer(bullet, game)
        val body =
            when {
                boss -> Color(0xFFFF5FD1)
                aimed -> Color(0xFFFFC046)
                else -> Color(0xFFFF6B6B)
            }
        val rim =
            when {
                boss -> Color(0xFF9C27B0)
                aimed -> Color(0xFFEF6C00)
                else -> Color(0xFFC62828)
            }
        val glow =
            when {
                boss -> Color(0xFFE040FB)
                aimed -> Color(0xFFFFB300)
                else -> Color(0xFFFF5252)
            }
        when (bullet.shape) {
            PlaneBulletShape.CIRCLE -> {
                val r = (bullet.radius * scale).coerceAtLeast(1.6f)
                drawCircle(glow.copy(alpha = 0.24f), r * 2.7f, Offset(x, y), blendMode = BlendMode.Plus)
                drawCircle(rim, r, Offset(x, y))
                drawCircle(body, r * 0.74f, Offset(x, y))
                drawCircle(Color.White.copy(alpha = 0.92f), r * 0.34f, Offset(x - r * 0.18f, y - r * 0.18f))
                if (boss) {
                    drawCircle(
                        glow.copy(alpha = 0.55f),
                        r * (1.35f + 0.15f * sin(tick * 0.2f)),
                        Offset(x, y),
                        style = PlaneStroke.of(r * 0.14f),
                        blendMode = BlendMode.Plus,
                    )
                }
            }
            PlaneBulletShape.CAPSULE -> {
                val r = (bullet.radius * scale).coerceAtLeast(1.4f)
                val dx = cos(bullet.angle) * bullet.halfLength * scale
                val dy = sin(bullet.angle) * bullet.halfLength * scale
                drawLine(
                    glow.copy(alpha = 0.24f),
                    Offset(x - dx, y - dy),
                    Offset(x + dx, y + dy),
                    r * 4f,
                    StrokeCap.Round,
                    blendMode = BlendMode.Plus,
                )
                drawLine(rim, Offset(x - dx, y - dy), Offset(x + dx, y + dy), r * 2.4f, StrokeCap.Round)
                drawLine(body, Offset(x - dx, y - dy), Offset(x + dx, y + dy), r * 1.7f, StrokeCap.Round)
                drawLine(Color.White.copy(alpha = 0.85f), Offset(x - dx, y - dy), Offset(x + dx, y + dy), r * 0.55f, StrokeCap.Round)
            }
            PlaneBulletShape.RECT -> {
                val hw = (bullet.radius * scale).coerceAtLeast(1.4f)
                val hh = (bullet.halfLength.coerceAtLeast(bullet.radius) * scale).coerceAtLeast(1.4f)
                drawRect(glow.copy(alpha = 0.22f), Offset(x - hw * 1.7f, y - hh * 1.7f), Size(hw * 3.4f, hh * 3.4f))
                drawRect(rim, Offset(x - hw, y - hh), Size(hw * 2f, hh * 2f))
                drawRect(body, Offset(x - hw * 0.72f, y - hh * 0.72f), Size(hw * 1.44f, hh * 1.44f))
                drawRect(
                    Color.White.copy(alpha = 0.85f),
                    Offset(x - hw * 0.72f, y - hh * 0.72f),
                    Size(hw * 1.44f, hh * 0.22f),
                )
            }
        }
    }
}

/**
 * 子弹速度方向是否（近似）指向玩家：用于「瞄准弹」警示配色。
 * 纯表现判断（点积阈值 ≈ 7°），不影响任何判定。
 */
private fun planeBulletAimedAtPlayer(
    bullet: PlaneBullet,
    game: PlaneGame,
): Boolean {
    val speed = hypot(bullet.vx, bullet.vy)
    if (speed <= 1f) return false
    val toPlayerX = game.player.x - bullet.x
    val toPlayerY = game.player.y - bullet.y
    val targetLen = hypot(toPlayerX, toPlayerY)
    if (targetLen <= 0.01f) return true
    val dot = (bullet.vx * toPlayerX + bullet.vy * toPlayerY) / (speed * targetLen)
    return dot > 0.9925f
}

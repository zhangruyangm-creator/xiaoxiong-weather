package com.xiaoxiong.weather.ui.game

import android.graphics.Paint
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import com.xiaoxiong.weather.ui.game.BearCatchLogic.CatchScene
import com.xiaoxiong.weather.ui.game.BearCatchLogic.ItemKind
import kotlin.math.cos
import kotlin.math.sin

/** 飘分浮字（接住/中招提示）。 */
internal class CatchFloatText(
    val text: String,
    val x: Float,
    var y: Float,
    var life: Float,
    val color: Color,
)

private val catchTextPaint =
    Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.WHITE
        textAlign = Paint.Align.CENTER
        typeface = android.graphics.Typeface.DEFAULT_BOLD
    }

/** 单位闪电（以原点为中心、落在 [-1,1]），绘制时 translate/scale 复用，避免每帧 new Path。 */
private val unitLightningPath: Path =
    Path().apply {
        moveTo(-0.5f, -1f)
        lineTo(0.2f, -0.1f)
        lineTo(-0.05f, -0.05f)
        lineTo(0.6f, 1f)
        lineTo(0.1f, 0.3f)
        lineTo(0.35f, 0.28f)
        lineTo(-0.6f, -0.5f)
        close()
    }

private val catchGroundBrush =
    Brush.verticalGradient(listOf(Color(0xFF8BC34A), Color(0xFF558B2F)))

// ---------------- 世界绘制 ----------------

internal fun DrawScope.drawCatchWorld(
    game: BearCatchGame,
    isDay: Boolean,
    sprites: BearSprites,
    texts: List<CatchFloatText>,
    /** 减弱动画：跳过天气氛围粒子，只保留静态天空底色。 */
    reduceMotion: Boolean = false,
) {
    val w = size.width
    val h = size.height
    val s = game.s
    val scene = game.scene

    drawCatchSky(scene, isDay, w, h)

    // 背景云（所有场景淡淡一层）
    drawCatchBgClouds(game)

    // 天气氛围粒子（作为背景层，与跑酷/摘星/迷宫共用渲染）
    drawCatchWeatherBackdrop(scene, game.time, s, w, h, reduceMotion)

    // HUD 暗角：天空/云层之上、掉落物等内容之下，只影响背景不压暗玩法元素
    drawCatchHudScrim(catchSceneSky(scene, isDay), w, h)

    // 掉落物
    for (it in game.items) {
        drawCatchItem(it, game.time)
    }

    // 地面（底部一条绿草坡）
    val groundY = game.catchY + game.catchR * 0.6f
    drawRect(
        catchGroundBrush,
        topLeft = Offset(0f, groundY),
        size = Size(w, h - groundY),
    )
    drawLine(
        Color(0xFF6FA03A),
        Offset(0f, groundY),
        Offset(w, groundY),
        strokeWidth = 3f * s,
        cap = StrokeCap.Round,
    )

    // 小熊
    drawCatchBear(game, sprites)

    // 爆点粒子
    for (p in game.particles) {
        val alpha = (p.life / p.maxLife).coerceIn(0f, 1f)
        drawCircle(
            p.color,
            (4f * s) * (alpha * 0.7f + 0.3f),
            Offset(p.x, p.y),
            alpha = alpha * 0.9f,
        )
    }

    // 飘字
    drawCatchTexts(game, texts)

    // 受击闪红
    if (game.shakeT > 0f) {
        drawRect(Color(0xFFFF5252).copy(alpha = game.shakeT * 0.22f))
    }
}

private fun DrawScope.drawCatchSky(
    scene: CatchScene,
    isDay: Boolean,
    w: Float,
    h: Float,
) {
    val sky = catchSceneSky(scene, isDay)
    drawRect(sky.skyBrush)
    drawHorizonGlow(h * 0.95f, midColor(sky.bottom, Color.White), h * 0.16f)
}

/** 顶部暗角：压暗 HUD 身后的天空/云层，浅色场景按 HUD 区间最亮色保证白字 ≥4.5:1。 */
private fun DrawScope.drawCatchHudScrim(
    sky: CatchSkyStyle,
    w: Float,
    h: Float,
) {
    if (sky.hudScrimAlpha <= 0f) return
    drawRect(
        sky.hudScrimBrush,
        topLeft = Offset.Zero,
        size = Size(w, h * CATCH_HUD_SCRIM_BAND),
    )
}

private fun DrawScope.drawCatchBgClouds(game: BearCatchGame) {
    val s = game.s
    val w = size.width
    for (i in 0 until 3) {
        val cy = (i * 97f + 40f) * s
        if (cy > size.height * 0.55f) continue
        val cx = ((i * 331f * s + game.time * (3f + i)) % (w + 200f * s)) - 100f * s
        val cw = (60f + i * 16f) * s
        drawCircle(Color.White.copy(alpha = 0.22f), cw * 0.55f, Offset(cx - cw * 0.4f, cy))
        drawCircle(Color.White.copy(alpha = 0.22f), cw * 0.75f, Offset(cx, cy + cw * 0.08f))
        drawCircle(Color.White.copy(alpha = 0.22f), cw * 0.5f, Offset(cx + cw * 0.55f, cy + cw * 0.12f))
        drawRect(Color.White.copy(alpha = 0.22f), Offset(cx - cw * 0.85f, cy), Size(cw * 1.7f, cw * 0.45f))
    }
}

private fun DrawScope.drawCatchWeatherBackdrop(
    scene: CatchScene,
    time: Float,
    s: Float,
    w: Float,
    h: Float,
    reduceMotion: Boolean,
) {
    // 减弱动画：雨/雪/风线/闪电全部为高频粒子，直接跳过，保留上方静态天空
    if (reduceMotion) return
    when (scene) {
        CatchScene.RAIN_LIGHT -> {
            drawRainLayered(
                fgCount = 10,
                bgCount = 5,
                time = time,
                width = w,
                height = h,
                scale = s,
                lenMul = 0.8f,
                alpha = 0.30f,
            )
            drawCatchWindLines(time, s, w, h, count = 3)
        }
        CatchScene.RAIN -> {
            drawRainLayered(
                fgCount = 24,
                bgCount = 12,
                time = time,
                width = w,
                height = h,
                scale = s,
                alpha = 0.40f,
            )
            drawCatchWindLines(time, s, w, h, count = 4)
        }
        CatchScene.RAIN_HEAVY -> {
            drawRainLayered(
                fgCount = 38,
                bgCount = 18,
                time = time,
                width = w,
                height = h,
                scale = s,
                lenMul = 1.1f,
                alpha = 0.55f,
            )
            drawCatchWindLines(time, s, w, h, count = 6)
        }
        CatchScene.STORM -> {
            drawRainLayered(
                fgCount = 28,
                bgCount = 14,
                time = time,
                width = w,
                height = h,
                scale = s,
                lenMul = 1.2f,
                alpha = 0.55f,
            )
            drawStormLightning(time, s, w, h)
            drawCatchWindLines(time, s, w, h, count = 6, speedMul = 1.4f)
        }
        CatchScene.FREEZING_RAIN -> drawFreezingRain(time, s, w, h)
        CatchScene.HAIL -> {
            drawRainLayered(
                fgCount = 12,
                bgCount = 6,
                time = time,
                width = w,
                height = h,
                scale = s,
                lenMul = 1.1f,
                alpha = 0.45f,
            )
            drawHailPellets(time, s, w, h, count = 28)
            drawStormLightning(time, s, w, h)
        }
        CatchScene.SNOW ->
            for (i in 0 until 18) {
                val x = ((i * 83 + time * 16f + sin(time * 1.2f + i) * 10f) % (w + 30f))
                val y = ((i * 61 + time * 34f) % (h + 30f)) - 15f
                drawCircle(Color.White.copy(alpha = 0.8f), (2f + i % 3) * s, Offset(x, y))
            }
        CatchScene.SUNNY, CatchScene.CLOUDY -> Unit
    }
}

/** 横风视觉线：几缕向右飘的细线，暗示掉落物会被风带着横移（预判落点）。 */
private fun DrawScope.drawCatchWindLines(
    time: Float,
    s: Float,
    w: Float,
    h: Float,
    count: Int,
    speedMul: Float = 1f,
) {
    val baseY = h * 0.18f
    for (i in 0 until count) {
        val drift = time * (90f * speedMul + (i % 3) * 26f * speedMul)
        val span = (w + 260f * s) % (w + 240f * s)
        val x = ((i * 173f * s + drift) % span) - 120f * s
        val y = baseY + ((i * 83f * s) % (h * 0.55f))
        val len = (90f + (i % 3) * 34f) * s
        val alpha = 0.10f + (i % 3) * 0.03f
        drawLine(
            color = Color.White.copy(alpha = alpha),
            start = Offset(x, y),
            end = Offset(x + len, y - len * 0.08f),
            strokeWidth = (1.4f + (i % 2)) * s,
            cap = StrokeCap.Round,
        )
    }
}

private fun DrawScope.drawCatchItem(
    it: BearCatchLogic.Item,
    time: Float,
) {
    val s = size.height / 820f
    val wobble = sin(time * 2f + it.phase) * 2f * s
    val x = it.x
    val y = it.y
    val r = it.r
    when (it.kind) {
        ItemKind.RAINDROP -> {
            val dropLen = r * 1.6f
            drawLine(
                Color(0xFF8FC3F0),
                Offset(x, y - dropLen / 2f),
                Offset(x, y + dropLen / 2f),
                strokeWidth = r * 0.7f,
                cap = StrokeCap.Round,
            )
            drawCircle(Color(0xFFADD8F7), r * 0.35f, Offset(x + wobble, y + dropLen * 0.3f))
        }
        ItemKind.SNOWFLAKE -> {
            for (i in 0 until 6) {
                val a = Math.PI.toFloat() * 2f * i / 6f + sin(time + it.phase) * 0.05f
                drawLine(
                    Color.White,
                    Offset(x + cos(a) * r * 0.2f, y + sin(a) * r * 0.2f),
                    Offset(x + cos(a) * r, y + sin(a) * r),
                    strokeWidth = (1.6f + (i % 2)) * s,
                    cap = StrokeCap.Round,
                )
            }
            drawCircle(Color.White.copy(alpha = 0.9f), r * 0.35f, Offset(x, y))
        }
        ItemKind.COIN -> {
            val pulse = 1f + 0.1f * sin(time * 4f + it.phase)
            drawCircle(Color(0xFFFFCA28).copy(alpha = 0.35f), r * pulse * 1.4f, Offset(x, y))
            drawCircle(Color(0xFFFFB300), r * pulse * 0.9f, Offset(x, y))
            drawCircle(Color(0xFFFFE082), r * pulse * 0.6f, Offset(x, y))
            drawCircle(Color(0xFFFFD54F), r * pulse * 0.3f, Offset(x, y))
        }
        ItemKind.PETAL -> {
            drawCircle(Color(0xFFF8BBD0), r * 0.7f, Offset(x, y))
            drawCircle(Color(0xFFF06292), r * 0.35f, Offset(x + wobble * 0.3f, y - r * 0.2f))
        }
        ItemKind.GIFT -> {
            val bump = 1f + 0.12f * sin(time * 5f + it.phase)
            drawSoftGlow(x, y, r * 1.5f, Color(0xFFFFE082))
            drawRoundRect(
                Color(0xFFFF8F00),
                topLeft = Offset(x - r * bump, y - r * bump),
                size = Size(r * 2f * bump, r * 2f * bump),
                cornerRadius = CornerRadius(r * 0.22f),
            )
            drawLine(
                Color(0xFFFFF3E0),
                Offset(x - r * bump, y),
                Offset(x + r * bump, y),
                strokeWidth = r * 0.35f,
                cap = StrokeCap.Round,
            )
            drawLine(
                Color(0xFFFFF3E0),
                Offset(x, y - r * bump),
                Offset(x, y + r * bump),
                strokeWidth = r * 0.35f,
                cap = StrokeCap.Round,
            )
        }
        ItemKind.BUBBLE -> {
            val pulse = 1f + 0.08f * sin(time * 3f + it.phase)
            drawCircle(
                Color(0xFF80DEEA).copy(alpha = 0.16f),
                r * pulse * 1.35f,
                Offset(x, y),
            )
            drawCircle(
                Color(0xFF80DEEA).copy(alpha = 0.45f),
                r * pulse,
                Offset(x, y),
            )
            drawCircle(
                Color.White.copy(alpha = 0.85f),
                r * 0.32f,
                Offset(x - r * 0.32f, y - r * 0.32f),
            )
            drawCircle(
                Color.White.copy(alpha = 0.55f),
                r * 0.16f,
                Offset(x + r * 0.3f, y + r * 0.2f),
            )
        }
        ItemKind.CLOUD -> {
            drawCircle(Color(0xFF78828E), r, Offset(x, y))
            drawCircle(Color(0xFF8A94A0), r * 0.72f, Offset(x - r * 0.4f, y - r * 0.25f))
            drawCircle(Color(0xFF97A2AE), r * 0.6f, Offset(x + r * 0.45f, y - r * 0.2f))
        }
        ItemKind.HAIL -> {
            drawCircle(Color(0xFFE9F0F6), r, Offset(x, y))
            drawCircle(Color(0xFFC4CFDA), r * 0.6f, Offset(x - r * 0.3f, y - r * 0.3f))
        }
        ItemKind.LIGHTNING -> {
            drawSoftGlow(x, y, r * 1.6f, Color(0xFFFFEB3B))
            withTransform({
                translate(x, y)
                scale(r, r, pivot = Offset.Zero)
            }) {
                drawPath(unitLightningPath, Color(0xFFFFF176))
                drawPath(
                    unitLightningPath,
                    Color(0xFFFFFDE7),
                    style = Stroke(width = (1.2f * s) / r.coerceAtLeast(0.1f)),
                )
            }
        }
    }
}

private fun DrawScope.drawCatchBear(
    game: BearCatchGame,
    sprites: BearSprites,
) {
    val bs = game.catchR * 1.55f
    val cy = game.catchY
    val dead = game.phase == GamePhase.OVER
    // 无敌闪烁
    val invBlink = game.invincibleT > 0f && ((game.invincibleT * 12f) % 1f < 0.5f)
    // 死亡淡出用 deadT（死亡后从 0 开始累计）：整局 time 很大时也能正常淡出
    val alphaBase = if (dead) (1f - (game.deadT * 2f).coerceIn(0f, 1f)) else 1f
    val alpha = (if (invBlink) alphaBase * 0.45f else alphaBase).coerceIn(0f, 1f)

    // 移动倾斜：跟随手指方向轻微侧倾
    val tiltMove = ((game.touchX ?: game.catchX) - game.catchX) * 0.06f

    // 接住挤压：x 展宽、y 压扁，0.16s 内快速回弹
    val squash =
        if (game.squashT in 0f..0.16f) {
            val k = game.squashT / 0.16f
            1f - 0.22f * (1f - k) * (1f - k)
        } else {
            1f
        }
    val scaleX = 2f - squash
    val scaleY = squash

    // 地面柔影，随无敌闪烁
    if (!dead) {
        val groundY = game.catchY + game.catchR * 0.6f
        drawOval(
            color = Color.Black.copy(alpha = if (invBlink) 0.08f else 0.18f),
            topLeft = Offset(game.catchX - bs * 0.55f, groundY - 4f),
            size = Size(bs * 1.1f, bs * 0.22f),
        )
    }

    // 一次性护盾泡泡：持有时显示淡蓝光圈；冷却中完全隐藏（避免误导）
    if (game.hasShield && !dead) {
        drawCircle(
            color = Color(0xFF80DEEA).copy(alpha = 0.28f),
            radius = bs * (0.82f + 0.06f * sin(game.time * 3f)),
            center = Offset(game.catchX, cy),
            style = Stroke(width = 2.5f * game.s),
        )
        drawCircle(
            color = Color(0xFF80DEEA).copy(alpha = 0.14f),
            radius = bs * (0.86f + 0.05f * sin(game.time * 3f + 1.2f)),
            center = Offset(game.catchX, cy),
        )
        for (i in 0 until BearCatchLogic.MAX_SHIELD) {
            val a = game.time * 1.4f + i * (Math.PI.toFloat() * 2f / BearCatchLogic.MAX_SHIELD)
            drawCircle(
                color = Color(0xFFFFE082),
                radius = 3.2f * game.s,
                center =
                    Offset(
                        game.catchX + cos(a) * bs * 0.72f,
                        cy + sin(a) * bs * 0.72f,
                    ),
            )
        }
    }

    drawGameBear(
        sprites = sprites,
        centerX = game.catchX,
        centerY = cy,
        bs = bs,
        pose =
            BearPose(
                time = game.time,
                dead = dead,
                frame = runCycleFrame(game.time * 3.2f),
                bodyTilt = tiltMove,
                scaleX = scaleX,
                scaleY = scaleY,
                alpha = alpha,
            ),
    )
}

private fun DrawScope.drawCatchTexts(
    game: BearCatchGame,
    texts: List<CatchFloatText>,
) {
    for (t in texts) {
        val k = (t.life / 0.8f).coerceIn(0f, 1f)
        catchTextPaint.color = t.color.copy(alpha = k).toArgb()
        catchTextPaint.textSize = 16f * game.s
        drawContext.canvas.nativeCanvas.drawText(t.text, t.x, t.y, catchTextPaint)
    }
}

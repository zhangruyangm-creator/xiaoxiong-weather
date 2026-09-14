package com.xiaoxiong.weather.ui.game

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.roundToInt
import kotlin.math.sin

private val honeyGlow = Color(0xFFFFC107)

internal val paddlePeach =
    PaddlePalette(
        base = Color(0xFFF2A65A),
        rim = Color(0xFFB85E14),
        accent = Color(0xFFFFE0B2),
        ear = Color(0xFFE08A3C),
        earInner = Color(0xFFFFD9A0),
        cheeks = Color(0x66F48FB1),
    )
internal val paddleSun =
    PaddlePalette(
        base = Color(0xFFE7B24C),
        rim = Color(0xFF8A5320),
        accent = Color(0xFFFFECB3),
        ear = Color(0xFFC98E33),
        earInner = Color(0xFFFFE082),
        cheeks = null,
    )
internal val paddleAi =
    PaddlePalette(
        base = Color(0xFF4DD0E1),
        rim = Color(0xFF00697A),
        accent = Color(0xFFB2EBF2),
        ear = Color(0xFF26C6DA),
        earInner = Color(0xFFB2EBF2),
        cheeks = null,
    )

internal class PaddlePalette(
    val base: Color,
    val rim: Color,
    val accent: Color,
    val ear: Color,
    val earInner: Color,
    val cheeks: Color?,
)

internal fun paletteId(character: BearCharacter): Int = if (character == BearCharacter.PEACH) 0 else 1

// ---------------------------------------------------------------------
// 挡板 / 蜜罐精灵图
// ---------------------------------------------------------------------

internal fun DrawScope.drawPaddleArt(
    r: Float,
    p: PaddlePalette,
) {
    // 柔和投影（朝右下，稍大稍淡）
    drawOval(
        Color.Black.copy(alpha = 0.10f),
        topLeft = Offset(-r * 1.00f + r * 0.16f, -r * 0.66f + r * 0.30f),
        size = Size(r * 2.32f, r * 1.84f),
    )
    drawOval(
        Color.Black.copy(alpha = 0.14f),
        topLeft = Offset(-r * 0.82f + r * 0.16f, -r * 0.52f + r * 0.30f),
        size = Size(r * 1.96f, r * 1.56f),
    )
    drawOval(
        Color.Black.copy(alpha = 0.16f),
        topLeft = Offset(-r * 0.62f + r * 0.16f, -r * 0.38f + r * 0.30f),
        size = Size(r * 1.56f, r * 1.24f),
    )
    // 小熊耳朵
    drawCircle(p.ear, r * 0.36f, Offset(-r * 0.60f, -r * 0.56f))
    drawCircle(p.ear, r * 0.36f, Offset(r * 0.60f, -r * 0.56f))
    drawCircle(p.earInner, r * 0.19f, Offset(-r * 0.60f, -r * 0.56f))
    drawCircle(p.earInner, r * 0.19f, Offset(r * 0.60f, -r * 0.56f))
    // 圆盘主体
    drawCircle(
        Brush.radialGradient(
            colors = listOf(lighten(p.base, 0.50f), p.base, darken(p.base, 0.45f)),
            center = Offset(-r * 0.30f, -r * 0.34f),
            radius = r * 1.55f,
        ),
        radius = r,
        center = Offset.Zero,
    )
    // 边缘圈与高光弧
    drawCircle(p.rim.copy(alpha = 0.92f), r, Offset.Zero, style = strokeOf(r * 0.14f))
    drawCircle(Color.White.copy(alpha = 0.20f), r * 0.93f, Offset.Zero, style = strokeOf(r * 0.035f))
    drawArc(
        Color.White.copy(alpha = 0.32f),
        198f,
        124f,
        false,
        Offset(-r * 0.90f, -r * 0.90f),
        Size(r * 1.80f, r * 1.80f),
        style = strokeOf(r * 0.07f),
    )
    // 环带
    drawCircle(p.accent.copy(alpha = 0.32f), r * 0.70f, Offset.Zero, style = strokeOf(r * 0.045f))
    // 爪印环
    for (k in 0 until 4) {
        val a = (45f + 90f * k) * PI.toFloat() / 180f
        drawPawMark(
            cos(a) * r * 0.70f,
            sin(a) * r * 0.70f,
            r * 0.115f,
            darken(p.rim, 0.05f).copy(alpha = 0.62f),
        )
    }
    // 脸
    drawOval(
        Color(0xFFFFF6E8).copy(alpha = 0.95f),
        topLeft = Offset(-r * 0.40f, -r * 0.10f),
        size = Size(r * 0.80f, r * 0.62f),
    )
    drawCircle(Color(0xFF3B2414), r * 0.10f, Offset(0f, r * 0.02f))
    drawCircle(Color(0xFF3B2414), r * 0.080f, Offset(-r * 0.20f, -r * 0.10f))
    drawCircle(Color(0xFF3B2414), r * 0.080f, Offset(r * 0.20f, -r * 0.10f))
    drawCircle(Color.White.copy(alpha = 0.85f), r * 0.028f, Offset(-r * 0.22f, -r * 0.12f))
    drawCircle(Color.White.copy(alpha = 0.85f), r * 0.028f, Offset(r * 0.18f, -r * 0.12f))
    drawArc(
        Color(0xFF3B2414).copy(alpha = 0.80f),
        20f,
        50f,
        false,
        Offset(-r * 0.13f, r * 0.10f),
        Size(r * 0.13f, r * 0.12f),
        style = strokeOf(r * 0.024f),
    )
    drawArc(
        Color(0xFF3B2414).copy(alpha = 0.80f),
        110f,
        50f,
        false,
        Offset(0f, r * 0.10f),
        Size(r * 0.13f, r * 0.12f),
        style = strokeOf(r * 0.024f),
    )
    p.cheeks?.let {
        drawCircle(it, r * 0.09f, Offset(-r * 0.33f, r * 0.13f))
        drawCircle(it, r * 0.09f, Offset(r * 0.33f, r * 0.13f))
    }
}

internal fun DrawScope.drawPuckArt(r: Float) {
    // 蜂蜜球体
    drawCircle(
        Brush.radialGradient(
            colors = listOf(Color(0xFFFFF6C8), Color(0xFFFFC42E), Color(0xFFE56700)),
            center = Offset(-r * 0.32f, -r * 0.36f),
            radius = r * 1.60f,
        ),
        radius = r,
        center = Offset.Zero,
    )
    // 边缘暗化 + 菲涅尔反光
    drawCircle(
        Brush.radialGradient(
            colors = listOf(Color.Transparent, Color.Transparent, Color.Black.copy(alpha = 0.40f)),
            center = Offset(-r * 0.30f, -r * 0.32f),
            radius = r * 1.45f,
        ),
        radius = r,
        center = Offset.Zero,
    )
    drawCircle(
        Brush.radialGradient(
            0.60f to Color.Transparent,
            0.85f to Color(0x33FFF3D0),
            1.00f to Color(0x59FFF3D0),
            center = Offset.Zero,
            radius = r,
        ),
        radius = r,
        center = Offset.Zero,
    )
    // 蜜罐盖环
    drawCircle(Color(0x668A4A10), r * 0.62f, Offset.Zero, style = strokeOf(r * 0.10f))
    drawCircle(Color.White.copy(alpha = 0.16f), r * 0.53f, Offset.Zero, style = strokeOf(r * 0.05f))
    // 高光
    drawCircle(
        Brush.radialGradient(
            colors = listOf(Color.White.copy(alpha = 0.75f), Color.Transparent),
            center = Offset(-r * 0.32f, -r * 0.35f),
            radius = r * 0.50f,
        ),
        radius = r * 0.50f,
        center = Offset(-r * 0.32f, -r * 0.35f),
    )
    drawCircle(Color.White.copy(alpha = 0.90f), r * 0.155f, Offset(-r * 0.32f, -r * 0.35f))
    drawCircle(Color.White.copy(alpha = 0.50f), r * 0.070f, Offset(-r * 0.10f, -r * 0.18f))
    drawCircle(Color(0x33FFE082), r * 0.40f, Offset(r * 0.25f, r * 0.40f))
}

internal fun DrawScope.drawBearPaddle(
    x: Float,
    y: Float,
    r: Float,
    palette: PaddlePalette,
    paletteId: Int,
    vx: Float,
    vy: Float,
    shortSide: Float,
    hit: Float,
    reduceMotion: Boolean,
) {
    if (r < 0.5f) return
    // 运动拖尾（减弱动态效果时关闭）
    val sp = hypot(vx, vy)
    if (!reduceMotion && sp > shortSide * 0.22f) {
        val k = (sp / (shortSide * 1.4f)).coerceIn(0f, 0.55f)
        val ux = vx / sp
        val uy = vy / sp
        drawCircle(palette.base.copy(alpha = 0.10f * k), r * 0.98f, Offset(x - ux * r * 0.60f, y - uy * r * 0.60f))
        drawCircle(palette.base.copy(alpha = 0.05f * k), r * 0.90f, Offset(x - ux * r * 1.25f, y - uy * r * 1.25f))
        drawCircle(palette.base.copy(alpha = 0.03f * k), r * 0.80f, Offset(x - ux * r * 1.90f, y - uy * r * 1.90f))
    }
    val sprite = HockeySpriteCache.paddle(r, paletteId, this, layoutDirection)
    if (sprite != null) {
        drawImage(
            image = sprite.bitmap,
            dstOffset = IntOffset((x - sprite.half).roundToInt(), (y - sprite.half).roundToInt()),
            dstSize = IntSize(sprite.bitmap.width, sprite.bitmap.height),
            filterQuality = FilterQuality.None,
        )
    }
    if (hit > 0.05f) {
        drawCircle(
            Color.White.copy(alpha = 0.30f * hit),
            r * (1.06f + 0.16f * (1f - hit)),
            Offset(x, y),
            style = strokeOf(r * 0.085f),
        )
        drawCircle(
            palette.accent.copy(alpha = 0.20f * hit),
            r * 0.80f,
            Offset(x, y),
            style = strokeOf(r * 0.10f),
        )
    }
}

internal fun DrawScope.drawHoneyPuck(
    game: HockeyGame,
    time: Float,
    reduceMotion: Boolean,
) {
    val p = game.puck
    val r = p.r
    if (r < 0.5f) return
    val short = game.table?.shortSide ?: return
    val sp = hypot(p.vx, p.vy)

    // 投影
    drawOval(
        Color.Black.copy(alpha = 0.16f),
        topLeft = Offset(p.x - r * 1.10f + r * 0.14f, p.y - r * 0.86f + r * 0.24f),
        size = Size(r * 2.20f, r * 1.72f),
    )
    drawOval(
        Color.Black.copy(alpha = 0.20f),
        topLeft = Offset(p.x - r * 0.88f + r * 0.14f, p.y - r * 0.66f + r * 0.24f),
        size = Size(r * 1.76f, r * 1.32f),
    )

    // 高速拖影
    if (!reduceMotion && sp > short * 0.16f) {
        val k = (sp / (short * 1.2f)).coerceIn(0f, 1f)
        val ux = p.vx / sp
        val uy = p.vy / sp
        for (i in 1..3) {
            drawCircle(
                honeyGlow.copy(alpha = 0.16f * k * (1f - i / 4f)),
                r * (1f - 0.14f * i),
                Offset(p.x - ux * r * (0.9f + 1.0f * i), p.y - uy * r * (0.9f + 1.0f * i)),
            )
        }
    }

    val sprite = HockeySpriteCache.puck(r, this, layoutDirection)
    if (sprite != null) {
        // 蜜罐盖反光随球滚动（烘焙精灵整体旋转）
        val deg = (time * 110f) % 360f
        withTransform({ rotate(degrees = deg, pivot = Offset(p.x, p.y)) }) {
            drawImage(
                image = sprite.bitmap,
                dstOffset = IntOffset((p.x - sprite.half).roundToInt(), (p.y - sprite.half).roundToInt()),
                dstSize = IntSize(sprite.bitmap.width, sprite.bitmap.height),
                filterQuality = FilterQuality.Low,
            )
        }
    }
}

internal fun DrawScope.drawPuckTrail(game: HockeyGame) {
    val trail = game.trail
    if (trail.isEmpty()) return
    val r = game.puck.r
    if (r < 0.5f) return
    for (i in trail.indices) {
        val (x, y) = trail[i]
        val a = (1f - i / 12f) * 0.24f
        if (a <= 0.01f) continue
        drawCircle(honeyGlow.copy(alpha = a), r * (0.78f - i * 0.02f), Offset(x, y))
    }
}

private fun lighten(
    c: Color,
    amount: Float,
): Color =
    Color(
        red = (c.red + (1f - c.red) * amount).coerceIn(0f, 1f),
        green = (c.green + (1f - c.green) * amount).coerceIn(0f, 1f),
        blue = (c.blue + (1f - c.blue) * amount).coerceIn(0f, 1f),
        alpha = c.alpha,
    )

private fun darken(
    c: Color,
    amount: Float,
): Color =
    Color(
        red = (c.red * (1f - amount)).coerceIn(0f, 1f),
        green = (c.green * (1f - amount)).coerceIn(0f, 1f),
        blue = (c.blue * (1f - amount)).coerceIn(0f, 1f),
        alpha = c.alpha,
    )

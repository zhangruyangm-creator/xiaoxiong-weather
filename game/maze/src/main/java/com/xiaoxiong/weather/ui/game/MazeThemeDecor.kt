package com.xiaoxiong.weather.ui.game

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin

/**
 * 迷宫主题的静态场景装饰（天空/场地外围），随静态层一起烘焙，不逐帧重画。
 * 所有位置由画布尺寸与棋盘布局推导，确定性、无时间参数。
 */
internal fun DrawScope.drawMazeSceneDecor(
    theme: MazeTheme,
    style: MazeThemeStyle,
    layout: MazeBoardLayout,
    rows: Int,
    cols: Int,
) {
    val boardW = layout.cell * cols
    val boardH = layout.cell * rows
    drawSceneBackdrop(style, layout, boardW, boardH)
    when (theme) {
        MazeTheme.GARDEN -> drawGardenDecor(style, layout, boardW, boardH)
        MazeTheme.RAIN -> drawRainDecor(style, layout, boardW, boardH)
        MazeTheme.SNOW -> drawSnowDecor(style, layout, boardW, boardH)
        MazeTheme.NIGHT -> drawNightDecor(style, layout, boardW, boardH)
        MazeTheme.FESTIVAL -> drawFestivalDecor(style, layout, boardW, boardH)
        MazeTheme.CLOUD -> drawCloudDecor(style, layout, boardW, boardH)
    }
}

/** 场地共性底层：棋盘后的柔光 + 地面投影带。 */
private fun DrawScope.drawSceneBackdrop(
    style: MazeThemeStyle,
    layout: MazeBoardLayout,
    boardW: Float,
    boardH: Float,
) {
    val cx = layout.ox + boardW * 0.5f
    val cy = layout.oy + boardH * 0.5f
    val radius = max(boardW, boardH) * 0.9f
    drawCircle(
        Brush.radialGradient(
            colors = listOf(style.skyBottom.copy(alpha = 0.42f), Color.Transparent),
            center = Offset(cx, cy),
            radius = radius,
        ),
        radius = radius,
        center = Offset(cx, cy),
    )
    val below = layout.oy + boardH
    val groundH = (size.height - below).coerceAtLeast(0f)
    if (groundH > 4f) {
        drawRect(
            Brush.verticalGradient(
                colors = listOf(style.wallDeep.copy(alpha = 0.24f), Color.Transparent),
            ),
            topLeft = Offset(0f, below),
            size = Size(size.width, groundH),
        )
    }
}

// ---------------------------------------------------------------------
// 花园
// ---------------------------------------------------------------------

private fun DrawScope.drawGardenDecor(
    style: MazeThemeStyle,
    layout: MazeBoardLayout,
    boardW: Float,
    boardH: Float,
) {
    val top = layout.oy
    val below = layout.oy + boardH
    // 太阳
    val sunX = size.width * 0.84f
    val sunY = (top * 0.34f).coerceAtLeast(size.height * 0.05f)
    val sunR = minOf(size.width, size.height) * 0.045f
    drawCircle(Color(0x33FFE082), sunR * 2.1f, Offset(sunX, sunY))
    drawCircle(Color(0xFFFFE082), sunR, Offset(sunX, sunY))
    drawCircle(Color(0xFFFFF3C4), sunR * 0.72f, Offset(sunX, sunY))
    for (i in 0 until 12) {
        val a = Math.PI.toFloat() * 2f * i / 12f
        drawLine(
            Color(0xCCFFD54F),
            Offset(sunX + cos(a) * sunR * 1.35f, sunY + sin(a) * sunR * 1.35f),
            Offset(sunX + cos(a) * sunR * 1.8f, sunY + sin(a) * sunR * 1.8f),
            strokeWidth = max(1.5f, sunR * 0.09f),
            cap = StrokeCap.Round,
        )
    }
    // 云
    drawStaticCloud(size.width * 0.18f, top * 0.30f, sunR * 0.9f, Color.White.copy(alpha = 0.72f))
    drawStaticCloud(size.width * 0.52f, top * 0.16f, sunR * 0.66f, Color.White.copy(alpha = 0.55f))
    // 远山
    if (top > 24f) {
        val hill =
            Path().apply {
                moveTo(0f, top + 2f)
                lineTo(0f, top * 0.82f)
                quadraticBezierTo(size.width * 0.16f, top * 0.60f, size.width * 0.34f, top * 0.80f)
                quadraticBezierTo(size.width * 0.52f, top * 0.96f, size.width * 0.70f, top * 0.76f)
                quadraticBezierTo(size.width * 0.86f, top * 0.60f, size.width, top * 0.86f)
                lineTo(size.width, top + 2f)
                close()
            }
        drawPath(hill, Color(0xFF7CB86A).copy(alpha = 0.55f))
        drawPath(hill, Color(0x33000000), style = mazeStroke(max(1f, layout.cell * 0.02f)))
    }
    // 地面花丛
    val groundH = size.height - below
    if (groundH > layout.cell * 0.8f) {
        val gy = below + groundH * 0.45f
        for (i in 0 until 5) {
            val hx = size.width * (0.08f + 0.20f * i) + mazeHash01(i, 201) * size.width * 0.05f
            val stemH = layout.cell * (0.22f + 0.18f * mazeHash01(i, 202))
            drawLine(
                Color(0xFF4E8A3A),
                Offset(hx, gy),
                Offset(hx, gy - stemH),
                strokeWidth = max(1.5f, layout.cell * 0.03f),
                cap = StrokeCap.Round,
            )
            val petal = if (i % 2 == 0) Color(0xFFFF7A9A) else Color(0xFFFFF176)
            for (p in 0 until 5) {
                val a = Math.PI.toFloat() * 2f * p / 5f
                drawCircle(
                    petal,
                    layout.cell * 0.055f,
                    Offset(hx + cos(a) * layout.cell * 0.06f, gy - stemH + sin(a) * layout.cell * 0.06f),
                )
            }
            drawCircle(Color(0xFFFFB300), layout.cell * 0.04f, Offset(hx, gy - stemH))
        }
    }
}

// ---------------------------------------------------------------------
// 雨巷
// ---------------------------------------------------------------------

private fun DrawScope.drawRainDecor(
    style: MazeThemeStyle,
    layout: MazeBoardLayout,
    boardW: Float,
    boardH: Float,
) {
    val top = layout.oy
    val below = layout.oy + boardH
    val cloud = Color(0xFFCBD6E2)
    drawStaticCloud(size.width * 0.16f, top * 0.22f, layout.cell * 0.9f, cloud.copy(alpha = 0.85f))
    drawStaticCloud(size.width * 0.46f, top * 0.14f, layout.cell * 1.05f, cloud.copy(alpha = 0.75f))
    drawStaticCloud(size.width * 0.80f, top * 0.24f, layout.cell * 0.85f, cloud.copy(alpha = 0.80f))
    if (top > layout.cell * 1.6f) {
        drawRect(
            Brush.verticalGradient(
                colors = listOf(Color.White.copy(alpha = 0.10f), Color.Transparent),
                startY = top * 0.55f,
                endY = top,
            ),
            topLeft = Offset(0f, top * 0.55f),
            size = Size(size.width, top * 0.45f),
        )
    }
    // 左下的伞
    val groundH = size.height - below
    if (groundH > layout.cell * 1.2f) {
        val ux = size.width * 0.13f
        val uy = below + groundH * 0.42f
        val ur = layout.cell * 0.46f
        val canopy = Color(0xFFEF6C6C)
        val under = Color(0xFF3A4555)
        val arcStart = 180f
        for (i in 0 until 4) {
            drawArc(
                color = if (i % 2 == 0) canopy else Color(0xFFE05A5A),
                startAngle = arcStart + i * 45f,
                sweepAngle = 45f,
                useCenter = true,
                topLeft = Offset(ux - ur, uy - ur),
                size = Size(ur * 2f, ur * 2f),
            )
            val a = Math.PI.toFloat() + Math.PI.toFloat() * (i + 0.5f) / 4f
            drawLine(
                under.copy(alpha = 0.45f),
                Offset(ux, uy),
                Offset(ux + cos(a) * ur, uy + sin(a) * ur),
                strokeWidth = max(1f, ur * 0.05f),
            )
        }
        drawLine(
            canopy,
            Offset(ux, uy),
            Offset(ux + ur * 0.10f, uy + ur * 1.05f),
            strokeWidth = max(2f, ur * 0.12f),
            cap = StrokeCap.Round,
        )
        drawArc(
            color = Color(0xFF8A5A3A),
            startAngle = 0f,
            sweepAngle = 190f,
            useCenter = false,
            topLeft = Offset(ux + ur * 0.10f - ur * 0.22f, uy + ur * 0.92f),
            size = Size(ur * 0.44f, ur * 0.44f),
            style = mazeStroke(max(2f, ur * 0.10f)),
        )
        // 水洼
        val puddle = Color(0xFF9FC4DC).copy(alpha = 0.30f)
        drawOval(puddle, Offset(ux - ur * 0.7f, uy + ur * 1.5f), Size(ur * 1.6f, ur * 0.34f))
        drawOval(puddle, Offset(size.width * 0.68f, uy + ur * 1.2f), Size(ur * 1.4f, ur * 0.30f))
    }
}

// ---------------------------------------------------------------------
// 雪林
// ---------------------------------------------------------------------

private fun DrawScope.drawSnowDecor(
    style: MazeThemeStyle,
    layout: MazeBoardLayout,
    boardW: Float,
    boardH: Float,
) {
    val top = layout.oy
    val below = layout.oy + boardH
    val cloud = Color(0xFFF4FAFF)
    drawStaticCloud(size.width * 0.20f, top * 0.20f, layout.cell * 1.0f, cloud.copy(alpha = 0.75f))
    drawStaticCloud(size.width * 0.72f, top * 0.13f, layout.cell * 0.8f, cloud.copy(alpha = 0.62f))
    val groundH = size.height - below
    // 雪堆
    if (groundH > layout.cell * 0.8f) {
        val gy = below + groundH * 0.7f
        for (i in 0 until 4) {
            val hx = size.width * (0.12f + 0.26f * i)
            drawOval(
                Color.White.copy(alpha = 0.85f),
                Offset(hx - layout.cell * 0.4f, gy - layout.cell * 0.14f),
                Size(layout.cell * 0.8f, layout.cell * 0.28f),
            )
        }
        drawPine(size.width * 0.14f, gy, layout.cell * 0.85f)
        drawPine(size.width * 0.28f, gy + layout.cell * 0.06f, layout.cell * 0.62f)
        drawPine(size.width * 0.86f, gy, layout.cell * 0.75f)
    }
}

private fun DrawScope.drawPine(
    x: Float,
    baseY: Float,
    s: Float,
) {
    val trunkW = s * 0.12f
    drawRect(
        Color(0xFF6B4A2E),
        topLeft = Offset(x - trunkW * 0.5f, baseY - s * 0.22f),
        size = Size(trunkW, s * 0.30f),
    )
    val layers = 3
    for (i in 0 until layers) {
        val ly = baseY - s * (0.18f + 0.24f * i)
        val half = s * (0.34f - 0.07f * i)
        val h = s * 0.34f
        val tree =
            Path().apply {
                moveTo(x, ly - h)
                lineTo(x - half, ly)
                lineTo(x + half, ly)
                close()
            }
        drawPath(tree, Color(0xFF3E7A52))
        // 积雪：只在左半边
        val cap =
            Path().apply {
                moveTo(x, ly - h)
                lineTo(x - half, ly)
                lineTo(x - half * 0.25f, ly)
                close()
            }
        drawPath(cap, Color.White.copy(alpha = 0.78f))
    }
}

// ---------------------------------------------------------------------
// 夜空
// ---------------------------------------------------------------------

private fun DrawScope.drawNightDecor(
    style: MazeThemeStyle,
    layout: MazeBoardLayout,
    boardW: Float,
    boardH: Float,
) {
    val top = layout.oy
    val skyH = (top * 0.92f).coerceAtLeast(0f)
    if (skyH > 20f) {
        for (i in 0 until 54) {
            val hx = size.width * mazeHash01(i * 3 + 1, 301)
            val hy = skyH * mazeHash01(i * 7 + 2, 302)
            val r = 0.8f + 2.0f * mazeHash01(i, 303)
            val a = 0.25f + 0.65f * mazeHash01(i + 17, 304)
            drawCircle(Color.White.copy(alpha = a), r, Offset(hx, hy))
            if (i % 9 == 0) {
                drawLine(
                    Color.White.copy(alpha = a * 0.6f),
                    Offset(hx - r * 2.4f, hy),
                    Offset(hx + r * 2.4f, hy),
                    strokeWidth = 1f,
                )
                drawLine(
                    Color.White.copy(alpha = a * 0.6f),
                    Offset(hx, hy - r * 2.4f),
                    Offset(hx, hy + r * 2.4f),
                    strokeWidth = 1f,
                )
            }
        }
    }
    // 弯月
    val mx = size.width * 0.82f
    val my = (top * 0.26f).coerceAtLeast(size.height * 0.04f)
    val mr = minOf(size.width, size.height) * 0.055f
    drawCircle(Color(0x33FFE9A8), mr * 2.4f, Offset(mx, my))
    drawCircle(Color(0xFFFFF3C4), mr, Offset(mx, my))
    drawCircle(style.skyTop, mr * 0.84f, Offset(mx + mr * 0.42f, my - mr * 0.22f))
    // 远山剪影
    if (top > 24f) {
        val hills =
            Path().apply {
                moveTo(0f, top + 2f)
                lineTo(0f, top * 0.90f)
                quadraticBezierTo(size.width * 0.22f, top * 0.66f, size.width * 0.44f, top * 0.88f)
                quadraticBezierTo(size.width * 0.66f, top * 1.06f, size.width * 0.84f, top * 0.84f)
                quadraticBezierTo(size.width * 0.94f, top * 0.72f, size.width, top * 0.86f)
                lineTo(size.width, top + 2f)
                close()
            }
        drawPath(hills, Color(0xFF0A1428).copy(alpha = 0.85f))
        drawPath(hills, style.wallTop.copy(alpha = 0.16f), style = mazeStroke(max(1f, layout.cell * 0.02f)))
    }
}

// ---------------------------------------------------------------------
// 节日
// ---------------------------------------------------------------------

private fun DrawScope.drawFestivalDecor(
    style: MazeThemeStyle,
    layout: MazeBoardLayout,
    boardW: Float,
    boardH: Float,
) {
    val top = layout.oy
    val stringY = (top * 0.42f).coerceAtLeast(size.height * 0.05f)
    val sag = minOf(size.width, size.height) * 0.04f
    val stringPath =
        Path().apply {
            moveTo(-10f, stringY * 0.55f)
            quadraticBezierTo(size.width * 0.5f, stringY + sag, size.width + 10f, stringY * 0.62f)
        }
    drawPath(stringPath, Color(0xFF4A2020), style = mazeStroke(max(2f, layout.cell * 0.035f)))
    for (i in 0 until 7) {
        val t = 0.08f + 0.14f * i
        val sxp = -10f + (size.width + 20f) * t
        // 二次贝塞尔取点：P0=-10, P1=w/2, P2=w+10
        val sy = (1 - t) * (1 - t) * (stringY * 0.55f) + 2 * (1 - t) * t * (stringY + sag) + t * t * (stringY * 0.62f)
        drawLine(
            Color(0xAA4A2020),
            Offset(sxp, sy),
            Offset(sxp, sy + layout.cell * 0.12f),
            strokeWidth = max(1f, layout.cell * 0.02f),
        )
        drawLantern(sxp, sy + layout.cell * 0.12f, layout.cell * 0.34f, i % 2 == 0)
    }
    // 角旗
    val banner = Color(0xFFB3261E)
    val leftBanner =
        Path().apply {
            moveTo(0f, 0f)
            lineTo(size.width * 0.16f, 0f)
            lineTo(0f, size.height * 0.12f)
            close()
        }
    drawPath(leftBanner, banner.copy(alpha = 0.85f))
    drawPath(leftBanner, Color(0xFFFFD25A).copy(alpha = 0.55f), style = mazeStroke(max(1f, layout.cell * 0.02f)))
    val rightBanner =
        Path().apply {
            moveTo(size.width, 0f)
            lineTo(size.width * 0.84f, 0f)
            lineTo(size.width, size.height * 0.12f)
            close()
        }
    drawPath(rightBanner, banner.copy(alpha = 0.85f))
    drawPath(rightBanner, Color(0xFFFFD25A).copy(alpha = 0.55f), style = mazeStroke(max(1f, layout.cell * 0.02f)))
    // 远处的静态烟花
    for (f in 0 until 2) {
        val fx0 = size.width * (0.24f + 0.42f * f)
        val fy0 = top * (0.24f + 0.12f * f)
        for (i in 0 until 10) {
            val a = Math.PI.toFloat() * 2f * i / 10f
            val rr = layout.cell * (0.30f + 0.10f * mazeHash01(f * 7 + i, 401))
            drawCircle(
                style.accent.copy(alpha = 0.30f),
                max(1f, layout.cell * 0.035f),
                Offset(fx0 + cos(a) * rr, fy0 + sin(a) * rr),
            )
        }
    }
}

private fun DrawScope.drawLantern(
    x: Float,
    y: Float,
    r: Float,
    warm: Boolean,
) {
    val body = if (warm) Color(0xFFFF5252) else Color(0xFFFFB300)
    drawCircle(Color(0xFFFFE082).copy(alpha = 0.22f), r * 1.7f, Offset(x, y + r * 0.4f))
    drawCircle(body, r * 0.72f, Offset(x, y + r * 0.4f))
    drawCircle(mazeLighten(body, 0.40f).copy(alpha = 0.6f), r * 0.32f, Offset(x - r * 0.22f, y + r * 0.24f))
    drawRoundRect(
        Color(0xFFFFD25A),
        Offset(x - r * 0.32f, y + r * 0.02f),
        Size(r * 0.64f, r * 0.22f),
        CornerRadius(r * 0.08f),
    )
    drawRoundRect(
        Color(0xFFFFD25A),
        Offset(x - r * 0.32f, y + r * 0.78f),
        Size(r * 0.64f, r * 0.18f),
        CornerRadius(r * 0.08f),
    )
    drawLine(
        Color(0xFFFFD25A),
        Offset(x, y + r * 0.96f),
        Offset(x, y + r * 1.5f),
        strokeWidth = max(1f, r * 0.10f),
        cap = StrokeCap.Round,
    )
    // 灯笼纸面竖纹
    drawLine(
        body.copy(alpha = 0.35f),
        Offset(x, y + r * 0.02f),
        Offset(x, y + r * 0.78f),
        strokeWidth = max(1f, r * 0.05f),
    )
}

// ---------------------------------------------------------------------
// 云端
// ---------------------------------------------------------------------

private fun DrawScope.drawCloudDecor(
    style: MazeThemeStyle,
    layout: MazeBoardLayout,
    boardW: Float,
    boardH: Float,
) {
    val below = layout.oy + boardH
    val cloud = Color.White.copy(alpha = 0.80f)
    drawStaticCloud(size.width * 0.12f, layout.oy * 0.18f, layout.cell * 1.25f, cloud)
    drawStaticCloud(size.width * 0.48f, layout.oy * 0.10f, layout.cell * 1.0f, cloud.copy(alpha = 0.65f))
    drawStaticCloud(size.width * 0.82f, layout.oy * 0.22f, layout.cell * 1.1f, cloud.copy(alpha = 0.72f))
    val groundH = size.height - below
    if (groundH > layout.cell * 0.6f) {
        drawStaticCloud(size.width * 0.20f, below + groundH * 0.55f, layout.cell * 1.1f, cloud)
        drawStaticCloud(size.width * 0.62f, below + groundH * 0.72f, layout.cell * 0.9f, cloud.copy(alpha = 0.7f))
    }
    // 淡彩虹：画在棋盘上方的天空带，只露出拱顶
    val rx = size.width * 0.5f
    val ry = layout.oy
    val rr = minOf(boardW * 0.42f, layout.oy * 0.92f)
    if (rr > layout.cell * 0.5f) {
        val colors = listOf(Color(0xFFFF8A80), Color(0xFFFFD54F), Color(0xFFA5D6A7), Color(0xFF90CAF9))
        for (i in colors.indices) {
            drawArc(
                colors[i].copy(alpha = 0.22f),
                startAngle = 180f,
                sweepAngle = 180f,
                useCenter = false,
                topLeft = Offset(rx - rr - i * layout.cell * 0.08f, ry - rr - i * layout.cell * 0.08f),
                size = Size((rr + i * layout.cell * 0.08f) * 2f, (rr + i * layout.cell * 0.08f) * 2f),
                style = mazeStroke(layout.cell * 0.07f),
            )
        }
    }
    // 小鸟
    for (i in 0 until 3) {
        val bx = size.width * (0.26f + 0.22f * i)
        val by = layout.oy * (0.30f + 0.10f * (i % 2))
        val s = layout.cell * 0.10f
        drawArc(
            Color(0xFF4A6A8A).copy(alpha = 0.65f),
            startAngle = 200f,
            sweepAngle = 140f,
            useCenter = false,
            topLeft = Offset(bx - s, by - s),
            size = Size(s, s),
            style = mazeStroke(max(1.5f, layout.cell * 0.028f)),
        )
        drawArc(
            Color(0xFF4A6A8A).copy(alpha = 0.65f),
            startAngle = 200f,
            sweepAngle = 140f,
            useCenter = false,
            topLeft = Offset(bx, by - s * 0.2f),
            size = Size(s, s),
            style = mazeStroke(max(1.5f, layout.cell * 0.028f)),
        )
    }
}

// ---------------------------------------------------------------------
// 公共
// ---------------------------------------------------------------------

private fun DrawScope.drawStaticCloud(
    x: Float,
    y: Float,
    scale: Float,
    color: Color,
) {
    drawCircle(color, scale * 0.62f, Offset(x, y))
    drawCircle(color, scale * 0.48f, Offset(x + scale * 0.62f, y + scale * 0.08f))
    drawCircle(color, scale * 0.42f, Offset(x - scale * 0.62f, y + scale * 0.12f))
    drawCircle(color, scale * 0.34f, Offset(x + scale * 0.06f, y - scale * 0.34f))
}

package com.xiaoxiong.weather.ui.game

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipPath
import kotlin.math.max
import kotlin.math.roundToInt

internal val playerGoal = Color(0xFFFFB74D)
internal val aiGoal = Color(0xFF4DD0E1)

private fun rinkMarkColor(theme: RinkTheme): Color =
    when (theme) {
        RinkTheme.ICE -> markBlue
        RinkTheme.BEAR -> canvasBlue
        RinkTheme.WOOD -> woodMark
        RinkTheme.CONCRETE -> concreteMark
    }

private fun rinkSoftColor(theme: RinkTheme): Color =
    when (theme) {
        RinkTheme.ICE, RinkTheme.BEAR -> Color.White
        RinkTheme.WOOD -> Color(0xFFFFE9C4)
        RinkTheme.CONCRETE -> Color(0xFFE8EBEE)
    }

/** 水泥地上的漆线更淡（褪色漆线），其余主题保持原透明度。 */
private fun rinkMarkAlpha(theme: RinkTheme): Float =
    when (theme) {
        RinkTheme.ICE -> 1f
        RinkTheme.BEAR, RinkTheme.WOOD -> 0.90f
        RinkTheme.CONCRETE -> 0.55f
    }

private fun rinkPawColor(theme: RinkTheme): Color =
    when (theme) {
        RinkTheme.ICE -> Color.White.copy(alpha = 0.10f)
        RinkTheme.BEAR -> canvasStitch.copy(alpha = 0.14f)
        RinkTheme.WOOD -> woodMark.copy(alpha = 0.16f)
        RinkTheme.CONCRETE -> concreteMark.copy(alpha = 0.12f)
    }

/**
 * 场地标记（中线 / 中圈 / 开球点 / 四角弧线）：几何与原来完全一致，
 * 仅按主题换配色与透明度（水泥地为褪色漆线）。
 */
internal fun DrawScope.drawRinkMarkings(
    t: HockeyLogic.Table,
    frame: Float,
    iceCorner: Float,
    theme: RinkTheme,
) {
    val l = t.left
    val tp = t.top
    val r = t.right
    val b = t.bottom
    val w = t.width
    val h = t.height
    val short = t.shortSide
    val midX = t.midX
    val midY = t.midY
    val lineW = short * 0.010f
    val mark = rinkMarkColor(theme)
    val soft = rinkSoftColor(theme)
    val k = rinkMarkAlpha(theme)

    // 中线 + 中圈 + 开球点
    drawLine(mark.copy(alpha = 0.42f * k), Offset(l + frame * 0.1f, midY), Offset(r - frame * 0.1f, midY), strokeWidth = lineW)
    drawLine(
        soft.copy(alpha = 0.22f * k),
        Offset(l + frame * 0.1f, midY - lineW * 0.55f),
        Offset(r - frame * 0.1f, midY - lineW * 0.55f),
        strokeWidth = lineW * 0.32f,
    )
    drawCircle(mark.copy(alpha = 0.40f * k), short * 0.155f, Offset(midX, midY), style = strokeOf(lineW * 0.75f))
    drawCircle(soft.copy(alpha = 0.20f * k), short * 0.055f, Offset(midX, midY), style = strokeOf(lineW * 0.40f))
    drawCircle(mark.copy(alpha = 0.50f * k), short * 0.014f, Offset(midX, midY))
    drawPawMark(midX, midY, short * 0.052f, rinkPawColor(theme))

    // 水泥地：中线附近几处褪色斑，模拟被踩花的旧漆
    if (theme == RinkTheme.CONCRETE) {
        for (i in 0 until 6) {
            val px = midX + (hockeyHash01(i * 9 + 2) - 0.5f) * w * 0.55f
            drawCircle(
                Color(0xFFACB1B5).copy(alpha = 0.20f),
                short * (0.012f + 0.018f * hockeyHash01(i * 5 + 3)),
                Offset(px, midY),
            )
        }
    }

    // 四个开球点
    val dotX = short * 0.26f
    val dotY = h * 0.20f
    for (sx in intArrayOf(-1, 1)) {
        for (sy in intArrayOf(-1, 1)) {
            val cx = midX + sx * dotX
            val cy = midY + sy * dotY
            drawCircle(mark.copy(alpha = 0.32f * k), short * 0.042f, Offset(cx, cy), style = strokeOf(lineW * 0.55f))
            drawCircle(mark.copy(alpha = 0.42f * k), short * 0.011f, Offset(cx, cy))
        }
    }

    // 四角弧线
    val cInset = short * 0.065f
    val rad = short * 0.10f
    val arcW = lineW * 0.60f
    drawCornerArc(l + cInset, tp + cInset, rad, 0f, arcW, mark, soft, k)
    drawCornerArc(r - cInset, tp + cInset, rad, 90f, arcW, mark, soft, k)
    drawCornerArc(r - cInset, b - cInset, rad, 180f, arcW, mark, soft, k)
    drawCornerArc(l + cInset, b - cInset, rad, 270f, arcW, mark, soft, k)

    // 中线位两侧墙短标记
    drawLine(
        soft.copy(alpha = 0.28f * k),
        Offset(l, midY),
        Offset(l + short * 0.035f, midY),
        strokeWidth = lineW * 0.55f,
    )
    drawLine(
        soft.copy(alpha = 0.28f * k),
        Offset(r - short * 0.035f, midY),
        Offset(r, midY),
        strokeWidth = lineW * 0.55f,
    )
    // 地面圆角内沿细线
    drawRoundRect(
        soft.copy(alpha = 0.10f * k),
        Offset(l + iceCorner * 0.10f, tp + iceCorner * 0.10f),
        Size(w - iceCorner * 0.20f, h - iceCorner * 0.20f),
        CornerRadius(iceCorner * 0.9f),
        style = strokeOf(lineW * 0.3f),
    )
}

private fun DrawScope.drawCornerArc(
    cx: Float,
    cy: Float,
    radius: Float,
    startAngle: Float,
    strokeWidth: Float,
    mark: Color,
    soft: Color,
    alphaMul: Float,
) {
    drawArc(
        color = mark.copy(alpha = 0.26f * alphaMul),
        startAngle = startAngle,
        sweepAngle = 90f,
        useCenter = false,
        topLeft = Offset(cx - radius, cy - radius),
        size = Size(radius * 2f, radius * 2f),
        style = strokeOf(strokeWidth),
    )
    drawArc(
        color = soft.copy(alpha = 0.16f * alphaMul),
        startAngle = startAngle + 12f,
        sweepAngle = 66f,
        useCenter = false,
        topLeft = Offset(cx - radius * 0.90f, cy - radius * 0.90f),
        size = Size(radius * 1.80f, radius * 1.80f),
        style = strokeOf(strokeWidth * 0.5f),
    )
}

internal fun DrawScope.drawPawMark(
    cx: Float,
    cy: Float,
    s: Float,
    color: Color,
) {
    drawCircle(color, s * 0.42f, Offset(cx, cy + s * 0.10f))
    drawCircle(color, s * 0.155f, Offset(cx - s * 0.36f, cy - s * 0.30f))
    drawCircle(color, s * 0.155f, Offset(cx - s * 0.12f, cy - s * 0.48f))
    drawCircle(color, s * 0.155f, Offset(cx + s * 0.12f, cy - s * 0.48f))
    drawCircle(color, s * 0.155f, Offset(cx + s * 0.36f, cy - s * 0.30f))
}

internal fun DrawScope.drawGoalRecess(
    t: HockeyLogic.Table,
    frame: Float,
    isTop: Boolean,
    team: Color,
) {
    val half = t.goalWidth * 0.5f
    val depth = frame * 0.92f
    if (depth <= 1f || half <= 1f) return
    val y0 = if (isTop) t.top - depth else t.bottom
    val x0 = t.midX - half
    val corner = CornerRadius(depth * 0.28f)
    val topLeft = Offset(x0, y0)

    drawRoundRect(
        Brush.verticalGradient(
            colors = listOf(Color(0xFF07131F), Color(0xFF102738), Color(0xFF07131F)),
            startY = y0,
            endY = y0 + depth,
        ),
        topLeft,
        Size(t.goalWidth, depth),
        corner,
    )
    // 网：细网格 + 纵深阴影（裁到圆角洞口内）
    val recessPath = HockeyRenderCache.recessPath
    recessPath.reset()
    recessPath.addRoundRect(RoundRect(x0, y0, x0 + t.goalWidth, y0 + depth, corner))
    clipPath(recessPath) {
        val cols = (t.goalWidth / t.shortSide * 32f).roundToInt().coerceIn(5, 14)
        for (i in 0 until cols) {
            val gx = x0 + t.goalWidth * (i + 0.5f) / cols
            drawLine(
                Color.White.copy(alpha = 0.15f),
                Offset(gx, y0 + depth * 0.10f),
                Offset(gx, y0 + depth * 0.94f),
                strokeWidth = max(0.7f, t.shortSide * 0.0022f),
            )
        }
        for (r2 in 0 until 3) {
            val gy = y0 + depth * (r2 + 1) / 4f
            drawLine(
                Color.White.copy(alpha = 0.13f),
                Offset(x0 + t.goalWidth * 0.04f, gy),
                Offset(x0 + t.goalWidth * 0.96f, gy),
                strokeWidth = max(0.7f, t.shortSide * 0.0020f),
            )
        }
        val backY = if (isTop) y0 + depth * 0.24f else y0 + depth * 0.76f
        drawCircle(
            Brush.radialGradient(
                colors = listOf(Color.Black.copy(alpha = 0.42f), Color.Transparent),
                center = Offset(t.midX, backY),
                radius = depth * 1.1f,
            ),
            radius = depth * 1.1f,
            center = Offset(t.midX, backY),
        )
    }
    // 洞口描边
    drawRoundRect(
        Color.Black.copy(alpha = 0.55f),
        topLeft,
        Size(t.goalWidth, depth),
        corner,
        style = strokeOf(max(1f, t.shortSide * 0.004f)),
    )
    // 门线
    val lineY = if (isTop) t.top else t.bottom
    drawLine(
        team.copy(alpha = 0.80f),
        Offset(x0, lineY),
        Offset(x0 + t.goalWidth, lineY),
        strokeWidth = t.shortSide * 0.009f,
    )
    drawLine(
        Color.White.copy(alpha = 0.40f),
        Offset(x0, lineY),
        Offset(x0 + t.goalWidth, lineY),
        strokeWidth = t.shortSide * 0.003f,
    )
    // 门柱（圆头 + 高光）：半径与 HockeyLogic 碰撞共用同一公式，保证判定与像素一致
    val postR = HockeyLogic.goalPostRadius(t)
    for (sx in intArrayOf(-1, 1)) {
        val px = t.midX + sx * half
        drawCircle(Color.Black.copy(alpha = 0.30f), postR * 1.08f, Offset(px + postR * 0.10f, lineY + postR * 0.16f))
        drawCircle(team, postR, Offset(px, lineY))
        drawCircle(team.copy(alpha = 0.5f), postR, Offset(px, lineY), style = strokeOf(postR * 0.28f))
        drawCircle(Color.White.copy(alpha = 0.55f), postR * 0.30f, Offset(px - postR * 0.32f, lineY - postR * 0.34f))
    }
}

internal fun DrawScope.drawCrease(
    cx: Float,
    baseY: Float,
    radius: Float,
    isTop: Boolean,
    color: Color,
    lineW: Float,
) {
    if (radius <= 1f) return
    val start = if (isTop) 0f else 180f
    val topLeft = Offset(cx - radius, baseY - radius)
    val sz = Size(radius * 2f, radius * 2f)
    drawArc(color.copy(alpha = 0.11f), start, 180f, true, topLeft, sz)
    drawArc(color.copy(alpha = 0.55f), start, 180f, false, topLeft, sz, style = strokeOf(lineW))
    drawArc(
        Color.White.copy(alpha = 0.20f),
        start + 14f,
        152f,
        false,
        Offset(cx - radius * 0.92f, baseY - radius * 0.92f),
        Size(radius * 1.84f, radius * 1.84f),
        style = strokeOf(lineW * 0.5f),
    )
    drawCircle(color.copy(alpha = 0.45f), lineW * 0.9f, Offset(cx, baseY))
}

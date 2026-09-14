package com.xiaoxiong.weather.ui.game

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.lerp
import com.xiaoxiong.weather.ui.weather.WeatherScene
import com.xiaoxiong.weather.ui.weather.WeatherSceneStyle
import kotlin.math.PI
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

// ---------------------------------------------------------------------
// 调色板与常量
// ---------------------------------------------------------------------

private val woodLight = Color(0xFFC0894A)
private val woodMid = Color(0xFF9B6533)
private val woodDark = Color(0xFF5E3A1C)
private val woodEdge = Color(0xFF3A2210)

private val iceDayTop = Color(0xFFEFF9FF)
private val iceDayMid = Color(0xFFD8EFFB)
private val iceDayBottom = Color(0xFFC2E3F4)
private val iceNightTop = Color(0xFFC7DFEF)
private val iceNightMid = Color(0xFFA9C9DE)
private val iceNightBottom = Color(0xFF8FB2CB)
private val iceCold = Color(0xFF9CC0DA)
private val iceWarm = Color(0xFFFFF0CE)
internal val markBlue = Color(0xFF3D6C96)
private val boardShade = Color(0x66031B2E)

// 场地主题配色：静态层按主题烘焙，几何（库边 / 球门 / 开球点 / 中线）不变
private val woodTableTop = Color(0xFFE8B76C)
private val woodTableMid = Color(0xFFD69C4E)
private val woodTableBottom = Color(0xFFBE7F33)
private val woodPlankSeam = Color(0xFF6D4519)
private val woodVarnish = Color(0xFFFFF3D0)
internal val woodMark = Color(0xFF4A2B0E)
private val walnutLight = Color(0xFF8A5A2B)
private val walnutMid = Color(0xFF5F3A1A)
private val walnutDark = Color(0xFF3A2210)
private val walnutBoardShade = Color(0x99512E10)

private val concreteDayTop = Color(0xFFC3C7CA)
private val concreteDayMid = Color(0xFFAFB4B8)
private val concreteDayBottom = Color(0xFF989EA3)
private val concreteNightTop = Color(0xFF8F9498)
private val concreteNightMid = Color(0xFF7E8388)
private val concreteNightBottom = Color(0xFF6C7176)
private val concreteGroove = Color(0xFF5F666C)
private val concreteCrack = Color(0xFF4A5055)
internal val concreteMark = Color(0xFF5A646B)
private val concreteBoardShade = Color(0x66474E54)

private val canvasCreamTop = Color(0xFFFFF7E6)
private val canvasCreamMid = Color(0xFFF5EDDA)
private val canvasIceBlue = Color(0xFFDCEBF4)
internal val canvasBlue = Color(0xFF7FA8C4)
internal val canvasStitch = Color(0xFF8FB6CF)
private val canvasBoardShade = Color(0x556A93B0)

// ---------------------------------------------------------------------
// 静态冰面：木框 / 冰面 / 标记 / 库边 / 球门
// ---------------------------------------------------------------------

internal fun DrawScope.drawHockeyTableStatic(
    game: HockeyGame,
    style: WeatherSceneStyle,
    isDay: Boolean,
) {
    val t = game.table ?: return
    val theme = game.rinkTheme
    val l = t.left
    val tp = t.top
    val r = t.right
    val b = t.bottom
    val w = t.width
    val h = t.height
    if (w <= 2f || h <= 2f) return
    val short = t.shortSide
    val margin = min(min(l, size.width - r), min(tp, size.height - b))
    val frame = min(short * 0.075f, margin * 0.86f).coerceAtLeast(2f)
    val fl = l - frame
    val ft = tp - frame
    val fr = r + frame
    val fb = b + frame
    val outerW = fr - fl
    val outerH = fb - ft
    val corner = min(frame * 0.85f, short * 0.045f)
    val iceCorner = corner * 0.82f
    val dark = !isDay
    val midX = t.midX
    val midY = t.midY
    val goalHalf = t.goalWidth * 0.5f

    // 整桌投影
    drawRoundRect(
        Color.Black.copy(alpha = if (dark) 0.42f else 0.30f),
        Offset(fl + frame * 0.14f, ft + frame * 0.22f),
        Size(outerW, outerH),
        CornerRadius(corner),
    )

    // 桌框：对角渐变（主题决定木色 / 帆布 / 灰调）
    val frameEdge = rinkFrameEdge(theme)
    drawRoundRect(
        Brush.linearGradient(
            colors = rinkFrameColors(theme),
            start = Offset(fl, ft),
            end = Offset(fr, fb),
        ),
        Offset(fl, ft),
        Size(outerW, outerH),
        CornerRadius(corner),
    )
    drawRinkFrameTexture(theme, fl, ft, fb, outerW, outerH, frame)
    // 桌框内侧高光 + 外描边
    drawRoundRect(
        Color.White.copy(alpha = if (dark) 0.05f else 0.13f),
        Offset(fl + frame * 0.14f, ft + frame * 0.14f),
        Size(outerW - frame * 0.28f, outerH - frame * 0.28f),
        CornerRadius(corner * 0.9f),
        style = strokeOf(frame * 0.16f),
    )
    drawRoundRect(
        frameEdge.copy(alpha = if (dark) 0.75f else 0.5f),
        Offset(fl, ft),
        Size(outerW, outerH),
        CornerRadius(corner),
        style = strokeOf(short * 0.006f),
    )
    // 四角收边斜接
    drawFrameMiter(fl, ft, l, tp, frame, frameEdge)
    drawFrameMiter(fr, ft, r, tp, frame, frameEdge)
    drawFrameMiter(fl, fb, l, b, frame, frameEdge)
    drawFrameMiter(fr, fb, r, b, frame, frameEdge)

    // 地面（按主题烘焙：冰面 / 木桌 / 水泥 / 画布）
    val surfacePath = HockeyRenderCache.icePath
    surfacePath.reset()
    surfacePath.addRoundRect(RoundRect(l, tp, r, b, CornerRadius(iceCorner)))
    clipPath(surfacePath) {
        drawRinkSurface(theme, t, style, isDay, midX, midY, dark)
    }

    drawRinkMarkings(t, frame, iceCorner, theme)

    // 库边（主题材质 + 内阴影 + 库鼻高光）
    val shade = rinkBoardShade(theme)
    val board = short * 0.028f
    rinkBoardRail(theme)?.let { rail ->
        drawRect(rail, Offset(l, tp), Size(w, board))
        drawRect(rail, Offset(l, b - board), Size(w, board))
        drawRect(rail, Offset(l, tp), Size(board, h))
        drawRect(rail, Offset(r - board, tp), Size(board, h))
    }
    drawRect(
        Brush.verticalGradient(listOf(shade, Color.Transparent), startY = tp, endY = tp + board),
        Offset(l, tp),
        Size(w, board),
    )
    drawRect(
        Brush.verticalGradient(listOf(Color.Transparent, shade), startY = b - board, endY = b),
        Offset(l, b - board),
        Size(w, board),
    )
    drawRect(
        Brush.horizontalGradient(listOf(shade, Color.Transparent), startX = l, endX = l + board),
        Offset(l, tp),
        Size(board, h),
    )
    drawRect(
        Brush.horizontalGradient(listOf(Color.Transparent, shade), startX = r - board, endX = r),
        Offset(r - board, tp),
        Size(board, h),
    )
    val nose = rinkBoardNose(theme)
    val goalLeft = midX - goalHalf
    val goalRight = midX + goalHalf
    drawLine(nose, Offset(l + board, tp + board), Offset(goalLeft, tp + board), strokeWidth = short * 0.006f)
    drawLine(nose, Offset(goalRight, tp + board), Offset(r - board, tp + board), strokeWidth = short * 0.006f)
    drawLine(nose, Offset(l + board, b - board), Offset(goalLeft, b - board), strokeWidth = short * 0.006f)
    drawLine(nose, Offset(goalRight, b - board), Offset(r - board, b - board), strokeWidth = short * 0.006f)
    drawLine(nose, Offset(l + board, tp + board), Offset(l + board, b - board), strokeWidth = short * 0.006f)
    drawLine(nose, Offset(r - board, tp + board), Offset(r - board, b - board), strokeWidth = short * 0.006f)
    drawRoundRect(
        frameEdge.copy(alpha = 0.35f),
        Offset(l, tp),
        Size(w, h),
        CornerRadius(iceCorner),
        style = strokeOf(short * 0.008f),
    )
    drawRoundRect(
        Color.White.copy(alpha = 0.18f),
        Offset(l + 1.5f, tp + 1.5f),
        Size(w - 3f, h - 3f),
        CornerRadius(iceCorner * 0.96f),
        style = strokeOf(short * 0.003f),
    )

    // 球门（洞口 + 网 + 门柱）
    drawGoalRecess(t, frame, isTop = true, team = aiGoal)
    drawGoalRecess(t, frame, isTop = false, team = playerGoal)
    // 门区半圆：木质主题用深色描边，水泥 / 画布沿用队伍色
    val creaseR = goalHalf * 1.28f
    val creaseW = short * 0.008f
    drawCrease(midX, tp, creaseR, isTop = true, color = creaseColor(theme, aiGoal), lineW = creaseW)
    drawCrease(midX, b, creaseR, isTop = false, color = creaseColor(theme, playerGoal), lineW = creaseW)
}

// ---------------------------------------------------------------------
// 场地主题调色 / 地面与边框烘焙
// ---------------------------------------------------------------------

private fun rinkFrameColors(theme: RinkTheme): List<Color> =
    when (theme) {
        RinkTheme.ICE -> listOf(woodLight, woodMid, woodDark)
        RinkTheme.BEAR -> listOf(Color(0xFFEADDC4), Color(0xFFD8C6A6), Color(0xFFC0AA88))
        RinkTheme.WOOD -> listOf(walnutLight, walnutMid, walnutDark)
        RinkTheme.CONCRETE -> listOf(Color(0xFFB6BBBF), Color(0xFF989DA2), Color(0xFF7B8085))
    }

private fun rinkFrameEdge(theme: RinkTheme): Color =
    when (theme) {
        RinkTheme.ICE, RinkTheme.WOOD -> woodEdge
        RinkTheme.BEAR -> Color(0xFF8FA8B8)
        RinkTheme.CONCRETE -> Color(0xFF5C6166)
    }

/** 桌框纹理：冰 / 木主题为木纹，画布为织纹，水泥为骨料斑点。 */
private fun DrawScope.drawRinkFrameTexture(
    theme: RinkTheme,
    fl: Float,
    ft: Float,
    fb: Float,
    outerW: Float,
    outerH: Float,
    frame: Float,
) {
    when (theme) {
        RinkTheme.ICE, RinkTheme.WOOD -> {
            val count = if (theme == RinkTheme.WOOD) 16 else 14
            val dark = if (theme == RinkTheme.WOOD) 0.11f else 0.07f
            val light = if (theme == RinkTheme.WOOD) 0.08f else 0.05f
            for (i in 0 until count) {
                val gx = fl + outerW * hockeyHash01(i * 5 + 31)
                val sway = sin(i * 1.7f) * frame * 0.05f
                drawLine(
                    if (i % 2 == 0) Color.Black.copy(alpha = dark) else Color.White.copy(alpha = light),
                    Offset(gx, ft + frame * 0.10f),
                    Offset(gx + sway, fb - frame * 0.10f),
                    strokeWidth = 0.8f + hockeyHash01(i * 2 + 7),
                )
            }
        }
        RinkTheme.BEAR -> {
            for (i in 0 until 12) {
                val gx = fl + outerW * hockeyHash01(i * 5 + 19)
                drawLine(
                    canvasStitch.copy(alpha = 0.14f),
                    Offset(gx, ft + frame * 0.12f),
                    Offset(gx, fb - frame * 0.12f),
                    strokeWidth = 0.8f,
                )
            }
        }
        RinkTheme.CONCRETE -> {
            for (i in 0 until 22) {
                val gx = fl + outerW * hockeyHash01(i * 7 + 13)
                val gy = ft + outerH * hockeyHash01(i * 11 + 5)
                drawCircle(
                    if (i % 3 == 0) Color.White.copy(alpha = 0.06f) else Color.Black.copy(alpha = 0.05f),
                    0.7f + hockeyHash01(i * 3 + 1) * 1.5f,
                    Offset(gx, gy),
                )
            }
        }
    }
}

private fun rinkBoardShade(theme: RinkTheme): Color =
    when (theme) {
        RinkTheme.ICE -> boardShade
        RinkTheme.BEAR -> canvasBoardShade
        RinkTheme.WOOD -> walnutBoardShade
        RinkTheme.CONCRETE -> concreteBoardShade
    }

/** 库边实体色：冰面沿用原「无实体库边」效果（null），其余主题有各自材质的库边。 */
private fun rinkBoardRail(theme: RinkTheme): Color? =
    when (theme) {
        RinkTheme.ICE -> null
        RinkTheme.BEAR -> Color(0xFFF1E5CE).copy(alpha = 0.62f)
        RinkTheme.WOOD -> Color(0xFF50300F).copy(alpha = 0.78f)
        RinkTheme.CONCRETE -> Color(0xFFD2D6D9).copy(alpha = 0.55f)
    }

private fun rinkBoardNose(theme: RinkTheme): Color =
    when (theme) {
        RinkTheme.ICE -> Color.White.copy(alpha = 0.30f)
        RinkTheme.BEAR -> Color.White.copy(alpha = 0.42f)
        RinkTheme.WOOD -> Color(0xFFFFD9A0).copy(alpha = 0.28f)
        RinkTheme.CONCRETE -> Color.White.copy(alpha = 0.38f)
    }

private fun creaseColor(
    theme: RinkTheme,
    team: Color,
): Color =
    when (theme) {
        RinkTheme.WOOD -> lerp(team, woodMark, 0.55f)
        RinkTheme.CONCRETE -> lerp(team, Color(0xFF6E7479), 0.45f)
        else -> team
    }

/** 主题地面：几何由外层 clipPath 保证，只画材质。 */
private fun DrawScope.drawRinkSurface(
    theme: RinkTheme,
    t: HockeyLogic.Table,
    style: WeatherSceneStyle,
    isDay: Boolean,
    midX: Float,
    midY: Float,
    dark: Boolean,
) {
    when (theme) {
        RinkTheme.ICE -> drawIceSurface(t, style, isDay, midX, midY, dark)
        RinkTheme.WOOD -> drawWoodSurface(t, midX, midY)
        RinkTheme.CONCRETE -> drawConcreteSurface(t, isDay, midX, midY)
        RinkTheme.BEAR -> drawBearSurface(t)
    }
}

/** 冰面：保持现有冷色渐变、划痕、反光与暗角。 */
private fun DrawScope.drawIceSurface(
    t: HockeyLogic.Table,
    style: WeatherSceneStyle,
    isDay: Boolean,
    midX: Float,
    midY: Float,
    dark: Boolean,
) {
    val l = t.left
    val tp = t.top
    val r = t.right
    val b = t.bottom
    val w = t.width
    val h = t.height
    val short = t.shortSide
    val iceTop = tintIce(if (isDay) iceDayTop else iceNightTop, style, isDay)
    val iceMid = tintIce(if (isDay) iceDayMid else iceNightMid, style, isDay)
    val iceBottom = tintIce(if (isDay) iceDayBottom else iceNightBottom, style, isDay)
    drawRect(
        Brush.verticalGradient(listOf(iceTop, iceMid, iceBottom), startY = tp, endY = b),
        Offset(l, tp),
        Size(w, h),
    )
    // 顶侧灯带反光
    drawCircle(
        Brush.radialGradient(
            colors = listOf(Color.White.copy(alpha = 0.14f), Color.Transparent),
            center = Offset(l + w * 0.22f, tp + h * 0.10f),
            radius = max(w, h) * 0.55f,
        ),
        radius = max(w, h) * 0.55f,
        center = Offset(l + w * 0.22f, tp + h * 0.10f),
    )
    // 细化划痕
    for (i in 0 until 120) {
        val fx = hockeyHash01(i * 3 + 1)
        val fy = hockeyHash01(i * 3 + 2)
        val ang = hockeyHash01(i * 5 + 4) * PI.toFloat()
        val len = short * (0.010f + hockeyHash01(i * 7 + 5) * 0.050f)
        val dx = cos(ang) * len * 0.5f
        val dy = sin(ang) * len * 0.5f
        val px = l + fx * w
        val py = tp + fy * h
        drawLine(
            if (i % 2 == 0) Color.White.copy(alpha = 0.05f) else Color(0xFF2E4B63).copy(alpha = 0.045f),
            Offset(px - dx, py - dy),
            Offset(px + dx, py + dy),
            strokeWidth = 0.6f + hockeyHash01(i * 11 + 6) * 1.1f,
        )
    }
    // 对角反光带
    drawRect(
        Brush.linearGradient(
            colors = listOf(Color.Transparent, Color.White.copy(alpha = 0.06f), Color.Transparent),
            start = Offset(l, tp + h * 0.72f),
            end = Offset(r, tp + h * 0.28f),
        ),
        Offset(l, tp),
        Size(w, h),
    )
    // 暗角
    drawRect(
        Brush.radialGradient(
            colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.05f), Color.Black.copy(alpha = if (dark) 0.30f else 0.20f)),
            center = Offset(midX, midY),
            radius = max(w, h) * 0.72f,
        ),
        Offset(l, tp),
        Size(w, h),
    )
}

/** 木质球桌：蜂蜜木纹条板 + 清漆反光 + 暗角。 */
private fun DrawScope.drawWoodSurface(
    t: HockeyLogic.Table,
    midX: Float,
    midY: Float,
) {
    val l = t.left
    val tp = t.top
    val r = t.right
    val b = t.bottom
    val w = t.width
    val h = t.height
    val short = t.shortSide
    drawRect(
        Brush.verticalGradient(listOf(woodTableTop, woodTableMid, woodTableBottom), startY = tp, endY = b),
        Offset(l, tp),
        Size(w, h),
    )
    // 竖向条板：分缝 + 顺纹
    val planks = (w / (short * 0.34f)).roundToInt().coerceIn(4, 9)
    val plankW = w / planks
    val seamW = max(1f, short * 0.0038f)
    for (i in 0 until planks) {
        val x0 = l + i * plankW
        drawRect(
            if (i % 2 == 0) Color.White.copy(alpha = 0.03f) else Color.Black.copy(alpha = 0.025f),
            Offset(x0, tp),
            Size(plankW, h),
        )
        drawLine(woodPlankSeam.copy(alpha = 0.55f), Offset(x0, tp), Offset(x0, b), strokeWidth = seamW)
        drawLine(
            woodPlankSeam.copy(alpha = 0.22f),
            Offset(x0 + seamW * 0.6f, tp),
            Offset(x0 + seamW * 0.6f, b),
            strokeWidth = max(0.6f, short * 0.0012f),
        )
        for (g in 0 until 2) {
            val gx = x0 + plankW * (0.32f + g * 0.36f)
            val sway = sin(i * 1.9f + g * 2.1f) * plankW * 0.05f
            drawLine(
                Color(0xFF8A5A28).copy(alpha = 0.10f + 0.08f * hockeyHash01(i * 13 + g)),
                Offset(gx + sway, tp),
                Offset(gx - sway, b),
                strokeWidth = 0.8f + hockeyHash01(i * 7 + g) * 1.1f,
            )
        }
    }
    // 清漆反光：顶部灯带 + 对角
    drawCircle(
        Brush.radialGradient(
            colors = listOf(woodVarnish.copy(alpha = 0.20f), Color.Transparent),
            center = Offset(l + w * 0.24f, tp + h * 0.10f),
            radius = max(w, h) * 0.55f,
        ),
        radius = max(w, h) * 0.55f,
        center = Offset(l + w * 0.24f, tp + h * 0.10f),
    )
    drawRect(
        Brush.linearGradient(
            colors = listOf(Color.Transparent, Color.White.copy(alpha = 0.08f), Color.Transparent),
            start = Offset(l, tp + h * 0.74f),
            end = Offset(r, tp + h * 0.26f),
        ),
        Offset(l, tp),
        Size(w, h),
    )
    drawRect(
        Brush.radialGradient(
            colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.06f), Color.Black.copy(alpha = 0.20f)),
            center = Offset(midX, midY),
            radius = max(w, h) * 0.72f,
        ),
        Offset(l, tp),
        Size(w, h),
    )
}

/** 水泥地：灰底 + 分格伸缩缝 + 细裂纹 + 骨料斑点。 */
private fun DrawScope.drawConcreteSurface(
    t: HockeyLogic.Table,
    isDay: Boolean,
    midX: Float,
    midY: Float,
) {
    val l = t.left
    val tp = t.top
    val r = t.right
    val b = t.bottom
    val w = t.width
    val h = t.height
    val short = t.shortSide
    drawRect(
        Brush.verticalGradient(
            listOf(
                if (isDay) concreteDayTop else concreteNightTop,
                if (isDay) concreteDayMid else concreteNightMid,
                if (isDay) concreteDayBottom else concreteNightBottom,
            ),
            startY = tp,
            endY = b,
        ),
        Offset(l, tp),
        Size(w, h),
    )
    // 分格伸缩缝
    val cell = max(short * 0.52f, 48f)
    val grooveW = max(1f, short * 0.0035f)
    var gx = l + cell
    while (gx < r) {
        drawLine(concreteGroove.copy(alpha = 0.40f), Offset(gx, tp), Offset(gx, b), strokeWidth = grooveW)
        drawLine(
            Color.White.copy(alpha = 0.12f),
            Offset(gx + grooveW * 0.7f, tp),
            Offset(gx + grooveW * 0.7f, b),
            strokeWidth = max(0.6f, short * 0.0012f),
        )
        gx += cell
    }
    var gy = tp + cell
    while (gy < b) {
        drawLine(concreteGroove.copy(alpha = 0.40f), Offset(l, gy), Offset(r, gy), strokeWidth = grooveW)
        drawLine(
            Color.White.copy(alpha = 0.12f),
            Offset(l, gy + grooveW * 0.7f),
            Offset(r, gy + grooveW * 0.7f),
            strokeWidth = max(0.6f, short * 0.0012f),
        )
        gy += cell
    }
    // 细裂纹：确定性折线
    for (i in 0 until 8) {
        var cx = l + hockeyHash01(i * 17 + 3) * w
        var cy = tp + hockeyHash01(i * 23 + 9) * h
        var ang = hockeyHash01(i * 29 + 1) * 2f * PI.toFloat()
        val segLen = short * (0.03f + hockeyHash01(i * 31 + 2) * 0.05f)
        for (seg in 0 until 5) {
            val nx = cx + cos(ang) * segLen
            val ny = cy + sin(ang) * segLen
            drawLine(concreteCrack.copy(alpha = 0.22f), Offset(cx, cy), Offset(nx, ny), strokeWidth = max(0.6f, short * 0.0015f))
            cx = nx
            cy = ny
            ang += (hockeyHash01(i * 37 + seg) - 0.5f) * 0.9f
        }
    }
    // 骨料斑点
    for (i in 0 until 90) {
        val px = l + hockeyHash01(i * 5 + 11) * w
        val py = tp + hockeyHash01(i * 7 + 23) * h
        drawCircle(
            if (i % 3 == 0) Color.White.copy(alpha = 0.05f) else Color.Black.copy(alpha = 0.05f),
            0.6f + hockeyHash01(i * 3 + 1) * 1.4f,
            Offset(px, py),
        )
    }
    drawRect(
        Brush.radialGradient(
            colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.05f), Color.Black.copy(alpha = 0.18f)),
            center = Offset(midX, midY),
            radius = max(w, h) * 0.72f,
        ),
        Offset(l, tp),
        Size(w, h),
    )
}

/**
 * 小熊画布：奶油 / 冰蓝底 + 极淡爪印 / 脸水印（alpha 0.05–0.08，大间距），
 * 针脚虚线 + 毛边收边，保证不喧宾夺主。
 */
private fun DrawScope.drawBearSurface(t: HockeyLogic.Table) {
    val l = t.left
    val tp = t.top
    val r = t.right
    val b = t.bottom
    val w = t.width
    val h = t.height
    val short = t.shortSide
    drawRect(
        Brush.verticalGradient(listOf(canvasCreamTop, canvasCreamMid, canvasIceBlue), startY = tp, endY = b),
        Offset(l, tp),
        Size(w, h),
    )
    // 重复水印：3 列大间距，爪印与熊脸交替
    val cols = 3
    val cell = w / cols
    val rowH = cell * 1.12f
    val rows = ceil(h / rowH).toInt() + 1
    for (row in 0 until rows) {
        for (col in 0 until cols) {
            val offset = if (row % 2 == 1) cell * 0.5f else 0f
            val cx = l + cell * (col + 0.5f) + offset
            val cy = tp + rowH * (row + 0.5f)
            val s = min(cell, rowH) * (0.30f + 0.05f * hockeyHash01(row * 11 + col * 5))
            val alpha = 0.05f + 0.03f * hockeyHash01(row * 7 + col * 13 + 4)
            if ((row + col) % 2 == 0) {
                drawPawMark(cx, cy, s, canvasBlue.copy(alpha = alpha))
            } else {
                drawBearFaceWatermark(cx, cy, s, canvasStitch.copy(alpha = alpha))
            }
        }
    }
    // 帆布织纹
    for (i in 0 until 36) {
        val py = tp + h * hockeyHash01(i * 3 + 5)
        drawLine(Color.White.copy(alpha = 0.03f), Offset(l, py), Offset(r, py), strokeWidth = 0.7f)
    }
    // 轻暗角
    drawRect(
        Brush.radialGradient(
            colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.04f), Color.Black.copy(alpha = 0.10f)),
            center = Offset(t.midX, t.midY),
            radius = max(w, h) * 0.72f,
        ),
        Offset(l, tp),
        Size(w, h),
    )
    drawStitchBorder(t, short)
}

/** 极简熊脸水印：头 + 双耳 + 眼睛 + 口鼻。 */
private fun DrawScope.drawBearFaceWatermark(
    cx: Float,
    cy: Float,
    s: Float,
    color: Color,
) {
    drawCircle(color, s * 0.42f, Offset(cx, cy))
    drawCircle(color, s * 0.17f, Offset(cx - s * 0.38f, cy - s * 0.34f))
    drawCircle(color, s * 0.17f, Offset(cx + s * 0.38f, cy - s * 0.34f))
    drawCircle(color.copy(alpha = color.alpha * 1.2f), s * 0.08f, Offset(cx - s * 0.15f, cy - s * 0.06f))
    drawCircle(color.copy(alpha = color.alpha * 1.2f), s * 0.08f, Offset(cx + s * 0.15f, cy - s * 0.06f))
    drawOval(
        color.copy(alpha = color.alpha * 0.9f),
        topLeft = Offset(cx - s * 0.14f, cy + s * 0.10f),
        size = Size(s * 0.28f, s * 0.18f),
    )
}

/** 画布收边：内圈针脚虚线 + 边缘毛刺。 */
private fun DrawScope.drawStitchBorder(
    t: HockeyLogic.Table,
    short: Float,
) {
    val inset = short * 0.024f
    val radius = (short * 0.045f * 0.82f - inset).coerceAtLeast(1f)
    val dash = max(2f, short * 0.016f)
    val gap = max(1.5f, short * 0.011f)
    val path = HockeyRenderCache.stitchPath
    path.reset()
    path.addRoundRect(RoundRect(t.left + inset, t.top + inset, t.right - inset, t.bottom - inset, CornerRadius(radius)))
    drawPath(
        path,
        canvasStitch.copy(alpha = 0.55f),
        style = Stroke(width = max(1f, short * 0.0035f), pathEffect = PathEffect.dashPathEffect(floatArrayOf(dash, gap))),
    )
    // 毛边：沿四边向内的短须
    val fray = short * (0.010f + 0.010f * hockeyHash01(5))
    for (i in 0 until 48) {
        val side = i % 4
        val u = hockeyHash01(i * 7 + 3)
        val push = short * (0.004f + 0.008f * hockeyHash01(i * 11 + 1))
        val x0: Float
        val y0: Float
        val dx: Float
        val dy: Float
        when (side) {
            0 -> {
                x0 = t.left + u * t.width
                y0 = t.top + inset
                dx = 0f
                dy = -push
            }
            1 -> {
                x0 = t.left + u * t.width
                y0 = t.bottom - inset
                dx = 0f
                dy = push
            }
            2 -> {
                x0 = t.left + inset
                y0 = t.top + u * t.height
                dx = -push
                dy = 0f
            }
            else -> {
                x0 = t.right - inset
                y0 = t.top + u * t.height
                dx = push
                dy = 0f
            }
        }
        drawLine(
            canvasStitch.copy(alpha = 0.28f),
            Offset(x0, y0),
            Offset(x0 + dx - push * 0.4f, y0 + dy + fray * 0.2f),
            strokeWidth = max(0.6f, short * 0.0015f),
        )
    }
}

private fun DrawScope.drawFrameMiter(
    ox: Float,
    oy: Float,
    ix: Float,
    iy: Float,
    frame: Float,
    edge: Color,
) {
    val dx = ix - ox
    val dy = iy - oy
    drawLine(edge.copy(alpha = 0.42f), Offset(ox, oy), Offset(ix, iy), strokeWidth = frame * 0.10f)
    drawLine(
        Color.White.copy(alpha = 0.09f),
        Offset(ox + dx * 0.12f, oy + dy * 0.12f),
        Offset(ox + dx * 0.42f, oy + dy * 0.42f),
        strokeWidth = frame * 0.05f,
    )
}

private fun tintIce(
    c: Color,
    style: WeatherSceneStyle,
    isDay: Boolean,
): Color {
    val cold =
        when (style.scene) {
            WeatherScene.SNOW,
            WeatherScene.HEAVY_SNOW,
            WeatherScene.FREEZING_RAIN,
            WeatherScene.HAIL,
            -> 0.16f
            WeatherScene.RAIN,
            WeatherScene.HEAVY_RAIN,
            WeatherScene.DRIZZLE,
            WeatherScene.THUNDER,
            WeatherScene.FOG,
            WeatherScene.OVERCAST,
            WeatherScene.UNKNOWN,
            -> 0.10f
            else -> 0f
        }
    return when {
        cold > 0f -> lerp(c, iceCold, cold)
        !isDay -> lerp(c, Color(0xFF5E7E96), 0.10f)
        style.scene == WeatherScene.CLEAR || style.scene == WeatherScene.PARTLY_CLOUDY -> lerp(c, iceWarm, 0.05f)
        else -> c
    }
}

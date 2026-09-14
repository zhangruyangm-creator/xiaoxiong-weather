package com.xiaoxiong.weather.ui.game

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.lerp
import com.xiaoxiong.weather.ui.weather.WeatherScene
import com.xiaoxiong.weather.ui.weather.WeatherSceneStyle
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.sin

/** 烘焙进静态层的台面绘制：木框 / 台呢 / 库边 / 开球线与置球点 / 菱形 / 袋口。 */
internal fun DrawScope.drawPoolTableStatic(
    game: PoolGame,
    style: WeatherSceneStyle,
    isDay: Boolean,
) {
    val scale = game.viewScale
    if (scale <= 0f) return
    val table = game.table
    val l = game.toCanvasX(table.left)
    val t = game.toCanvasY(table.top)
    val r = game.toCanvasX(table.right)
    val b = game.toCanvasY(table.bottom)
    if (r - l <= 1f || b - t <= 1f) return

    val ballR = table.ballRadius * scale
    if (ballR < 0.5f) return
    val frame = ballR * 3.4f
    val fl = l - frame
    val ft = t - frame
    val fr = r + frame
    val fb = b + frame
    val corner = frame * 0.55f
    val dark = !isDay
    val felt = feltBase(style, isDay, game.clothTheme)

    // 整桌投影
    drawRoundRect(
        Color.Black.copy(alpha = if (dark) 0.44f else 0.30f),
        Offset(fl + frame * 0.30f, ft + frame * 0.46f),
        Size(fr - fl, fb - ft),
        CornerRadius(corner),
    )

    // 木框：对角线性渐变做木质明暗
    drawRoundRect(
        Brush.linearGradient(
            colors = listOf(honeyWoodLight, honeyWoodMid, honeyWoodDark),
            start = Offset(fl, ft),
            end = Offset(fr, fb),
        ),
        Offset(fl, ft),
        Size(fr - fl, fb - ft),
        CornerRadius(corner),
    )
    // 木框内侧高光
    drawRoundRect(
        Color.White.copy(alpha = if (dark) 0.05f else 0.13f),
        Offset(fl + frame * 0.14f, ft + frame * 0.14f),
        Size(fr - fl - frame * 0.28f, fb - ft - frame * 0.28f),
        CornerRadius(corner * 0.9f),
        style = Stroke(width = frame * 0.16f),
    )
    // 外描边
    drawRoundRect(
        woodEdge.copy(alpha = if (dark) 0.75f else 0.5f),
        Offset(fl, ft),
        Size(fr - fl, fb - ft),
        CornerRadius(corner),
        style = Stroke(width = ballR * 0.22f),
    )
    // 极淡木纹：沿长轴的确定性细纹，仅烘焙一次
    for (i in 0 until 12) {
        val gx = fl + (fr - fl) * hash01(i * 5 + 31)
        val sway = sin(i * 1.7f) * frame * 0.06f
        drawLine(
            if (i % 2 == 0) woodGrainDark else woodGrainLight,
            Offset(gx, ft + frame * 0.08f),
            Offset(gx + sway, fb - frame * 0.08f),
            strokeWidth = 0.8f + hash01(i * 2 + 7),
        )
    }

    // 四角斜接缝
    val seamDark = Color.Black.copy(alpha = 0.22f)
    val seamHi = Color.White.copy(alpha = 0.10f)
    drawMiter(fl, ft, l, t, seamDark, seamHi, ballR)
    drawMiter(fr, ft, r, t, seamDark, seamHi, ballR)
    drawMiter(fl, fb, l, b, seamDark, seamHi, ballR)
    drawMiter(fr, fb, r, b, seamDark, seamHi, ballR)

    // 台呢
    drawRect(felt, Offset(l, t), Size(r - l, b - t))

    val cx = (l + r) * 0.5f
    val cy = (t + b) * 0.5f
    val cushion = ballR * 0.85f

    clipRect(l, t, r, b) {
        // 径向暗角
        drawRect(
            Brush.radialGradient(
                colors =
                    listOf(
                        Color.Transparent,
                        Color.Black.copy(alpha = 0.05f),
                        Color.Black.copy(alpha = if (dark) 0.34f else 0.24f),
                    ),
                center = Offset(cx, cy),
                radius = max(r - l, b - t) * 0.72f,
            ),
            Offset(l, t),
            Size(r - l, b - t),
        )

        // 织纹：确定性伪随机，短线横竖交错。长度/线宽以球半径为尺度（像素级），
        // 不能用 viewScale 放大——那会让几百条半透明巨线铺满整张台呢，把主题色洗成灰。
        val texCount = 240
        val hi = Color.White.copy(alpha = 0.045f)
        val lo = Color.Black.copy(alpha = 0.05f)
        for (i in 0 until texCount) {
            val fx = hash01(i * 2 + 1)
            val fy = hash01(i * 2 + 2)
            val px = l + fx * (r - l)
            val py = t + fy * (b - t)
            val rnd = hash01(i * 5 + 3)
            val len = clothWeaveLength(ballR, rnd)
            val width = clothWeaveWidth(ballR)
            if (i % 2 == 0) {
                drawLine(hi, Offset(px - len * 0.5f, py), Offset(px + len * 0.5f, py), strokeWidth = width)
            } else {
                drawLine(lo, Offset(px, py - len * 0.5f), Offset(px, py + len * 0.5f), strokeWidth = width)
            }
        }
    }

    // 库边内阴影：四边向内心渐隐
    drawRect(
        Brush.verticalGradient(listOf(cushionShade.copy(alpha = 0.55f), Color.Transparent), startY = t, endY = t + cushion),
        Offset(l, t),
        Size(r - l, cushion),
    )
    drawRect(
        Brush.verticalGradient(listOf(Color.Transparent, cushionShade.copy(alpha = 0.55f)), startY = b - cushion, endY = b),
        Offset(l, b - cushion),
        Size(r - l, cushion),
    )
    drawRect(
        Brush.horizontalGradient(listOf(cushionShade.copy(alpha = 0.55f), Color.Transparent), startX = l, endX = l + cushion),
        Offset(l, t),
        Size(cushion, b - t),
    )
    drawRect(
        Brush.horizontalGradient(listOf(Color.Transparent, cushionShade.copy(alpha = 0.55f)), startX = r - cushion, endX = r),
        Offset(r - cushion, t),
        Size(cushion, b - t),
    )
    // 库鼻高光
    val nose = Color.White.copy(alpha = 0.08f)
    drawLine(nose, Offset(l + cushion, t + cushion), Offset(r - cushion, t + cushion), strokeWidth = ballR * 0.05f)
    drawLine(nose, Offset(l + cushion, b - cushion), Offset(r - cushion, b - cushion), strokeWidth = ballR * 0.05f)
    drawLine(nose, Offset(l + cushion, t + cushion), Offset(l + cushion, b - cushion), strokeWidth = ballR * 0.05f)
    drawLine(nose, Offset(r - cushion, t + cushion), Offset(r - cushion, b - cushion), strokeWidth = ballR * 0.05f)
    // 台呢边界细描边
    drawRect(
        woodEdge.copy(alpha = 0.35f),
        Offset(l, t),
        Size(r - l, b - t),
        style = Stroke(width = ballR * 0.10f),
    )
    // 库边内沿接缝：极淡一圈，强调台呢与库边的分界
    drawRect(
        cushionSeam,
        Offset(l + cushion * 0.42f, t + cushion * 0.42f),
        Size(r - l - cushion * 0.84f, b - t - cushion * 0.84f),
        style = Stroke(width = ballR * 0.05f),
    )

    // 开球线与置球点
    val headY = game.toCanvasY(table.headStringY)
    drawDashedLine(
        Offset(l + ballR * 0.6f, headY),
        Offset(r - ballR * 0.6f, headY),
        Color.White.copy(alpha = 0.20f),
        ballR * 0.09f,
        ballR * 0.55f,
        ballR * 0.45f,
    )
    val footY = game.toCanvasY(table.footSpotY)
    drawCircle(Color.White.copy(alpha = 0.32f), ballR * 0.20f, Offset(cx, footY))
    drawCircle(Color.White.copy(alpha = 0.18f), ballR * 0.10f, Offset(cx, cy))

    // 袋口（盖在木框/台呢交界上）：黑洞按口线裁切成真实切口，只保留袋外半平面；
    // 木框收边/高光/内阴影/网线在同一裁剪内，口线处不再出现横跨洞口的描边。
    for (pocket in table.pockets) {
        val px = game.toCanvasX(pocket.x)
        val py = game.toCanvasY(pocket.y)
        // 黑洞填充半径覆盖整个口宽（角袋 1.15×捕获半径 ≥ 鼻点距 3.2 ballR），木缘沿用原视觉基准半径
        val fillR = max(pocket.radius * scale * 1.15f, ballR * 1.9f)
        val rimR = max(pocket.radius * scale * 1.05f, ballR * 1.9f)
        val mouth = pocketMouth(pocket, table)
        val clip =
            pocketClipPath(
                game.toCanvasX(mouth.noseAX),
                game.toCanvasY(mouth.noseAY),
                game.toCanvasX(mouth.noseBX),
                game.toCanvasY(mouth.noseBY),
                mouth.outwardX,
                mouth.outwardY,
                fillR * 3f,
            )
        clipPath(clip) {
            drawPocket(px, py, fillR, rimR, ballR, atan2(mouth.outwardY, mouth.outwardX))
        }
    }

    // 库边 facing（钳口）：把 12 段短斜边用库边色画在洞口上，连通「库边断开→斜口→洞」
    for (rail in table.rails) {
        if (!isPocketJaw(rail, table)) continue
        drawPocketJaw(
            game.toCanvasX(rail.x1),
            game.toCanvasY(rail.y1),
            game.toCanvasX(rail.x2),
            game.toCanvasY(rail.y2),
            ballR,
        )
    }

    // 库边菱形标记：上下短库各 3 个；左右长库每段 3 个（中袋 k==4 处无标记，与真实台一致）
    val dm = ballR * 0.40f
    for (k in 1..3) {
        val fx = l + (r - l) * k / 4f
        drawDiamond(fx, t - frame * 0.5f, dm)
        drawDiamond(fx, b + frame * 0.5f, dm)
    }
    for (k in 1..7) {
        if (k == 4) continue
        val fy = t + (b - t) * k / 8f
        drawDiamond(l - frame * 0.5f, fy, dm)
        drawDiamond(r + frame * 0.5f, fy, dm)
    }
}

/** 台呢主题的基础色（0..3 有效；越界回退默认湖蓝）。纯函数，便于单测四档差异。 */
internal fun clothThemeFeltColor(
    theme: Int,
    isDay: Boolean,
): Color =
    if (theme in clothDayThemes.indices) {
        Color(if (isDay) clothDayThemes[theme] else clothNightThemes[theme])
    } else if (isDay) {
        feltDayBase
    } else {
        feltNightBase
    }

/** 非晴/多云场景的冷色叠加比例（internal 供单测核对，避免"洗灰"式过度叠加）。 */
internal const val FELT_COLD_BLEND = 0.16f

/**
 * 台呢基色 = 主题色（或默认色）叠加场景冷色：非晴/多云场景把台面拉向冷色，
 * 让天气氛围进入台面；晴/多云保持主题原色。纯函数，便于单测天气下主题仍可区分。
 */
internal fun feltBase(
    style: WeatherSceneStyle,
    isDay: Boolean,
    theme: Int,
): Color {
    val base = clothThemeFeltColor(theme, isDay)
    val cold =
        when (style.scene) {
            WeatherScene.RAIN,
            WeatherScene.HEAVY_RAIN,
            WeatherScene.DRIZZLE,
            WeatherScene.THUNDER,
            WeatherScene.HAIL,
            WeatherScene.FREEZING_RAIN,
            WeatherScene.SNOW,
            WeatherScene.HEAVY_SNOW,
            WeatherScene.FOG,
            WeatherScene.OVERCAST,
            WeatherScene.UNKNOWN,
            -> FELT_COLD_BLEND
            else -> 0f
        }
    return if (cold > 0f) lerp(base, feltColdTint, cold) else base
}

/** 织纹短线长度（px）：以球半径为尺度（约 2~8px @ 1080p），不随 viewScale 放大。 */
internal fun clothWeaveLength(
    ballR: Float,
    rnd: Float,
): Float = ballR * (0.08f + rnd * 0.21f)

/** 织纹线宽（px）：以球半径为尺度，不随 viewScale 放大。 */
internal fun clothWeaveWidth(ballR: Float): Float = ballR * 0.04f

private fun DrawScope.drawMiter(
    ox: Float,
    oy: Float,
    ix: Float,
    iy: Float,
    dark: Color,
    hi: Color,
    ballR: Float,
) {
    drawLine(dark, Offset(ox, oy), Offset(ix, iy), strokeWidth = ballR * 0.12f)
    // 靠近外角的一小段高光，模拟斜接面受光
    val dx = ix - ox
    val dy = iy - oy
    drawLine(
        hi,
        Offset(ox + dx * 0.12f, oy + dy * 0.12f),
        Offset(ox + dx * 0.42f, oy + dy * 0.42f),
        strokeWidth = ballR * 0.06f,
    )
}

/**
 * 袋口「口外半平面」裁剪多边形（canvas 坐标）：以两鼻点连线为界，向袋外方向
 * 与口线两端各外扩 [reach]，保证袋口圆/木缘外弧/网线只在袋外一侧可见。
 */
private fun pocketClipPath(
    noseAX: Float,
    noseAY: Float,
    noseBX: Float,
    noseBY: Float,
    outX: Float,
    outY: Float,
    reach: Float,
): Path {
    val dx = noseBX - noseAX
    val dy = noseBY - noseAY
    val len = hypot(dx, dy).coerceAtLeast(1e-6f)
    val ux = dx / len
    val uy = dy / len
    val path = PoolRenderCache.pocketClip
    path.reset()
    val aX = noseAX - ux * reach
    val aY = noseAY - uy * reach
    val bX = noseBX + ux * reach
    val bY = noseBY + uy * reach
    path.moveTo(aX, aY)
    path.lineTo(bX, bY)
    path.lineTo(bX + outX * reach, bY + outY * reach)
    path.lineTo(aX + outX * reach, aY + outY * reach)
    path.close()
    return path
}

/**
 * 单个袋口：木框收边 + 黑洞 + 网线 + 内阴影 + 内沿受光。
 * 调用方按 [PocketMouth] 做口外半平面裁剪，因此所有圆形元素在口线处被切断，
 * 不会再出现「贴一枚圆币」的整圈轮廓。
 *
 * [holeR] 为黑洞填充半径（完全覆盖口宽），[rimR] 为木缘视觉基准半径（沿用原比例），
 * [outAngle] 为袋外方向角（弧度），让黑洞最深点与内阴影偏向袋外一侧。
 */
private fun DrawScope.drawPocket(
    px: Float,
    py: Float,
    holeR: Float,
    rimR: Float,
    ballR: Float,
    outAngle: Float,
) {
    // 木框收边：只留口外一侧的外弧（口线处被 clip 切断）
    drawCircle(honeyWoodDark.copy(alpha = 0.95f), rimR * 1.26f, Offset(px, py))
    drawCircle(pocketRim.copy(alpha = 0.85f), rimR * 1.26f, Offset(px, py), style = Stroke(width = ballR * 0.14f))
    drawCircle(woodEdge.copy(alpha = 0.6f), rimR * 1.36f, Offset(px, py), style = Stroke(width = ballR * 0.10f))
    // 洞口外阴影：让袋口整体下沉
    drawCircle(
        Brush.radialGradient(
            colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.28f)),
            center = Offset(px, py),
            radius = rimR * 1.30f,
        ),
        radius = rimR * 1.30f,
        center = Offset(px, py),
    )
    // 洞：径向渐变最深点沿袋外方向偏移，边到纯黑
    drawCircle(
        Brush.radialGradient(
            colors = listOf(pocketInk, pocketDeep, Color.Black),
            center = Offset(px + cos(outAngle) * holeR * 0.14f, py + sin(outAngle) * holeR * 0.14f),
            radius = holeR * 1.20f,
        ),
        radius = holeR,
        center = Offset(px, py),
    )
    // 袋网：从内壁向心的细网线 + 网底阴影（随黑洞一起被口线裁剪）
    for (i in 0 until 10) {
        val a = i * (2f * PI.toFloat() / 10f)
        val rx = cos(a)
        val ry = sin(a)
        drawLine(
            pocketNetLine,
            Offset(px + rx * holeR * 0.92f, py + ry * holeR * 0.92f),
            Offset(px + rx * holeR * 0.28f, py + ry * holeR * 0.28f + holeR * 0.12f),
            strokeWidth = ballR * 0.05f,
        )
    }
    drawCircle(Color.Black.copy(alpha = 0.50f), holeR * 0.32f, Offset(px, py + holeR * 0.10f))
    val outDeg = outAngle * 180f / PI.toFloat()
    // 内阴影：袋外一侧的外弧下半圈更暗，强调洞壁下沉
    drawArc(
        color = Color.Black.copy(alpha = 0.55f),
        startAngle = outDeg - 70f,
        sweepAngle = 140f,
        useCenter = false,
        topLeft = Offset(px - holeR, py - holeR),
        size = Size(holeR * 2f, holeR * 2f),
        style = Stroke(width = ballR * 0.22f),
    )
    // 洞口内沿受光：袋外一侧内壁极淡的一圈反光，增强下沉感
    drawArc(
        color = Color.White.copy(alpha = 0.07f),
        startAngle = outDeg - 55f,
        sweepAngle = 110f,
        useCenter = false,
        topLeft = Offset(px - holeR * 0.94f, py - holeR * 0.94f),
        size = Size(holeR * 1.88f, holeR * 1.88f),
        style = Stroke(width = ballR * 0.10f),
    )
    // 口线内侧的布料/皮革反光：让「切口」边缘更清晰
    drawArc(
        color = Color.White.copy(alpha = 0.10f),
        startAngle = outDeg + 110f,
        sweepAngle = 140f,
        useCenter = false,
        topLeft = Offset(px - holeR, py - holeR),
        size = Size(holeR * 2f, holeR * 2f),
        style = Stroke(width = ballR * 0.12f),
    )
}

/**
 * 钳口 facing：与库边同色的粗圆头短线，从库端鼻点沿钳口线段向袋外延伸，
 * 把「库边断开」与黑洞之间的斜面补上，连成一体。
 */
private fun DrawScope.drawPocketJaw(
    ax: Float,
    ay: Float,
    bx: Float,
    by: Float,
    ballR: Float,
) {
    drawLine(
        cushionShade.copy(alpha = 0.92f),
        Offset(ax, ay),
        Offset(bx, by),
        strokeWidth = ballR * 0.62f,
        cap = StrokeCap.Round,
    )
    // 库端鼻点处的细微高光，与库鼻高光一致
    drawLine(
        Color.White.copy(alpha = 0.09f),
        Offset(ax, ay),
        Offset(bx, by),
        strokeWidth = ballR * 0.18f,
        cap = StrokeCap.Round,
    )
}

private fun DrawScope.drawDiamond(
    cx: Float,
    cy: Float,
    s: Float,
) {
    drawCircle(Color.Black.copy(alpha = 0.18f), s * 1.35f, Offset(cx, cy + s * 0.16f))
    rotate(degrees = 45f, pivot = Offset(cx, cy)) {
        drawRect(diamondIvory, Offset(cx - s, cy - s), Size(s * 2f, s * 2f))
    }
    rotate(degrees = 45f, pivot = Offset(cx, cy)) {
        drawRect(Color.Black.copy(alpha = 0.22f), Offset(cx - s, cy - s), Size(s * 2f, s * 2f), style = Stroke(s * 0.30f))
    }
}

/** 确定性伪随机 [0,1]，用于织纹，保证同一 index 每帧结果一致。 */
private fun hash01(n: Int): Float {
    var h = n * 374761393 + 668265263
    h = (h xor (h shr 13)) * 1274126177
    h = h xor (h shr 16)
    return (h and 0x7FFFFFFF) / 2147483647f
}

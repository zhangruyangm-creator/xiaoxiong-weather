package com.xiaoxiong.weather.ui.game

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import com.xiaoxiong.weather.ui.weather.WeatherSceneStyle
import kotlin.math.PI
import kotlin.math.atan2

// ---------------------------------------------------------------------
// 球与落袋动画
// ---------------------------------------------------------------------

internal fun DrawScope.drawPoolBalls(
    game: PoolGame,
    style: WeatherSceneStyle,
    isDay: Boolean,
) {
    if (game.balls.isEmpty()) return
    val scale = game.viewScale
    if (scale <= 0f) return
    val ballR = game.table.ballRadius * scale
    if (ballR < 0.5f) return
    // 夜色下球体略压暗；用场景内容色做一点环境反光
    val dim = if (isDay) 1f else 0.90f
    val rimLight = lerp(Color.White, style.contentColor, 0.30f)

    for (ball in game.balls) {
        drawOneBall(game, ball, ballR, dim, rimLight)
    }
}

private fun DrawScope.drawOneBall(
    game: PoolGame,
    ball: PoolBall,
    ballR: Float,
    dim: Float,
    rimLight: Color,
) {
    if (!ball.onTable && !ball.potted) return
    var cx = game.toCanvasX(ball.x)
    var cy = game.toCanvasY(ball.y)
    var r = ballR
    var alpha = 1f

    if (ball.potted) {
        val t = ball.pocketAnim.coerceIn(0f, 1f)
        if (t >= 1f) return
        val pocket = game.table.pockets.getOrNull(ball.pocketIndex)
        if (pocket != null) {
            val k = t * t * (3f - 2f * t)
            cx += (game.toCanvasX(pocket.x) - cx) * k
            cy += (game.toCanvasY(pocket.y) - cy) * k
        }
        alpha = 1f - t
        r = ballR * (1f - 0.78f * t)
    }
    if (alpha <= 0.02f || r <= 0.2f) return

    val base = ballBaseColor(ball)
    // 跳球：按离台高度抬高球体，阴影留在台面并随高度变小变淡
    val groundCy = cy
    val heightFrac = (ball.z / (ball.radius * 4f)).coerceIn(0f, 1f)
    val lift = if (ball.airborne) ball.z * game.viewScale else 0f
    if (lift > 0f) cy -= lift

    // 运动拖影（减弱动画/腾空时关闭）
    if (ball.onTable && !ball.airborne && !game.reduceMotion) {
        val speed = ball.speed
        if (speed > 1.4f) {
            val k = (speed / PoolConfig.MAX_SPEED).coerceIn(0f, 0.6f)
            val ux = ball.vx / speed
            val uy = ball.vy / speed
            drawCircle(
                Color.White.copy(alpha = 0.10f * k * alpha),
                r * 0.92f,
                Offset(cx - ux * r * 0.9f, cy - uy * r * 0.9f),
            )
            drawCircle(
                Color.White.copy(alpha = 0.06f * k * alpha),
                r * 0.80f,
                Offset(cx - ux * r * 1.8f, cy - uy * r * 1.8f),
            )
        }
    }

    drawBallShadow(cx, groundCy, r * (1f - 0.28f * heightFrac), alpha * (1f - 0.4f * heightFrac))
    drawSphereBase(cx, cy, r, base, alpha * dim)
    if (ball.kind == PoolBallKind.STRIPE) {
        drawStripeBand(ball, cx, cy, r, alpha)
    }
    drawSphereGloss(cx, cy, r, alpha, rimLight)
    // 8 号/白球加一圈淡淡描边，提升低对比场景与色觉障碍下的可辨识度
    when (ball.kind) {
        PoolBallKind.EIGHT ->
            drawCircle(
                Color.White.copy(alpha = 0.30f * alpha),
                r * 0.985f,
                Offset(cx, cy),
                style =
                    Stroke(
                        width =
                            r * 0.06f,
                    ),
            )
        PoolBallKind.CUE ->
            drawCircle(
                Color(0xFF37474F).copy(alpha = 0.35f * alpha),
                r * 0.985f,
                Offset(cx, cy),
                style =
                    Stroke(
                        width =
                            r * 0.05f,
                    ),
            )
        else -> Unit
    }
    if (ball.kind != PoolBallKind.CUE) {
        drawBallNumber(ball, cx, cy, r, alpha)
    } else {
        // 白球小红点，方便辨认
        drawCircle(Color(0xFFE53935).copy(alpha = 0.9f * alpha), r * 0.13f, Offset(cx + r * 0.30f, cy + r * 0.26f))
    }
}

private fun ballBaseColor(ball: PoolBall): Color =
    when (ball.kind) {
        PoolBallKind.CUE -> cueWhite
        PoolBallKind.EIGHT -> eightBlack
        PoolBallKind.SOLID -> numberHue(ball.number)
        PoolBallKind.STRIPE -> stripeWhite
    }

private fun numberHue(number: Int): Color {
    val i = ((number - 1) % 8).coerceIn(0, ballHues.size - 1)
    return Color(ballHues[i])
}

private fun DrawScope.drawBallShadow(
    cx: Float,
    cy: Float,
    r: Float,
    alpha: Float,
) {
    val ox = r * 0.24f
    val oy = r * 0.30f
    drawOval(
        Color.Black.copy(alpha = 0.09f * alpha),
        topLeft = Offset(cx - r * 1.06f + ox, cy - r * 0.78f + oy),
        size = Size(r * 2.12f, r * 1.56f),
    )
    drawOval(
        Color.Black.copy(alpha = 0.13f * alpha),
        topLeft = Offset(cx - r * 0.86f + ox, cy - r * 0.66f + oy),
        size = Size(r * 1.72f, r * 1.30f),
    )
    drawOval(
        Color.Black.copy(alpha = 0.16f * alpha),
        topLeft = Offset(cx - r * 0.66f + ox, cy - r * 0.54f + oy),
        size = Size(r * 1.32f, r * 1.06f),
    )
}

private fun DrawScope.drawSphereBase(
    cx: Float,
    cy: Float,
    r: Float,
    base: Color,
    alpha: Float,
) {
    val key = brushKey(r, base.toArgb())
    val brush =
        sphereBaseBrushes[key]
            ?: Brush
                .radialGradient(
                    colors = listOf(lighten(base, 0.42f), base, darken(base, 0.55f)),
                    center = Offset(-r * 0.34f, -r * 0.36f),
                    radius = r * 1.52f,
                ).also { sphereBaseBrushes[key] = it }
    translate(cx, cy) {
        drawCircle(brush, radius = r, center = Offset.Zero, alpha = alpha)
    }
}

private fun DrawScope.drawSphereGloss(
    cx: Float,
    cy: Float,
    r: Float,
    alpha: Float,
    rimLight: Color,
) {
    val rimKey = brushKey(r, rimLight.toArgb())
    val shadeKey = radiusKey(r)
    val shade =
        sphereShadeBrushes[shadeKey]
            ?: Brush
                .radialGradient(
                    colors = listOf(Color.Transparent, Color.Transparent, Color.Black.copy(alpha = 0.34f)),
                    center = Offset(-r * 0.30f, -r * 0.32f),
                    radius = r * 1.45f,
                ).also { sphereShadeBrushes[shadeKey] = it }
    val fresnel =
        sphereRimBrushes[rimKey]
            ?: Brush
                .radialGradient(
                    0.60f to Color.Transparent,
                    0.84f to rimLight.copy(alpha = 0.10f),
                    1.00f to rimLight.copy(alpha = 0.30f),
                    center = Offset.Zero,
                    radius = r,
                ).also { sphereRimBrushes[rimKey] = it }
    val ambient =
        sphereAmbientBrushes[rimKey]
            ?: Brush
                .radialGradient(
                    colors = listOf(rimLight.copy(alpha = 0.22f), Color.Transparent),
                    center = Offset(r * 0.26f, r * 0.34f),
                    radius = r * 0.95f,
                ).also { sphereAmbientBrushes[rimKey] = it }
    val highlight =
        sphereHighlightBrushes[shadeKey]
            ?: Brush
                .radialGradient(
                    colors = listOf(Color.White.copy(alpha = 0.55f), Color.Transparent),
                    center = Offset(-r * 0.34f, -r * 0.37f),
                    radius = r * 0.44f,
                ).also { sphereHighlightBrushes[shadeKey] = it }
    val highlightCenter = Offset(-r * 0.34f, -r * 0.37f)

    translate(cx, cy) {
        // 边缘暗化 + 环境反光
        drawCircle(shade, radius = r, center = Offset.Zero, alpha = alpha)
        // 菲涅尔边缘环境光：贴边一圈柔和反光，让球体轮廓更立体
        drawCircle(fresnel, radius = r, center = Offset.Zero, alpha = alpha)
        drawCircle(ambient, radius = r, center = Offset.Zero, alpha = alpha)
        // 主高光：柔和光斑衰减 + 高亮小核
        drawCircle(highlight, radius = r * 0.44f, center = highlightCenter, alpha = alpha)
        drawCircle(Color.White.copy(alpha = 0.82f * alpha), r * 0.15f, highlightCenter)
        drawCircle(Color.White.copy(alpha = 0.28f * alpha), r * 0.075f, Offset(-r * 0.12f, -r * 0.20f))
    }
}

private fun DrawScope.drawStripeBand(
    ball: PoolBall,
    cx: Float,
    cy: Float,
    r: Float,
    alpha: Float,
) {
    val n = ball.markingNormal()
    val angleDeg = atan2(n[1], n[0]) * 180f / PI.toFloat()
    val half = r * 0.44f
    val path = PoolRenderCache.ballClip
    withTransform({
        translate(cx, cy)
        rotate(degrees = angleDeg, pivot = Offset.Zero)
    }) {
        path.reset()
        path.addOval(Rect(-r, -r, r, r))
        clipPath(path) {
            drawRect(
                numberHue(ball.number),
                topLeft = Offset(-r, -half),
                size = Size(r * 2f, half * 2f),
                alpha = 0.97f * alpha,
            )
            drawRect(
                Color.Black.copy(alpha = 0.12f * alpha),
                topLeft = Offset(-r, -half),
                size = Size(r * 2f, r * 0.07f),
            )
            drawRect(
                Color.Black.copy(alpha = 0.12f * alpha),
                topLeft = Offset(-r, half - r * 0.07f),
                size = Size(r * 2f, r * 0.07f),
            )
        }
    }
}

private fun DrawScope.drawBallNumber(
    ball: PoolBall,
    cx: Float,
    cy: Float,
    r: Float,
    alpha: Float,
) {
    val n = ball.markingNormal()
    val nz = n[2]
    if (nz <= 0.12f) return
    val nx = n[0]
    val ny = n[1]
    val deg = atan2(ny, nx) * 180f / PI.toFloat()
    val visibility = ((nz - 0.12f) / 0.5f).coerceIn(0f, 1f)
    val spot = r * 0.40f
    val px = cx + r * nx * 0.92f
    val py = cy + r * ny * 0.92f
    val visualAlpha = alpha * visibility

    withTransform({
        translate(px, py)
        rotate(degrees = deg, pivot = Offset.Zero)
    }) {
        drawOval(
            Color(0xFFF6F1E4).copy(alpha = 0.96f * visualAlpha),
            topLeft = Offset(-spot * nz, -spot),
            size = Size(spot * 2f * nz, spot * 2f),
        )
    }

    val paint = PoolRenderCache.numberPaint
    paint.textSize = r * 0.80f
    paint.alpha = (255f * visualAlpha).toInt().coerceIn(0, 255)
    val canvas = drawContext.canvas.nativeCanvas
    canvas.save()
    canvas.translate(px, py)
    canvas.rotate(deg)
    canvas.drawText(ball.number.toString(), 0f, paint.textSize * 0.34f, paint)
    canvas.restore()
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

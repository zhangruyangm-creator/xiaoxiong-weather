package com.xiaoxiong.weather.ui.game

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp

private val BEAR_FUR = Color(0xFFE8C9A0)
private val BEAR_DARK = Color(0xFF5C4033)
private val BEAR_MUZZLE = Color(0xFFF5E6D3)
private val DOUBLE_ACCENT = Color(0xFFFFB74D)
private val DOUBLE_ACCENT_DEEP = Color(0xFFE65100)

/** 黑块上的小熊脸；[primed] 为双连点第一击后更明显。 */
internal fun DrawScope.drawRainTileBearFace(
    centerX: Float,
    centerY: Float,
    cellMin: Float,
    alpha: Float,
    primed: Boolean = false,
) {
    if (alpha <= 0f) return
    val s = cellMin * if (primed) 0.38f else 0.32f
    val a = alpha.coerceIn(0f, 1f)
    val fur = BEAR_FUR.copy(alpha = a * if (primed) 0.95f else 0.72f)
    val dark = BEAR_DARK.copy(alpha = a)
    drawCircle(fur, s * 0.42f, Offset(centerX, centerY))
    drawCircle(dark, s * 0.17f, Offset(centerX - s * 0.38f, centerY - s * 0.34f))
    drawCircle(dark, s * 0.17f, Offset(centerX + s * 0.38f, centerY - s * 0.34f))
    drawCircle(BEAR_FUR.copy(alpha = a * 0.85f), s * 0.12f, Offset(centerX - s * 0.38f, centerY - s * 0.34f))
    drawCircle(BEAR_FUR.copy(alpha = a * 0.85f), s * 0.12f, Offset(centerX + s * 0.38f, centerY - s * 0.34f))
    drawCircle(dark, s * 0.07f, Offset(centerX - s * 0.14f, centerY - s * 0.06f))
    drawCircle(dark, s * 0.07f, Offset(centerX + s * 0.14f, centerY - s * 0.06f))
    drawOval(
        BEAR_MUZZLE.copy(alpha = a * 0.85f),
        topLeft = Offset(centerX - s * 0.14f, centerY + s * 0.08f),
        size = Size(s * 0.28f, s * 0.18f),
    )
    drawCircle(dark, s * 0.06f, Offset(centerX, centerY + s * 0.12f))
}

/** 双连点块未激活：橙框 + 露耳 + 中央「双点」标记，与单击块/小熊块区分。 */
internal fun DrawScope.drawRainTileDoubleHint(
    centerX: Float,
    centerY: Float,
    cellMin: Float,
) {
    val s = cellMin * 0.34f
    val ear = DOUBLE_ACCENT.copy(alpha = 0.92f)
    drawCircle(ear, s * 0.18f, Offset(centerX - s * 0.62f, centerY - s * 0.38f))
    drawCircle(ear, s * 0.18f, Offset(centerX + s * 0.62f, centerY - s * 0.38f))
    drawCircle(BEAR_DARK.copy(alpha = 0.55f), s * 0.07f, Offset(centerX - s * 0.62f, centerY - s * 0.38f))
    drawCircle(BEAR_DARK.copy(alpha = 0.55f), s * 0.07f, Offset(centerX + s * 0.62f, centerY - s * 0.38f))
    val dotR = s * 0.11f
    val gap = s * 0.14f
    drawCircle(DOUBLE_ACCENT_DEEP, dotR, Offset(centerX, centerY - gap))
    drawCircle(DOUBLE_ACCENT_DEEP, dotR, Offset(centerX, centerY + gap))
}

/** 双连点黑块外圈描边（虚线 + 实线双层）。 */
internal fun DrawScope.drawRainTileDoubleFrame(
    left: Float,
    top: Float,
    cellW: Float,
    cellH: Float,
) {
    val inset = 2.5f
    val w = cellW - inset * 2f
    val h = cellH - inset * 2f
    if (w <= 0f || h <= 0f) return
    val tl = Offset(left + inset, top + inset)
    val stroke = 2.5f.dp.toPx()
    drawRoundRect(
        color = DOUBLE_ACCENT.copy(alpha = 0.55f),
        topLeft = tl,
        size = Size(w, h),
        cornerRadius = CornerRadius(6f, 6f),
        style = Stroke(width = stroke, pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 7f), 0f)),
    )
    drawRoundRect(
        color = DOUBLE_ACCENT_DEEP.copy(alpha = 0.35f),
        topLeft = tl,
        size = Size(w, h),
        cornerRadius = CornerRadius(6f, 6f),
        style = Stroke(width = stroke * 0.6f),
    )
}

/** 点中反馈：短暂星屑。 */
internal fun DrawScope.drawRainHitSparkles(
    centerX: Float,
    centerY: Float,
    cellMin: Float,
    phase01: Float,
) {
    if (phase01 <= 0f || phase01 >= 1f) return
    val fade = 1f - phase01
    val r = cellMin * 0.08f
    repeat(6) { i ->
        val ang = i * 60f + phase01 * 120f
        val rad = Math.toRadians(ang.toDouble())
        val dist = cellMin * (0.15f + 0.25f * phase01)
        val x = centerX + (dist * kotlin.math.cos(rad)).toFloat()
        val y = centerY + (dist * kotlin.math.sin(rad)).toFloat()
        drawCircle(BEAR_FUR.copy(alpha = 0.85f * fade), r, Offset(x, y))
    }
}

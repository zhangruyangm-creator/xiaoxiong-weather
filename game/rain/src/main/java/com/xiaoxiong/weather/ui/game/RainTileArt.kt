package com.xiaoxiong.weather.ui.game

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope

private val BEAR_FUR = Color(0xFFE8C9A0)
private val BEAR_DARK = Color(0xFF5C4033)
private val BEAR_MUZZLE = Color(0xFFF5E6D3)

/** 黑块上的小熊脸水印；[primed] 为双连点第一击后更明显。 */
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
    val fur = BEAR_FUR.copy(alpha = a * if (primed) 0.95f else 0.55f)
    val dark = BEAR_DARK.copy(alpha = a)
    drawCircle(fur, s * 0.42f, Offset(centerX, centerY))
    drawCircle(dark, s * 0.17f, Offset(centerX - s * 0.38f, centerY - s * 0.34f))
    drawCircle(dark, s * 0.17f, Offset(centerX + s * 0.38f, centerY - s * 0.34f))
    drawCircle(BEAR_FUR.copy(alpha = a * 0.7f), s * 0.12f, Offset(centerX - s * 0.38f, centerY - s * 0.34f))
    drawCircle(BEAR_FUR.copy(alpha = a * 0.7f), s * 0.12f, Offset(centerX + s * 0.38f, centerY - s * 0.34f))
    drawCircle(dark, s * 0.07f, Offset(centerX - s * 0.14f, centerY - s * 0.06f))
    drawCircle(dark, s * 0.07f, Offset(centerX + s * 0.14f, centerY - s * 0.06f))
    drawOval(
        BEAR_MUZZLE.copy(alpha = a * 0.85f),
        topLeft = Offset(centerX - s * 0.14f, centerY + s * 0.08f),
        size = Size(s * 0.28f, s * 0.18f),
    )
    drawCircle(dark, s * 0.06f, Offset(centerX, centerY + s * 0.12f))
}

/** 双连点块未激活时：仅露双耳提示。 */
internal fun DrawScope.drawRainTileDoubleHint(
    centerX: Float,
    centerY: Float,
    cellMin: Float,
) {
    val s = cellMin * 0.28f
    val c = BEAR_FUR.copy(alpha = 0.45f)
    drawCircle(c, s * 0.14f, Offset(centerX - s * 0.55f, centerY - s * 0.35f))
    drawCircle(c, s * 0.14f, Offset(centerX + s * 0.55f, centerY - s * 0.35f))
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

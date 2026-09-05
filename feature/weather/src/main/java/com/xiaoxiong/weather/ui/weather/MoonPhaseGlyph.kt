package com.xiaoxiong.weather.ui.weather

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos

/**
 * 北半球视角的小月相圆盘：随 [cycleFraction]（0 新月 → 0.5 满月 → 1 新月）变化。
 */
@Composable
internal fun MoonPhaseGlyph(
    cycleFraction: Float,
    modifier: Modifier = Modifier,
    size: Dp = 18.dp,
    litColor: Color = Color(0xFFFFF1C2),
    darkColor: Color = Color(0xFF2C3144),
) {
    Canvas(modifier = modifier.size(size)) {
        drawMoonPhaseDisk(
            cycleFraction = cycleFraction,
            lit = litColor,
            dark = darkColor,
        )
    }
}

internal fun DrawScope.drawMoonPhaseDisk(
    cycleFraction: Float,
    lit: Color,
    dark: Color,
) {
    val r = size.minDimension / 2f * 0.92f
    val c = Offset(this.size.width / 2f, this.size.height / 2f)
    var p = cycleFraction % 1f
    if (p < 0f) p += 1f

    drawCircle(dark, r, c)

    val cosPhase = cos((p * 2.0 * PI)).toFloat()
    val waxing = p < 0.5f

    if (cosPhase <= -0.985f) {
        drawCircle(lit, r, c)
        return
    }
    if (cosPhase >= 0.985f) {
        drawCircle(lit.copy(alpha = 0.28f), r, c, style = Stroke(width = r * 0.1f))
        return
    }

    val path = Path()
    val disc = Rect(c.x - r, c.y - r, c.x + r, c.y + r)
    val termRx = abs(cosPhase) * r
    val term = Rect(c.x - termRx, c.y - r, c.x + termRx, c.y + r)

    if (waxing) {
        path.moveTo(c.x, c.y - r)
        path.arcTo(disc, -90f, 180f, forceMoveTo = false)
        if (cosPhase > 0f) {
            path.arcTo(term, 90f, -180f, forceMoveTo = false)
        } else {
            path.arcTo(term, 90f, 180f, forceMoveTo = false)
        }
    } else {
        path.moveTo(c.x, c.y + r)
        path.arcTo(disc, 90f, 180f, forceMoveTo = false)
        if (cosPhase > 0f) {
            path.arcTo(term, -90f, -180f, forceMoveTo = false)
        } else {
            path.arcTo(term, -90f, 180f, forceMoveTo = false)
        }
    }
    path.close()
    drawPath(path, lit)
}

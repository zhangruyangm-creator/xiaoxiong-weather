package com.xiaoxiong.weather.ui.game

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.lerp

/** 天空中间过渡色，让两色渐变多一个柔和过渡段。 */
fun midColor(
    a: Color,
    b: Color,
): Color = lerp(a, b, 0.5f)

/** 天空三色渐变：顶、中、底。 */
fun skyGradient(
    top: Color,
    bottom: Color,
): List<Color> = listOf(top, midColor(top, bottom), bottom)

/** 地平线柔光带：以 groundY 为界向上渐隐，模拟太阳/光线在地平线的扩散。 */
fun DrawScope.drawHorizonGlow(
    groundY: Float,
    color: Color,
    bandH: Float,
) {
    val h = bandH
    drawRect(
        Brush.verticalGradient(
            listOf(Color.Transparent, color.copy(alpha = 0.30f), Color.Transparent),
            startY = groundY - h,
            endY = groundY,
        ),
        topLeft = Offset(0f, groundY - h),
        size = Size(size.width, h),
    )
}

/** 任意位置的柔和径向光晕（太阳/云海/雾）。 */
fun DrawScope.drawSoftGlow(
    cx: Float,
    cy: Float,
    radius: Float,
    color: Color,
) {
    drawCircle(
        Brush.radialGradient(
            listOf(color.copy(alpha = 0.45f), color.copy(alpha = 0f)),
            center = Offset(cx, cy),
            radius = radius,
        ),
        radius = radius,
        center = Offset(cx, cy),
    )
}

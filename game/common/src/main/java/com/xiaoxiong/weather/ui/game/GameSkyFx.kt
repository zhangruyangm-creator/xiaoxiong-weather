package com.xiaoxiong.weather.ui.game

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.toArgb

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

/**
 * 地平线光晕笔刷缓存：groundY/bandH/颜色不变时复用同一 Brush 实例，
 * 避免热路径每帧 new Brush + list。
 */
private class HorizonGlowBrushCache {
    private var groundY = Float.NaN
    private var bandH = Float.NaN
    private var baseArgb = 0
    private var brush: Brush? = null

    fun get(
        groundY: Float,
        bandH: Float,
        color: Color,
    ): Brush {
        val argb = color.toArgb()
        val cached = brush
        if (cached != null && this.groundY == groundY && this.bandH == bandH && baseArgb == argb) {
            return cached
        }
        return Brush
            .verticalGradient(
                listOf(Color.Transparent, color.copy(alpha = 0.30f), Color.Transparent),
                startY = groundY - bandH,
                endY = groundY,
            ).also {
                brush = it
                this.groundY = groundY
                this.bandH = bandH
                baseArgb = argb
            }
    }
}

private val horizonGlowBrushCache = HorizonGlowBrushCache()

/** 地平线柔光带：以 groundY 为界向上渐隐，模拟太阳/光线在地平线的扩散。 */
fun DrawScope.drawHorizonGlow(
    groundY: Float,
    color: Color,
    bandH: Float,
) {
    val h = bandH
    drawRect(
        horizonGlowBrushCache.get(groundY, h, color),
        topLeft = Offset(0f, groundY - h),
        size = Size(size.width, h),
    )
}

/**
 * 柔和径向光晕笔刷缓存：渐变以原点为中心缓存，绘制时再平移到目标坐标，
 * 这样移动中的光晕（掉落物描边等）也能复用同一实例。
 */
private class SoftGlowBrushCache {
    private var radius = Float.NaN
    private var baseArgb = 0
    private var brush: Brush? = null

    fun get(
        radius: Float,
        color: Color,
    ): Brush {
        val argb = color.toArgb()
        val cached = brush
        if (cached != null && this.radius == radius && baseArgb == argb) return cached
        return Brush
            .radialGradient(
                listOf(color.copy(alpha = 0.45f), color.copy(alpha = 0f)),
                center = Offset.Zero,
                radius = radius,
            ).also {
                brush = it
                this.radius = radius
                baseArgb = argb
            }
    }
}

private val softGlowBrushCache = SoftGlowBrushCache()

/** 任意位置的柔和径向光晕（太阳/云海/雾）。 */
fun DrawScope.drawSoftGlow(
    cx: Float,
    cy: Float,
    radius: Float,
    color: Color,
) {
    translate(cx, cy) {
        drawCircle(
            softGlowBrushCache.get(radius, color),
            radius = radius,
            center = Offset.Zero,
        )
    }
}

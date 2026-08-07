package com.xiaoxiong.weather.ui.weather

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import com.xiaoxiong.weather.util.WeatherCodes
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

// 与 WeatherAtmosphere 场景渐变同色系；渐变填充保证白卡片和深色氛围上都有层次
private val SunCenter = Color(0xFFFFE082)
private val SunEdge = Color(0xFFFFB300)
private val MoonCenter = Color(0xFFFFF8E1)
private val MoonEdge = Color(0xFFFFD54F)
private val CloudTop = Color(0xFFFFFFFF)
private val CloudMid = Color(0xFFECEFF1)
private val CloudBottom = Color(0xFFB9C6CF)
private val StormCloudTop = Color(0xFFADB9C4)
private val StormCloudBottom = Color(0xFF5F7A8A)
private val RainTop = Color(0xFF90CAF9)
private val RainBottom = Color(0xFF42A5F5)
private val SnowBlue = Color(0xFF90CAF9)
private val BoltTop = Color(0xFFFFE082)
private val BoltBottom = Color(0xFFFFB300)
private val FogGray = Color(0xFFA8B8C4)
private val ThermoTop = Color(0xFFFFAB91)
private val ThermoBottom = Color(0xFFF4511E)

/**
 * 自绘彩色天气图标，替代系统 emoji（跨设备渲染一致、与大气动画同色系）。
 * 所有图形按 size.minDimension 等比绘制，任意尺寸可用。
 */
@Composable
fun WeatherIcon(
    code: Int?,
    isDay: Boolean,
    modifier: Modifier = Modifier,
    precipMmPerHour: Double? = null
) {
    val scene = weatherSceneOf(code, isDay)
    Canvas(modifier = modifier) {
        val u = size.minDimension
        val cx = size.width / 2f
        val cy = size.height / 2f
        when (scene) {
            WeatherScene.CLEAR -> if (isDay) {
                drawSun(Offset(cx, cy), radius = 0.23f * u, rayInner = 0.32f * u, rayOuter = 0.43f * u, rayWidth = 0.05f * u)
            } else {
                drawMoon(Offset(cx - 0.05f * u, cy + 0.05f * u), r = 0.27f * u)
                drawStar(Offset(cx + 0.30f * u, cy - 0.28f * u), 0.07f * u)
                drawStar(Offset(cx - 0.36f * u, cy - 0.30f * u), 0.05f * u)
            }

            WeatherScene.PARTLY_CLOUDY -> {
                if (isDay) {
                    drawSun(Offset(cx - 0.19f * u, cy - 0.19f * u), radius = 0.155f * u, rayInner = 0.23f * u, rayOuter = 0.30f * u, rayWidth = 0.042f * u)
                } else {
                    drawMoon(Offset(cx - 0.20f * u, cy - 0.20f * u), r = 0.14f * u)
                }
                drawCloud(cx + 0.10f * u, cy + 0.10f * u, 0.72f * u)
            }

            WeatherScene.OVERCAST -> {
                drawCloud(cx - 0.08f * u, cy - 0.13f * u, 0.62f * u, storm = true)
                drawCloud(cx + 0.08f * u, cy + 0.06f * u, 0.78f * u)
            }

            // 纯雾线（无云）：三条横向渐隐线，左右缘错落，任何底色上都干净
            WeatherScene.FOG -> {
                val xs = listOf(-0.28f to 0.20f, -0.20f to 0.28f, -0.26f to 0.16f)
                val ys = listOf(-0.16f, 0f, 0.16f)
                val ws = listOf(0.05f, 0.06f, 0.05f)
                xs.forEachIndexed { i, (x0, x1) ->
                    val sx = cx + x0 * u
                    val ex = cx + x1 * u
                    val ly = cy + ys[i] * u
                    drawLine(
                        brush = Brush.horizontalGradient(
                            0f to FogGray.copy(alpha = 0f),
                            0.18f to FogGray,
                            0.82f to FogGray,
                            1f to FogGray.copy(alpha = 0f),
                            startX = sx,
                            endX = ex
                        ),
                        start = Offset(sx, ly),
                        end = Offset(ex, ly),
                        strokeWidth = ws[i] * u,
                        cap = StrokeCap.Round
                    )
                }
            }

            // 降水全部收在云宽度（±0.31u）之内，从云底（≈cy+0.06u）下方开始
            WeatherScene.DRIZZLE -> {
                drawCloud(cx, cy - 0.10f * u, 0.80f * u)
                drawDrops(
                    cx, cy + 0.13f * u, u,
                    listOf(-0.21f to 0.075f, -0.07f to 0.075f, 0.07f to 0.075f, 0.21f to 0.075f)
                )
            }

            // 雨按实测雨强分档：小雨 2 滴 / 中雨 3 滴 / 大雨 4 滴+风暴云 / 暴雨 5 滴+风暴云；
            // 无速率数据时按场景回落（RAIN→中雨，HEAVY_RAIN→大雨）
            WeatherScene.RAIN, WeatherScene.HEAVY_RAIN -> {
                val level = precipMmPerHour?.let { WeatherCodes.rainIntensity(it) }
                val storm = when (level) {
                    WeatherCodes.RainIntensity.HEAVY,
                    WeatherCodes.RainIntensity.TORRENTIAL,
                    WeatherCodes.RainIntensity.VIOLENT -> true
                    null -> scene == WeatherScene.HEAVY_RAIN
                    else -> false
                }
                val drops = when (level) {
                    WeatherCodes.RainIntensity.NONE,
                    WeatherCodes.RainIntensity.LIGHT ->
                        listOf(-0.10f to 0.12f, 0.10f to 0.12f)
                    WeatherCodes.RainIntensity.MODERATE ->
                        listOf(-0.16f to 0.15f, 0f to 0.15f, 0.16f to 0.15f)
                    WeatherCodes.RainIntensity.HEAVY ->
                        listOf(-0.22f to 0.16f, -0.07f to 0.12f, 0.07f to 0.16f, 0.22f to 0.12f)
                    WeatherCodes.RainIntensity.TORRENTIAL,
                    WeatherCodes.RainIntensity.VIOLENT ->
                        listOf(-0.24f to 0.17f, -0.12f to 0.12f, 0f to 0.17f, 0.12f to 0.12f, 0.24f to 0.17f)
                    null -> if (scene == WeatherScene.HEAVY_RAIN) {
                        listOf(-0.22f to 0.16f, -0.07f to 0.12f, 0.07f to 0.16f, 0.22f to 0.12f)
                    } else {
                        listOf(-0.16f to 0.15f, 0f to 0.15f, 0.16f to 0.15f)
                    }
                }
                drawCloud(
                    cx,
                    cy - (if (storm) 0.12f else 0.10f) * u,
                    (if (storm) 0.82f else 0.80f) * u,
                    storm = storm
                )
                drawDrops(cx, cy + 0.13f * u, u, drops)
            }

            WeatherScene.FREEZING_RAIN -> {
                drawCloud(cx, cy - 0.10f * u, 0.80f * u)
                drawDrops(
                    cx - 0.09f * u, cy + 0.13f * u, u,
                    listOf(-0.09f to 0.13f, 0.07f to 0.13f)
                )
                drawSnowflake(Offset(cx + 0.17f * u, cy + 0.21f * u), 0.105f * u)
            }

            // 雪同套分档：小雪 2 小晶 / 中雪 2 晶 / 大雪 3 晶+风暴云 / 暴雪 3 大晶+风暴云
            WeatherScene.SNOW, WeatherScene.HEAVY_SNOW -> {
                val level = precipMmPerHour?.let { WeatherCodes.snowIntensity(code, it) }
                val storm = when (level) {
                    WeatherCodes.SnowIntensity.HEAVY,
                    WeatherCodes.SnowIntensity.BLIZZARD -> true
                    null -> scene == WeatherScene.HEAVY_SNOW
                    else -> false
                }
                drawCloud(cx, cy - 0.11f * u, 0.80f * u, storm = storm)
                when (level) {
                    WeatherCodes.SnowIntensity.NONE,
                    WeatherCodes.SnowIntensity.LIGHT -> {
                        drawSnowflake(Offset(cx - 0.10f * u, cy + 0.20f * u), 0.075f * u)
                        drawSnowflake(Offset(cx + 0.12f * u, cy + 0.24f * u), 0.06f * u)
                    }

                    WeatherCodes.SnowIntensity.HEAVY -> {
                        drawSnowflake(Offset(cx - 0.20f * u, cy + 0.18f * u), 0.08f * u)
                        drawSnowflake(Offset(cx, cy + 0.26f * u), 0.095f * u)
                        drawSnowflake(Offset(cx + 0.20f * u, cy + 0.18f * u), 0.08f * u)
                    }

                    // 暴雪：两排 5 片错落，靠密度与大雪区分（单靠放大在小尺寸下看不出）
                    WeatherCodes.SnowIntensity.BLIZZARD -> {
                        drawSnowflake(Offset(cx - 0.22f * u, cy + 0.15f * u), 0.075f * u)
                        drawSnowflake(Offset(cx, cy + 0.15f * u), 0.075f * u)
                        drawSnowflake(Offset(cx + 0.22f * u, cy + 0.15f * u), 0.075f * u)
                        drawSnowflake(Offset(cx - 0.11f * u, cy + 0.30f * u), 0.065f * u)
                        drawSnowflake(Offset(cx + 0.11f * u, cy + 0.30f * u), 0.065f * u)
                    }

                    else -> {
                        // MODERATE 或无数据：SNOW→中雪两片，HEAVY_SNOW→大雪三片
                        if (level == null && scene == WeatherScene.HEAVY_SNOW) {
                            drawSnowflake(Offset(cx - 0.20f * u, cy + 0.18f * u), 0.08f * u)
                            drawSnowflake(Offset(cx, cy + 0.26f * u), 0.095f * u)
                            drawSnowflake(Offset(cx + 0.20f * u, cy + 0.18f * u), 0.08f * u)
                        } else {
                            drawSnowflake(Offset(cx - 0.14f * u, cy + 0.20f * u), 0.09f * u)
                            drawSnowflake(Offset(cx + 0.13f * u, cy + 0.24f * u), 0.075f * u)
                        }
                    }
                }
            }

            WeatherScene.THUNDER -> {
                drawCloud(cx, cy - 0.14f * u, 0.80f * u, storm = true)
                drawBolt(cx, cy + 0.0f * u, u)
            }

            WeatherScene.HAIL -> {
                drawCloud(cx, cy - 0.10f * u, 0.80f * u)
                listOf(-0.17f, 0f, 0.17f).forEach { dx ->
                    val center = Offset(cx + dx * u, cy + 0.19f * u)
                    drawCircle(
                        brush = Brush.radialGradient(
                            listOf(Color.White, RainBottom),
                            center = center,
                            radius = 0.072f * u
                        ),
                        radius = 0.072f * u,
                        center = center
                    )
                }
            }

            WeatherScene.UNKNOWN -> drawThermometer(cx, cy, u)
        }
    }
}

private fun DrawScope.drawSun(
    center: Offset,
    radius: Float,
    rayInner: Float,
    rayOuter: Float,
    rayWidth: Float
) {
    for (i in 0 until 8) {
        val a = i * PI / 4
        val dx = cos(a).toFloat()
        val dy = sin(a).toFloat()
        drawLine(
            SunEdge,
            Offset(center.x + dx * rayInner, center.y + dy * rayInner),
            Offset(center.x + dx * rayOuter, center.y + dy * rayOuter),
            strokeWidth = rayWidth,
            cap = StrokeCap.Round
        )
    }
    drawCircle(
        brush = Brush.radialGradient(listOf(SunCenter, SunEdge), center = center, radius = radius),
        radius = radius,
        center = center
    )
}

// 月牙用 saveLayer + Clear 裁剪：CLEAR 只作用于离屏层，不会穿透到底层卡片背景
private fun DrawScope.drawMoon(center: Offset, r: Float) {
    drawIntoCanvas { canvas ->
        canvas.saveLayer(Rect(Offset.Zero, size), Paint())
        drawCircle(
            brush = Brush.radialGradient(listOf(MoonCenter, MoonEdge), center = center, radius = r),
            radius = r,
            center = center
        )
        drawCircle(
            Color.Black,
            radius = r * 0.85f,
            center = Offset(center.x + r * 0.55f, center.y - r * 0.35f),
            blendMode = BlendMode.Clear
        )
        canvas.restore()
    }
}

private fun DrawScope.drawStar(center: Offset, r: Float) {
    val path = Path().apply {
        moveTo(center.x, center.y - r)
        quadraticTo(center.x, center.y, center.x + r, center.y)
        quadraticTo(center.x, center.y, center.x, center.y + r)
        quadraticTo(center.x, center.y, center.x - r, center.y)
        quadraticTo(center.x, center.y, center.x, center.y - r)
        close()
    }
    drawPath(path, MoonEdge)
}

private fun cloudPath(cx: Float, cy: Float, s: Float): Path = Path().apply {
    addOval(Rect(center = Offset(cx - 0.21f * s, cy + 0.01f * s), radius = 0.185f * s))
    addOval(Rect(center = Offset(cx + 0.03f * s, cy - 0.11f * s), radius = 0.24f * s))
    addOval(Rect(center = Offset(cx + 0.25f * s, cy + 0.02f * s), radius = 0.155f * s))
    addRoundRect(
        RoundRect(
            rect = Rect(cx - 0.375f * s, cy - 0.06f * s, cx + 0.395f * s, cy + 0.20f * s),
            cornerRadius = CornerRadius(0.12f * s)
        )
    )
}

private fun DrawScope.drawCloud(cx: Float, cy: Float, s: Float, storm: Boolean = false) {
    val colors = if (storm) {
        listOf(StormCloudTop, StormCloudBottom)
    } else {
        listOf(CloudTop, CloudMid, CloudBottom)
    }
    drawPath(
        path = cloudPath(cx, cy, s),
        brush = Brush.verticalGradient(colors, startY = cy - 0.35f * s, endY = cy + 0.20f * s)
    )
}

// 水滴：尖端朝上、圆底朝下，纵向渐变
private fun teardropPath(x: Float, y: Float, h: Float): Path {
    val r = h * 0.30f
    val bulbCy = y + h - r
    return Path().apply {
        moveTo(x, y)
        cubicTo(x - h * 0.26f, y + h * 0.40f, x - r, bulbCy - r * 0.55f, x - r, bulbCy)
        arcTo(Rect(x - r, bulbCy - r, x + r, bulbCy + r), 180f, -180f, false)
        cubicTo(x + r, bulbCy - r * 0.55f, x + h * 0.26f, y + h * 0.40f, x, y)
        close()
    }
}

/** drops: (x 偏移 u, 高度 u)，数量与尺寸按降水强度区分 */
private fun DrawScope.drawDrops(cx: Float, topY: Float, u: Float, drops: List<Pair<Float, Float>>) {
    drops.forEach { (dx, h) ->
        val px = cx + dx * u
        val ph = h * u
        drawPath(
            path = teardropPath(px, topY, ph),
            brush = Brush.verticalGradient(listOf(RainTop, RainBottom), startY = topY, endY = topY + ph)
        )
    }
}

private fun DrawScope.drawSnowflake(center: Offset, r: Float, color: Color = SnowBlue) {
    for (i in 0 until 3) {
        val a = i * PI / 3
        val dx = (cos(a) * r).toFloat()
        val dy = (sin(a) * r).toFloat()
        drawLine(
            color,
            Offset(center.x - dx, center.y - dy),
            Offset(center.x + dx, center.y + dy),
            strokeWidth = r * 0.24f,
            cap = StrokeCap.Round
        )
    }
    drawCircle(color, radius = r * 0.20f, center = center)
}

// 闪电略微探入云底，宽高比 ~0.26:0.34，避免细长"面条"感
private fun DrawScope.drawBolt(cx: Float, topY: Float, u: Float) {
    val path = Path().apply {
        moveTo(cx + 0.10f * u, topY)
        lineTo(cx - 0.10f * u, topY + 0.18f * u)
        lineTo(cx + 0.005f * u, topY + 0.18f * u)
        lineTo(cx - 0.08f * u, topY + 0.34f * u)
        lineTo(cx + 0.16f * u, topY + 0.12f * u)
        lineTo(cx + 0.05f * u, topY + 0.12f * u)
        close()
    }
    drawPath(
        path = path,
        brush = Brush.verticalGradient(listOf(BoltTop, BoltBottom), startY = topY, endY = topY + 0.34f * u)
    )
}

private fun DrawScope.drawThermometer(cx: Float, cy: Float, u: Float) {
    val tubeW = 0.16f * u
    val bodyBrush = Brush.verticalGradient(
        listOf(ThermoTop, ThermoBottom),
        startY = cy - 0.34f * u,
        endY = cy + 0.38f * u
    )
    drawRoundRect(
        bodyBrush,
        topLeft = Offset(cx - tubeW / 2, cy - 0.34f * u),
        size = Size(tubeW, 0.52f * u),
        cornerRadius = CornerRadius(tubeW / 2)
    )
    drawCircle(bodyBrush, radius = 0.16f * u, center = Offset(cx, cy + 0.22f * u))
    drawRoundRect(
        Color.White.copy(alpha = 0.85f),
        topLeft = Offset(cx - tubeW * 0.22f, cy - 0.28f * u),
        size = Size(tubeW * 0.44f, 0.40f * u),
        cornerRadius = CornerRadius(tubeW * 0.22f)
    )
}

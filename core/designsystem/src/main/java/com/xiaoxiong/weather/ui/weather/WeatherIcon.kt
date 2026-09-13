package com.xiaoxiong.weather.ui.weather

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathOperation
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
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

/** 缓存好的「路径 + 渐变」：drawWithCache 内构建一次，绘制阶段直接复用。 */
private class PathBrush(
    val path: Path,
    val brush: Brush,
)

/**
 * 自绘彩色天气图标，替代系统 emoji（跨设备渲染一致、与大气动画同色系）。
 * 所有图形按 size.minDimension 等比绘制，任意尺寸可用。
 *
 * 性能：路径/渐变在 [drawWithCache] 内随尺寸与场景构建一次，
 * 滚动列表高频重组时不会每次绘制都新建 Path/Brush；月亮用差值路径替代 saveLayer。
 */
@Composable
fun WeatherIcon(
    code: Int?,
    isDay: Boolean,
    modifier: Modifier = Modifier,
    precipMmPerHour: Double? = null,
) {
    val scene = weatherSceneOf(code, isDay)
    Canvas(
        modifier =
            modifier.drawWithCache {
                val u = size.minDimension
                val cx = size.width / 2f
                val cy = size.height / 2f
                when (scene) {
                    WeatherScene.CLEAR ->
                        if (isDay) {
                            val center = Offset(cx, cy)
                            val radius = 0.23f * u
                            val rays = sunRays(center, 0.32f * u, 0.43f * u)
                            val brush = radialBrush(SunCenter, SunEdge, center, radius)
                            onDrawBehind { drawSun(rays, brush, center, radius, rayWidth = 0.05f * u) }
                        } else {
                            val center = Offset(cx - 0.05f * u, cy + 0.05f * u)
                            val r = 0.27f * u
                            val moon = moonCrescentPath(center, r)
                            val brush = radialBrush(MoonCenter, MoonEdge, center, r)
                            val stars =
                                listOf(
                                    starPath(Offset(cx + 0.30f * u, cy - 0.28f * u), 0.07f * u),
                                    starPath(Offset(cx - 0.36f * u, cy - 0.30f * u), 0.05f * u),
                                )
                            onDrawBehind { drawMoonAndStars(moon, brush, stars) }
                        }

                    WeatherScene.PARTLY_CLOUDY -> {
                        val cloud = cloudArt(cx + 0.10f * u, cy + 0.10f * u, 0.72f * u)
                        if (isDay) {
                            val center = Offset(cx - 0.19f * u, cy - 0.19f * u)
                            val radius = 0.155f * u
                            val rays = sunRays(center, 0.23f * u, 0.30f * u)
                            val brush = radialBrush(SunCenter, SunEdge, center, radius)
                            onDrawBehind {
                                drawSun(rays, brush, center, radius, rayWidth = 0.042f * u)
                                drawPathBrush(cloud)
                            }
                        } else {
                            val center = Offset(cx - 0.20f * u, cy - 0.20f * u)
                            val r = 0.14f * u
                            val moon = moonCrescentPath(center, r)
                            val brush = radialBrush(MoonCenter, MoonEdge, center, r)
                            onDrawBehind {
                                drawPath(moon, brush)
                                drawPathBrush(cloud)
                            }
                        }
                    }

                    WeatherScene.OVERCAST -> {
                        val back = cloudArt(cx - 0.08f * u, cy - 0.13f * u, 0.62f * u, storm = true)
                        val front = cloudArt(cx + 0.08f * u, cy + 0.06f * u, 0.78f * u)
                        onDrawBehind {
                            drawPathBrush(back)
                            drawPathBrush(front)
                        }
                    }

                    // 纯雾线（无云）：三条横向渐隐线，左右缘错落，任何底色上都干净
                    WeatherScene.FOG -> {
                        val fogLines = fogLineArt(cx, cy, u)
                        onDrawBehind { fogLines.forEach { drawFogLine(it) } }
                    }

                    // 降水全部收在云宽度（±0.31u）之内，从云底（≈cy+0.06u）下方开始
                    WeatherScene.DRIZZLE -> {
                        val cloud = cloudArt(cx, cy - 0.10f * u, 0.80f * u)
                        val drops =
                            dropArt(
                                cx,
                                cy + 0.13f * u,
                                u,
                                listOf(-0.21f to 0.075f, -0.07f to 0.075f, 0.07f to 0.075f, 0.21f to 0.075f),
                            )
                        onDrawBehind {
                            drawPathBrush(cloud)
                            drops.forEach { drawPathBrush(it) }
                        }
                    }

                    // 雨按实测雨强分档：小雨 2 滴 / 中雨 3 滴 / 大雨 4 滴+风暴云 / 暴雨 5 滴+风暴云；
                    // 无速率数据时按场景回落（RAIN→中雨，HEAVY_RAIN→大雨）
                    WeatherScene.RAIN, WeatherScene.HEAVY_RAIN -> {
                        val level = precipMmPerHour?.let { WeatherCodes.rainIntensity(it) }
                        val storm =
                            when (level) {
                                WeatherCodes.RainIntensity.HEAVY,
                                WeatherCodes.RainIntensity.TORRENTIAL,
                                WeatherCodes.RainIntensity.VIOLENT,
                                -> true
                                null -> scene == WeatherScene.HEAVY_RAIN
                                else -> false
                            }
                        val drops =
                            when (level) {
                                WeatherCodes.RainIntensity.NONE,
                                WeatherCodes.RainIntensity.LIGHT,
                                ->
                                    listOf(-0.10f to 0.12f, 0.10f to 0.12f)
                                WeatherCodes.RainIntensity.MODERATE ->
                                    listOf(-0.16f to 0.15f, 0f to 0.15f, 0.16f to 0.15f)
                                WeatherCodes.RainIntensity.HEAVY ->
                                    listOf(-0.22f to 0.16f, -0.07f to 0.12f, 0.07f to 0.16f, 0.22f to 0.12f)
                                WeatherCodes.RainIntensity.TORRENTIAL,
                                WeatherCodes.RainIntensity.VIOLENT,
                                ->
                                    listOf(-0.24f to 0.17f, -0.12f to 0.12f, 0f to 0.17f, 0.12f to 0.12f, 0.24f to 0.17f)
                                null ->
                                    if (scene == WeatherScene.HEAVY_RAIN) {
                                        listOf(-0.22f to 0.16f, -0.07f to 0.12f, 0.07f to 0.16f, 0.22f to 0.12f)
                                    } else {
                                        listOf(-0.16f to 0.15f, 0f to 0.15f, 0.16f to 0.15f)
                                    }
                            }
                        val cloud =
                            cloudArt(
                                cx,
                                cy - (if (storm) 0.12f else 0.10f) * u,
                                (if (storm) 0.82f else 0.80f) * u,
                                storm = storm,
                            )
                        val dropArt = dropArt(cx, cy + 0.13f * u, u, drops)
                        onDrawBehind {
                            drawPathBrush(cloud)
                            dropArt.forEach { drawPathBrush(it) }
                        }
                    }

                    WeatherScene.FREEZING_RAIN -> {
                        val cloud = cloudArt(cx, cy - 0.10f * u, 0.80f * u)
                        val drops =
                            dropArt(
                                cx - 0.09f * u,
                                cy + 0.13f * u,
                                u,
                                listOf(-0.09f to 0.13f, 0.07f to 0.13f),
                            )
                        val flake = Offset(cx + 0.17f * u, cy + 0.21f * u) to 0.105f * u
                        onDrawBehind {
                            drawPathBrush(cloud)
                            drops.forEach { drawPathBrush(it) }
                            drawSnowflake(flake.first, flake.second)
                        }
                    }

                    // 雪同套分档：小雪 2 小晶 / 中雪 2 晶 / 大雪 3 晶+风暴云 / 暴雪 3 大晶+风暴云
                    WeatherScene.SNOW, WeatherScene.HEAVY_SNOW -> {
                        val level = precipMmPerHour?.let { WeatherCodes.snowIntensity(code, it) }
                        val storm =
                            when (level) {
                                WeatherCodes.SnowIntensity.HEAVY,
                                WeatherCodes.SnowIntensity.BLIZZARD,
                                -> true
                                null -> scene == WeatherScene.HEAVY_SNOW
                                else -> false
                            }
                        val cloud = cloudArt(cx, cy - 0.11f * u, 0.80f * u, storm = storm)
                        val flakes =
                            when (level) {
                                WeatherCodes.SnowIntensity.NONE,
                                WeatherCodes.SnowIntensity.LIGHT,
                                ->
                                    listOf(
                                        Offset(cx - 0.10f * u, cy + 0.20f * u) to 0.075f * u,
                                        Offset(cx + 0.12f * u, cy + 0.24f * u) to 0.06f * u,
                                    )
                                WeatherCodes.SnowIntensity.HEAVY ->
                                    listOf(
                                        Offset(cx - 0.20f * u, cy + 0.18f * u) to 0.08f * u,
                                        Offset(cx, cy + 0.26f * u) to 0.095f * u,
                                        Offset(cx + 0.20f * u, cy + 0.18f * u) to 0.08f * u,
                                    )
                                // 暴雪：两排 5 片错落，靠密度与大雪区分（单靠放大在小尺寸下看不出）
                                WeatherCodes.SnowIntensity.BLIZZARD ->
                                    listOf(
                                        Offset(cx - 0.22f * u, cy + 0.15f * u) to 0.075f * u,
                                        Offset(cx, cy + 0.15f * u) to 0.075f * u,
                                        Offset(cx + 0.22f * u, cy + 0.15f * u) to 0.075f * u,
                                        Offset(cx - 0.11f * u, cy + 0.30f * u) to 0.065f * u,
                                        Offset(cx + 0.11f * u, cy + 0.30f * u) to 0.065f * u,
                                    )
                                else ->
                                    // MODERATE 或无数据：SNOW→中雪两片，HEAVY_SNOW→大雪三片
                                    if (level == null && scene == WeatherScene.HEAVY_SNOW) {
                                        listOf(
                                            Offset(cx - 0.20f * u, cy + 0.18f * u) to 0.08f * u,
                                            Offset(cx, cy + 0.26f * u) to 0.095f * u,
                                            Offset(cx + 0.20f * u, cy + 0.18f * u) to 0.08f * u,
                                        )
                                    } else {
                                        listOf(
                                            Offset(cx - 0.14f * u, cy + 0.20f * u) to 0.09f * u,
                                            Offset(cx + 0.13f * u, cy + 0.24f * u) to 0.075f * u,
                                        )
                                    }
                            }
                        onDrawBehind {
                            drawPathBrush(cloud)
                            flakes.forEach { (center, r) -> drawSnowflake(center, r) }
                        }
                    }

                    WeatherScene.THUNDER -> {
                        val cloud = cloudArt(cx, cy - 0.14f * u, 0.80f * u, storm = true)
                        val bolt = boltArt(cx, cy, u)
                        onDrawBehind {
                            drawPathBrush(cloud)
                            drawPathBrush(bolt)
                        }
                    }

                    WeatherScene.HAIL -> {
                        val cloud = cloudArt(cx, cy - 0.10f * u, 0.80f * u)
                        val balls =
                            listOf(-0.17f, 0f, 0.17f).map { dx ->
                                val center = Offset(cx + dx * u, cy + 0.19f * u)
                                val radius = 0.072f * u
                                Triple(center, radius, radialBrush(Color.White, RainBottom, center, radius))
                            }
                        onDrawBehind {
                            drawPathBrush(cloud)
                            balls.forEach { (center, radius, brush) ->
                                drawCircle(brush = brush, radius = radius, center = center)
                            }
                        }
                    }

                    WeatherScene.UNKNOWN -> {
                        val tubeW = 0.16f * u
                        val bodyBrush = thermometerBrush(cy, u)
                        onDrawBehind { drawThermometer(cx, cy, u, tubeW, bodyBrush) }
                    }
                }
            },
        onDraw = {},
    )
}

private fun radialBrush(
    centerColor: Color,
    edgeColor: Color,
    center: Offset,
    radius: Float,
): Brush = Brush.radialGradient(listOf(centerColor, edgeColor), center = center, radius = radius)

private fun sunRays(
    center: Offset,
    inner: Float,
    outer: Float,
): List<Pair<Offset, Offset>> =
    (0 until 8).map { i ->
        val a = i * PI / 4
        val dx = cos(a).toFloat()
        val dy = sin(a).toFloat()
        Offset(center.x + dx * inner, center.y + dy * inner) to
            Offset(center.x + dx * outer, center.y + dy * outer)
    }

private fun DrawScope.drawSun(
    rays: List<Pair<Offset, Offset>>,
    brush: Brush,
    center: Offset,
    radius: Float,
    rayWidth: Float,
) {
    rays.forEach { (start, end) ->
        drawLine(
            SunEdge,
            start,
            end,
            strokeWidth = rayWidth,
            cap = StrokeCap.Round,
        )
    }
    drawCircle(
        brush = brush,
        radius = radius,
        center = center,
    )
}

/**
 * 月牙差值路径：外圆减掉偏移的裁剪圆，替代旧 saveLayer + Clear 实现，
 * 无需离屏层，也不依赖 BlendMode（视觉一致：外圆径向渐变 + 缺口）。
 */
private fun moonCrescentPath(
    center: Offset,
    r: Float,
): Path {
    val lit = Path().apply { addOval(Rect(center = center, radius = r)) }
    val cut =
        Path().apply {
            addOval(
                Rect(
                    center = Offset(center.x + r * 0.55f, center.y - r * 0.35f),
                    radius = r * 0.85f,
                ),
            )
        }
    return Path().apply { op(lit, cut, PathOperation.Difference) }
}

private fun DrawScope.drawMoonAndStars(
    moon: Path,
    moonBrush: Brush,
    stars: List<Path>,
) {
    drawPath(moon, moonBrush)
    stars.forEach { drawPath(it, MoonEdge) }
}

private fun starPath(
    center: Offset,
    r: Float,
): Path =
    Path().apply {
        moveTo(center.x, center.y - r)
        quadraticTo(center.x, center.y, center.x + r, center.y)
        quadraticTo(center.x, center.y, center.x, center.y + r)
        quadraticTo(center.x, center.y, center.x - r, center.y)
        quadraticTo(center.x, center.y, center.x, center.y - r)
        close()
    }

private fun cloudPath(
    cx: Float,
    cy: Float,
    s: Float,
): Path =
    Path().apply {
        addOval(Rect(center = Offset(cx - 0.21f * s, cy + 0.01f * s), radius = 0.185f * s))
        addOval(Rect(center = Offset(cx + 0.03f * s, cy - 0.11f * s), radius = 0.24f * s))
        addOval(Rect(center = Offset(cx + 0.25f * s, cy + 0.02f * s), radius = 0.155f * s))
        addRoundRect(
            RoundRect(
                rect = Rect(cx - 0.375f * s, cy - 0.06f * s, cx + 0.395f * s, cy + 0.20f * s),
                cornerRadius = CornerRadius(0.12f * s),
            ),
        )
    }

private fun cloudArt(
    cx: Float,
    cy: Float,
    s: Float,
    storm: Boolean = false,
): PathBrush {
    val colors =
        if (storm) {
            listOf(StormCloudTop, StormCloudBottom)
        } else {
            listOf(CloudTop, CloudMid, CloudBottom)
        }
    return PathBrush(
        path = cloudPath(cx, cy, s),
        brush = Brush.verticalGradient(colors, startY = cy - 0.35f * s, endY = cy + 0.20f * s),
    )
}

private fun DrawScope.drawPathBrush(art: PathBrush) {
    drawPath(art.path, art.brush)
}

// 水滴：尖端朝上、圆底朝下，纵向渐变
private fun teardropPath(
    x: Float,
    y: Float,
    h: Float,
): Path {
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
private fun dropArt(
    cx: Float,
    topY: Float,
    u: Float,
    drops: List<Pair<Float, Float>>,
): List<PathBrush> =
    drops.map { (dx, h) ->
        val px = cx + dx * u
        val ph = h * u
        PathBrush(
            path = teardropPath(px, topY, ph),
            brush = Brush.verticalGradient(listOf(RainTop, RainBottom), startY = topY, endY = topY + ph),
        )
    }

private data class FogLine(
    val brush: Brush,
    val start: Offset,
    val end: Offset,
    val strokeWidth: Float,
)

private fun fogLineArt(
    cx: Float,
    cy: Float,
    u: Float,
): List<FogLine> {
    val xs = listOf(-0.28f to 0.20f, -0.20f to 0.28f, -0.26f to 0.16f)
    val ys = listOf(-0.16f, 0f, 0.16f)
    val ws = listOf(0.05f, 0.06f, 0.05f)
    return xs.mapIndexed { i, (x0, x1) ->
        val sx = cx + x0 * u
        val ex = cx + x1 * u
        val ly = cy + ys[i] * u
        FogLine(
            brush =
                Brush.horizontalGradient(
                    0f to FogGray.copy(alpha = 0f),
                    0.18f to FogGray,
                    0.82f to FogGray,
                    1f to FogGray.copy(alpha = 0f),
                    startX = sx,
                    endX = ex,
                ),
            start = Offset(sx, ly),
            end = Offset(ex, ly),
            strokeWidth = ws[i] * u,
        )
    }
}

private fun DrawScope.drawFogLine(line: FogLine) {
    drawLine(
        brush = line.brush,
        start = line.start,
        end = line.end,
        strokeWidth = line.strokeWidth,
        cap = StrokeCap.Round,
    )
}

private fun DrawScope.drawSnowflake(
    center: Offset,
    r: Float,
    color: Color = SnowBlue,
) {
    for (i in 0 until 3) {
        val a = i * PI / 3
        val dx = (cos(a) * r).toFloat()
        val dy = (sin(a) * r).toFloat()
        drawLine(
            color,
            Offset(center.x - dx, center.y - dy),
            Offset(center.x + dx, center.y + dy),
            strokeWidth = r * 0.24f,
            cap = StrokeCap.Round,
        )
    }
    drawCircle(color, radius = r * 0.20f, center = center)
}

// 闪电略微探入云底，宽高比 ~0.26:0.34，避免细长"面条"感
private fun boltArt(
    cx: Float,
    topY: Float,
    u: Float,
): PathBrush {
    val path =
        Path().apply {
            moveTo(cx + 0.10f * u, topY)
            lineTo(cx - 0.10f * u, topY + 0.18f * u)
            lineTo(cx + 0.005f * u, topY + 0.18f * u)
            lineTo(cx - 0.08f * u, topY + 0.34f * u)
            lineTo(cx + 0.16f * u, topY + 0.12f * u)
            lineTo(cx + 0.05f * u, topY + 0.12f * u)
            close()
        }
    return PathBrush(
        path = path,
        brush = Brush.verticalGradient(listOf(BoltTop, BoltBottom), startY = topY, endY = topY + 0.34f * u),
    )
}

private fun thermometerBrush(
    cy: Float,
    u: Float,
): Brush =
    Brush.verticalGradient(
        listOf(ThermoTop, ThermoBottom),
        startY = cy - 0.34f * u,
        endY = cy + 0.38f * u,
    )

private fun DrawScope.drawThermometer(
    cx: Float,
    cy: Float,
    u: Float,
    tubeW: Float,
    bodyBrush: Brush,
) {
    drawRoundRect(
        bodyBrush,
        topLeft = Offset(cx - tubeW / 2, cy - 0.34f * u),
        size = Size(tubeW, 0.52f * u),
        cornerRadius = CornerRadius(tubeW / 2),
    )
    drawCircle(bodyBrush, radius = 0.16f * u, center = Offset(cx, cy + 0.22f * u))
    drawRoundRect(
        Color.White.copy(alpha = 0.85f),
        topLeft = Offset(cx - tubeW * 0.22f, cy - 0.28f * u),
        size = Size(tubeW * 0.44f, 0.40f * u),
        cornerRadius = CornerRadius(tubeW * 0.22f),
    )
}

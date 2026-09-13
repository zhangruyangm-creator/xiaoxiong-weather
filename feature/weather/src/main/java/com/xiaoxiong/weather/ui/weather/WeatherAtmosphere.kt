package com.xiaoxiong.weather.ui.weather

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.dp
import com.xiaoxiong.weather.ui.LocalReduceMotion
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

@Composable
fun WeatherAtmosphereCard(
    code: Int?,
    isDay: Boolean?,
    paused: Boolean = false,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.(WeatherSceneStyle) -> Unit,
) {
    val appDark = MaterialTheme.colorScheme.surface.luminance() < 0.5f
    val style = remember(code, isDay, appDark) { weatherSceneStyle(code, isDay, appDark) }
    val shape = RoundedCornerShape(20.dp)

    Box(
        modifier =
            modifier
                .clip(shape)
                .background(Brush.verticalGradient(style.gradient)),
    ) {
        WeatherAtmosphereOverlay(scene = style.scene, isDay = isDay != false, paused = paused)
        content(style)
    }
}

@Composable
private fun WeatherAtmosphereOverlay(
    scene: WeatherScene,
    isDay: Boolean,
    paused: Boolean = false,
) {
    val particles =
        remember(scene) {
            List(
                when (scene) {
                    WeatherScene.DRIZZLE -> 28
                    WeatherScene.RAIN -> 48
                    WeatherScene.HEAVY_RAIN, WeatherScene.THUNDER -> 72
                    WeatherScene.HAIL -> 56
                    WeatherScene.FREEZING_RAIN -> 52
                    WeatherScene.SNOW -> 36
                    WeatherScene.HEAVY_SNOW -> 64
                    else -> 0
                },
            ) {
                Particle(
                    x = Random.nextFloat(),
                    y = Random.nextFloat(),
                    speed = 0.35f + Random.nextFloat() * 0.9f,
                    size = 1.5f + Random.nextFloat() * 3.5f,
                    drift = -0.15f + Random.nextFloat() * 0.3f,
                )
            }
        }

    // 除 UNKNOWN 外都有动画元素（粒子/星光/云漂移/太阳光芒）；UNKNOWN、减弱动画或暂停时不启动无限动画
    val reduceMotion = LocalReduceMotion.current
    if (scene == WeatherScene.UNKNOWN || reduceMotion || paused) {
        AtmosphereCanvas(scene, isDay, particles, phase = { 0f }, flash = { 0f }, drift = { 0f })
    } else {
        AnimatedAtmosphereCanvas(scene, isDay, particles)
    }
}

@Composable
private fun AnimatedAtmosphereCanvas(
    scene: WeatherScene,
    isDay: Boolean,
    particles: List<Particle>,
) {
    val transition = rememberInfiniteTransition(label = "weather-atmosphere")
    // 不用 `by`：在 draw 里读 State，只重绘 Canvas，避免整卡每帧重组
    val phase =
        transition.animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec =
                infiniteRepeatable(
                    animation = tween(durationMillis = 2200, easing = LinearEasing),
                    repeatMode = RepeatMode.Restart,
                ),
            label = "phase",
        )
    val flash =
        transition.animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec =
                infiniteRepeatable(
                    animation = tween(durationMillis = 3200, easing = LinearEasing),
                    repeatMode = RepeatMode.Restart,
                ),
            label = "flash",
        )
    // 慢速漂移：云层/雾带/太阳光芒用，周期拉长避免晃眼
    val drift =
        transition.animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec =
                infiniteRepeatable(
                    animation = tween(durationMillis = 12000, easing = LinearEasing),
                    repeatMode = RepeatMode.Restart,
                ),
            label = "drift",
        )
    AtmosphereCanvas(
        scene,
        isDay,
        particles,
        phase = { phase.value },
        flash = { flash.value },
        drift = { drift.value },
    )
}

private const val TWO_PI_F = 6.2831853f

@Composable
private fun AtmosphereCanvas(
    scene: WeatherScene,
    isDay: Boolean,
    particles: List<Particle>,
    phase: () -> Float,
    flash: () -> Float,
    drift: () -> Float,
) {
    // 纯装饰背景：明确标记为无语义，TalkBack 直接跳过，天气信息由上层 Text 朗读
    Canvas(modifier = Modifier.fillMaxSize().clearAndSetSemantics {}) {
        when (scene) {
            WeatherScene.CLEAR -> {
                if (isDay) {
                    drawDayNoon(phase, drift)
                } else {
                    drawNightSky(phase)
                }
            }

            WeatherScene.PARTLY_CLOUDY -> {
                // 夜间不能画太阳/光芒：按 isDay 分支，夜间复用晴朗夜空的月亮+星星
                if (isDay) {
                    drawDayNoon(phase, drift)
                } else {
                    drawNightSky(phase)
                }
                // 云层各自带相位差的缓慢摆动，模拟微风
                val swayA = sin(drift() * TWO_PI_F) * size.width * 0.03f
                val swayB = sin(drift() * TWO_PI_F + 2.1f) * size.width * 0.025f
                drawCloud(size.width * 0.25f + swayA, size.height * 0.22f, 1.1f, Color.White.copy(alpha = 0.75f))
                drawCloud(size.width * 0.55f + swayB, size.height * 0.18f, 0.9f, Color.White.copy(alpha = 0.65f))
            }

            WeatherScene.OVERCAST -> {
                val swayA = sin(drift() * TWO_PI_F) * size.width * 0.035f
                val swayB = sin(drift() * TWO_PI_F + 1.6f) * size.width * 0.03f
                val swayC = sin(drift() * TWO_PI_F + 3.2f) * size.width * 0.025f
                drawCloud(size.width * 0.2f + swayA, size.height * 0.18f, 1.3f, Color.White.copy(alpha = 0.35f))
                drawCloud(size.width * 0.55f + swayB, size.height * 0.14f, 1.4f, Color.White.copy(alpha = 0.28f))
                drawCloud(size.width * 0.75f + swayC, size.height * 0.24f, 1.1f, Color.White.copy(alpha = 0.3f))
            }

            WeatherScene.FOG -> {
                for (i in 0..4) {
                    val y = size.height * (0.25f + i * 0.12f)
                    // 雾带左右缓慢流动；加宽两端避免摆动时露出边缘
                    val sway = sin(drift() * TWO_PI_F + i * 1.1f) * size.width * 0.04f
                    drawRect(
                        color = Color.White.copy(alpha = 0.18f + 0.04f * i),
                        topLeft = Offset(-size.width * 0.05f + sway, y),
                        size = Size(size.width * 1.1f, 18.dp.toPx()),
                    )
                }
            }

            WeatherScene.DRIZZLE,
            WeatherScene.RAIN,
            WeatherScene.HEAVY_RAIN,
            WeatherScene.THUNDER,
            -> {
                drawCloud(size.width * 0.3f, size.height * 0.14f, 1.25f, Color.White.copy(alpha = 0.28f))
                drawCloud(size.width * 0.7f, size.height * 0.12f, 1.15f, Color.White.copy(alpha = 0.22f))

                val length =
                    when (scene) {
                        WeatherScene.DRIZZLE -> 10.dp.toPx()
                        WeatherScene.RAIN -> 16.dp.toPx()
                        else -> 22.dp.toPx()
                    }
                val rainColor =
                    Color.White.copy(
                        alpha =
                            when (scene) {
                                WeatherScene.DRIZZLE -> 0.35f
                                WeatherScene.RAIN -> 0.5f
                                else -> 0.65f
                            },
                    )
                val phaseV = phase()
                particles.forEach { p ->
                    val y = ((p.y + phaseV * p.speed) % 1.2f) * size.height
                    val x = ((p.x + phaseV * p.drift) % 1f) * size.width
                    drawLine(
                        color = rainColor,
                        start = Offset(x, y),
                        end = Offset(x - 4f, y + length),
                        strokeWidth = p.size * 0.45f,
                    )
                }

                if (scene == WeatherScene.THUNDER) {
                    val boltVisible = (flash() * 10).toInt() % 10 in 7..8
                    if (boltVisible) {
                        drawLightning(size.width * 0.62f, size.height * 0.08f)
                        drawRect(Color(0x66FFF59D))
                    }
                }
            }

            WeatherScene.HAIL -> {
                drawCloud(size.width * 0.3f, size.height * 0.14f, 1.25f, Color.White.copy(alpha = 0.28f))
                drawCloud(size.width * 0.7f, size.height * 0.12f, 1.15f, Color.White.copy(alpha = 0.22f))
                val hailColor = Color(0xFFE3F2FD).copy(alpha = 0.9f)
                val phaseV = phase()
                particles.forEach { p ->
                    val y = ((p.y + phaseV * p.speed * 1.15f) % 1.15f) * size.height
                    val x = ((p.x + phaseV * p.drift * 0.4f) % 1f) * size.width
                    drawCircle(
                        color = hailColor,
                        radius = p.size * 0.85f,
                        center = Offset(x, y),
                    )
                }
            }

            WeatherScene.FREEZING_RAIN -> {
                drawCloud(size.width * 0.3f, size.height * 0.14f, 1.2f, Color.White.copy(alpha = 0.3f))
                val iceLine = Color(0xFFB3E5FC).copy(alpha = 0.7f)
                val iceTip = Color.White.copy(alpha = 0.55f)
                val iceLen = 14.dp.toPx()
                val phaseV = phase()
                particles.forEach { p ->
                    val y = ((p.y + phaseV * p.speed) % 1.2f) * size.height
                    val x = ((p.x + phaseV * p.drift) % 1f) * size.width
                    drawLine(
                        color = iceLine,
                        start = Offset(x, y),
                        end = Offset(x - 3f, y + iceLen),
                        strokeWidth = p.size * 0.4f,
                    )
                    drawCircle(
                        color = iceTip,
                        radius = 1.6f,
                        center = Offset(x - 3f, y + iceLen),
                    )
                }
            }

            WeatherScene.SNOW -> {
                val snowColor = Color.White.copy(alpha = 0.85f)
                val phaseV = phase()
                particles.forEach { p ->
                    val y = ((p.y + phaseV * p.speed * 0.45f) % 1.1f) * size.height
                    val x = (p.x * size.width) + sin((phaseV + p.y) * 6f) * 10f
                    drawCircle(
                        color = snowColor,
                        radius = p.size,
                        center = Offset(x, y),
                    )
                }
            }

            WeatherScene.HEAVY_SNOW -> {
                val snowColor = Color.White.copy(alpha = 0.92f)
                val phaseV = phase()
                particles.forEach { p ->
                    val y = ((p.y + phaseV * p.speed * 0.85f) % 1.15f) * size.height
                    val x = (p.x * size.width) + sin((phaseV + p.y) * 8f) * 16f
                    drawCircle(
                        color = snowColor,
                        radius = p.size * 1.15f,
                        center = Offset(x, y),
                    )
                }
            }

            WeatherScene.UNKNOWN -> Unit
        }
    }
}

private data class Particle(
    val x: Float,
    val y: Float,
    val speed: Float,
    val size: Float,
    val drift: Float,
)

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawDayNoon(
    phase: () -> Float,
    drift: () -> Float,
) {
    // 太阳：光晕脉动 + 光芒慢转（晴朗 / 少云白天共用）
    val cx = size.width * 0.82f
    val cy = size.height * 0.22f
    val pulse = 0.3f + 0.08f * sin(phase() * TWO_PI_F)
    drawCircle(Color(0xFFFFFDE7).copy(alpha = pulse), radius = 58.dp.toPx(), center = Offset(cx, cy))
    drawSunRays(cx, cy, 40.dp.toPx(), 54.dp.toPx(), drift() * TWO_PI_F)
    drawCircle(Color(0xFFFFF176).copy(alpha = 0.95f), radius = 34.dp.toPx(), center = Offset(cx, cy))
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawNightSky(phase: () -> Float) {
    // 月亮 + 星星（晴朗 / 少云夜间共用）
    val cx = size.width * 0.8f
    val cy = size.height * 0.2f
    drawCircle(Color(0xFFFFF59D), radius = 26.dp.toPx(), center = Offset(cx, cy))
    drawCircle(Color(0xFF203A43), radius = 20.dp.toPx(), center = Offset(cx - 10.dp.toPx(), cy - 6.dp.toPx()))
    repeat(12) { i ->
        val sx = size.width * ((i * 37) % 100) / 100f
        val sy = size.height * ((i * 53) % 55) / 100f
        drawCircle(Color.White.copy(alpha = 0.55f + 0.35f * sin(phase() * 6 + i)), radius = 2f, center = Offset(sx, sy))
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawSunRays(
    cx: Float,
    cy: Float,
    innerR: Float,
    outerR: Float,
    rotation: Float,
) {
    val rayColor = Color(0xFFFFF176).copy(alpha = 0.8f)
    for (i in 0..7) {
        val angle = rotation + i * TWO_PI_F / 8f
        val dx = cos(angle)
        val dy = sin(angle)
        drawLine(
            color = rayColor,
            start = Offset(cx + dx * innerR, cy + dy * innerR),
            end = Offset(cx + dx * outerR, cy + dy * outerR),
            strokeWidth = 3.dp.toPx(),
            cap = StrokeCap.Round,
        )
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawCloud(
    cx: Float,
    cy: Float,
    scale: Float,
    color: Color,
) {
    val u = 18.dp.toPx() * scale
    drawCircle(color, radius = u * 0.9f, center = Offset(cx, cy))
    drawCircle(color, radius = u, center = Offset(cx + u * 0.9f, cy - u * 0.15f))
    drawCircle(color, radius = u * 0.75f, center = Offset(cx + u * 1.7f, cy + u * 0.05f))
    drawCircle(color, radius = u * 0.7f, center = Offset(cx - u * 0.7f, cy + u * 0.1f))
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawLightning(
    startX: Float,
    startY: Float,
) {
    val path =
        Path().apply {
            moveTo(startX, startY)
            lineTo(startX - 18.dp.toPx(), startY + 40.dp.toPx())
            lineTo(startX - 4.dp.toPx(), startY + 40.dp.toPx())
            lineTo(startX - 28.dp.toPx(), startY + 95.dp.toPx())
            lineTo(startX + 8.dp.toPx(), startY + 48.dp.toPx())
            lineTo(startX - 6.dp.toPx(), startY + 48.dp.toPx())
            close()
        }
    drawPath(path, color = Color(0xFFFFF59D), style = Fill)
}

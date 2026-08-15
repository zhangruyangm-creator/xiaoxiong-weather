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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
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

enum class WeatherScene {
    CLEAR,
    PARTLY_CLOUDY,
    OVERCAST,
    FOG,
    DRIZZLE,
    RAIN,
    HEAVY_RAIN,
    THUNDER,
    HAIL,
    FREEZING_RAIN,
    SNOW,
    HEAVY_SNOW,
    UNKNOWN,
}

data class WeatherSceneStyle(
    val scene: WeatherScene,
    val gradient: List<Color>,
    val contentColor: Color,
    val mutedContentColor: Color,
)

fun weatherSceneOf(
    code: Int?,
    isDay: Boolean?,
): WeatherScene =
    when (code) {
        0, 1 -> WeatherScene.CLEAR
        2 -> WeatherScene.PARTLY_CLOUDY
        3 -> WeatherScene.OVERCAST
        45, 48 -> WeatherScene.FOG
        51, 53, 80 -> WeatherScene.DRIZZLE
        55, 61, 63, 81 -> WeatherScene.RAIN
        65, 82 -> WeatherScene.HEAVY_RAIN
        56, 57, 66, 67 -> WeatherScene.FREEZING_RAIN
        95, 96 -> WeatherScene.THUNDER
        99 -> WeatherScene.HAIL
        71, 73, 77, 85 -> WeatherScene.SNOW
        75, 86 -> WeatherScene.HEAVY_SNOW
        else -> WeatherScene.UNKNOWN
    }
// 夜间晴天用更深蓝紫，仍归 CLEAR，在样式里区分

fun weatherSceneStyle(
    code: Int?,
    isDay: Boolean?,
    appDark: Boolean = false,
): WeatherSceneStyle {
    val scene = weatherSceneOf(code, isDay)
    val day = isDay != false
    return when (scene) {
        WeatherScene.CLEAR ->
            if (day) {
                WeatherSceneStyle(
                    scene = scene,
                    gradient = listOf(Color(0xFF56CCF2), Color(0xFF2F80ED), Color(0xFF6DD5FA)),
                    contentColor = Color.White,
                    mutedContentColor = Color.White.copy(alpha = 0.82f),
                )
            } else {
                WeatherSceneStyle(
                    scene = scene,
                    gradient = listOf(Color(0xFF0F2027), Color(0xFF203A43), Color(0xFF2C5364)),
                    contentColor = Color(0xFFEAF2FF),
                    mutedContentColor = Color(0xFFEAF2FF).copy(alpha = 0.8f),
                )
            }

        WeatherScene.PARTLY_CLOUDY ->
            WeatherSceneStyle(
                scene = scene,
                gradient = listOf(Color(0xFF89CFF0), Color(0xFF7F9CF5), Color(0xFFB8C6FF)),
                contentColor = Color.White,
                mutedContentColor = Color.White.copy(alpha = 0.85f),
            )

        WeatherScene.OVERCAST ->
            WeatherSceneStyle(
                scene = scene,
                gradient = listOf(Color(0xFF6B7B8C), Color(0xFF4A5568), Color(0xFF718096)),
                contentColor = Color.White,
                mutedContentColor = Color.White.copy(alpha = 0.85f),
            )

        WeatherScene.FOG ->
            if (appDark) {
                WeatherSceneStyle(
                    scene = scene,
                    gradient = listOf(Color(0xFF3A4550), Color(0xFF2A3340), Color(0xFF4A5568)),
                    contentColor = Color(0xFFECEFF1),
                    mutedContentColor = Color(0xFFCFD8DC),
                )
            } else {
                WeatherSceneStyle(
                    scene = scene,
                    gradient = listOf(Color(0xFFB0BEC5), Color(0xFF90A4AE), Color(0xFFCFD8DC)),
                    contentColor = Color(0xFF263238),
                    mutedContentColor = Color(0xFF37474F),
                )
            }

        WeatherScene.DRIZZLE ->
            WeatherSceneStyle(
                scene = scene,
                gradient = listOf(Color(0xFF74A9CF), Color(0xFF5C8DB8), Color(0xFF89B4D4)),
                contentColor = Color.White,
                mutedContentColor = Color.White.copy(alpha = 0.85f),
            )

        WeatherScene.RAIN ->
            WeatherSceneStyle(
                scene = scene,
                gradient = listOf(Color(0xFF3D5A80), Color(0xFF293241), Color(0xFF4A6FA5)),
                contentColor = Color.White,
                mutedContentColor = Color.White.copy(alpha = 0.85f),
            )

        WeatherScene.HEAVY_RAIN ->
            WeatherSceneStyle(
                scene = scene,
                gradient = listOf(Color(0xFF1B263B), Color(0xFF0D1B2A), Color(0xFF415A77)),
                contentColor = Color.White,
                mutedContentColor = Color.White.copy(alpha = 0.85f),
            )

        WeatherScene.THUNDER ->
            WeatherSceneStyle(
                scene = scene,
                gradient = listOf(Color(0xFF232526), Color(0xFF414345), Color(0xFF1A1A2E)),
                contentColor = Color(0xFFFFF3E0),
                mutedContentColor = Color(0xFFFFE0B2),
            )

        WeatherScene.HAIL ->
            WeatherSceneStyle(
                scene = scene,
                gradient = listOf(Color(0xFF4A6572), Color(0xFF344955), Color(0xFF6B8A99)),
                contentColor = Color.White,
                mutedContentColor = Color.White.copy(alpha = 0.85f),
            )

        WeatherScene.FREEZING_RAIN ->
            WeatherSceneStyle(
                scene = scene,
                gradient = listOf(Color(0xFF5B7C8A), Color(0xFF3E5A66), Color(0xFF7FA0AE)),
                contentColor = Color.White,
                mutedContentColor = Color.White.copy(alpha = 0.85f),
            )

        WeatherScene.SNOW ->
            if (appDark) {
                WeatherSceneStyle(
                    scene = scene,
                    gradient = listOf(Color(0xFF3E5160), Color(0xFF2C3A47), Color(0xFF5A7180)),
                    contentColor = Color(0xFFE3F2FD),
                    mutedContentColor = Color(0xFFBBDEFB),
                )
            } else {
                WeatherSceneStyle(
                    scene = scene,
                    gradient = listOf(Color(0xFFE3F2FD), Color(0xFFBBDEFB), Color(0xFF90CAF9)),
                    contentColor = Color(0xFF1A237E),
                    mutedContentColor = Color(0xFF283593),
                )
            }

        WeatherScene.HEAVY_SNOW ->
            WeatherSceneStyle(
                scene = scene,
                gradient = listOf(Color(0xFF546E7A), Color(0xFF37474F), Color(0xFF78909C)),
                contentColor = Color.White,
                mutedContentColor = Color.White.copy(alpha = 0.9f),
            )

        WeatherScene.UNKNOWN ->
            WeatherSceneStyle(
                scene = scene,
                gradient = listOf(Color(0xFF667EEA), Color(0xFF764BA2)),
                contentColor = Color.White,
                mutedContentColor = Color.White.copy(alpha = 0.85f),
            )
    }
}

@Composable
fun WeatherAtmosphereCard(
    code: Int?,
    isDay: Boolean?,
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
        WeatherAtmosphereOverlay(scene = style.scene, isDay = isDay != false)
        content(style)
    }
}

@Composable
private fun WeatherAtmosphereOverlay(
    scene: WeatherScene,
    isDay: Boolean,
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

    // 除 UNKNOWN 外都有动画元素（粒子/星光/云漂移/太阳光芒）；UNKNOWN 或减弱动画时不启动无限动画
    val reduceMotion = LocalReduceMotion.current
    if (scene == WeatherScene.UNKNOWN || reduceMotion) {
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
    val phase by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec =
            infiniteRepeatable(
                animation = tween(durationMillis = 2200, easing = LinearEasing),
                repeatMode = RepeatMode.Restart,
            ),
        label = "phase",
    )
    val flash by transition.animateFloat(
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
    val drift by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec =
            infiniteRepeatable(
                animation = tween(durationMillis = 12000, easing = LinearEasing),
                repeatMode = RepeatMode.Restart,
            ),
        label = "drift",
    )
    AtmosphereCanvas(scene, isDay, particles, phase = { phase }, flash = { flash }, drift = { drift })
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
                    // 太阳：光晕脉动 + 光芒慢转
                    val cx = size.width * 0.82f
                    val cy = size.height * 0.22f
                    val pulse = 0.3f + 0.08f * sin(phase() * TWO_PI_F)
                    drawCircle(Color(0xFFFFFDE7).copy(alpha = pulse), radius = 58.dp.toPx(), center = Offset(cx, cy))
                    drawSunRays(cx, cy, 40.dp.toPx(), 54.dp.toPx(), drift() * TWO_PI_F)
                    drawCircle(Color(0xFFFFF176).copy(alpha = 0.95f), radius = 34.dp.toPx(), center = Offset(cx, cy))
                } else {
                    // 月亮 + 星星
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
            }

            WeatherScene.PARTLY_CLOUDY -> {
                val sunX = size.width * 0.78f
                val sunY = size.height * 0.2f
                drawSunRays(sunX, sunY, 32.dp.toPx(), 42.dp.toPx(), drift() * TWO_PI_F)
                drawSun(sunX, sunY, 26.dp.toPx())
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
                        size =
                            androidx.compose.ui.geometry
                                .Size(size.width * 1.1f, 18.dp.toPx()),
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
                val alpha =
                    when (scene) {
                        WeatherScene.DRIZZLE -> 0.35f
                        WeatherScene.RAIN -> 0.5f
                        else -> 0.65f
                    }
                particles.forEach { p ->
                    val y = ((p.y + phase() * p.speed) % 1.2f) * size.height
                    val x = ((p.x + phase() * p.drift) % 1f) * size.width
                    drawLine(
                        color = Color.White.copy(alpha = alpha),
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
                particles.forEach { p ->
                    val y = ((p.y + phase() * p.speed * 1.15f) % 1.15f) * size.height
                    val x = ((p.x + phase() * p.drift * 0.4f) % 1f) * size.width
                    drawCircle(
                        color = Color(0xFFE3F2FD).copy(alpha = 0.9f),
                        radius = p.size * 0.85f,
                        center = Offset(x, y),
                    )
                }
            }

            WeatherScene.FREEZING_RAIN -> {
                drawCloud(size.width * 0.3f, size.height * 0.14f, 1.2f, Color.White.copy(alpha = 0.3f))
                particles.forEach { p ->
                    val y = ((p.y + phase() * p.speed) % 1.2f) * size.height
                    val x = ((p.x + phase() * p.drift) % 1f) * size.width
                    drawLine(
                        color = Color(0xFFB3E5FC).copy(alpha = 0.7f),
                        start = Offset(x, y),
                        end = Offset(x - 3f, y + 14.dp.toPx()),
                        strokeWidth = p.size * 0.4f,
                    )
                    drawCircle(
                        color = Color.White.copy(alpha = 0.55f),
                        radius = 1.6f,
                        center = Offset(x - 3f, y + 14.dp.toPx()),
                    )
                }
            }

            WeatherScene.SNOW -> {
                particles.forEach { p ->
                    val y = ((p.y + phase() * p.speed * 0.45f) % 1.1f) * size.height
                    val x = (p.x * size.width) + sin((phase() + p.y) * 6f) * 10f
                    drawCircle(
                        color = Color.White.copy(alpha = 0.85f),
                        radius = p.size,
                        center = Offset(x, y),
                    )
                }
            }

            WeatherScene.HEAVY_SNOW -> {
                particles.forEach { p ->
                    val y = ((p.y + phase() * p.speed * 0.85f) % 1.15f) * size.height
                    val x = (p.x * size.width) + sin((phase() + p.y) * 8f) * 16f
                    drawCircle(
                        color = Color.White.copy(alpha = 0.92f),
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

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawSun(
    cx: Float,
    cy: Float,
    r: Float,
) {
    drawCircle(Color(0xFFFFF176).copy(alpha = 0.95f), radius = r, center = Offset(cx, cy))
    drawCircle(Color(0xFFFFFDE7).copy(alpha = 0.28f), radius = r * 1.7f, center = Offset(cx, cy))
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

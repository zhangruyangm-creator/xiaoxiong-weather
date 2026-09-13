package com.xiaoxiong.weather.ui.weather

import androidx.annotation.StringRes
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.xiaoxiong.weather.R
import com.xiaoxiong.weather.ui.theme.AccentCoral
import com.xiaoxiong.weather.ui.theme.SoftTeal
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

internal data class RadarEchoDot(
    val angleDeg: Float,
    val radiusFrac: Float,
    val sizeDp: Float,
)

/**
 * 卡通雷达扫描占位：同心圆 + 扫掠波束 + 闪烁回波点，替代死板黑屏转圈。
 */
@Composable
internal fun RadarScanLoadingOverlay(
    cityName: String,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
    @StringRes detailRes: Int = R.string.radar_scan_detail,
) {
    // 减弱动画时不做扫掠/脉冲，画静态波束即可（扫掠 2400ms 无限动画纯装饰）
    val reduceMotion = com.xiaoxiong.weather.ui.LocalReduceMotion.current
    val transition = if (reduceMotion) null else rememberInfiniteTransition(label = "radar-scan")
    // 在 Canvas draw 里读 State，避免扫掠动画带动整层重组
    val sweep =
        transition?.animateFloat(
            initialValue = 0f,
            targetValue = 360f,
            animationSpec =
                infiniteRepeatable(
                    animation = tween(durationMillis = 2400, easing = LinearEasing),
                    repeatMode = RepeatMode.Restart,
                ),
            label = "sweep",
        )
    val pulse =
        transition?.animateFloat(
            initialValue = 0.55f,
            targetValue = 1f,
            animationSpec =
                infiniteRepeatable(
                    animation = tween(durationMillis = 1200, easing = LinearEasing),
                    repeatMode = RepeatMode.Reverse,
                ),
            label = "pulse",
        )
    val echoes =
        remember {
            List(7) {
                RadarEchoDot(
                    angleDeg = Random.nextFloat() * 360f,
                    radiusFrac = 0.22f + Random.nextFloat() * 0.55f,
                    sizeDp = 3.5f + Random.nextFloat() * 4f,
                )
            }
        }
    val gridColor = Color(0xFF3D8BFF).copy(alpha = 0.35f)
    val beamColor = SoftTeal
    val glowColor = Color(0xFF56CCF2)

    Box(
        modifier =
            modifier.background(
                Brush.radialGradient(
                    colors = listOf(Color(0xFF243B55), Color(0xFF141E30), Color(0xFF0B1220)),
                ),
            ),
        contentAlignment = Alignment.Center,
    ) {
        val canvasModifier =
            Modifier
                .fillMaxSize()
                .padding(if (compact) 28.dp else 20.dp)
                .drawWithCache {
                    val cx = size.width / 2f
                    val cy = size.height / 2f
                    val maxR = minOf(size.width, size.height) / 2f * 0.92f
                    val center = Offset(cx, cy)
                    // 只缓存不随 sweep/pulse 变化的几何与画刷；旋转/透明度留在绘制块里逐帧读取
                    val glowBrush =
                        Brush.radialGradient(
                            colors = listOf(glowColor.copy(alpha = 0.14f), Color.Transparent),
                            center = center,
                            radius = maxR,
                        )
                    val ringStroke = Stroke(width = 1.5.dp.toPx())
                    val crossStroke = 1.dp.toPx()
                    val wedgePath =
                        Path().apply {
                            moveTo(cx, cy)
                            lineTo(cx + maxR, cy)
                            arcTo(
                                Rect(cx - maxR, cy - maxR, cx + maxR, cy + maxR),
                                0f,
                                -55f,
                                forceMoveTo = false,
                            )
                            close()
                        }
                    val wedgeBrush =
                        Brush.linearGradient(
                            colors =
                                listOf(
                                    beamColor.copy(alpha = 0.48f),
                                    beamColor.copy(alpha = 0.06f),
                                ),
                            start = Offset(cx + maxR, cy),
                            end = Offset(cx + maxR * 0.35f, cy - maxR * 0.55f),
                        )
                    val rayStroke = 2.dp.toPx()
                    val echoCenters =
                        echoes.map { echo ->
                            val rad = maxR * echo.radiusFrac
                            val radAng = Math.toRadians(echo.angleDeg.toDouble())
                            Offset(cx + rad * cos(radAng).toFloat(), cy + rad * sin(radAng).toFloat())
                        }
                    val echoRadii = echoes.map { it.sizeDp.dp.toPx() }
                    val hubGlowRadius = 10.dp.toPx()
                    val hubRadius = 4.5.dp.toPx()
                    val hubStroke = Stroke(width = 1.5.dp.toPx())

                    onDrawBehind {
                        val sweepV = sweep?.value ?: 45f
                        val pulseV = pulse?.value ?: 0.8f

                        // 外圈光晕
                        drawCircle(
                            brush = glowBrush,
                            radius = maxR,
                            center = center,
                            alpha = pulseV,
                        )

                        // 同心圆环
                        for (i in 1..4) {
                            drawCircle(
                                color = gridColor.copy(alpha = 0.22f + i * 0.04f),
                                radius = maxR * i / 4f,
                                center = center,
                                style = ringStroke,
                            )
                        }
                        // 十字准星
                        drawLine(
                            color = gridColor.copy(alpha = 0.35f),
                            start = Offset(cx - maxR, cy),
                            end = Offset(cx + maxR, cy),
                            strokeWidth = crossStroke,
                        )
                        drawLine(
                            color = gridColor.copy(alpha = 0.35f),
                            start = Offset(cx, cy - maxR),
                            end = Offset(cx, cy + maxR),
                            strokeWidth = crossStroke,
                        )

                        // 扫掠扇形：Path 楔形 + 主扫射线已缓存，这里只做旋转与脉冲透明度
                        rotate(degrees = sweepV, pivot = center) {
                            drawPath(wedgePath, brush = wedgeBrush, alpha = pulseV)
                            drawLine(
                                color = Color.White.copy(alpha = 0.85f),
                                start = center,
                                end = Offset(cx + maxR, cy),
                                strokeWidth = rayStroke,
                                cap = StrokeCap.Round,
                            )
                        }

                        // 回波点：扫到附近时更亮
                        echoes.forEachIndexed { index, echo ->
                            val delta = ((sweepV - echo.angleDeg + 540f) % 360f)
                            val lit =
                                when {
                                    delta < 40f -> 1f - delta / 40f
                                    else -> 0.18f
                                }
                            drawCircle(
                                color = beamColor.copy(alpha = 0.25f + 0.65f * lit),
                                radius = echoRadii[index] * (0.75f + 0.4f * lit),
                                center = echoCenters[index],
                            )
                        }

                        // 中心定位
                        drawCircle(
                            color = AccentCoral.copy(alpha = 0.3f),
                            radius = hubGlowRadius * pulseV,
                            center = center,
                        )
                        drawCircle(
                            color = AccentCoral,
                            radius = hubRadius,
                            center = center,
                        )
                        drawCircle(
                            color = Color.White.copy(alpha = 0.9f),
                            radius = hubRadius,
                            center = center,
                            style = hubStroke,
                        )
                    }
                }
        Spacer(modifier = canvasModifier)

        Column(
            modifier =
                Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = if (compact) 12.dp else 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text =
                    if (compact) {
                        stringResource(R.string.radar_scan_sync)
                    } else {
                        stringResource(R.string.radar_scan_city, cityName)
                    },
                color = Color.White.copy(alpha = 0.92f),
                style =
                    if (compact) {
                        MaterialTheme.typography.labelLarge
                    } else {
                        MaterialTheme.typography.titleSmall
                    },
                fontWeight = FontWeight.SemiBold,
            )
            if (!compact) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = stringResource(detailRes),
                    color = Color.White.copy(alpha = 0.65f),
                    style = MaterialTheme.typography.labelMedium,
                )
            }
        }
    }
}

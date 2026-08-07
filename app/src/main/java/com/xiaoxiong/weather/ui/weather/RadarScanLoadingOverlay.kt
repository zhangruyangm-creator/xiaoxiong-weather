package com.xiaoxiong.weather.ui.weather

import androidx.annotation.StringRes
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
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
    val sizeDp: Float
)

/**
 * 卡通雷达扫描占位：同心圆 + 扫掠波束 + 闪烁回波点，替代死板黑屏转圈。
 */
@Composable
internal fun RadarScanLoadingOverlay(
    cityName: String,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
    @StringRes detailRes: Int = R.string.radar_scan_detail
) {
    val transition = rememberInfiniteTransition(label = "radar-scan")
    val sweep by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2400, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "sweep"
    )
    val pulse by transition.animateFloat(
        initialValue = 0.55f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1200, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse"
    )
    val echoes = remember {
        List(7) {
            RadarEchoDot(
                angleDeg = Random.nextFloat() * 360f,
                radiusFrac = 0.22f + Random.nextFloat() * 0.55f,
                sizeDp = 3.5f + Random.nextFloat() * 4f
            )
        }
    }
    val gridColor = Color(0xFF3D8BFF).copy(alpha = 0.35f)
    val beamColor = SoftTeal
    val glowColor = Color(0xFF56CCF2)

    Box(
        modifier = modifier.background(
            Brush.radialGradient(
                colors = listOf(Color(0xFF243B55), Color(0xFF141E30), Color(0xFF0B1220))
            )
        ),
        contentAlignment = Alignment.Center
    ) {
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .padding(if (compact) 28.dp else 20.dp)
        ) {
            val cx = size.width / 2f
            val cy = size.height / 2f
            val maxR = minOf(size.width, size.height) / 2f * 0.92f

            // 外圈光晕
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        glowColor.copy(alpha = 0.14f * pulse),
                        Color.Transparent
                    ),
                    center = Offset(cx, cy),
                    radius = maxR
                ),
                radius = maxR,
                center = Offset(cx, cy)
            )

            // 同心圆环
            for (i in 1..4) {
                drawCircle(
                    color = gridColor.copy(alpha = 0.22f + i * 0.04f),
                    radius = maxR * i / 4f,
                    center = Offset(cx, cy),
                    style = Stroke(width = 1.5.dp.toPx())
                )
            }
            // 十字准星
            drawLine(
                color = gridColor.copy(alpha = 0.35f),
                start = Offset(cx - maxR, cy),
                end = Offset(cx + maxR, cy),
                strokeWidth = 1.dp.toPx()
            )
            drawLine(
                color = gridColor.copy(alpha = 0.35f),
                start = Offset(cx, cy - maxR),
                end = Offset(cx, cy + maxR),
                strokeWidth = 1.dp.toPx()
            )

            // 扫掠扇形（用多条半透明线近似）
            rotate(degrees = sweep, pivot = Offset(cx, cy)) {
                val wedge = 48
                for (i in 0 until wedge) {
                    val t = i / (wedge - 1f)
                    val alpha = (1f - t) * 0.42f * pulse
                    val angle = Math.toRadians((-t * 55.0))
                    val ex = cx + maxR * cos(angle).toFloat()
                    val ey = cy + maxR * sin(angle).toFloat()
                    drawLine(
                        color = beamColor.copy(alpha = alpha),
                        start = Offset(cx, cy),
                        end = Offset(ex, ey),
                        strokeWidth = 3.dp.toPx(),
                        cap = StrokeCap.Round
                    )
                }
                // 主扫射线
                drawLine(
                    color = Color.White.copy(alpha = 0.85f),
                    start = Offset(cx, cy),
                    end = Offset(cx + maxR, cy),
                    strokeWidth = 2.dp.toPx(),
                    cap = StrokeCap.Round
                )
            }

            // 回波点：扫到附近时更亮
            echoes.forEach { echo ->
                val delta = ((sweep - echo.angleDeg + 540f) % 360f)
                val lit = when {
                    delta < 40f -> 1f - delta / 40f
                    else -> 0.18f
                }
                val rad = maxR * echo.radiusFrac
                val radAng = Math.toRadians(echo.angleDeg.toDouble())
                val x = cx + rad * cos(radAng).toFloat()
                val y = cy + rad * sin(radAng).toFloat()
                drawCircle(
                    color = beamColor.copy(alpha = 0.25f + 0.65f * lit),
                    radius = echo.sizeDp.dp.toPx() * (0.75f + 0.4f * lit),
                    center = Offset(x, y)
                )
            }

            // 中心定位
            drawCircle(
                color = AccentCoral.copy(alpha = 0.3f),
                radius = 10.dp.toPx() * pulse,
                center = Offset(cx, cy)
            )
            drawCircle(
                color = AccentCoral,
                radius = 4.5.dp.toPx(),
                center = Offset(cx, cy)
            )
            drawCircle(
                color = Color.White.copy(alpha = 0.9f),
                radius = 4.5.dp.toPx(),
                center = Offset(cx, cy),
                style = Stroke(width = 1.5.dp.toPx())
            )
        }

        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = if (compact) 12.dp else 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = if (compact) {
                    stringResource(R.string.radar_scan_sync)
                } else {
                    stringResource(R.string.radar_scan_city, cityName)
                },
                color = Color.White.copy(alpha = 0.92f),
                style = if (compact) {
                    MaterialTheme.typography.labelLarge
                } else {
                    MaterialTheme.typography.titleSmall
                },
                fontWeight = FontWeight.SemiBold
            )
            if (!compact) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = stringResource(detailRes),
                    color = Color.White.copy(alpha = 0.65f),
                    style = MaterialTheme.typography.labelMedium
                )
            }
        }
    }
}

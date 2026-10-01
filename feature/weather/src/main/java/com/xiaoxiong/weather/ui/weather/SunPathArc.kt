package com.xiaoxiong.weather.ui.weather

import androidx.annotation.DrawableRes
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.xiaoxiong.weather.R
import com.xiaoxiong.weather.ui.rememberSampledDrawable
import java.time.LocalDate
import java.time.LocalTime
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

@Composable
internal fun SunPathArc(
    phase: SunPathPhase?,
    sunriseLabel: String,
    sunsetLabel: String,
    modifier: Modifier = Modifier,
    onDate: LocalDate = LocalDate.now(),
    onTime: LocalTime = LocalTime.now(),
    sunrise: LocalTime? = null,
) {
    val scheme = MaterialTheme.colorScheme
    val dark = scheme.surface.luminance() < 0.5f
    val dayArc = if (dark) Color(0xFFFFB74D) else Color(0xFFFF9800)
    val nightArc = if (dark) Color(0xFF90CAF9) else Color(0xFF5C6BC0)
    val horizon = scheme.onSurface.copy(alpha = 0.28f)
    val track = scheme.onSurface.copy(alpha = 0.12f)
    val skyTop =
        if (phase?.isDay != false) {
            if (dark) Color(0x44FFB74D) else Color(0x33FFCC80)
        } else {
            if (dark) Color(0x335C6BC0) else Color(0x225C6BC0)
        }
    val skyBottom = Color.Transparent
    val bearMoment =
        remember(phase, onDate, onTime, sunrise) {
            sunPathBearMoment(phase, onDate, onTime, sunrise = sunrise)
        }
    val holiday = remember(bearMoment, onDate) { holidayBearOverride(onDate, bearMoment) }
    val bearRes =
        remember(bearMoment, onDate, holiday) {
            holiday?.bearResFor(onDate) ?: bearMoment.bearResFor(onDate)
        }
    val bearCaptionRes =
        remember(bearMoment, onDate, holiday) {
            holiday?.captionResFor(onDate) ?: bearMoment.captionResFor(onDate)
        }

    Column(modifier = modifier.fillMaxWidth()) {
        Box(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .height(132.dp)
                    .padding(horizontal = 8.dp),
        ) {
            Canvas(modifier = Modifier.fillMaxWidth().height(132.dp)) {
                val padX = 28.dp.toPx()
                val padTop = 16.dp.toPx()
                val nightDepth = 26.dp.toPx()
                val padBottom = nightDepth + 10.dp.toPx()
                val diameter =
                    (size.width - padX * 2)
                        .coerceAtMost(2f * (size.height - padTop - padBottom))
                val radius = diameter / 2f
                val cx = size.width / 2f
                val cy = padTop + radius
                val oval =
                    Rect(
                        offset = Offset(cx - radius, cy - radius),
                        size = Size(diameter, diameter),
                    )
                val nightOval =
                    Rect(
                        left = cx - radius,
                        top = cy - nightDepth,
                        right = cx + radius,
                        bottom = cy + nightDepth,
                    )

                val skyPath =
                    Path().apply {
                        moveTo(cx - radius, cy)
                        arcTo(oval, 180f, 180f, forceMoveTo = false)
                        close()
                    }
                drawPath(
                    path = skyPath,
                    brush =
                        Brush.verticalGradient(
                            colors = listOf(skyTop, skyBottom),
                            startY = cy - radius,
                            endY = cy,
                        ),
                )

                drawArc(
                    color = track,
                    startAngle = 180f,
                    sweepAngle = 180f,
                    useCenter = false,
                    topLeft = oval.topLeft,
                    size = oval.size,
                    style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round),
                )
                drawArc(
                    color = track.copy(alpha = 0.7f),
                    startAngle = 0f,
                    sweepAngle = 180f,
                    useCenter = false,
                    topLeft = nightOval.topLeft,
                    size = nightOval.size,
                    style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round),
                )

                val p = phase?.progress?.coerceIn(0f, 1f)
                if (phase != null && p != null) {
                    if (phase.isDay) {
                        drawArc(
                            color = dayArc,
                            startAngle = 180f,
                            sweepAngle = 180f * p,
                            useCenter = false,
                            topLeft = oval.topLeft,
                            size = oval.size,
                            style = Stroke(width = 4.dp.toPx(), cap = StrokeCap.Round),
                        )
                        val angle = Math.PI - p * Math.PI
                        val marker =
                            Offset(
                                x = cx + radius * cos(angle).toFloat(),
                                y = cy - radius * sin(angle).toFloat(),
                            )
                        drawCircle(color = dayArc.copy(alpha = 0.28f), radius = 16.dp.toPx(), center = marker)
                        drawCircle(color = dayArc, radius = 9.dp.toPx(), center = marker)
                        drawCircle(color = Color.White.copy(alpha = 0.85f), radius = 3.5.dp.toPx(), center = marker)
                    } else {
                        drawArc(
                            color = nightArc.copy(alpha = 0.75f),
                            startAngle = 0f,
                            sweepAngle = 180f * p,
                            useCenter = false,
                            topLeft = nightOval.topLeft,
                            size = nightOval.size,
                            style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round),
                        )
                        val angle = p * Math.PI
                        val marker =
                            Offset(
                                x = cx + radius * cos(angle).toFloat(),
                                y = cy + nightDepth * sin(angle).toFloat(),
                            )
                        drawCircle(color = nightArc.copy(alpha = 0.3f), radius = 14.dp.toPx(), center = marker)
                        drawCircle(color = nightArc, radius = 8.dp.toPx(), center = marker)
                        drawCircle(
                            color = Color.White.copy(alpha = 0.55f),
                            radius = 3.dp.toPx(),
                            center = marker + Offset(2.dp.toPx(), -1.5.dp.toPx()),
                        )
                    }
                }

                drawLine(
                    color = horizon,
                    start = Offset(cx - radius - 4.dp.toPx(), cy),
                    end = Offset(cx + radius + 4.dp.toPx(), cy),
                    strokeWidth = 1.5.dp.toPx(),
                )
                drawCircle(color = dayArc.copy(alpha = 0.7f), radius = 4.dp.toPx(), center = Offset(cx - radius, cy))
                drawCircle(color = dayArc.copy(alpha = 0.7f), radius = 4.dp.toPx(), center = Offset(cx + radius, cy))
            }
        }

        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(horizontalAlignment = Alignment.Start, modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.sunrise),
                    style = MaterialTheme.typography.labelMedium,
                    color = scheme.onSurfaceVariant,
                )
                Text(
                    text = sunriseLabel,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = scheme.onSurface,
                )
            }
            SunPathBearChip(
                bearRes = bearRes,
                caption = stringResource(bearCaptionRes),
            )
            Column(horizontalAlignment = Alignment.End, modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.sunset),
                    style = MaterialTheme.typography.labelMedium,
                    color = scheme.onSurfaceVariant,
                )
                Text(
                    text = sunsetLabel,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = scheme.onSurface,
                )
            }
        }
        Spacer(modifier = Modifier.height(4.dp))
    }
}

@Composable
private fun SunPathBearChip(
    @DrawableRes bearRes: Int,
    caption: String,
) {
    var enlarged by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(18.dp)
    val imageShape = RoundedCornerShape(16.dp)
    val enlargeLabel = stringResource(R.string.sun_bear_enlarge)
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier =
            Modifier
                .width(148.dp)
                .clip(shape)
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f))
                .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.18f), shape)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = ripple(),
                    role = Role.Button,
                    onClickLabel = enlargeLabel,
                    onClick = { enlarged = true },
                ).padding(horizontal = 8.dp, vertical = 8.dp),
    ) {
        val thumbPx =
            with(LocalDensity.current) {
                148
                    .dp
                    .toPx()
                    .roundToInt()
                    .coerceAtLeast(128)
            }
        val thumb = rememberSampledDrawable(bearRes, thumbPx)
        Image(
            bitmap = thumb.bitmap,
            // 相邻 Text(caption) 已朗读描述，图片置 null 避免重复
            contentDescription = null,
            contentScale = ContentScale.Fit,
            modifier =
                Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f)
                    .clip(imageShape)
                    .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.35f))
                    .border(1.5.dp, Color.White.copy(alpha = 0.55f), imageShape),
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = caption,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 2,
            textAlign = TextAlign.Center,
        )
    }
    if (enlarged) {
        SunPathBearEnlargedDialog(
            caption = caption,
            bearRes = bearRes,
            onDismiss = { enlarged = false },
        )
    }
}

@Composable
private fun SunPathBearEnlargedDialog(
    caption: String,
    @DrawableRes bearRes: Int,
    onDismiss: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val cardShape = RoundedCornerShape(28.dp)
    val imageShape = RoundedCornerShape(22.dp)
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(
            shape = cardShape,
            color = scheme.surface,
            tonalElevation = 6.dp,
            modifier =
                Modifier
                    .padding(horizontal = 28.dp)
                    .widthIn(max = 360.dp)
                    .fillMaxWidth(),
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 22.dp),
            ) {
                val enlargePx =
                    with(LocalDensity.current) {
                        360
                            .dp
                            .toPx()
                            .roundToInt()
                            .coerceAtLeast(256)
                    }
                val enlarged = rememberSampledDrawable(bearRes, enlargePx)
                Image(
                    bitmap = enlarged.bitmap,
                    contentDescription = caption,
                    contentScale = ContentScale.Fit,
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .aspectRatio(1f)
                            .clip(imageShape)
                            .border(1.5.dp, scheme.outline.copy(alpha = 0.2f), imageShape)
                            .background(scheme.surfaceVariant.copy(alpha = 0.35f)),
                )
                Spacer(modifier = Modifier.height(14.dp))
                Text(
                    text = caption,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = scheme.onSurface,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

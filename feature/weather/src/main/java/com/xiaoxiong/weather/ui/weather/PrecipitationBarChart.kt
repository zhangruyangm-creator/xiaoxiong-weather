package com.xiaoxiong.weather.ui.weather

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.xiaoxiong.weather.R
import com.xiaoxiong.weather.data.HourlyWeather
import com.xiaoxiong.weather.ui.theme.AccentCoral
import com.xiaoxiong.weather.ui.theme.SkyBlue
import com.xiaoxiong.weather.ui.theme.SoftTeal
import com.xiaoxiong.weather.util.formatHourLabel
import com.xiaoxiong.weather.util.indexOfCurrentHour
import java.util.Locale
import kotlin.math.max

private data class PrecipBar(
    val item: HourlyWeather,
    val isFuture: Boolean,
    val isNow: Boolean,
)

/**
 * 过去几小时（蓝）+ 未来几小时预测（橙）降雨量柱状图。
 */
@Composable
fun PrecipitationBarChart(
    hourly: List<HourlyWeather>,
    currentTime: String,
    modifier: Modifier = Modifier,
) {
    val bars =
        remember(hourly, currentTime) {
            buildPrecipBars(hourly, currentTime)
        }

    SoftCard(modifier = modifier.fillMaxWidth()) {
        SectionHeader(
            title = stringResource(R.string.precip_chart_title),
            subtitle = stringResource(R.string.precip_chart_sub),
            icon = Icons.Filled.WaterDrop,
        )
        Spacer(modifier = Modifier.height(8.dp))

        if (bars.isEmpty()) {
            Text(
                stringResource(R.string.precip_chart_empty),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            return@SoftCard
        }

        val maxPrecip =
            remember(bars) {
                max(bars.maxOf { it.item.precipitationMm ?: 0.0 }, 0.5)
            }
        val (pastTotal, futureTotal, pastCount, futureCount) =
            remember(bars) {
                var pastSum = 0.0
                var futureSum = 0.0
                var pastN = 0
                var futureN = 0
                for (bar in bars) {
                    val mm = bar.item.precipitationMm ?: 0.0
                    if (bar.isFuture) {
                        futureSum += mm
                        futureN++
                    } else {
                        pastSum += mm
                        pastN++
                    }
                }
                Quad(pastSum, futureSum, pastN, futureN)
            }
        val summary =
            buildString {
                append(
                    if (pastTotal <= 0.01) {
                        stringResource(R.string.precip_past_dry)
                    } else {
                        stringResource(R.string.precip_past_mm, pastTotal)
                    },
                )
                append(" · ")
                append(
                    if (futureTotal <= 0.01) {
                        stringResource(R.string.precip_future_dry)
                    } else {
                        stringResource(R.string.precip_future_mm, futureTotal)
                    },
                )
            }

        Text(
            text = summary,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.height(12.dp))

        val pastBrush =
            remember {
                Brush.verticalGradient(listOf(SoftTeal, SkyBlue))
            }
        val futureBrush =
            remember {
                Brush.verticalGradient(listOf(Color(0xFFFFB74D), AccentCoral))
            }
        val labelNow = stringResource(R.string.label_now)
        val axisColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.45f)
        val labelArgb = MaterialTheme.colorScheme.onSurfaceVariant.toArgb()
        val valueArgb = MaterialTheme.colorScheme.onSurface.toArgb()
        val nowMarkerArgb = MaterialTheme.colorScheme.error.toArgb()
        val axisLabelPaint =
            remember(labelArgb) {
                android.graphics.Paint().apply {
                    color = labelArgb
                    textSize = 26f
                    isAntiAlias = true
                }
            }
        val valuePaint =
            remember(valueArgb) {
                android.graphics.Paint().apply {
                    color = valueArgb
                    textSize = 22f
                    textAlign = android.graphics.Paint.Align.CENTER
                    isAntiAlias = true
                    isFakeBoldText = true
                }
            }
        val hourPaint =
            remember {
                android.graphics.Paint().apply {
                    textSize = 22f
                    textAlign = android.graphics.Paint.Align.CENTER
                    isAntiAlias = true
                }
            }

        val precipA11yDesc =
            stringResource(
                R.string.cd_precip_chart,
                String.format(Locale.US, "%.1f", maxPrecip),
            )
        Canvas(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .height(190.dp)
                    .semantics { contentDescription = precipA11yDesc },
        ) {
            val leftPad = 40.dp.toPx()
            val rightPad = 8.dp.toPx()
            val topPad = 18.dp.toPx()
            val bottomPad = 30.dp.toPx()
            val chartW = size.width - leftPad - rightPad
            val chartH = size.height - topPad - bottomPad
            val count = bars.size
            val gap = 6.dp.toPx()
            val barWidth = ((chartW - gap * (count - 1)) / count).coerceAtLeast(6.dp.toPx())
            val corner = CornerRadius(5.dp.toPx(), 5.dp.toPx())

            for (i in 0..3) {
                val y = topPad + chartH * i / 3f
                val value = maxPrecip * (1 - i / 3.0)
                drawLine(
                    color = axisColor,
                    start = Offset(leftPad, y),
                    end = Offset(size.width - rightPad, y),
                    strokeWidth = 1f,
                )
                drawContext.canvas.nativeCanvas.drawText(
                    String.format(Locale.US, "%.1f", value),
                    4f,
                    y + 10f,
                    axisLabelPaint,
                )
            }

            bars.forEachIndexed { index, bar ->
                val precip = (bar.item.precipitationMm ?: 0.0).coerceAtLeast(0.0)
                val h =
                    ((precip / maxPrecip) * chartH).toFloat().coerceAtLeast(
                        if (precip > 0) 4.dp.toPx() else 0f,
                    )
                val x = leftPad + index * (barWidth + gap)
                val y = topPad + chartH - h
                drawRoundRect(
                    brush = if (bar.isFuture) futureBrush else pastBrush,
                    topLeft = Offset(x, y),
                    size = Size(barWidth, h.coerceAtLeast(0f)),
                    cornerRadius = corner,
                )

                if (precip > 0.01) {
                    drawContext.canvas.nativeCanvas.drawText(
                        String.format(Locale.US, "%.1f", precip),
                        x + barWidth / 2,
                        y - 6.dp.toPx(),
                        valuePaint,
                    )
                }

                val hourLabel = if (bar.isNow) labelNow else formatHourLabel(bar.item.time)
                hourPaint.color = if (bar.isNow) nowMarkerArgb else labelArgb
                hourPaint.isFakeBoldText = bar.isNow
                drawContext.canvas.nativeCanvas.drawText(
                    hourLabel,
                    x + barWidth / 2,
                    size.height - 4.dp.toPx(),
                    hourPaint,
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            LegendSwatch(SkyBlue, stringResource(R.string.precip_legend_past))
            LegendSwatch(AccentCoral, stringResource(R.string.precip_legend_future))
            Spacer(modifier = Modifier.weight(1f))
            Text(
                text = stringResource(R.string.precip_bar_counts, pastCount, futureCount),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private data class Quad(
    val pastTotal: Double,
    val futureTotal: Double,
    val pastCount: Int,
    val futureCount: Int,
)

private fun buildPrecipBars(
    hourly: List<HourlyWeather>,
    currentTime: String,
): List<PrecipBar> {
    val currentIndex = indexOfCurrentHour(hourly.map { it.time }, currentTime)
    if (currentIndex < 0 || hourly.isEmpty()) return emptyList()

    val pastSlice = hourly.take(currentIndex + 1).takeLast(6)
    val pastStart = currentIndex - pastSlice.lastIndex
    val pastBars =
        pastSlice.mapIndexed { idx, item ->
            PrecipBar(
                item = item,
                isFuture = false,
                isNow = pastStart + idx == currentIndex,
            )
        }
    val futureBars =
        hourly
            .drop(currentIndex + 1)
            .take(8)
            .map { PrecipBar(item = it, isFuture = true, isNow = false) }
    return pastBars + futureBars
}

@Composable
private fun LegendSwatch(
    color: Color,
    label: String,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Spacer(
            modifier =
                Modifier
                    .width(12.dp)
                    .height(10.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(color),
        )
        Text(
            text = "  $label",
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Medium,
        )
    }
}

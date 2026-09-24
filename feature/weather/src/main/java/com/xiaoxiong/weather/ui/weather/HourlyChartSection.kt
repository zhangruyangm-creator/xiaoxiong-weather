package com.xiaoxiong.weather.ui.weather

import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ShowChart
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.toColorInt
import com.xiaoxiong.weather.R
import com.xiaoxiong.weather.data.HourlyWeather
import com.xiaoxiong.weather.data.TemperatureUnit
import com.xiaoxiong.weather.ui.LocalTemperatureUnit
import com.xiaoxiong.weather.ui.ReduceMotionAwareVisibility
import com.xiaoxiong.weather.util.convertCelsiusForDisplay
import com.xiaoxiong.weather.util.formatHourDetailLabel
import com.xiaoxiong.weather.util.formatHourLabel
import com.xiaoxiong.weather.util.indexOfCurrentHour
import com.xiaoxiong.weather.util.isSameHour
import java.util.Locale
import kotlin.math.abs
import kotlin.math.max

enum class HourlyMetric(
    @param:StringRes val shortLabelRes: Int,
    @param:StringRes val labelCelsiusRes: Int,
    @param:StringRes val labelFahrenheitRes: Int,
) {
    TEMPERATURE(
        R.string.metric_temp_short,
        R.string.metric_temp_c,
        R.string.metric_temp_f,
    ),
    HUMIDITY(
        R.string.metric_humidity_short,
        R.string.metric_humidity_unit,
        R.string.metric_humidity_unit,
    ),
    WIND(
        R.string.metric_wind_short,
        R.string.metric_wind_unit,
        R.string.metric_wind_unit,
    ),
    PRECIP_PROB(
        R.string.metric_precip_prob_short,
        R.string.metric_precip_prob_unit,
        R.string.metric_precip_prob_unit,
    ),
    PRECIP_MM(
        R.string.metric_precip_mm_short,
        R.string.metric_precip_mm_unit,
        R.string.metric_precip_mm_unit,
    ),
    VISIBILITY(
        R.string.metric_visibility_short,
        R.string.metric_visibility_unit,
        R.string.metric_visibility_unit,
    ),
    ;

    @StringRes
    fun labelRes(unit: TemperatureUnit): Int = if (unit == TemperatureUnit.FAHRENHEIT) labelFahrenheitRes else labelCelsiusRes
}

/**
 * 选中时刻在新小时列表中的下标：按时间重定位，滚窗/静默刷新后不漂移；
 * 该时刻已滚出窗口时返回 null（替代旧的下标越界兜底清除）。
 */
internal fun remapSelectedIndex(
    selectedTime: String?,
    times: List<String>,
): Int? = selectedTime?.let { time -> times.indexOf(time).takeIf { it >= 0 } }

private val SelectedLineColor = Color(0xFFFFC107)

// 懒初始化：避免 JVM 单测加载本文件时触发 android.graphics.Color 的 "not mocked"
private val SelectedLabelColorInt: Int by lazy { "#F9A825".toColorInt() }

@Composable
fun HourlyChartSection(
    hourly: List<HourlyWeather>,
    currentTime: String,
    /** 城市标识：换城时清空选点；静默刷新（hourly 列表变化）不打扰用户 */
    cityId: Long,
    onDragStateChange: (Boolean) -> Unit = {},
) {
    // 指标/展开态/选中时刻跨旋转保留；选中按时间而不是下标记录，
    // 整点滚窗后按时间重新定位，选中点不随时间窗口漂移
    var metric by rememberSaveable { mutableStateOf(HourlyMetric.TEMPERATURE) }
    var selectedTime by rememberSaveable { mutableStateOf<String?>(null) }
    var detailsExpanded by rememberSaveable { mutableStateOf(false) }
    // 仅在换城或切换指标时清掉选中标记：静默刷新生成的新列表不清，
    // 否则用户选中的时刻会被周期性刷新悄悄重置。
    // 已处理的键也保存，避免旋转重建时把刚恢复的选中点误清。
    var selectionResetKey by rememberSaveable { mutableStateOf("$cityId|${metric.name}") }
    LaunchedEffect(cityId, metric) {
        val key = "$cityId|${metric.name}"
        if (selectionResetKey != key) {
            selectionResetKey = key
            selectedTime = null
        }
    }
    val tempUnit = LocalTemperatureUnit.current

    val times = remember(hourly) { hourly.map { it.time } }
    val currentIndex =
        remember(times, currentTime) {
            indexOfCurrentHour(times, currentTime)
        }
    // 按时间重定位：静默刷新/滚窗后选中点不漂移；该时刻滚出窗口则为 null
    val selectedIndex = remember(selectedTime, times) { remapSelectedIndex(selectedTime, times) }
    val latestTimes by rememberUpdatedState(times)

    val values =
        remember(hourly, metric, tempUnit) {
            hourly.map { item ->
                when (metric) {
                    HourlyMetric.TEMPERATURE -> convertCelsiusForDisplay(item.temperatureC, tempUnit)
                    HourlyMetric.HUMIDITY -> item.humidityPercent?.toDouble()
                    HourlyMetric.VISIBILITY -> item.visibilityMeters?.div(1000.0)
                    HourlyMetric.PRECIP_PROB -> item.precipProbability?.toDouble()
                    HourlyMetric.PRECIP_MM -> item.precipitationMm
                    HourlyMetric.WIND -> item.windSpeedKmh
                }
            }
        }
    // 拖动时每帧都要画选中值标签：预派生字符串，避免每帧 String.format
    val valueLabels =
        remember(values, metric, tempUnit) {
            values.map { it?.let { value -> formatMetricValue(metric, value, tempUnit) } }
        }

    val showSelectedMarker =
        selectedIndex != null &&
            selectedIndex in values.indices &&
            selectedIndex != currentIndex

    // Canvas 内容对读屏不可见，提供整体摘要；逐点明细由下方小时列表承担
    val chartA11yDesc =
        run {
            val numeric = values.filterNotNull()
            if (numeric.size < 2) {
                null
            } else {
                stringResource(
                    R.string.cd_hourly_chart,
                    stringResource(metric.labelRes(tempUnit)),
                    formatMetricValue(metric, numeric.max(), tempUnit),
                    formatMetricValue(metric, numeric.min(), tempUnit),
                )
            }
        }

    val lineColor = MaterialTheme.colorScheme.primary
    val gridColor = MaterialTheme.colorScheme.outlineVariant
    val nowColor = MaterialTheme.colorScheme.error
    val labelColor = MaterialTheme.colorScheme.onSurfaceVariant
    val surfaceColor = MaterialTheme.colorScheme.surface
    val axisArgb =
        labelColor
            .copy(alpha = 0.92f)
            .let {
                android.graphics.Color.argb(
                    (it.alpha * 255).toInt(),
                    (it.red * 255).toInt(),
                    (it.green * 255).toInt(),
                    (it.blue * 255).toInt(),
                )
            }

    // 复用 Paint，避免每次 draw 新建
    val axisPaint =
        remember {
            android.graphics.Paint().apply {
                isAntiAlias = true
                textSize = 26f
            }
        }
    val valuePaint =
        remember {
            android.graphics.Paint().apply {
                isAntiAlias = true
                textSize = 28f
                isFakeBoldText = true
                textAlign = android.graphics.Paint.Align.CENTER
            }
        }

    SoftCard(modifier = Modifier.fillMaxWidth()) {
        SectionHeader(
            title = stringResource(R.string.chart_title),
            subtitle = stringResource(R.string.chart_sub),
            icon = Icons.AutoMirrored.Filled.ShowChart,
        )
        Spacer(modifier = Modifier.height(10.dp))

        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            HourlyMetric.entries.forEach { option ->
                FilterChip(
                    selected = metric == option,
                    onClick = { metric = option },
                    label = { Text(stringResource(option.shortLabelRes)) },
                    colors =
                        FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                            selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
                        ),
                )
            }
        }

        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = stringResource(metric.labelRes(tempUnit)),
            style = MaterialTheme.typography.labelMedium,
            color = labelColor,
        )
        Spacer(modifier = Modifier.height(10.dp))

        Box(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .height(230.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(
                        Brush.verticalGradient(
                            listOf(
                                lineColor.copy(alpha = 0.08f),
                                Color.Transparent,
                            ),
                        ),
                    ).semantics { chartA11yDesc?.let { contentDescription = it } }
                    .pointerInput(values.size, currentIndex) {
                        fun nearestIndex(x: Float): Int? {
                            if (values.size < 2) return null
                            val leftPad = 44.dp.toPx()
                            val rightPad = 14.dp.toPx()
                            val chartW = size.width - leftPad - rightPad
                            val clampedX = x.coerceIn(leftPad, size.width - rightPad)
                            return values.indices.minBy { i ->
                                val px = leftPad + chartW * i / (values.size - 1)
                                abs(px - clampedX)
                            }
                        }

                        fun selectAt(x: Float) {
                            val nearest = nearestIndex(x) ?: return
                            selectedTime = latestTimes.getOrNull(nearest)
                        }

                        awaitEachGesture {
                            val down = awaitFirstDown(requireUnconsumed = false)
                            selectAt(down.position.x)
                            onDragStateChange(true)
                            var lockedToAxis: Boolean? = null
                            try {
                                drag(down.id) { change ->
                                    val delta = change.positionChange()
                                    if (delta != Offset.Zero) {
                                        val axis =
                                            lockedToAxis ?: run {
                                                // 首次明显位移决定手势走向：水平→选时间，垂直→交给外层滚动
                                                val h = abs(delta.x)
                                                val v = abs(delta.y)
                                                if (h > 2f && h > v) {
                                                    true
                                                } else if (v > 2f && v > h) {
                                                    false
                                                } else {
                                                    null
                                                }
                                            }
                                        lockedToAxis = axis
                                        if (axis == true) {
                                            // 水平拖拽：继续选时间并消费位移
                                            selectAt(change.position.x)
                                            change.consume()
                                        }
                                        // 垂直拖拽：不 consume，让事件继续流向外层页面的滚动容器
                                    }
                                }
                            } finally {
                                onDragStateChange(false)
                            }
                        }
                    }.drawWithCache {
                        val numeric = values.mapNotNull { it }
                        if (numeric.isEmpty() || values.size < 2) {
                            return@drawWithCache onDrawBehind { }
                        }

                        val minV = numeric.minOrNull() ?: 0.0
                        val maxV = numeric.maxOrNull() ?: 1.0
                        val range = max(maxV - minV, 0.1)
                        val leftPad = 44.dp.toPx()
                        val rightPad = 14.dp.toPx()
                        val topPad = 30.dp.toPx()
                        val bottomPad = 30.dp.toPx()
                        val chartW = size.width - leftPad - rightPad
                        val chartH = size.height - topPad - bottomPad
                        val last = (values.size - 1).coerceAtLeast(1)

                        fun xOf(index: Int): Float = leftPad + chartW * index / last

                        fun yOf(value: Double): Float = topPad + ((maxV - value) / range * chartH).toFloat()

                        val points = ArrayList<Offset>(values.size)
                        values.forEachIndexed { index, value ->
                            if (value != null) {
                                points += Offset(xOf(index), yOf(value))
                            }
                        }

                        val linePath = Path()
                        val fillPath = Path()
                        if (points.size >= 2) {
                            buildSmoothPath(linePath, points)
                            fillPath.addPath(linePath)
                            fillPath.lineTo(points.last().x, topPad + chartH)
                            fillPath.lineTo(points.first().x, topPad + chartH)
                            fillPath.close()
                        }

                        val fillBrush =
                            Brush.verticalGradient(
                                colors =
                                    listOf(
                                        lineColor.copy(alpha = 0.32f),
                                        lineColor.copy(alpha = 0.06f),
                                        Color.Transparent,
                                    ),
                                startY = topPad,
                                endY = topPad + chartH,
                            )

                        // 注意：cache 块内只能读 currentIndex；selectedIndex 必须在
                        // onDrawBehind 里读，否则拖动每帧都会让整份缓存（Path/Brush）重建。
                        val step = max(1, values.size / 5)
                        val minLabelGapPx = 48.dp.toPx()
                        // 每帧复用，避免在 onDrawBehind 里新建 ArrayList
                        val drawnXs = FloatArray(values.size)

                        onDrawBehind {
                            // 网格
                            val yTicks = 4
                            for (i in 0..yTicks) {
                                val y = topPad + chartH * i / yTicks
                                drawLine(
                                    color = gridColor.copy(alpha = 0.35f),
                                    start = Offset(leftPad, y),
                                    end = Offset(size.width - rightPad, y),
                                    strokeWidth = 1.2f,
                                )
                                val tickValue = maxV - range * i / yTicks
                                axisPaint.color = axisArgb
                                axisPaint.textAlign = android.graphics.Paint.Align.LEFT
                                drawContext.canvas.nativeCanvas.drawText(
                                    String.format(Locale.US, "%.0f", tickValue),
                                    6f,
                                    y + 9f,
                                    axisPaint,
                                )
                            }

                            if (points.size >= 2) {
                                // 面积填充
                                drawPath(path = fillPath, brush = fillBrush)
                                // 轻微外发光
                                drawPath(
                                    path = linePath,
                                    color = lineColor.copy(alpha = 0.22f),
                                    style = Stroke(width = 12f, cap = StrokeCap.Round, join = StrokeJoin.Round),
                                )
                                // 主折线
                                drawPath(
                                    path = linePath,
                                    color = lineColor,
                                    style = Stroke(width = 4.5f, cap = StrokeCap.Round, join = StrokeJoin.Round),
                                )
                                // 稀疏数据点，降低绘制量
                                val pointStep = max(1, points.size / 10)
                                points.forEachIndexed { i, point ->
                                    if (i % pointStep != 0 && i != points.lastIndex) return@forEachIndexed
                                    drawCircle(color = lineColor.copy(alpha = 0.9f), radius = 4.5f, center = point)
                                    drawCircle(color = surfaceColor, radius = 2.2f, center = point)
                                }
                            }

                            fun drawMarker(
                                index: Int,
                                color: Color,
                                labelColorInt: Int,
                            ) {
                                val value = values[index] ?: return
                                val x = xOf(index)
                                val y = yOf(value)
                                drawLine(
                                    color = color.copy(alpha = 0.85f),
                                    start = Offset(x, topPad),
                                    end = Offset(x, topPad + chartH),
                                    strokeWidth = 2.5f,
                                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 8f)),
                                )
                                drawCircle(color = color.copy(alpha = 0.22f), radius = 16f, center = Offset(x, y))
                                drawCircle(color = color, radius = 8.5f, center = Offset(x, y))
                                drawCircle(color = Color.White, radius = 3.5f, center = Offset(x, y))

                                val label = valueLabels.getOrNull(index) ?: return
                                valuePaint.color = labelColorInt
                                val textWidth = valuePaint.measureText(label)
                                val padH = 10f
                                val padV = 8f
                                val boxW = textWidth + padH * 2
                                val boxH = 34f
                                val boxX = (x - boxW / 2).coerceIn(leftPad, size.width - rightPad - boxW)
                                val boxY = (y - 42f).coerceAtLeast(8f)
                                drawRoundRect(
                                    color = color.copy(alpha = 0.92f),
                                    topLeft = Offset(boxX, boxY),
                                    size = Size(boxW, boxH),
                                    cornerRadius = CornerRadius(10f, 10f),
                                )
                                valuePaint.color = android.graphics.Color.WHITE
                                drawContext.canvas.nativeCanvas.drawText(
                                    label,
                                    boxX + boxW / 2,
                                    boxY + boxH / 2 + 10f,
                                    valuePaint,
                                )
                            }

                            val selected = selectedIndex
                            val hasCurrent = currentIndex in values.indices
                            val hasSelected =
                                selected != null && selected in values.indices && selected != currentIndex
                            if (hasCurrent) {
                                drawMarker(currentIndex, nowColor, android.graphics.Color.RED)
                            }
                            if (hasSelected) {
                                drawMarker(selected!!, SelectedLineColor, SelectedLabelColorInt)
                            }

                            var drawnCount = 0

                            fun canDrawAt(x: Float): Boolean {
                                for (i in 0 until drawnCount) {
                                    if (abs(drawnXs[i] - x) < minLabelGapPx) return false
                                }
                                return true
                            }

                            fun drawTimeLabel(
                                index: Int,
                                color: Int,
                                bold: Boolean,
                            ) {
                                val x = xOf(index)
                                if (!canDrawAt(x)) return
                                axisPaint.color = color
                                axisPaint.isFakeBoldText = bold
                                axisPaint.textAlign = android.graphics.Paint.Align.CENTER
                                drawContext.canvas.nativeCanvas.drawText(
                                    formatHourLabel(hourly[index].time),
                                    x,
                                    size.height - 8f,
                                    axisPaint,
                                )
                                if (drawnCount < drawnXs.size) drawnXs[drawnCount++] = x
                            }

                            // 当前/选中标签优先占位，其余标签按间隔去重，避免互相压字
                            if (hasCurrent) {
                                drawTimeLabel(currentIndex, android.graphics.Color.RED, bold = true)
                            }
                            if (hasSelected) {
                                drawTimeLabel(selected!!, SelectedLabelColorInt, bold = true)
                            }
                            values.indices.forEach { index ->
                                if (index == currentIndex || (hasSelected && index == selected)) return@forEach
                                if (index % step != 0 && index != values.lastIndex) return@forEach
                                drawTimeLabel(index, axisArgb, bold = false)
                            }
                        }
                    },
        )

        Spacer(modifier = Modifier.height(8.dp))
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            LegendDot(color = nowColor)
            Text(
                stringResource(R.string.label_current),
                style = MaterialTheme.typography.labelMedium,
                color = labelColor,
            )
            if (currentIndex in values.indices) {
                Text(
                    text = valueLabels.getOrNull(currentIndex).orEmpty(),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = nowColor,
                )
            }
            val selIdx = selectedIndex.takeIf { showSelectedMarker }
            if (selIdx != null) {
                Spacer(modifier = Modifier.width(6.dp))
                LegendDot(color = SelectedLineColor)
                Text(
                    text =
                        stringResource(
                            R.string.chart_selected_at,
                            formatHourLabel(hourly[selIdx].time),
                        ),
                    style = MaterialTheme.typography.labelMedium,
                    color = labelColor,
                )
                Text(
                    text = valueLabels.getOrNull(selIdx).orEmpty(),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = SelectedLineColor,
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))
        HourlyDetailToggle(
            expanded = detailsExpanded,
            onClick = { detailsExpanded = !detailsExpanded },
        )
        ReduceMotionAwareVisibility(visible = detailsExpanded) {
            Column {
                Spacer(modifier = Modifier.height(6.dp))
                // 稳定实例：拖动折线时 CompactHourlyList 重组，各行靠稳定参数跳过
                val onSelectHour = remember { { index: Int -> selectedTime = latestTimes.getOrNull(index) } }
                CompactHourlyList(
                    hourly = hourly,
                    currentTime = currentTime,
                    metric = metric,
                    selectedIndex = selectedIndex,
                    tempUnit = tempUnit,
                    onSelect = onSelectHour,
                )
            }
        }
    }
}

/** Catmull-Rom 转三次贝塞尔，折线更顺滑 */
private fun buildSmoothPath(
    path: Path,
    points: List<Offset>,
) {
    path.reset()
    if (points.isEmpty()) return
    path.moveTo(points.first().x, points.first().y)
    if (points.size == 1) return
    for (i in 0 until points.lastIndex) {
        val p0 = points.getOrElse(i - 1) { points[i] }
        val p1 = points[i]
        val p2 = points[i + 1]
        val p3 = points.getOrElse(i + 2) { p2 }
        val c1x = p1.x + (p2.x - p0.x) / 6f
        val c1y = p1.y + (p2.y - p0.y) / 6f
        val c2x = p2.x - (p3.x - p1.x) / 6f
        val c2y = p2.y - (p3.y - p1.y) / 6f
        path.cubicTo(c1x, c1y, c2x, c2y, p2.x, p2.y)
    }
}

private fun formatMetricValue(
    metric: HourlyMetric,
    value: Double,
    tempUnit: TemperatureUnit = TemperatureUnit.CELSIUS,
): String =
    when (metric) {
        HourlyMetric.TEMPERATURE ->
            String.format(
                Locale.US,
                "%.1f%s",
                value,
                if (tempUnit == TemperatureUnit.FAHRENHEIT) "\u00B0F" else "\u00B0C",
            )
        HourlyMetric.HUMIDITY, HourlyMetric.PRECIP_PROB -> String.format(Locale.US, "%.0f%%", value)
        HourlyMetric.PRECIP_MM -> String.format(Locale.US, "%.1fmm", value)
        HourlyMetric.VISIBILITY -> String.format(Locale.US, "%.1fkm", value)
        HourlyMetric.WIND -> String.format(Locale.US, "%.0f", value)
    }

@Composable
private fun LegendDot(color: Color) {
    Box(
        modifier =
            Modifier
                .size(10.dp)
                .background(color, CircleShape),
    )
}

@Composable
private fun HourlyDetailToggle(
    expanded: Boolean,
    onClick: () -> Unit,
) {
    val toggleLabel =
        stringResource(
            if (expanded) {
                R.string.chart_hourly_detail_collapse
            } else {
                R.string.chart_hourly_detail_expand
            },
        )
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .clickable(
                    role = Role.Button,
                    onClickLabel = toggleLabel,
                    onClick = onClick,
                ).padding(horizontal = 4.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = stringResource(R.string.chart_hourly_detail),
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = toggleLabel,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Icon(
            imageVector = if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(20.dp),
        )
    }
}

@Composable
private fun CompactHourlyList(
    hourly: List<HourlyWeather>,
    currentTime: String,
    metric: HourlyMetric,
    selectedIndex: Int?,
    tempUnit: TemperatureUnit,
    onSelect: (Int) -> Unit,
) {
    Text(
        text = stringResource(R.string.chart_hourly_detail),
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.onSurface,
    )
    Spacer(modifier = Modifier.height(6.dp))
    // 不嵌套纵向滚动，避免抢走外层页面的上滑；点选仍可联动折线
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        hourly.forEachIndexed { index, item ->
            // 行独立成可跳过组合：拖动折线改 selectedIndex 时只有选中态变化的两行重组
            HourlyDetailRow(
                index = index,
                item = item,
                currentTime = currentTime,
                metric = metric,
                isSelected = selectedIndex == index,
                tempUnit = tempUnit,
                onSelect = onSelect,
            )
        }
    }
}

@Composable
private fun HourlyDetailRow(
    index: Int,
    item: HourlyWeather,
    currentTime: String,
    metric: HourlyMetric,
    isSelected: Boolean,
    tempUnit: TemperatureUnit,
    onSelect: (Int) -> Unit,
) {
    val labelNow = stringResource(R.string.label_now)
    val labelSelected = stringResource(R.string.label_selected)
    val labelToday = stringResource(R.string.label_today)
    val labelTomorrow = stringResource(R.string.label_tomorrow)
    val datePattern = stringResource(R.string.hour_detail_date)
    val unitKm = stringResource(R.string.unit_km)
    val unitMm = stringResource(R.string.unit_mm)
    val unitKmh = stringResource(R.string.unit_kmh)

    val isNow = isSameHour(item.time, currentTime)
    val showSelected = isSelected && !isNow
    val timeText =
        formatHourDetailLabel(
            isoTime = item.time,
            referenceIsoTime = currentTime,
            todayLabel = labelToday,
            tomorrowLabel = labelTomorrow,
            otherDateLabel = { month, day ->
                String.format(datePattern, month, day)
            },
        )
    val valueText =
        when (metric) {
            HourlyMetric.TEMPERATURE ->
                convertCelsiusForDisplay(item.temperatureC, tempUnit)
                    ?.let {
                        String.format(
                            Locale.US,
                            "%.1f%s",
                            it,
                            if (tempUnit == TemperatureUnit.FAHRENHEIT) "\u00B0F" else "\u00B0C",
                        )
                    } ?: "--"
            HourlyMetric.HUMIDITY -> item.humidityPercent?.let { "$it%" } ?: "--"
            HourlyMetric.VISIBILITY ->
                item.visibilityMeters?.let { String.format(Locale.US, "%.1f %s", it / 1000, unitKm) } ?: "--"
            HourlyMetric.PRECIP_PROB -> item.precipProbability?.let { "$it%" } ?: "--"
            HourlyMetric.PRECIP_MM ->
                item.precipitationMm?.let { String.format(Locale.US, "%.1f %s", it, unitMm) } ?: "--"
            HourlyMetric.WIND -> item.windSpeedKmh?.let { String.format(Locale.US, "%.0f %s", it, unitKmh) } ?: "--"
        }
    val bg =
        when {
            isNow -> MaterialTheme.colorScheme.errorContainer
            showSelected -> MaterialTheme.colorScheme.tertiaryContainer
            else -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
        }
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(bg)
                .border(
                    width = if (isNow || showSelected) 1.dp else 0.dp,
                    color =
                        when {
                            isNow -> MaterialTheme.colorScheme.error.copy(alpha = 0.35f)
                            showSelected -> SelectedLineColor.copy(alpha = 0.45f)
                            else -> Color.Transparent
                        },
                    shape = RoundedCornerShape(12.dp),
                ).clickable { onSelect(index) }
                .padding(horizontal = 12.dp, vertical = 9.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = timeText,
                fontWeight = if (isNow || showSelected) FontWeight.Bold else FontWeight.Medium,
                color =
                    when {
                        isNow -> MaterialTheme.colorScheme.onErrorContainer
                        showSelected -> MaterialTheme.colorScheme.onTertiaryContainer
                        else -> MaterialTheme.colorScheme.onSurface
                    },
            )
            when {
                isNow ->
                    Text(
                        text = "  $labelNow",
                        color = MaterialTheme.colorScheme.error,
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp,
                    )
                showSelected ->
                    Text(
                        text = "  $labelSelected",
                        color = SelectedLineColor,
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp,
                    )
            }
        }
        Text(
            text = valueText,
            fontWeight = if (isNow || showSelected) FontWeight.Bold else FontWeight.SemiBold,
            color =
                when {
                    isNow -> MaterialTheme.colorScheme.error
                    showSelected -> SelectedLineColor
                    else -> MaterialTheme.colorScheme.onSurface
                },
        )
    }
}

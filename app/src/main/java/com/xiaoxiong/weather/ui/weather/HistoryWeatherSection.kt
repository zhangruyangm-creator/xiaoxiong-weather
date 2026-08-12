package com.xiaoxiong.weather.ui.weather

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.History
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.xiaoxiong.weather.R
import com.xiaoxiong.weather.data.HistoryWeather
import com.xiaoxiong.weather.data.TemperatureUnit
import com.xiaoxiong.weather.util.formatDateLabel
import com.xiaoxiong.weather.util.formatHourLabel
import com.xiaoxiong.weather.util.formatTemperature
import com.xiaoxiong.weather.util.weatherCodeDescription
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

/**
 * 七日页底部的历史天气入口卡片：点击打开完整的历史天气页面。
 */
@Composable
internal fun HistoryWeatherSection(onOpen: () -> Unit) {
    HistoryEntryCard(onClick = onOpen)
}

@Composable
private fun HistoryEntryCard(onClick: () -> Unit) {
    SoftCard(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onClick),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier =
                    Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(
                            Brush.linearGradient(
                                listOf(
                                    MaterialTheme.colorScheme.primary.copy(alpha = 0.22f),
                                    MaterialTheme.colorScheme.secondary.copy(alpha = 0.16f),
                                ),
                            ),
                        ),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Filled.History,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.history_open),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = stringResource(R.string.history_open_sub),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Icon(
                imageVector = Icons.Filled.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.outline,
            )
        }
    }
}

/**
 * 单日历史天气卡片：大天气图标 + 日期 + 温度范围 + 平均 + 各项指标。
 * 供历史天气页面的单日查询复用。
 */
@Composable
internal fun HistoryDayCard(
    weather: HistoryWeather,
    tempUnit: TemperatureUnit,
) {
    val isDay = true
    SoftCard(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier =
                    Modifier
                        .size(64.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.18f)),
                contentAlignment = Alignment.Center,
            ) {
                WeatherIcon(
                    code = weather.weatherCode,
                    isDay = isDay,
                    modifier = Modifier.size(44.dp),
                )
            }
            Spacer(modifier = Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = formatDateLabel(weather.date),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = weatherCodeDescription(weather.weatherCode),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text =
                        stringResource(
                            R.string.history_temp_range,
                            formatTemperature(weather.tempMaxC, tempUnit),
                            formatTemperature(weather.tempMinC, tempUnit),
                        ),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                weather.tempMeanC?.let {
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text =
                            stringResource(
                                R.string.history_temp_mean,
                                formatTemperature(it, tempUnit),
                            ),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        val precip =
            weather.precipSumMm?.let { "%.1f mm".format(it) } ?: "--"
        val humidity = weather.humidityMeanPercent?.let { "$it%" } ?: "--"
        val wind = weather.windMaxKmh?.let { "%.0f km/h".format(it) } ?: "--"
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            HistoryMetaChip(
                label = stringResource(R.string.history_meta_precip, precip),
                modifier = Modifier.weight(1f),
            )
            HistoryMetaChip(
                label = stringResource(R.string.history_meta_humidity, humidity),
                modifier = Modifier.weight(1f),
            )
            HistoryMetaChip(
                label = stringResource(R.string.history_meta_wind, wind),
                modifier = Modifier.weight(1f),
            )
        }

        val sunrise = weather.sunrise?.let { formatHourLabel(it) } ?: "--"
        val sunset = weather.sunset?.let { formatHourLabel(it) } ?: "--"
        Spacer(modifier = Modifier.height(8.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            HistoryMetaChip(
                label = stringResource(R.string.history_sunrise, sunrise),
                modifier = Modifier.weight(1f),
            )
            HistoryMetaChip(
                label = stringResource(R.string.history_sunset, sunset),
                modifier = Modifier.weight(1f),
            )
        }

        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = stringResource(R.string.history_attribution),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.outline,
        )
    }
}

@Composable
private fun HistoryMetaChip(
    label: String,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier =
            modifier
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                .padding(horizontal = 8.dp, vertical = 7.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
        )
    }
}

/**
 * 历史日期选择：范围 [1940-01-01, 昨天]（ERA5 数据延迟约 1 天）。
 * 供历史天气页面的单日查询复用。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun HistoryDatePickerDialog(
    onConfirm: (LocalDate) -> Unit,
    onDismiss: () -> Unit,
) {
    val today = remember { LocalDate.now() }
    val minUtc =
        remember {
            LocalDate
                .of(1940, 1, 1)
                .atStartOfDay(ZoneOffset.UTC)
                .toInstant()
                .toEpochMilli()
        }
    val maxUtc =
        remember {
            today
                .minusDays(1)
                .atStartOfDay(ZoneOffset.UTC)
                .toInstant()
                .toEpochMilli()
        }
    val selectableDates =
        remember {
            object : SelectableDates {
                override fun isSelectableDate(utcTimeMillis: Long): Boolean = utcTimeMillis in minUtc..maxUtc

                override fun isSelectableYear(year: Int): Boolean = year in 1940..today.year
            }
        }
    val state = rememberDatePickerState(selectableDates = selectableDates)
    val selected =
        state.selectedDateMillis?.let { millis ->
            Instant
                .ofEpochMilli(millis)
                .atZone(ZoneOffset.UTC)
                .toLocalDate()
        }

    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                onClick = { selected?.let(onConfirm) },
                enabled = selected != null,
            ) {
                Text(stringResource(R.string.history_pick_confirm))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.history_cancel))
            }
        },
    ) {
        Column {
            // 标题放在对话框顶部、独立排版，避免 DatePicker 内置标题区
            // 在字体缩放/窄屏下被裁剪（material3 已知问题），换行也能完整显示
            Text(
                text = stringResource(R.string.history_pick_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(start = 24.dp, top = 20.dp, end = 24.dp, bottom = 8.dp),
            )
            DatePicker(
                state = state,
                title = null,
                modifier =
                    Modifier
                        .verticalScroll(rememberScrollState()),
            )
        }
    }
}

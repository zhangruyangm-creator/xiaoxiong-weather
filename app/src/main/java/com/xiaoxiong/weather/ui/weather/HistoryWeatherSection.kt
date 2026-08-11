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
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import com.xiaoxiong.weather.ui.LocalTemperatureUnit
import com.xiaoxiong.weather.util.formatDateLabel
import com.xiaoxiong.weather.util.formatHourLabel
import com.xiaoxiong.weather.util.formatTemperature
import com.xiaoxiong.weather.util.weatherCodeDescription
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

/**
 * 七日页底部的历史天气入口与结果展示。
 * 入口卡片点击弹出日历（可选范围 1940 至昨天），选中后回调 [onPickDate] 查询；
 * 结果以 [weather] / [isLoading] / [failed] 三种状态展示。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun HistoryWeatherSection(
    weather: HistoryWeather?,
    isLoading: Boolean,
    failed: Boolean,
    onPickDate: (LocalDate) -> Unit,
) {
    var showPicker by remember { mutableStateOf(false) }
    val tempUnit = LocalTemperatureUnit.current

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (showPicker) {
            HistoryDatePickerDialog(
                onConfirm = { date ->
                    showPicker = false
                    onPickDate(date)
                },
                onDismiss = { showPicker = false },
            )
        }

        HistoryEntryCard(onClick = { showPicker = true })

        when {
            isLoading -> {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(22.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = stringResource(R.string.history_loading),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            weather != null -> HistoryDayCard(weather = weather, tempUnit = tempUnit)

            failed -> {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = stringResource(R.string.history_failed),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    TextButton(onClick = { showPicker = true }) {
                        Text(stringResource(R.string.history_retry))
                    }
                }
            }
        }
    }
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
 */
@Composable
private fun HistoryDayCard(
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
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HistoryDatePickerDialog(
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
        DatePicker(
            state = state,
            title = {
                Text(
                    text = stringResource(R.string.history_pick_title),
                    style = MaterialTheme.typography.titleMedium,
                )
            },
            modifier =
                Modifier
                    .verticalScroll(rememberScrollState()),
        )
    }
}

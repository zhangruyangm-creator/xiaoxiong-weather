package com.xiaoxiong.weather.ui.weather

import androidx.activity.compose.BackHandler
import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AcUnit
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.xiaoxiong.weather.R
import com.xiaoxiong.weather.data.HistoryExtreme
import com.xiaoxiong.weather.data.HistoryMonthSummary
import com.xiaoxiong.weather.data.HistoryRangeSummary
import com.xiaoxiong.weather.data.HistoryWeather
import com.xiaoxiong.weather.data.TemperatureUnit
import com.xiaoxiong.weather.util.formatDateLabel
import com.xiaoxiong.weather.util.formatTemperature
import com.xiaoxiong.weather.util.weatherCodeDescription
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import java.util.Locale

private enum class HistoryQueryMode { DAY, RANGE }

/**
 * 历史天气页面：
 * - 单日查询：日历选一天，看当天完整天气；
 * - 区间统计：选起止日期，看区间内最高温/最低温/最多降水及其出现日期，附逐日明细。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryWeatherScreen(
    temperatureUnit: TemperatureUnit,
    historyWeather: HistoryWeather?,
    isHistoryLoading: Boolean,
    historyFailed: Boolean,
    historyNetworkFailed: Boolean,
    onPickDate: (LocalDate) -> Unit,
    historyRange: HistoryRangeSummary?,
    isHistoryRangeLoading: Boolean,
    historyRangeFailed: Boolean,
    historyRangeNetworkFailed: Boolean,
    onFetchRange: (LocalDate, LocalDate) -> Unit,
    onBack: () -> Unit,
) {
    // 页面级 UI 状态跨进程重建保留：枚举存 name，日期均以 ISO 字符串保存
    var modeName by rememberSaveable { mutableStateOf(HistoryQueryMode.DAY.name) }
    val mode = HistoryQueryMode.entries.firstOrNull { it.name == modeName } ?: HistoryQueryMode.DAY
    var pickedDay by rememberSaveable { mutableStateOf<String?>(null) }
    var rangeStart by rememberSaveable { mutableStateOf<String?>(null) }
    var rangeEnd by rememberSaveable { mutableStateOf<String?>(null) }
    // 查询键提升到页面层：切换单日/区间模式时 RangeQueryContent 会销毁重建，
    // 放在内部会丢失，导致切回区间模式后已有结果显示为空
    var rangeQueriedKey by rememberSaveable { mutableStateOf<String?>(null) }
    var showDayPicker by remember { mutableStateOf(false) }
    var showStartPicker by remember { mutableStateOf(false) }
    var showEndPicker by remember { mutableStateOf(false) }

    fun confirmDay(date: LocalDate) {
        pickedDay = date.toString()
        showDayPicker = false
        onPickDate(date)
    }

    fun confirmStart(date: LocalDate) {
        rangeStart = date.toString()
        showStartPicker = false
    }

    fun confirmEnd(date: LocalDate) {
        rangeEnd = date.toString()
        showEndPicker = false
    }

    // 进程重建后 rememberSaveable 恢复了已选日期/查询键，但 ViewModel 是全新的（无数据/无 loading）：
    // 仅在「有键、无数据、非 loading、非 failed」时自动补一次查询，避免界面停在「未查询」态。
    // 自动补查是「首次组合一次性」的：之后切 Tab / 换城清空数据都不再静默重发请求。
    val restoreReplay = remember { HistoryRestoreReplayOnce() }
    LaunchedEffect(mode) {
        if (!restoreReplay.tryConsume()) return@LaunchedEffect
        when (mode) {
            HistoryQueryMode.DAY ->
                pendingHistoryDayRestore(
                    pickedDay = pickedDay,
                    hasWeather = historyWeather != null,
                    isLoading = isHistoryLoading,
                    failed = historyFailed,
                    networkFailed = historyNetworkFailed,
                )?.let(onPickDate)
            HistoryQueryMode.RANGE ->
                pendingHistoryRangeRestore(
                    start = rangeStart,
                    end = rangeEnd,
                    queriedKey = rangeQueriedKey,
                    hasSummary = historyRange != null,
                    isLoading = isHistoryRangeLoading,
                    failed = historyRangeFailed,
                    networkFailed = historyRangeNetworkFailed,
                )?.let { (start, end) -> onFetchRange(start, end) }
        }
    }

    BackHandler(onBack = onBack)

    AppScreenBackground(modifier = Modifier.fillMaxSize()) {
        Scaffold(
            containerColor = Color.Transparent,
            topBar = {
                TopAppBar(
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
                    title = {
                        Text(
                            text = stringResource(R.string.history_title),
                            fontWeight = FontWeight.Bold,
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = stringResource(R.string.cd_back),
                            )
                        }
                    },
                )
            },
        ) { padding ->
            Column(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .padding(padding),
            ) {
                SingleChoiceSegmentedButtonRow(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 4.dp),
                ) {
                    SegmentedButton(
                        selected = mode == HistoryQueryMode.DAY,
                        onClick = { modeName = HistoryQueryMode.DAY.name },
                        shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
                    ) {
                        Text(stringResource(R.string.history_mode_day))
                    }
                    SegmentedButton(
                        selected = mode == HistoryQueryMode.RANGE,
                        onClick = { modeName = HistoryQueryMode.RANGE.name },
                        shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
                    ) {
                        Text(stringResource(R.string.history_mode_range))
                    }
                }

                if (mode == HistoryQueryMode.DAY) {
                    DayQueryContent(
                        temperatureUnit = temperatureUnit,
                        pickedDay = pickedDay,
                        weather = historyWeather,
                        isLoading = isHistoryLoading,
                        failed = historyFailed,
                        networkFailed = historyNetworkFailed,
                        onPick = { showDayPicker = true },
                        onRetryDate = onPickDate,
                    )
                } else {
                    RangeQueryContent(
                        temperatureUnit = temperatureUnit,
                        start = rangeStart,
                        end = rangeEnd,
                        summary = historyRange,
                        isLoading = isHistoryRangeLoading,
                        failed = historyRangeFailed,
                        networkFailed = historyRangeNetworkFailed,
                        queriedKey = rangeQueriedKey,
                        onQueriedKeyChange = { rangeQueriedKey = it },
                        onPickStart = { showStartPicker = true },
                        onPickEnd = { showEndPicker = true },
                        onQuery = onFetchRange,
                    )
                }
            }
        }
    }

    if (showDayPicker) {
        HistoryDatePickerDialog(
            onConfirm = ::confirmDay,
            onDismiss = { showDayPicker = false },
        )
    }
    if (showStartPicker) {
        HistoryDatePickerDialog(
            onConfirm = ::confirmStart,
            onDismiss = { showStartPicker = false },
        )
    }
    if (showEndPicker) {
        HistoryDatePickerDialog(
            onConfirm = ::confirmEnd,
            onDismiss = { showEndPicker = false },
        )
    }
}

@Composable
private fun DayQueryContent(
    temperatureUnit: TemperatureUnit,
    pickedDay: String?,
    weather: HistoryWeather?,
    isLoading: Boolean,
    failed: Boolean,
    networkFailed: Boolean,
    onPick: () -> Unit,
    onRetryDate: (LocalDate) -> Unit,
) {
    val pickedDate =
        remember(pickedDay) {
            pickedDay?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
        }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            DateSelectButton(
                label = stringResource(R.string.history_pick_day),
                selectedDate = pickedDay,
                onClick = onPick,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        if (isLoading) {
            item { LoadingRow() }
        } else if (failed || networkFailed) {
            item {
                FailedCard(
                    message =
                        stringResource(
                            if (networkFailed) {
                                R.string.history_network_failed
                            } else {
                                R.string.history_failed
                            },
                        ),
                    // 已选过日期时直接按原日期重试，不再重开日历
                    retryLabelRes =
                        if (pickedDate != null) {
                            R.string.label_retry
                        } else {
                            R.string.history_retry
                        },
                    onRetry = { pickedDate?.let(onRetryDate) ?: onPick() },
                )
            }
        } else if (weather != null) {
            item {
                HistoryDayCard(weather = weather, tempUnit = temperatureUnit)
            }
        } else if (pickedDate == null) {
            // 未查询：明确引导先选日期，而不是误报「无数据」
            item { StatusRow(text = stringResource(R.string.history_idle_day)) }
        } else {
            item { StatusRow(text = stringResource(R.string.history_empty_day)) }
        }
    }
}

@Composable
private fun RangeQueryContent(
    temperatureUnit: TemperatureUnit,
    start: String?,
    end: String?,
    summary: HistoryRangeSummary?,
    isLoading: Boolean,
    failed: Boolean,
    networkFailed: Boolean,
    queriedKey: String?,
    onQueriedKeyChange: (String?) -> Unit,
    onPickStart: () -> Unit,
    onPickEnd: () -> Unit,
    onQuery: (LocalDate, LocalDate) -> Unit,
) {
    val startDate = start?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
    val endDate = end?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
    val rangeDays =
        remember(startDate, endDate) {
            if (startDate != null && endDate != null && !endDate.isBefore(startDate)) {
                ChronoUnit.DAYS.between(startDate, endDate) + 1
            } else {
                null
            }
        }
    val tooLong = rangeDays != null && rangeDays > MAX_RANGE_DAYS
    val canQuery = rangeDays != null && !tooLong
    val currentKey = if (startDate != null && endDate != null) "$startDate|$endDate" else null
    // 当前所选区间是否已查询：区分「未查询」与「查过但无数据/失败」
    val queried = currentKey != null && currentKey == queriedKey
    // 仅展示「与当前所选区间一致」的查询结果，避免改日期后残留旧区间统计
    val showResult = summary != null && queried
    // 长区间按月聚合展示，短区间保留逐日明细
    val useMonthly = rangeDays != null && rangeDays > RANGE_DAILY_DETAIL_MAX_DAYS
    // 当前展开的月份（"yyyy-MM"）；点击行展开当月明细，再点收回
    var expandedMonth by rememberSaveable { mutableStateOf<String?>(null) }
    // 按月展开时避免每次重组都 O(days) 过滤
    val daysByMonth =
        remember(summary) {
            historyDaysByMonth(summary?.days.orEmpty())
        }

    fun runQuery() {
        if (canQuery) {
            onQueriedKeyChange(currentKey)
            expandedMonth = null
            startDate?.let { s -> endDate?.let { e -> onQuery(s, e) } }
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                DateSelectButton(
                    label = stringResource(R.string.history_pick_start),
                    selectedDate = start,
                    onClick = onPickStart,
                    modifier = Modifier.weight(1f),
                )
                DateSelectButton(
                    label = stringResource(R.string.history_pick_end),
                    selectedDate = end,
                    onClick = onPickEnd,
                    modifier = Modifier.weight(1f),
                )
            }
        }
        item {
            Button(
                onClick = ::runQuery,
                enabled = canQuery,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.history_query))
            }
        }
        if (tooLong) {
            item {
                StatusRow(
                    text = stringResource(R.string.history_range_limit, MAX_RANGE_DAYS),
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }
        if (isLoading) {
            item { LoadingRow() }
        } else if (queried && (failed || networkFailed)) {
            item {
                FailedCard(
                    message =
                        stringResource(
                            if (networkFailed) {
                                R.string.history_range_network_failed
                            } else {
                                R.string.history_range_failed
                            },
                        ),
                    onRetry = ::runQuery,
                )
            }
        } else if (showResult) {
            item {
                Text(
                    text =
                        stringResource(
                            R.string.history_range_summary,
                            formatDateLabel(summary.startDate),
                            formatDateLabel(summary.endDate),
                            summary.days.size,
                        ),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            item {
                ExtremeStatCard(
                    title = stringResource(R.string.history_range_max_temp),
                    icon = Icons.Filled.LocalFireDepartment,
                    extreme = summary.hottest,
                    valueOf = { formatTemperature(it, temperatureUnit) },
                    accent = Color(0xFFE57373),
                )
            }
            item {
                ExtremeStatCard(
                    title = stringResource(R.string.history_range_min_temp),
                    icon = Icons.Filled.AcUnit,
                    extreme = summary.coldest,
                    valueOf = { formatTemperature(it, temperatureUnit) },
                    accent = Color(0xFF64B5F6),
                )
            }
            item {
                ExtremeStatCard(
                    title = stringResource(R.string.history_range_max_precip),
                    icon = Icons.Filled.WaterDrop,
                    extreme = summary.wettest,
                    valueOf = { String.format(Locale.US, "%.1f mm", it) },
                    accent = Color(0xFF4FC3F7),
                )
            }
            if (useMonthly && summary.months.isNotEmpty()) {
                item {
                    Row(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .padding(top = 4.dp, bottom = 2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = stringResource(R.string.history_range_rows_title_monthly),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.weight(1f),
                        )
                        Text(
                            text = stringResource(R.string.history_rows_col_min),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.outline,
                            modifier = Modifier.width(44.dp),
                            textAlign = TextAlign.End,
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = stringResource(R.string.history_rows_col_max),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.outline,
                            modifier = Modifier.width(44.dp),
                            textAlign = TextAlign.End,
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = stringResource(R.string.history_rows_col_precip),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.outline,
                            modifier = Modifier.width(46.dp),
                            textAlign = TextAlign.End,
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = stringResource(R.string.history_rows_col_rain_days),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.outline,
                            modifier = Modifier.width(40.dp),
                            textAlign = TextAlign.End,
                        )
                    }
                }
                items(items = summary.months, key = { monthKey(it.year, it.month) }) { month ->
                    val key = monthKey(month.year, month.month)
                    val expanded = expandedMonth == key
                    HistoryMonthRow(
                        month = month,
                        tempUnit = temperatureUnit,
                        expanded = expanded,
                        onClick = { expandedMonth = if (expanded) null else key },
                    )
                    if (expanded) {
                        // groupBy 结果已按月份键缓存，展开时不再逐日 startsWith 过滤
                        daysByMonth[key].orEmpty().forEach { day ->
                            HistoryDayRow(day = day, tempUnit = temperatureUnit)
                            HorizontalDivider(
                                color = MaterialTheme.colorScheme.outline.copy(alpha = 0.12f),
                            )
                        }
                    }
                }
            } else if (summary.days.isNotEmpty()) {
                item {
                    Row(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .padding(top = 4.dp, bottom = 2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = stringResource(R.string.history_range_rows_title),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.weight(1f),
                        )
                        Text(
                            text = stringResource(R.string.history_rows_col_min),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.outline,
                            modifier = Modifier.width(44.dp),
                            textAlign = TextAlign.End,
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = stringResource(R.string.history_rows_col_max),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.outline,
                            modifier = Modifier.width(44.dp),
                            textAlign = TextAlign.End,
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = stringResource(R.string.history_rows_col_precip),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.outline,
                            modifier = Modifier.width(46.dp),
                            textAlign = TextAlign.End,
                        )
                    }
                }
                items(items = summary.days, key = { it.date }) { day ->
                    HistoryDayRow(day = day, tempUnit = temperatureUnit)
                    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.12f))
                }
            }
        } else if (!queried) {
            // 未查询：引导用户完成查询，而不是误报「区间无数据」
            item { StatusRow(text = stringResource(R.string.history_idle_range)) }
        } else {
            item { StatusRow(text = stringResource(R.string.history_empty_range)) }
        }
    }
}

@Composable
private fun DateSelectButton(
    label: String,
    selectedDate: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    OutlinedButton(onClick = onClick, modifier = modifier) {
        Icon(
            imageVector = Icons.Filled.CalendarMonth,
            contentDescription = null,
            modifier = Modifier.size(18.dp),
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(text = selectedDate ?: label, maxLines = 1)
    }
}

@Composable
private fun StatusRow(
    text: String,
    color: Color = MaterialTheme.colorScheme.onSurfaceVariant,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = color,
        )
    }
}

@Composable
private fun LoadingRow() {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp),
        horizontalArrangement = Arrangement.Center,
    ) {
        CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
    }
}

@Composable
private fun FailedCard(
    message: String,
    onRetry: () -> Unit,
    @StringRes retryLabelRes: Int = R.string.history_retry,
) {
    SoftCard(modifier = Modifier.fillMaxWidth()) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center,
            )
            Spacer(modifier = Modifier.height(10.dp))
            TextButton(onClick = onRetry) {
                Text(stringResource(retryLabelRes))
            }
        }
    }
}

@Composable
private fun ExtremeStatCard(
    title: String,
    icon: ImageVector,
    extreme: HistoryExtreme?,
    valueOf: (Double) -> String,
    accent: Color,
) {
    SoftCard(modifier = Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier =
                    Modifier
                        .size(42.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(accent.copy(alpha = 0.9f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(22.dp),
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (extreme != null) {
                    Text(
                        text = valueOf(extreme.value),
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        text =
                            stringResource(
                                R.string.history_range_extreme_date,
                                formatDateLabel(extreme.date),
                            ),
                        style = MaterialTheme.typography.labelMedium,
                        color = accent,
                    )
                }
            }
        }
    }
}

@Composable
private fun HistoryDayRow(
    day: HistoryWeather,
    tempUnit: TemperatureUnit,
) {
    val precip = day.precipSumMm?.let { String.format(Locale.US, "%.1f", it) } ?: "--"
    val iconDescription = weatherCodeDescription(day.weatherCode)
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = formatDateLabel(day.date),
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
        )
        WeatherIcon(
            code = day.weatherCode,
            isDay = true,
            modifier =
                Modifier
                    .size(22.dp)
                    .semantics { contentDescription = iconDescription },
        )
        Spacer(modifier = Modifier.width(10.dp))
        Text(
            text = formatTemperature(day.tempMinC, tempUnit),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(44.dp),
            textAlign = TextAlign.End,
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = formatTemperature(day.tempMaxC, tempUnit),
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.width(44.dp),
            textAlign = TextAlign.End,
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = precip,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(46.dp),
            textAlign = TextAlign.End,
        )
    }
}

@Composable
private fun HistoryMonthRow(
    month: HistoryMonthSummary,
    tempUnit: TemperatureUnit,
    expanded: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = stringResource(R.string.history_month_label, month.year, month.month),
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = month.lowestTempC?.let { formatTemperature(it, tempUnit) } ?: "--",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(44.dp),
            textAlign = TextAlign.End,
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = month.highestTempC?.let { formatTemperature(it, tempUnit) } ?: "--",
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.width(44.dp),
            textAlign = TextAlign.End,
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = month.totalPrecipMm?.let { String.format(Locale.US, "%.1f", it) } ?: "--",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(46.dp),
            textAlign = TextAlign.End,
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = stringResource(R.string.history_days_count, month.precipDays),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(40.dp),
            textAlign = TextAlign.End,
        )
        Icon(
            imageVector = if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
            contentDescription =
                stringResource(if (expanded) R.string.cd_collapse else R.string.cd_expand),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(20.dp),
        )
    }
}

private const val MAX_RANGE_DAYS = 3660L

/** 短区间上限：超过后改为按月聚合展示（逐日明细折叠进月份里）。 */
private const val RANGE_DAILY_DETAIL_MAX_DAYS = 62L

/** 月份 key 统一补零（"2026-08"），与 HistoryWeather.date 的 ISO 前缀匹配。 */
internal fun monthKey(
    year: Int,
    month: Int,
): String = String.format(Locale.US, "%d-%02d", year, month)

/** 逐日明细按月份（ISO 日期的前 7 位）分组，供按月展开复用，避免每次重组过滤。 */
internal fun historyDaysByMonth(days: List<HistoryWeather>): Map<String, List<HistoryWeather>> = days.groupBy { it.date.take(7) }

/**
 * 自动补查一次性闸门：进程恢复后的首次组合消费一次，之后切 Tab / 换城清空数据
 * 都不会再触发静默补查（旧日期在新城市上自动查询既无必要也容易打扰）。
 */
internal class HistoryRestoreReplayOnce {
    private var consumed = false

    /** 首次调用返回 true 并置位；后续调用一律返回 false。 */
    fun tryConsume(): Boolean {
        if (consumed) return false
        consumed = true
        return true
    }
}

/**
 * 进程重建后是否要自动补查单日：有已选日期、当前无数据、非加载中且未失败时返回该日期。
 * 失败（无数据/网络错误）不自动重试，仍交由用户点重试，避免错误循环。
 */
internal fun pendingHistoryDayRestore(
    pickedDay: String?,
    hasWeather: Boolean,
    isLoading: Boolean,
    failed: Boolean,
    networkFailed: Boolean,
): LocalDate? {
    if (hasWeather || isLoading || failed || networkFailed) return null
    return pickedDay?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
}

/**
 * 进程重建后是否要自动补查区间：所选区间与已查询键一致、当前无数据、非加载中且未失败时返回区间。
 */
internal fun pendingHistoryRangeRestore(
    start: String?,
    end: String?,
    queriedKey: String?,
    hasSummary: Boolean,
    isLoading: Boolean,
    failed: Boolean,
    networkFailed: Boolean,
): Pair<LocalDate, LocalDate>? {
    if (hasSummary || isLoading || failed || networkFailed) return null
    val startDate = start?.let { runCatching { LocalDate.parse(it) }.getOrNull() } ?: return null
    val endDate = end?.let { runCatching { LocalDate.parse(it) }.getOrNull() } ?: return null
    if (queriedKey != "$startDate|$endDate") return null
    return startDate to endDate
}

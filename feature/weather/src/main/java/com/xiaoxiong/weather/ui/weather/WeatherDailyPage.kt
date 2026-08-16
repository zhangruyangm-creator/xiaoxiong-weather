package com.xiaoxiong.weather.ui.weather

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.xiaoxiong.weather.R
import com.xiaoxiong.weather.data.DailyCardId
import com.xiaoxiong.weather.data.DailyCardLayout
import com.xiaoxiong.weather.data.DailyCardPref
import com.xiaoxiong.weather.data.DailyWeather
import com.xiaoxiong.weather.data.TemperatureUnit
import com.xiaoxiong.weather.data.WeatherSnapshot
import com.xiaoxiong.weather.ui.LocalTemperatureUnit
import com.xiaoxiong.weather.util.formatDateLabel
import com.xiaoxiong.weather.util.formatHourLabel
import com.xiaoxiong.weather.util.formatTemperature
import com.xiaoxiong.weather.util.weatherCodeDescription

@Composable
fun DailyPage(
    weather: WeatherSnapshot?,
    isOffline: Boolean,
    lastUpdatedText: String?,
    phenologyShiftDays: Int? = null,
    onPhenologyPageShown: () -> Unit = {},
    onOpenHistory: () -> Unit = {},
    cardLayout: List<DailyCardPref> = DailyCardLayout.defaults,
) {
    val layout = remember(cardLayout) { DailyCardLayout.mergeWithDefaults(cardLayout) }
    val todayLabel = stringResource(R.string.label_today)
    val tempUnit = LocalTemperatureUnit.current
    val appDark = MaterialTheme.colorScheme.surface.luminance() < 0.5f
    var expandedDate by remember(weather?.city?.id, weather?.daily?.firstOrNull()?.date) {
        mutableStateOf<String?>(null)
    }
    val weekBounds =
        remember(weather?.daily) {
            weather?.daily?.let { weekTemperatureBounds(it) }
        }

    // 进入七日页（或页面内换城）时才按需补齐物候偏移，见 WeatherViewModel.ensurePhenologyShift
    LaunchedEffect(weather?.city) {
        if (weather != null) onPhenologyPageShown()
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (weather == null) {
            item { EmptyHint(stringResource(R.string.daily_empty)) }
            return@LazyColumn
        }
        if (isOffline) {
            item { OfflineBanner(lastUpdatedText) }
        }
        item {
            SectionHeader(
                title = stringResource(R.string.daily_section_title),
                subtitle = stringResource(R.string.daily_section_sub),
                icon = Icons.Filled.CalendarMonth,
            )
        }
        val visibleCards = layout.filter { it.visible }
        visibleCards.forEach { pref ->
            when (pref.id) {
                DailyCardId.FORECAST ->
                    itemsIndexed(
                        items = weather.daily,
                        key = { _, day -> day.date },
                    ) { index, day ->
                        DailyDayCard(
                            day = day,
                            isToday = index == 0,
                            todayLabel = todayLabel,
                            tempUnit = tempUnit,
                            appDark = appDark,
                            weekMinC = weekBounds?.first,
                            weekMaxC = weekBounds?.second,
                            expanded = expandedDate == day.date,
                            onToggleExpand = {
                                expandedDate = if (expandedDate == day.date) null else day.date
                            },
                        )
                    }
                DailyCardId.SEASON_TIPS ->
                    item {
                        SeasonTipsSection(
                            city = weather.city,
                            referenceIsoTime = weather.current.time,
                            elevationMeters = weather.elevationMeters,
                            phenologyShiftDays = phenologyShiftDays,
                        )
                    }
                DailyCardId.HISTORY ->
                    item {
                        HistoryWeatherSection(onOpen = onOpenHistory)
                    }
            }
        }
        item { Spacer(modifier = Modifier.height(4.dp)) }
    }
}

@Composable
private fun DailyDayCard(
    day: DailyWeather,
    isToday: Boolean,
    todayLabel: String,
    tempUnit: TemperatureUnit,
    appDark: Boolean,
    weekMinC: Double?,
    weekMaxC: Double?,
    expanded: Boolean,
    onToggleExpand: () -> Unit,
) {
    val style =
        remember(day.weatherCode, appDark) {
            weatherSceneStyle(day.weatherCode, isDay = true, appDark = appDark)
        }
    val cardBrush =
        remember(style.gradient) {
            Brush.linearGradient(style.gradient)
        }
    val accentBrush =
        remember(style.gradient) {
            Brush.horizontalGradient(
                listOf(
                    style.gradient.first().copy(alpha = 0.95f),
                    style.gradient.last().copy(alpha = 0.75f),
                ),
            )
        }
    val trackColor = style.contentColor.copy(alpha = 0.22f)
    val shape = RoundedCornerShape(if (isToday) 24.dp else 20.dp)
    val expandLabel =
        stringResource(
            if (expanded) R.string.daily_collapse else R.string.daily_expand,
        )

    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .shadow(
                    elevation = if (isToday) 10.dp else 5.dp,
                    shape = shape,
                    clip = false,
                    ambientColor = style.gradient.first().copy(alpha = 0.35f),
                    spotColor = style.gradient.last().copy(alpha = 0.4f),
                ).clip(shape)
                .background(cardBrush)
                .border(
                    width = if (isToday) 1.5.dp else 1.dp,
                    color = Color.White.copy(alpha = if (isToday) 0.42f else 0.22f),
                    shape = shape,
                ).clickable(
                    role = Role.Button,
                    onClickLabel = expandLabel,
                    onClick = onToggleExpand,
                ).padding(horizontal = 14.dp, vertical = 14.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = if (isToday) todayLabel else formatDateLabel(day.date),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = style.contentColor,
                    )
                    if (isToday) {
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = formatDateLabel(day.date),
                            style = MaterialTheme.typography.labelMedium,
                            color = style.mutedContentColor,
                        )
                    }
                }
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = weatherCodeDescription(day.weatherCode),
                    style = MaterialTheme.typography.bodyMedium,
                    color = style.mutedContentColor,
                )
            }

            Box(
                modifier =
                    Modifier
                        .size(56.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.18f))
                        .border(1.dp, Color.White.copy(alpha = 0.28f), CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                WeatherIcon(
                    code = day.weatherCode,
                    isDay = true,
                    modifier = Modifier.size(34.dp),
                )
            }
            Spacer(modifier = Modifier.width(4.dp))
            Icon(
                imageVector = if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                contentDescription = expandLabel,
                tint = style.contentColor.copy(alpha = 0.9f),
                modifier = Modifier.size(22.dp),
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                text = formatTemperature(day.tempMinC, tempUnit),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = style.mutedContentColor,
            )
            DailyTempRangeBar(
                minC = day.tempMinC,
                maxC = day.tempMaxC,
                weekMinC = weekMinC,
                weekMaxC = weekMaxC,
                trackColor = trackColor,
                fillBrush = accentBrush,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = formatTemperature(day.tempMaxC, tempUnit),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = style.contentColor,
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        val precip = day.precipSumMm?.let { "%.1fmm".format(it) } ?: "--"
        val prob = day.precipProbabilityMax?.let { "$it%" } ?: "--"
        val uv = day.uvIndexMax?.let { "%.1f".format(it) } ?: "--"
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            DailyMetaChip(
                label = stringResource(R.string.daily_chip_precip, precip),
                contentColor = style.contentColor,
                modifier = Modifier.weight(1f),
            )
            DailyMetaChip(
                label = stringResource(R.string.daily_chip_prob, prob),
                contentColor = style.contentColor,
                modifier = Modifier.weight(1f),
            )
            DailyMetaChip(
                label = stringResource(R.string.daily_chip_uv, uv),
                contentColor = style.contentColor,
                modifier = Modifier.weight(1f),
            )
        }

        AnimatedVisibility(
            visible = expanded,
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut(),
        ) {
            Column {
                Spacer(modifier = Modifier.height(12.dp))
                val sunrise = day.sunrise?.let { formatHourLabel(it) } ?: "--"
                val sunset = day.sunset?.let { formatHourLabel(it) } ?: "--"
                val wind = day.windMaxKmh?.let { "%.0f km/h".format(it) } ?: "--"
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    DailyMetaChip(
                        label = stringResource(R.string.daily_detail_sunrise, sunrise),
                        contentColor = style.contentColor,
                        modifier = Modifier.weight(1f),
                    )
                    DailyMetaChip(
                        label = stringResource(R.string.daily_detail_sunset, sunset),
                        contentColor = style.contentColor,
                        modifier = Modifier.weight(1f),
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
                DailyMetaChip(
                    label = stringResource(R.string.daily_detail_wind, wind),
                    contentColor = style.contentColor,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

@Composable
private fun DailyTempRangeBar(
    minC: Double?,
    maxC: Double?,
    weekMinC: Double?,
    weekMaxC: Double?,
    trackColor: Color,
    fillBrush: Brush,
    modifier: Modifier = Modifier,
) {
    val range =
        if (weekMinC != null && weekMaxC != null) {
            weekAlignedTempBarRange(minC, maxC, weekMinC, weekMaxC)
        } else {
            // 无周界时退回「日较差宽度」示意
            val min = minC ?: 0.0
            val max = maxC ?: min
            val span = (max - min).coerceAtLeast(1.0)
            val fraction = (span / 18.0).toFloat().coerceIn(0.28f, 1f)
            DailyTempBarRange(start = 0f, end = fraction)
        }

    BoxWithConstraints(
        modifier =
            modifier
                .height(8.dp)
                .clip(CircleShape)
                .background(trackColor),
    ) {
        val barWidth = maxWidth * range.width
        val startPad = maxWidth * range.start
        Box(
            modifier =
                Modifier
                    .padding(start = startPad)
                    .width(barWidth)
                    .fillMaxHeight()
                    .clip(CircleShape)
                    .background(fillBrush),
        )
    }
}

@Composable
private fun DailyMetaChip(
    label: String,
    contentColor: Color,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier =
            modifier
                .clip(RoundedCornerShape(12.dp))
                .background(Color.White.copy(alpha = 0.16f))
                .border(1.dp, Color.White.copy(alpha = 0.2f), RoundedCornerShape(12.dp))
                .padding(horizontal = 8.dp, vertical = 7.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Medium,
            color = contentColor.copy(alpha = 0.95f),
            maxLines = 1,
        )
    }
}

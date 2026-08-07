package com.xiaoxiong.weather.ui.weather

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import com.xiaoxiong.weather.data.HourlyWeather
import com.xiaoxiong.weather.data.WeatherSnapshot
import com.xiaoxiong.weather.ui.LocalTemperatureUnit
import com.xiaoxiong.weather.ui.theme.AccentCoral
import com.xiaoxiong.weather.util.parseFlexibleDateTime
import com.xiaoxiong.weather.util.formatHourLabel
import com.xiaoxiong.weather.util.formatTemperature
import com.xiaoxiong.weather.util.indexOfCurrentHour
import com.xiaoxiong.weather.util.isSameHour

@Composable
internal fun HourlyPage(
    weather: WeatherSnapshot?,
    isOffline: Boolean,
    lastUpdatedText: String?,
    onChartDragStateChange: (Boolean) -> Unit = {}
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        if (weather == null) {
            item { EmptyHint(stringResource(R.string.hourly_empty)) }
            return@LazyColumn
        }
        if (isOffline) {
            item { OfflineBanner(lastUpdatedText) }
        }
        item {
            SectionHeader(
                title = stringResource(R.string.hourly_section_title),
                subtitle = stringResource(R.string.hourly_section_sub),
                icon = Icons.Filled.Schedule
            )
        }
        item {
            PrecipitationBarChart(
                hourly = weather.hourly,
                currentTime = weather.current.time
            )
        }
        item {
            HourlyStrip(
                hourly = weather.hourly,
                currentTime = weather.current.time
            )
        }
        item {
            HourlyChartSection(
                hourly = weather.hourly,
                currentTime = weather.current.time,
                onDragStateChange = onChartDragStateChange
            )
        }
    }
}

@Composable
internal fun HourlyStrip(
    hourly: List<HourlyWeather>,
    currentTime: String
) {
    val currentIndex = remember(hourly, currentTime) {
        indexOfCurrentHour(hourly.map { it.time }, currentTime)
            .coerceIn(0, (hourly.size - 1).coerceAtLeast(0))
    }
    val listState = rememberLazyListState(
        initialFirstVisibleItemIndex = currentIndex
    )
    LaunchedEffect(hourly, currentTime, currentIndex) {
        if (hourly.isEmpty()) return@LaunchedEffect
        listState.scrollToItem(currentIndex)
    }

    val labelNow = stringResource(R.string.label_now)
    val humidityNa = stringResource(R.string.humidity_short_na)
    val nowBrush = remember {
        Brush.verticalGradient(listOf(AccentCoral.copy(alpha = 0.9f), AccentCoral))
    }
    val surface = MaterialTheme.colorScheme.surface
    val surfaceVariant = MaterialTheme.colorScheme.surfaceVariant
    val idleBrush = remember(surface, surfaceVariant) {
        Brush.verticalGradient(
            listOf(surface, surfaceVariant.copy(alpha = 0.65f))
        )
    }
    val tempUnit = LocalTemperatureUnit.current

    LazyRow(
        state = listState,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        items(
            items = hourly,
            key = { it.time }
        ) { item ->
            val isNow = isSameHour(item.time, currentTime)
            Column(
                modifier = Modifier
                    .clip(RoundedCornerShape(18.dp))
                    .background(if (isNow) nowBrush else idleBrush)
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                val fg = if (isNow) Color.White else MaterialTheme.colorScheme.onSurface
                Text(
                    text = if (isNow) labelNow else formatHourLabel(item.time),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = fg
                )
                Spacer(modifier = Modifier.height(4.dp))
                WeatherIcon(
                    code = item.weatherCode,
                    isDay = parseFlexibleDateTime(item.time)?.hour?.let { it in 6..18 } ?: true,
                    modifier = Modifier.size(28.dp),
                    precipMmPerHour = item.precipitationMm
                )
                Text(
                    formatTemperature(item.temperatureC, tempUnit),
                    fontWeight = FontWeight.SemiBold,
                    color = fg
                )
                Text(
                    text = item.humidityPercent?.let { stringResource(R.string.humidity_short, it) }
                        ?: humidityNa,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (isNow) {
                        Color.White.copy(alpha = 0.9f)
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    }
                )
            }
        }
    }
}

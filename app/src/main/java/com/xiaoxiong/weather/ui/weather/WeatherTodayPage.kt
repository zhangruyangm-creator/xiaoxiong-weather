package com.xiaoxiong.weather.ui.weather

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Air
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.WbTwilight
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.xiaoxiong.weather.R
import com.xiaoxiong.weather.data.AirQuality
import com.xiaoxiong.weather.data.AlertSeverity
import com.xiaoxiong.weather.data.CurrentWeather
import com.xiaoxiong.weather.data.DailyWeather
import com.xiaoxiong.weather.data.RadarMapRepository
import com.xiaoxiong.weather.data.TodayCardId
import com.xiaoxiong.weather.data.TodayCardLayout
import com.xiaoxiong.weather.data.TodayCardPref
import com.xiaoxiong.weather.data.WeatherAlert
import com.xiaoxiong.weather.data.WeatherSnapshot
import com.xiaoxiong.weather.ui.LocalTemperatureUnit
import com.xiaoxiong.weather.ui.theme.AccentCoral
import com.xiaoxiong.weather.ui.theme.Leaf
import com.xiaoxiong.weather.util.AlertBearIcon
import com.xiaoxiong.weather.util.WeatherCodes
import com.xiaoxiong.weather.util.aqiLabelText
import com.xiaoxiong.weather.util.formatHourLabel
import com.xiaoxiong.weather.util.formatTemperature
import com.xiaoxiong.weather.util.formatVisibility
import com.xiaoxiong.weather.util.parseFlexibleDateTime
import com.xiaoxiong.weather.util.uvLabelText
import com.xiaoxiong.weather.util.weatherCodeDescription
import com.xiaoxiong.weather.util.windDirectionText

@Composable
internal fun TodayPage(
    weather: WeatherSnapshot?,
    isOffline: Boolean,
    lastUpdatedText: String?,
    radarMapRepository: RadarMapRepository,
    cardLayout: List<TodayCardPref> = TodayCardLayout.DEFAULT,
) {
    val layout = remember(cardLayout) { TodayCardLayout.mergeWithDefaults(cardLayout) }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        if (weather == null) {
            item { EmptyHint(stringResource(R.string.today_empty)) }
            return@LazyColumn
        }
        if (isOffline) {
            item { OfflineBanner(lastUpdatedText) }
        }
        val visibleCards =
            layout.filter { pref ->
                pref.visible &&
                    when (pref.id) {
                        TodayCardId.ALERTS -> weather.alerts.isNotEmpty()
                        TodayCardId.CURRENT -> true
                        TodayCardId.LIFE_INDICES -> weather.lifeIndices.isNotEmpty()
                        TodayCardId.AIR_QUALITY -> weather.airQuality != null
                        TodayCardId.RADAR -> true
                        TodayCardId.SUN_EXTRAS -> true
                    }
            }
        items(
            items = visibleCards,
            key = { it.id.name },
        ) { pref ->
            when (pref.id) {
                TodayCardId.ALERTS -> AlertsSection(weather.alerts)
                TodayCardId.CURRENT -> CurrentWeatherCard(weather)
                TodayCardId.LIFE_INDICES -> LifeIndicesSection(weather.lifeIndices)
                TodayCardId.AIR_QUALITY ->
                    weather.airQuality?.let { aq ->
                        AirQualityCard(aq, weather.daily.firstOrNull())
                    }
                TodayCardId.RADAR ->
                    RadarCloudMapSection(
                        snapshot = weather,
                        repository = radarMapRepository,
                    )
                TodayCardId.SUN_EXTRAS -> SunAndExtrasCard(weather)
            }
        }
        item { Spacer(modifier = Modifier.height(8.dp)) }
    }
}

@Composable
internal fun AlertsSection(alerts: List<WeatherAlert>) {
    if (alerts.isEmpty()) return

    val ordered =
        remember(alerts) {
            alerts.sortedByDescending { alertSeverityRank(it.severity) }
        }
    // 刷新后预警集合变化时恢复折叠
    val alertKey = remember(ordered) { ordered.joinToString("|") { it.title } }

    if (ordered.size == 1) {
        AlertCard(alert = ordered[0])
        return
    }

    var expanded by remember(alertKey) { mutableStateOf(false) }
    val stackBehind = ordered.drop(1).take(2)

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        // 折叠：扑克牌叠层，默认只展开优先级最高的一条
        Box(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = ripple(bounded = true),
                        onClick = { expanded = !expanded },
                    ).padding(bottom = if (expanded) 0.dp else (stackBehind.size * 8).dp),
        ) {
            if (!expanded) {
                stackBehind.forEachIndexed { index, alert ->
                    val depth = index + 1
                    val peekColor =
                        when (alert.severity) {
                            AlertSeverity.WARNING -> AccentCoral
                            AlertSeverity.WATCH -> Color(0xFFFFB74D)
                            AlertSeverity.INFO -> Leaf
                        }
                    Box(
                        modifier =
                            Modifier
                                .matchParentSize()
                                .offset(y = (depth * 8).dp)
                                .zIndex(depth.toFloat())
                                .clip(RoundedCornerShape(18.dp))
                                .background(peekColor.copy(alpha = 0.72f - index * 0.12f))
                                .border(
                                    width = 1.dp,
                                    color = Color.White.copy(alpha = 0.28f),
                                    shape = RoundedCornerShape(18.dp),
                                ),
                    )
                }
            }
            AlertCard(
                alert = ordered[0],
                modifier = Modifier.zIndex(10f),
                trailingLabel =
                    if (expanded) {
                        stringResource(R.string.alerts_collapse)
                    } else {
                        stringResource(R.string.alerts_expand, ordered.size)
                    },
                expandedIcon = expanded,
            )
        }

        AnimatedVisibility(
            visible = expanded,
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut(),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                ordered.drop(1).forEach { alert ->
                    AlertCard(alert = alert)
                }
            }
        }
    }
}

private fun alertSeverityRank(severity: AlertSeverity): Int =
    when (severity) {
        AlertSeverity.WARNING -> 3
        AlertSeverity.WATCH -> 2
        AlertSeverity.INFO -> 1
    }

@Composable
private fun AlertCard(
    alert: WeatherAlert,
    modifier: Modifier = Modifier,
    trailingLabel: String? = null,
    expandedIcon: Boolean = false,
) {
    val colors =
        when (alert.severity) {
            AlertSeverity.WARNING -> AccentCoral to Color.White
            AlertSeverity.WATCH -> Color(0xFFFFB74D) to Color(0xFF3E2723)
            AlertSeverity.INFO -> Leaf to Color.White
        }
    Box(
        modifier =
            modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(18.dp))
                .background(
                    Brush.horizontalGradient(
                        listOf(colors.first, colors.first.copy(alpha = 0.82f)),
                    ),
                ).border(
                    width = 1.dp,
                    color = Color.White.copy(alpha = 0.18f),
                    shape = RoundedCornerShape(18.dp),
                ).padding(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Image(
                painter =
                    painterResource(
                        AlertBearIcon.drawableRes(
                            alert.kind,
                            alert.precipMmPerHour,
                            alert.severity,
                        ),
                    ),
                contentDescription = alert.title,
                contentScale = ContentScale.Crop,
                modifier =
                    Modifier
                        .size(56.dp)
                        .clip(CircleShape)
                        .border(2.dp, Color.White.copy(alpha = 0.55f), CircleShape)
                        .background(Color.White.copy(alpha = 0.18f)),
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = alert.title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = colors.second,
                )
                Text(
                    text = alert.message,
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.second.copy(alpha = 0.95f),
                )
            }
            if (trailingLabel != null) {
                Spacer(modifier = Modifier.width(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = trailingLabel,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = colors.second.copy(alpha = 0.9f),
                    )
                    Icon(
                        imageVector =
                            if (expandedIcon) {
                                Icons.Filled.ExpandLess
                            } else {
                                Icons.Filled.ExpandMore
                            },
                        contentDescription = trailingLabel,
                        tint = colors.second.copy(alpha = 0.9f),
                        modifier = Modifier.size(20.dp),
                    )
                }
            }
        }
    }
}

@Composable
internal fun CurrentWeatherCard(snapshot: WeatherSnapshot) {
    val current = snapshot.current
    val precipRate = WeatherCodes.intensityPrecipMmPerHour(current)
    val displayCode = WeatherCodes.displayCode(current.weatherCode, precipRate)
    WeatherAtmosphereCard(
        code = displayCode,
        isDay = current.isDay,
        modifier = Modifier.fillMaxWidth(),
    ) { style ->
        Column(modifier = Modifier.padding(20.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = snapshot.city.displayName,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = style.contentColor,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (snapshot.city.isCurrentLocation) {
                        Text(
                            text = stringResource(R.string.gps_location),
                            style = MaterialTheme.typography.labelMedium,
                            color = style.mutedContentColor,
                        )
                    }
                }
                WeatherIcon(
                    code = displayCode,
                    isDay = current.isDay ?: true,
                    modifier = Modifier.size(64.dp),
                    precipMmPerHour = precipRate,
                )
            }

            Spacer(modifier = Modifier.height(10.dp))
            val tempUnit = LocalTemperatureUnit.current
            Text(
                text = formatTemperature(current.temperatureC, tempUnit),
                style = MaterialTheme.typography.displaySmall,
                fontWeight = FontWeight.Bold,
                color = style.contentColor,
            )
            Text(
                text = weatherCodeDescription(displayCode, precipRate),
                style = MaterialTheme.typography.titleMedium,
                color = style.contentColor,
            )
            val today = snapshot.daily.firstOrNull()
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text =
                    stringResource(
                        R.string.high_low_temps,
                        formatTemperature(today?.tempMaxC, tempUnit),
                        formatTemperature(today?.tempMinC, tempUnit),
                    ),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Medium,
                color = style.contentColor,
            )

            Spacer(modifier = Modifier.height(16.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                FrostedMetricTile(
                    stringResource(R.string.metric_feels_like),
                    formatTemperature(current.apparentTemperatureC, tempUnit),
                    Modifier.weight(1f),
                    style.contentColor,
                    style.mutedContentColor,
                )
                FrostedMetricTile(
                    stringResource(R.string.metric_humidity),
                    current.humidityPercent?.let { "$it%" } ?: "--",
                    Modifier.weight(1f),
                    style.contentColor,
                    style.mutedContentColor,
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                FrostedMetricTile(
                    stringResource(R.string.metric_visibility),
                    formatVisibility(current.visibilityMeters),
                    Modifier.weight(1f),
                    style.contentColor,
                    style.mutedContentColor,
                )
                FrostedMetricTile(
                    stringResource(R.string.metric_wind),
                    current.windSpeedKmh?.let { "${"%.0f".format(it)} km/h" } ?: "--",
                    Modifier.weight(1f),
                    style.contentColor,
                    style.mutedContentColor,
                )
            }

            Spacer(modifier = Modifier.height(12.dp))
            MetricGrid(
                current = current,
                labelColor = style.mutedContentColor,
                valueColor = style.contentColor,
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text =
                    stringResource(
                        R.string.local_time_updated,
                        formatHourLabel(current.time),
                    ),
                style = MaterialTheme.typography.bodySmall,
                color = style.mutedContentColor,
            )
        }
    }
}

@Composable
internal fun MetricGrid(
    current: CurrentWeather,
    labelColor: Color,
    valueColor: Color,
) {
    val precipRate = WeatherCodes.intensityPrecipMmPerHour(current)
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FrostedMetricTile(
                stringResource(R.string.metric_gust),
                current.windGustsKmh?.let { "${"%.0f".format(it)} km/h" } ?: "--",
                Modifier.weight(1f),
                valueColor,
                labelColor,
            )
            FrostedMetricTile(
                stringResource(R.string.metric_wind_dir),
                windDirectionText(current.windDirectionDeg),
                Modifier.weight(1f),
                valueColor,
                labelColor,
            )
        }
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FrostedMetricTile(
                stringResource(R.string.metric_pressure),
                current.pressureHpa?.let { "${"%.0f".format(it)} hPa" } ?: "--",
                Modifier.weight(1f),
                valueColor,
                labelColor,
            )
            FrostedMetricTile(
                stringResource(R.string.metric_dew_point),
                formatTemperature(current.dewPointC, LocalTemperatureUnit.current),
                Modifier.weight(1f),
                valueColor,
                labelColor,
            )
        }
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FrostedMetricTile(
                stringResource(R.string.metric_precip),
                precipRate?.let { "${"%.1f".format(it)} mm" } ?: "--",
                Modifier.weight(1f),
                valueColor,
                labelColor,
            )
            FrostedMetricTile(
                stringResource(R.string.metric_cloud),
                current.cloudCoverPercent?.let { "$it%" } ?: "--",
                Modifier.weight(1f),
                valueColor,
                labelColor,
            )
        }
    }
}

@Composable
internal fun AirQualityCard(
    airQuality: AirQuality,
    today: DailyWeather?,
) {
    SoftCard(modifier = Modifier.fillMaxWidth()) {
        SectionHeader(
            title = stringResource(R.string.aq_title),
            subtitle = stringResource(R.string.aq_sub),
            icon = Icons.Filled.Air,
        )
        Spacer(modifier = Modifier.height(12.dp))
        val aqi = airQuality.europeanAqi
        QualityBar(
            label = stringResource(R.string.aq_europe, aqiLabelText(aqi)),
            valueText = aqi?.toString() ?: "--",
            progress = ((aqi ?: 0) / 100f).coerceIn(0f, 1f),
            barColor =
                when {
                    aqi == null -> MaterialTheme.colorScheme.outline
                    aqi <= 40 -> Leaf
                    aqi <= 60 -> Color(0xFFFFB74D)
                    else -> AccentCoral
                },
        )
        Spacer(modifier = Modifier.height(12.dp))
        val uv = airQuality.uvIndex ?: today?.uvIndexMax
        QualityBar(
            label = stringResource(R.string.aq_uv, uvLabelText(uv)),
            valueText = uv?.let { "%.1f".format(it) } ?: "--",
            progress = ((uv ?: 0.0) / 11.0).toFloat().coerceIn(0f, 1f),
            barColor = Color(0xFFFFCA28),
        )
        Spacer(modifier = Modifier.height(14.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            MiniStat("PM2.5", airQuality.pm25?.let { "%.1f".format(it) } ?: "--", Modifier.weight(1f))
            MiniStat("PM10", airQuality.pm10?.let { "%.1f".format(it) } ?: "--", Modifier.weight(1f))
        }
        Spacer(modifier = Modifier.height(8.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            MiniStat(
                stringResource(R.string.aq_us),
                airQuality.usAqi?.toString() ?: "--",
                Modifier.weight(1f),
            )
            MiniStat(
                stringResource(R.string.aq_dust),
                airQuality.dustUgM3?.let { "%.0f".format(it) } ?: "--",
                Modifier.weight(1f),
            )
        }
    }
}

@Composable
internal fun MiniStat(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier =
            modifier
                .clip(RoundedCornerShape(14.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f))
                .padding(10.dp),
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            value,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

@Composable
internal fun SunAndExtrasCard(snapshot: WeatherSnapshot) {
    val today = snapshot.daily.firstOrNull()
    val sunriseLabel = today?.sunrise?.let { formatHourLabel(it) } ?: "--"
    val sunsetLabel = today?.sunset?.let { formatHourLabel(it) } ?: "--"
    val phase =
        remember(snapshot.current.time, today?.sunrise, today?.sunset) {
            val now = parseFlexibleDateTime(snapshot.current.time.orEmpty())
            val rise = today?.sunrise?.let { parseFlexibleDateTime(it) }
            val set = today?.sunset?.let { parseFlexibleDateTime(it) }
            if (now != null && rise != null && set != null) sunPathPhase(now, rise, set) else null
        }
    SoftCard(modifier = Modifier.fillMaxWidth()) {
        SectionHeader(
            title = stringResource(R.string.extras_title),
            subtitle = stringResource(R.string.extras_sub),
            icon = Icons.Filled.WbTwilight,
        )
        Spacer(modifier = Modifier.height(8.dp))
        val onDateTime =
            remember(snapshot.current.time) {
                parseFlexibleDateTime(snapshot.current.time.orEmpty())
            }
        val onDate = onDateTime?.toLocalDate() ?: java.time.LocalDate.now()
        val onTime = onDateTime?.toLocalTime() ?: java.time.LocalTime.now()
        SunPathArc(
            phase = phase,
            sunriseLabel = sunriseLabel,
            sunsetLabel = sunsetLabel,
            onDate = onDate,
            onTime = onTime,
        )
        Spacer(modifier = Modifier.height(8.dp))
        val dayNight =
            when (phase?.isDay ?: snapshot.current.isDay) {
                true -> stringResource(R.string.daytime)
                false -> stringResource(R.string.nighttime)
                null -> "--"
            }
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            MiniStat(
                stringResource(R.string.precip_probability),
                today?.precipProbabilityMax?.let { "$it%" } ?: "--",
                Modifier.weight(1f),
            )
            MiniStat(stringResource(R.string.day_night), dayNight, Modifier.weight(1f))
        }
    }
}

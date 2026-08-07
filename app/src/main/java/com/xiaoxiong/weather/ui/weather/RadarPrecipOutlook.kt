package com.xiaoxiong.weather.ui.weather

import android.content.res.Resources
import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.xiaoxiong.weather.R
import com.xiaoxiong.weather.data.CurrentWeather
import com.xiaoxiong.weather.data.HourlyWeather
import com.xiaoxiong.weather.ui.theme.AccentCoral
import com.xiaoxiong.weather.ui.theme.Leaf
import com.xiaoxiong.weather.util.WeatherCodes
import com.xiaoxiong.weather.util.formatVisibility
import com.xiaoxiong.weather.util.formatWindDirection
import com.xiaoxiong.weather.util.upcomingFromCurrent

internal data class RadarPrecipOutlook(
    val maxProbPercent: Int?,
    val precipSumMm: Double?,
    val windText: String,
    val visibilityText: String,
    @StringRes val messageRes: Int
) {
    companion object {
        fun from(
            resources: Resources,
            current: CurrentWeather,
            hourly: List<HourlyWeather>
        ): RadarPrecipOutlook {
            // 含当前小时起共 3 帧 ≈ 未来约 2 小时窗口
            val window = upcomingFromCurrent(hourly, { it.time }, current.time, count = 3)
            val maxProb = window.mapNotNull { it.precipProbability }.maxOrNull()
            val precipSum = window.mapNotNull { it.precipitationMm }
                .takeIf { it.isNotEmpty() }
                ?.sum()
            val windSpeed = current.windSpeedKmh?.let { "${"%.0f".format(it)} km/h" } ?: "--"
            val windDir = formatWindDirection(resources, current.windDirectionDeg)
            val windText = if (windDir == "--" && windSpeed == "--") {
                "--"
            } else if (windDir == "--") {
                windSpeed
            } else {
                "$windDir $windSpeed"
            }
            val messageRes = when {
                (maxProb ?: 0) >= 60 && (precipSum ?: 0.0) >= 2.0 ->
                    R.string.radar_msg_heavy
                (maxProb ?: 0) >= 40 || (precipSum ?: 0.0) >= 0.1 ->
                    R.string.radar_msg_light
                (WeatherCodes.intensityPrecipMmPerHour(current) ?: 0.0) >= 0.1 ->
                    R.string.radar_msg_active
                else ->
                    R.string.radar_msg_quiet
            }
            return RadarPrecipOutlook(
                maxProbPercent = maxProb,
                precipSumMm = precipSum,
                windText = windText,
                visibilityText = formatVisibility(current.visibilityMeters),
                messageRes = messageRes
            )
        }
    }
}

@Composable
internal fun RadarSituationStrip(outlook: RadarPrecipOutlook) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            MiniStat(
                label = stringResource(R.string.radar_prob_2h),
                value = outlook.maxProbPercent?.let { "$it%" } ?: "--",
                modifier = Modifier.weight(1f)
            )
            MiniStat(
                label = stringResource(R.string.radar_sum_2h),
                value = outlook.precipSumMm?.let { "${"%.1f".format(it)} mm" } ?: "--",
                modifier = Modifier.weight(1f)
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            MiniStat(
                label = stringResource(R.string.radar_wind),
                value = outlook.windText,
                modifier = Modifier.weight(1f)
            )
            MiniStat(
                label = stringResource(R.string.radar_visibility),
                value = outlook.visibilityText,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
internal fun RadarIntensityLegend() {
    val legendBrush = remember {
        Brush.horizontalGradient(
            listOf(
                Leaf.copy(alpha = 0.85f),
                Color(0xFFFFCA28),
                AccentCoral,
                Color(0xFFE53935)
            )
        )
    }
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            text = stringResource(R.string.radar_legend_title),
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(10.dp)
                .clip(RoundedCornerShape(5.dp))
                .background(legendBrush)
        ) {}
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                stringResource(R.string.radar_legend_weak),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                stringResource(R.string.radar_legend_mid),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                stringResource(R.string.radar_legend_strong),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                stringResource(R.string.radar_legend_extreme),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Text(
            text = stringResource(R.string.radar_legend_note),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

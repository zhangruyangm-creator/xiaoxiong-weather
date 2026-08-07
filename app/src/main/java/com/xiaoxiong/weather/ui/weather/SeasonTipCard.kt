package com.xiaoxiong.weather.ui.weather

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocalFlorist
import androidx.compose.material.icons.filled.Park
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.xiaoxiong.weather.R
import com.xiaoxiong.weather.data.City
import com.xiaoxiong.weather.data.SeasonTipGenerator

@Composable
internal fun SeasonTipsSection(
    city: City,
    referenceIsoTime: String?,
    elevationMeters: Double? = null,
) {
    val tips =
        remember(
            city.latitude,
            city.longitude,
            city.admin1,
            referenceIsoTime,
            elevationMeters,
        ) {
            SeasonTipGenerator.tipsForCity(city, referenceIsoTime, elevationMeters)
        }
    if (tips.isEmpty()) return
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        tips.forEach { tip ->
            SeasonTipCard(tip)
        }
    }
}

@Composable
private fun SeasonTipCard(tip: SeasonTipGenerator.Tip) {
    val icon =
        when (tip.kind) {
            SeasonTipGenerator.Kind.FOLIAGE -> Icons.Filled.Park
            else -> Icons.Filled.LocalFlorist
        }
    val kindTitle =
        when (tip.kind) {
            SeasonTipGenerator.Kind.WINTER_BLOOM -> stringResource(R.string.season_winter_bloom)
            SeasonTipGenerator.Kind.SPRING_BLOOM -> stringResource(R.string.season_spring_bloom)
            SeasonTipGenerator.Kind.SUMMER_BLOOM -> stringResource(R.string.season_summer_bloom)
            SeasonTipGenerator.Kind.AUTUMN_BLOOM -> stringResource(R.string.season_autumn_bloom)
            SeasonTipGenerator.Kind.FOLIAGE -> stringResource(R.string.season_foliage)
        }
    val phaseLabel =
        when (tip.phase) {
            SeasonTipGenerator.Phase.UPCOMING -> stringResource(R.string.season_phase_upcoming)
            SeasonTipGenerator.Phase.STARTING -> stringResource(R.string.season_phase_starting)
            SeasonTipGenerator.Phase.PEAK -> stringResource(R.string.season_phase_peak)
            SeasonTipGenerator.Phase.ENDING -> stringResource(R.string.season_phase_ending)
        }
    val windowText =
        stringResource(
            R.string.season_window,
            tip.windowStart.monthValue,
            tip.windowStart.dayOfMonth,
            tip.windowEnd.monthValue,
            tip.windowEnd.dayOfMonth,
        )
    val subjects = stringResource(tip.subjectsRes)
    val summary =
        when (tip.kind) {
            SeasonTipGenerator.Kind.FOLIAGE ->
                when (tip.phase) {
                    SeasonTipGenerator.Phase.UPCOMING ->
                        stringResource(R.string.season_foliage_upcoming, windowText, subjects)
                    SeasonTipGenerator.Phase.STARTING ->
                        stringResource(R.string.season_foliage_starting, subjects)
                    SeasonTipGenerator.Phase.PEAK ->
                        stringResource(R.string.season_foliage_peak, subjects)
                    SeasonTipGenerator.Phase.ENDING ->
                        stringResource(R.string.season_foliage_ending, subjects)
                }
            else ->
                when (tip.phase) {
                    SeasonTipGenerator.Phase.UPCOMING ->
                        stringResource(R.string.season_blossom_upcoming, windowText, subjects)
                    SeasonTipGenerator.Phase.STARTING ->
                        stringResource(R.string.season_blossom_starting, subjects)
                    SeasonTipGenerator.Phase.PEAK ->
                        stringResource(R.string.season_blossom_peak, subjects)
                    SeasonTipGenerator.Phase.ENDING ->
                        stringResource(R.string.season_blossom_ending, subjects)
                }
        }

    SoftCard(modifier = Modifier.fillMaxWidth()) {
        SectionHeader(
            title = kindTitle,
            subtitle = stringResource(R.string.season_sub, phaseLabel),
            icon = icon,
        )
        Spacer(modifier = Modifier.height(10.dp))
        Text(
            text = summary,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = stringResource(R.string.season_disclaimer),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.outline,
        )
    }
}

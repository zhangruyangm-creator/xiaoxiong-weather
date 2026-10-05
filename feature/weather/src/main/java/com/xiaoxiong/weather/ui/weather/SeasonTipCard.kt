package com.xiaoxiong.weather.ui.weather

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AcUnit
import androidx.compose.material.icons.filled.LocalFlorist
import androidx.compose.material.icons.filled.Park
import androidx.compose.material.icons.filled.Spa
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.xiaoxiong.weather.R
import com.xiaoxiong.weather.data.City
import com.xiaoxiong.weather.data.SeasonTipGenerator
import com.xiaoxiong.weather.data.todayInSnapshotZone
import com.xiaoxiong.weather.ui.rememberTodayEpochDay

@Composable
internal fun SeasonTipsSection(
    city: City,
    elevationMeters: Double? = null,
    phenologyShiftDays: Int? = null,
    phenologyShiftLoading: Boolean = false,
    timezoneId: String? = city.timezone,
) {
    val todayTick = rememberTodayEpochDay().value
    val onDate =
        remember(todayTick, timezoneId) {
            todayInSnapshotZone(timezoneId)
        }
    val display =
        remember(
            city.latitude,
            city.longitude,
            city.admin1,
            onDate,
            elevationMeters,
            phenologyShiftDays,
        ) {
            SeasonTipGenerator.displayFor(
                latitude = city.latitude,
                longitude = city.longitude,
                onDate = onDate,
                elevationMeters = elevationMeters,
                admin1 = city.admin1,
                phenologyShiftDays = phenologyShiftDays,
            )
        }
    if (!display.hasContent && !phenologyShiftLoading) return

    SeasonTipsCardContent(
        display = display,
        globalPhenologyShiftDays = phenologyShiftDays,
        phenologyShiftLoading = phenologyShiftLoading,
        compact = false,
    )
}

@Composable
internal fun SeasonTipsCardContent(
    display: SeasonTipGenerator.SeasonDisplay,
    globalPhenologyShiftDays: Int? = null,
    phenologyShiftLoading: Boolean = false,
    compact: Boolean = false,
) {
    SoftCard(modifier = Modifier.fillMaxWidth()) {
        SectionHeader(
            title = stringResource(R.string.season_section_title),
            subtitle = stringResource(R.string.season_sub),
            icon = Icons.Filled.LocalFlorist,
        )
        Spacer(modifier = Modifier.height(10.dp))

        if (phenologyShiftLoading) {
            Text(
                text = stringResource(R.string.season_phenology_loading),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
            )
            Spacer(modifier = Modifier.height(8.dp))
        }

        Column(verticalArrangement = Arrangement.spacedBy(if (compact) 8.dp else 12.dp)) {
            display.tips.forEach { tip ->
                SeasonTipRow(tip)
            }
            display.nextOutlook?.let { outlook ->
                SeasonTipRow(outlook, isGapOutlook = true)
            }
        }

        globalPhenologyShiftDays?.takeIf { it != 0 && !phenologyShiftLoading }?.let { shiftDays ->
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text =
                    if (shiftDays < 0) {
                        stringResource(R.string.season_phenology_early, -shiftDays)
                    } else {
                        stringResource(R.string.season_phenology_late, shiftDays)
                    },
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
            )
        }

        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = stringResource(R.string.season_disclaimer),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.outline,
        )
    }
}

@Composable
private fun SeasonTipRow(
    tip: SeasonTipGenerator.Tip,
    isGapOutlook: Boolean = tip.isGapOutlook,
) {
    val icon = iconForKind(tip.kind)
    Row(verticalAlignment = Alignment.Top) {
        Icon(
            icon,
            contentDescription = null,
            modifier = Modifier.size(22.dp),
            tint = MaterialTheme.colorScheme.primary,
        )
        Spacer(modifier = Modifier.size(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = seasonTipHeadline(tip, includeWindow = true, forceGapCopy = isGapOutlook),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = seasonTipBody(tip, isGapOutlook),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun seasonTipHeadline(
    tip: SeasonTipGenerator.Tip,
    includeWindow: Boolean,
    forceGapCopy: Boolean = tip.isGapOutlook,
): String {
    val kindTitle = kindTitle(tip.kind)
    val phaseLabel = phaseLabel(tip.phase)
    if (forceGapCopy) {
        val days = tip.daysUntilNext ?: return "$kindTitle · $phaseLabel"
        val windowText = formatSeasonWindow(tip.windowStart, tip.windowEnd)
        return stringResource(
            R.string.season_gap_headline,
            kindTitle,
            days,
            windowText,
        )
    }
    return "$kindTitle · $phaseLabel"
}

@Composable
private fun seasonTipBody(
    tip: SeasonTipGenerator.Tip,
    isGapOutlook: Boolean,
): String {
    val windowText = formatSeasonWindow(tip.windowStart, tip.windowEnd)
    val subjects = stringResource(tip.subjectsRes)
    if (isGapOutlook) {
        return stringResource(R.string.season_gap_body, windowText, subjects)
    }
    return when (tip.kind) {
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
}

@Composable
private fun formatSeasonWindow(
    start: java.time.MonthDay,
    end: java.time.MonthDay,
): String =
    stringResource(
        R.string.season_window,
        start.monthValue,
        start.dayOfMonth,
        end.monthValue,
        end.dayOfMonth,
    )

@Composable
private fun kindTitle(kind: SeasonTipGenerator.Kind): String =
    when (kind) {
        SeasonTipGenerator.Kind.WINTER_BLOOM -> stringResource(R.string.season_winter_bloom)
        SeasonTipGenerator.Kind.SPRING_BLOOM -> stringResource(R.string.season_spring_bloom)
        SeasonTipGenerator.Kind.SUMMER_BLOOM -> stringResource(R.string.season_summer_bloom)
        SeasonTipGenerator.Kind.AUTUMN_BLOOM -> stringResource(R.string.season_autumn_bloom)
        SeasonTipGenerator.Kind.FOLIAGE -> stringResource(R.string.season_foliage)
    }

@Composable
private fun phaseLabel(phase: SeasonTipGenerator.Phase): String =
    when (phase) {
        SeasonTipGenerator.Phase.UPCOMING -> stringResource(R.string.season_phase_upcoming)
        SeasonTipGenerator.Phase.STARTING -> stringResource(R.string.season_phase_starting)
        SeasonTipGenerator.Phase.PEAK -> stringResource(R.string.season_phase_peak)
        SeasonTipGenerator.Phase.ENDING -> stringResource(R.string.season_phase_ending)
    }

private fun iconForKind(kind: SeasonTipGenerator.Kind): ImageVector =
    when (kind) {
        SeasonTipGenerator.Kind.WINTER_BLOOM -> Icons.Filled.AcUnit
        SeasonTipGenerator.Kind.SPRING_BLOOM -> Icons.Filled.LocalFlorist
        SeasonTipGenerator.Kind.SUMMER_BLOOM -> Icons.Filled.WbSunny
        SeasonTipGenerator.Kind.AUTUMN_BLOOM -> Icons.Filled.Spa
        SeasonTipGenerator.Kind.FOLIAGE -> Icons.Filled.Park
    }

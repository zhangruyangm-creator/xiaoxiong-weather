package com.xiaoxiong.weather.ui.weather

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.xiaoxiong.weather.R
import com.xiaoxiong.weather.data.RadarMapRepository
import com.xiaoxiong.weather.data.WeatherSnapshot

internal enum class PrecipMapMode { RADAR, CLOUD }

/**
 * 降水与雷达：短时态势 + RainViewer 回波 / NASA GIBS 卫星云图。
 */
@Composable
fun RadarCloudMapSection(
    snapshot: WeatherSnapshot,
    repository: RadarMapRepository,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val resources = context.resources
    val city = snapshot.city
    val outlook =
        remember(snapshot.current, snapshot.hourly, resources.configuration) {
            RadarPrecipOutlook.from(resources, snapshot.current, snapshot.hourly)
        }
    var mapMode by remember { mutableStateOf(PrecipMapMode.RADAR) }

    SoftCard(modifier = modifier.fillMaxWidth()) {
        SectionHeader(
            title = stringResource(R.string.radar_title),
            subtitle = stringResource(R.string.radar_sub),
            icon = Icons.Filled.Cloud,
        )
        Spacer(modifier = Modifier.height(12.dp))
        RadarSituationStrip(outlook = outlook)
        Spacer(modifier = Modifier.height(10.dp))
        Text(
            text = stringResource(outlook.messageRes),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(modifier = Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(
                selected = mapMode == PrecipMapMode.RADAR,
                onClick = { mapMode = PrecipMapMode.RADAR },
                label = { Text(stringResource(R.string.map_tab_radar)) },
                colors =
                    FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                        selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    ),
            )
            FilterChip(
                selected = mapMode == PrecipMapMode.CLOUD,
                onClick = { mapMode = PrecipMapMode.CLOUD },
                label = { Text(stringResource(R.string.map_tab_cloud)) },
                colors =
                    FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                        selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    ),
            )
        }
        Spacer(modifier = Modifier.height(10.dp))
        when (mapMode) {
            PrecipMapMode.RADAR -> {
                LiveRadarPane(
                    city = city,
                    repository = repository,
                    timezoneId = snapshot.timezone,
                )
                Spacer(modifier = Modifier.height(10.dp))
                RadarIntensityLegend()
            }
            PrecipMapMode.CLOUD -> {
                // 仅在切到云图时加载，避免拖慢雷达首屏
                SatelliteCloudPane(city = city, repository = repository)
            }
        }
    }
}

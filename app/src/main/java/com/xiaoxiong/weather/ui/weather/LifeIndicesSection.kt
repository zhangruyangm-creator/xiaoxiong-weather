package com.xiaoxiong.weather.ui.weather

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.DirectionsRun
import androidx.compose.material.icons.filled.AcUnit
import androidx.compose.material.icons.filled.Checkroom
import androidx.compose.material.icons.filled.DeviceThermostat
import androidx.compose.material.icons.filled.DryCleaning
import androidx.compose.material.icons.filled.LocalCarWash
import androidx.compose.material.icons.filled.Spa
import androidx.compose.material.icons.filled.Umbrella
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.xiaoxiong.weather.R
import com.xiaoxiong.weather.data.LifeIndex
import com.xiaoxiong.weather.data.LifeIndexKind
import com.xiaoxiong.weather.data.LifeIndexLevel
import com.xiaoxiong.weather.ui.theme.Leaf

@Composable
fun LifeIndicesSection(indices: List<LifeIndex>) {
    if (indices.isEmpty()) return
    SoftCard(modifier = Modifier.fillMaxWidth()) {
        SectionHeader(
            title = stringResource(R.string.life_title),
            subtitle = stringResource(R.string.life_sub),
            icon = Icons.Filled.Spa
        )
        Spacer(modifier = Modifier.height(12.dp))
        val rows = indices.chunked(2)
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            rows.forEach { row ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    row.forEach { index ->
                        LifeIndexTile(
                            index = index,
                            modifier = Modifier.weight(1f)
                        )
                    }
                    if (row.size == 1) {
                        Spacer(modifier = Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

@Composable
private fun LifeIndexTile(
    index: LifeIndex,
    modifier: Modifier = Modifier
) {
    val levelColor = when (index.level) {
        LifeIndexLevel.GOOD -> Leaf
        LifeIndexLevel.FAIR -> Color(0xFFFFB74D)
        LifeIndexLevel.POOR -> MaterialTheme.colorScheme.error
    }
    val levelLabel = index.levelLabel ?: stringResource(
        when (index.level) {
            LifeIndexLevel.GOOD -> R.string.life_level_good
            LifeIndexLevel.FAIR -> R.string.life_level_fair
            LifeIndexLevel.POOR -> R.string.life_level_poor
        }
    )
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f))
            .padding(12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = iconFor(index.kind),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.size(6.dp))
            Text(
                text = index.title,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f),
                maxLines = 1
            )
            Text(
                text = levelLabel,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = levelColor
            )
        }
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = index.summary,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 3
        )
    }
}

private fun iconFor(kind: LifeIndexKind): ImageVector = when (kind) {
    LifeIndexKind.COMFORT -> Icons.Filled.DeviceThermostat
    LifeIndexKind.CLOTHING -> Icons.Filled.Checkroom
    LifeIndexKind.UMBRELLA -> Icons.Filled.Umbrella
    LifeIndexKind.UV -> Icons.Filled.WbSunny
    LifeIndexKind.CAR_WASH -> Icons.Filled.LocalCarWash
    LifeIndexKind.LAUNDRY -> Icons.Filled.DryCleaning
    LifeIndexKind.SPORT -> Icons.AutoMirrored.Filled.DirectionsRun
    LifeIndexKind.COLD -> Icons.Filled.AcUnit
}

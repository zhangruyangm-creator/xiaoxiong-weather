package com.xiaoxiong.weather.ui.weather

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.xiaoxiong.weather.R
import com.xiaoxiong.weather.ui.theme.AccentCoral

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WeatherTopBar(
    viewModel: WeatherViewModel,
    state: WeatherUiState,
    onLocate: () -> Unit,
    onToggleFavorite: () -> Unit,
    onShare: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    // 收藏状态只在顶栏星标使用：组件内收集，收藏变化仅重组顶栏
    val favorites by viewModel.favorites.collectAsStateWithLifecycle()
    val isFavorite = viewModel.isFavorite(state.selectedCity, favorites)
    TopAppBar(
        colors =
            TopAppBarDefaults.topAppBarColors(
                containerColor = Color.Transparent,
                scrolledContainerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f),
                titleContentColor = MaterialTheme.colorScheme.onSurface,
                actionIconContentColor = MaterialTheme.colorScheme.onSurface,
            ),
        title = {
            Column {
                Text(
                    stringResource(R.string.app_name),
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                val updated = state.lastUpdatedText
                val subtitle =
                    when {
                        state.isOffline && updated != null ->
                            stringResource(R.string.subtitle_offline_updated, updated)
                        updated != null && state.usingCurrentLocation ->
                            stringResource(R.string.subtitle_location_updated, updated)
                        updated != null ->
                            stringResource(R.string.subtitle_updated_refresh, updated)
                        state.usingCurrentLocation ->
                            stringResource(R.string.subtitle_current_location)
                        else -> stringResource(R.string.subtitle_search_world)
                    }
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        actions = {
            Row(
                modifier = Modifier.padding(end = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                RoundAction(onClick = onLocate) {
                    Icon(
                        Icons.Default.MyLocation,
                        contentDescription = stringResource(R.string.cd_locate),
                    )
                }
                RoundAction(
                    onClick = onToggleFavorite,
                    enabled = state.selectedCity != null,
                ) {
                    Icon(
                        imageVector = if (isFavorite) Icons.Filled.Remove else Icons.Filled.Add,
                        contentDescription =
                            stringResource(
                                if (isFavorite) {
                                    R.string.cd_favorite_remove
                                } else {
                                    R.string.cd_favorite_add
                                },
                            ),
                        tint =
                            if (isFavorite) {
                                AccentCoral
                            } else {
                                MaterialTheme.colorScheme.onSurface
                            },
                    )
                }
                RoundAction(
                    onClick = onShare,
                    enabled = state.weather != null,
                ) {
                    Icon(
                        Icons.Default.Share,
                        contentDescription = stringResource(R.string.cd_share),
                    )
                }
                RoundAction(onClick = onOpenSettings) {
                    Icon(
                        Icons.Default.Settings,
                        contentDescription = stringResource(R.string.cd_settings),
                    )
                }
            }
        },
    )
}

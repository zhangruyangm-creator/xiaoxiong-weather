package com.xiaoxiong.weather.ui.weather

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Whatshot
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.xiaoxiong.weather.R
import com.xiaoxiong.weather.data.City
import com.xiaoxiong.weather.data.FavoriteCityStore
import com.xiaoxiong.weather.data.isSamePlace
import java.util.Locale

@Composable
fun SearchHeader(
    viewModel: WeatherViewModel,
    query: String,
    selectedCity: City?,
    isSearching: Boolean,
    suggestions: List<City>,
    onQueryChange: (String) -> Unit,
    onSelectCity: (City) -> Unit,
    onClearRecent: () -> Unit,
) {
    // 收藏/最近/热门城市只在搜索面板使用，下沉到组件内收集，
    // 避免任一变化触发整个天气页重组
    val favorites by viewModel.favorites.collectAsStateWithLifecycle()
    val recentCities by viewModel.recentCities.collectAsStateWithLifecycle()
    val popularCities by viewModel.popularCities.collectAsStateWithLifecycle()
    val focusManager = LocalFocusManager.current
    var searchFocused by remember { mutableStateOf(false) }
    val showDiscovery = searchFocused && query.trim().length < 2
    val favoriteIds =
        remember(favorites) {
            favorites.map { approxKey(it) }.toSet()
        }
    val recentForChips =
        remember(recentCities, favoriteIds) {
            recentCities.filterNot { approxKey(it) in favoriteIds }.take(8)
        }
    val popularForChips =
        remember(popularCities, favoriteIds, recentCities) {
            val recentKeys = recentCities.map { approxKey(it) }.toSet()
            popularCities
                .filterNot {
                    approxKey(it) in favoriteIds || approxKey(it) in recentKeys
                }.take(12)
        }

    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 6.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        OutlinedTextField(
            value = query,
            onValueChange = onQueryChange,
            modifier =
                Modifier
                    .fillMaxWidth()
                    .onFocusChanged { searchFocused = it.isFocused },
            singleLine = true,
            shape = RoundedCornerShape(18.dp),
            label = { Text(stringResource(R.string.search_city_label)) },
            placeholder = { Text(stringResource(R.string.search_city_hint)) },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
            trailingIcon = {
                if (query.isNotEmpty()) {
                    IconButton(onClick = { onQueryChange("") }) {
                        Icon(
                            Icons.Default.Clear,
                            contentDescription = stringResource(R.string.cd_clear),
                        )
                    }
                }
            },
            colors =
                OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f),
                    unfocusedContainerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.75f),
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.35f),
                ),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions =
                KeyboardActions(
                    onSearch = {
                        suggestions.firstOrNull()?.let(onSelectCity)
                        focusManager.clearFocus()
                    },
                ),
        )

        if (favorites.isNotEmpty()) {
            CityChipRow(
                cities = favorites,
                selectedCity = selectedCity,
                onSelectCity = onSelectCity,
            )
            // 达到上限后新增会被拒绝（收藏旧项不再被静默丢弃），这里给出可见提示
            if (favorites.size >= FavoriteCityStore.MAX_FAVORITES) {
                Text(
                    text =
                        stringResource(
                            R.string.search_favorite_limit_reached,
                            FavoriteCityStore.MAX_FAVORITES,
                        ),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 4.dp),
                )
            }
        }

        if (showDiscovery && recentForChips.isNotEmpty()) {
            DiscoverySection(
                title = stringResource(R.string.search_recent),
                icon = Icons.Filled.History,
                actionLabel = stringResource(R.string.search_recent_clear),
                onAction = onClearRecent,
            ) {
                CityChipRow(
                    cities = recentForChips,
                    selectedCity = selectedCity,
                    onSelectCity = onSelectCity,
                )
            }
        }

        if (showDiscovery && popularForChips.isNotEmpty()) {
            DiscoverySection(
                title = stringResource(R.string.search_popular),
                icon = Icons.Filled.Whatshot,
            ) {
                CityChipRow(
                    cities = popularForChips,
                    selectedCity = selectedCity,
                    onSelectCity = onSelectCity,
                )
            }
        }

        if (showDiscovery && recentForChips.isEmpty() && popularForChips.isEmpty() && favorites.isEmpty()) {
            Text(
                text = stringResource(R.string.search_hint_empty),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 4.dp),
            )
        }

        if (isSearching) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
            ) {
                CircularProgressIndicator(
                    modifier =
                        Modifier
                            .padding(8.dp)
                            .size(22.dp),
                    strokeWidth = 2.dp,
                )
            }
        }

        suggestions.forEach { city ->
            SoftCard(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .clickable {
                            onSelectCity(city)
                            focusManager.clearFocus()
                        },
            ) {
                Text(
                    text = city.name,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = city.displayName,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun DiscoverySection(
    title: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(16.dp),
            )
            Spacer(modifier = Modifier.size(6.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f),
            )
            if (actionLabel != null && onAction != null) {
                TextButton(onClick = onAction) {
                    Text(actionLabel)
                }
            }
        }
        content()
    }
}

@Composable
private fun CityChipRow(
    cities: List<City>,
    selectedCity: City?,
    onSelectCity: (City) -> Unit,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        cities.forEach { city ->
            val selected = selectedCity?.isSamePlace(city) == true
            FilterChip(
                selected = selected,
                onClick = { onSelectCity(city) },
                label = {
                    Text(
                        text = city.name,
                        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                    )
                },
                leadingIcon =
                    if (selected) {
                        {
                            Icon(
                                imageVector = Icons.Filled.Check,
                                contentDescription = stringResource(R.string.cd_current_city),
                                modifier = Modifier.size(FilterChipDefaults.IconSize),
                            )
                        }
                    } else {
                        null
                    },
                colors =
                    FilterChipDefaults.filterChipColors(
                        containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f),
                        labelColor = MaterialTheme.colorScheme.onSurface,
                        selectedContainerColor = MaterialTheme.colorScheme.primary,
                        selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
                        selectedLeadingIconColor = MaterialTheme.colorScheme.onPrimary,
                    ),
            )
        }
    }
}

private fun approxKey(city: City): String = String.format(Locale.US, "%.2f,%.2f", city.latitude, city.longitude)

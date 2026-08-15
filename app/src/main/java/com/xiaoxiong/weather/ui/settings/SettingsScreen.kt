package com.xiaoxiong.weather.ui.settings

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.ShowChart
import androidx.compose.material.icons.filled.Accessibility
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material.icons.filled.Thermostat
import androidx.compose.material.icons.filled.ViewAgenda
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.work.ExistingPeriodicWorkPolicy
import com.xiaoxiong.weather.R
import com.xiaoxiong.weather.data.AppFontScale
import com.xiaoxiong.weather.data.AppSettings
import com.xiaoxiong.weather.data.AppSettingsStore
import com.xiaoxiong.weather.data.BackgroundRefreshInterval
import com.xiaoxiong.weather.data.DebugWeatherStore
import com.xiaoxiong.weather.data.TemperatureUnit
import com.xiaoxiong.weather.data.ThemeMode
import com.xiaoxiong.weather.ui.weather.AppScreenBackground
import com.xiaoxiong.weather.ui.weather.SectionHeader
import com.xiaoxiong.weather.ui.weather.SoftCard
import com.xiaoxiong.weather.worker.WeatherRefreshWorker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    settingsStore: AppSettingsStore,
    scope: CoroutineScope,
    onBack: () -> Unit,
    onOpenPrivacy: () -> Unit,
    onOpenTodayCards: () -> Unit = {},
    onOpenHourlyCards: () -> Unit = {},
    onOpenDailyCards: () -> Unit = {},
    onDebugUnlocked: () -> Unit = {},
) {
    val settings by settingsStore.settings.collectAsStateWithLifecycle(
        initialValue = AppSettings(),
    )
    val context = LocalContext.current
    var aboutTapCount by remember { mutableIntStateOf(0) }

    BackHandler(onBack = onBack)

    AppScreenBackground(modifier = Modifier.fillMaxSize()) {
        Scaffold(
            containerColor = Color.Transparent,
            topBar = {
                TopAppBar(
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
                    title = {
                        Text(stringResource(R.string.settings_title), fontWeight = FontWeight.Bold)
                    },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(
                                Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = stringResource(R.string.cd_back),
                            )
                        }
                    },
                )
            },
        ) { padding ->
            LazyColumn(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .padding(padding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                item {
                    SoftCard(modifier = Modifier.fillMaxWidth()) {
                        SectionHeader(
                            title = stringResource(R.string.settings_notifications),
                            subtitle =
                                stringResource(
                                    R.string.settings_notifications_subtitle,
                                    settings.backgroundRefreshInterval.hours,
                                ),
                            icon = Icons.Filled.Notifications,
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        SettingsSwitchRow(
                            title = stringResource(R.string.settings_notifications_toggle),
                            subtitle = stringResource(R.string.settings_notifications_toggle_sub),
                            checked = settings.notificationsEnabled,
                            onCheckedChange = {
                                scope.launch { settingsStore.setNotificationsEnabled(it) }
                            },
                        )
                    }
                }
                item {
                    SoftCard(modifier = Modifier.fillMaxWidth()) {
                        SectionHeader(
                            title = stringResource(R.string.settings_refresh_interval),
                            subtitle = stringResource(R.string.settings_refresh_interval_sub),
                            icon = Icons.Filled.Schedule,
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        BackgroundRefreshInterval.entries.forEach { option ->
                            SettingsChoiceRow(
                                title = stringResource(option.labelRes),
                                selected = settings.backgroundRefreshInterval == option,
                                onClick = {
                                    scope.launch {
                                        settingsStore.setBackgroundRefreshInterval(option)
                                        WeatherRefreshWorker.enqueue(
                                            context = context,
                                            intervalHours = option.hours,
                                            policy = ExistingPeriodicWorkPolicy.UPDATE,
                                        )
                                    }
                                },
                            )
                        }
                    }
                }
                item {
                    SoftCard(modifier = Modifier.fillMaxWidth()) {
                        SectionHeader(
                            title = stringResource(R.string.settings_temp_unit),
                            subtitle = stringResource(R.string.settings_temp_unit_sub),
                            icon = Icons.Filled.Thermostat,
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        SettingsChoiceRow(
                            title = stringResource(R.string.settings_celsius),
                            selected = settings.temperatureUnit == TemperatureUnit.CELSIUS,
                            onClick = {
                                scope.launch {
                                    settingsStore.setTemperatureUnit(TemperatureUnit.CELSIUS)
                                }
                            },
                        )
                        SettingsChoiceRow(
                            title = stringResource(R.string.settings_fahrenheit),
                            selected = settings.temperatureUnit == TemperatureUnit.FAHRENHEIT,
                            onClick = {
                                scope.launch {
                                    settingsStore.setTemperatureUnit(TemperatureUnit.FAHRENHEIT)
                                }
                            },
                        )
                    }
                }
                item {
                    SoftCard(modifier = Modifier.fillMaxWidth()) {
                        SectionHeader(
                            title = stringResource(R.string.settings_theme),
                            icon = Icons.Filled.Palette,
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        SettingsChoiceRow(
                            title = stringResource(R.string.settings_theme_system),
                            selected = settings.themeMode == ThemeMode.SYSTEM,
                            onClick = {
                                scope.launch { settingsStore.setThemeMode(ThemeMode.SYSTEM) }
                            },
                        )
                        SettingsChoiceRow(
                            title = stringResource(R.string.settings_theme_light),
                            selected = settings.themeMode == ThemeMode.LIGHT,
                            onClick = {
                                scope.launch { settingsStore.setThemeMode(ThemeMode.LIGHT) }
                            },
                        )
                        SettingsChoiceRow(
                            title = stringResource(R.string.settings_theme_dark),
                            selected = settings.themeMode == ThemeMode.DARK,
                            onClick = {
                                scope.launch { settingsStore.setThemeMode(ThemeMode.DARK) }
                            },
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        SettingsSwitchRow(
                            title = stringResource(R.string.settings_dynamic_color),
                            subtitle = stringResource(R.string.settings_dynamic_color_sub),
                            checked = settings.dynamicColor,
                            onCheckedChange = {
                                scope.launch { settingsStore.setDynamicColor(it) }
                            },
                        )
                    }
                }
                item {
                    SoftCard(modifier = Modifier.fillMaxWidth()) {
                        SectionHeader(
                            title = stringResource(R.string.settings_font),
                            subtitle = stringResource(R.string.settings_font_sub),
                            icon = Icons.Filled.TextFields,
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        AppFontScale.entries.forEach { option ->
                            SettingsChoiceRow(
                                title = stringResource(option.labelRes),
                                selected = settings.fontScale == option,
                                onClick = {
                                    scope.launch { settingsStore.setFontScale(option) }
                                },
                            )
                        }
                    }
                }
                item {
                    SoftCard(modifier = Modifier.fillMaxWidth()) {
                        SectionHeader(
                            title = stringResource(R.string.settings_accessibility),
                            subtitle = stringResource(R.string.settings_accessibility_sub),
                            icon = Icons.Filled.Accessibility,
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        SettingsSwitchRow(
                            title = stringResource(R.string.settings_reduce_motion),
                            subtitle = stringResource(R.string.settings_reduce_motion_sub),
                            checked = settings.reduceMotion,
                            onCheckedChange = {
                                scope.launch { settingsStore.setReduceMotion(it) }
                            },
                        )
                    }
                }
                item {
                    SoftCard(modifier = Modifier.fillMaxWidth()) {
                        SectionHeader(
                            title = stringResource(R.string.settings_launch),
                            subtitle = stringResource(R.string.settings_launch_sub),
                            icon = Icons.Filled.MyLocation,
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        SettingsSwitchRow(
                            title = stringResource(R.string.settings_prefer_location),
                            subtitle = stringResource(R.string.settings_prefer_location_sub),
                            checked = settings.preferLocationOnLaunch,
                            onCheckedChange = {
                                scope.launch { settingsStore.setPreferLocationOnLaunch(it) }
                            },
                        )
                    }
                }
                item {
                    SoftCard(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .clickable(onClick = onOpenTodayCards),
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                SectionHeader(
                                    title = stringResource(R.string.settings_today_cards),
                                    subtitle = stringResource(R.string.settings_today_cards_sub),
                                    icon = Icons.Filled.ViewAgenda,
                                )
                            }
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
                item {
                    SoftCard(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .clickable(onClick = onOpenHourlyCards),
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                SectionHeader(
                                    title = stringResource(R.string.settings_hourly_cards),
                                    subtitle = stringResource(R.string.settings_hourly_cards_sub),
                                    icon = Icons.AutoMirrored.Filled.ShowChart,
                                )
                            }
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
                item {
                    SoftCard(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .clickable(onClick = onOpenDailyCards),
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                SectionHeader(
                                    title = stringResource(R.string.settings_daily_cards),
                                    subtitle = stringResource(R.string.settings_daily_cards_sub),
                                    icon = Icons.Filled.CalendarMonth,
                                )
                            }
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
                item {
                    SoftCard(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .clickable {
                                    aboutTapCount += 1
                                    if (aboutTapCount >= DebugWeatherStore.ABOUT_TAP_COUNT) {
                                        aboutTapCount = 0
                                        onDebugUnlocked()
                                    }
                                },
                    ) {
                        SectionHeader(
                            title = stringResource(R.string.settings_about),
                            subtitle = stringResource(R.string.settings_about_sub),
                            icon = Icons.Filled.Info,
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        TextButton(onClick = onOpenPrivacy) {
                            Text(stringResource(R.string.settings_privacy_policy))
                        }
                        Text(
                            text = stringResource(R.string.settings_about_permissions),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = stringResource(R.string.settings_alert_disclaimer_title),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = stringResource(R.string.settings_alert_disclaimer_body),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SettingsSwitchRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                subtitle,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun SettingsChoiceRow(
    title: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            color =
                if (selected) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurface
                },
        )
        if (selected) {
            Text("✓", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
        }
    }
}

package com.xiaoxiong.weather.ui.settings

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ShowChart
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.xiaoxiong.weather.R
import com.xiaoxiong.weather.data.AppSettings
import com.xiaoxiong.weather.data.AppSettingsStore
import com.xiaoxiong.weather.data.HourlyCardLayout
import com.xiaoxiong.weather.ui.weather.AppScreenBackground
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/** 小时页卡片设置屏：交互逻辑复用通用 [CardsSettingsScreen]。 */
@Composable
fun HourlyCardsSettingsScreen(
    settingsStore: AppSettingsStore,
    scope: CoroutineScope,
    onBack: () -> Unit,
) {
    val settings by settingsStore.settings.collectAsStateWithLifecycle(
        initialValue = AppSettings(),
    )
    AppScreenBackground(modifier = Modifier.fillMaxSize()) {
        CardsSettingsScreen(
            config =
                CardsSettingsConfig(
                    layout = HourlyCardLayout,
                    titleRes = R.string.settings_hourly_cards_title,
                    sectionTitleRes = R.string.settings_hourly_cards,
                    hintRes = R.string.settings_hourly_cards_hint,
                    lockedHintRes = R.string.settings_hourly_cards_locked,
                    resetRes = R.string.settings_hourly_cards_reset,
                    icon = Icons.AutoMirrored.Filled.ShowChart,
                ),
            savedCards = settings.hourlyCards,
            onCardsChange = { next -> scope.launch { settingsStore.setHourlyCards(next) } },
            onReset = { scope.launch { settingsStore.resetHourlyCards() } },
            onBack = onBack,
        )
    }
}

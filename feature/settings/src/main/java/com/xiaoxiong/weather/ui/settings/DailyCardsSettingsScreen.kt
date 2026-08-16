package com.xiaoxiong.weather.ui.settings

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.xiaoxiong.weather.R
import com.xiaoxiong.weather.data.AppSettings
import com.xiaoxiong.weather.data.AppSettingsStore
import com.xiaoxiong.weather.data.DailyCardLayout
import com.xiaoxiong.weather.ui.weather.AppScreenBackground
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/** 七日页卡片设置屏：交互逻辑复用通用 [CardsSettingsScreen]。 */
@Composable
fun DailyCardsSettingsScreen(
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
                    layout = DailyCardLayout,
                    titleRes = R.string.settings_daily_cards_title,
                    sectionTitleRes = R.string.settings_daily_cards,
                    hintRes = R.string.settings_daily_cards_hint,
                    lockedHintRes = R.string.settings_daily_cards_locked,
                    resetRes = R.string.settings_daily_cards_reset,
                    icon = Icons.Filled.CalendarMonth,
                ),
            savedCards = settings.dailyCards,
            onCardsChange = { next -> scope.launch { settingsStore.setDailyCards(next) } },
            onReset = { scope.launch { settingsStore.resetDailyCards() } },
            onBack = onBack,
        )
    }
}

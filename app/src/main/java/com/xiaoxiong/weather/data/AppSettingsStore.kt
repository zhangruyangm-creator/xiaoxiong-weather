package com.xiaoxiong.weather.data

import android.content.Context
import androidx.annotation.StringRes
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.xiaoxiong.weather.R
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.appSettingsDataStore by preferencesDataStore("app_settings_v1")

enum class ThemeMode {
    SYSTEM, LIGHT, DARK
}

enum class TemperatureUnit {
    CELSIUS, FAHRENHEIT
}

/** 仅影响本 App 的 Compose 字号，不改系统设置。 */
enum class AppFontScale(@StringRes val labelRes: Int) {
    FOLLOW_SYSTEM(R.string.font_follow_system),
    SMALL(R.string.font_small),
    STANDARD(R.string.font_standard),
    LARGE(R.string.font_large),
    EXTRA_LARGE(R.string.font_extra_large);

    /** @param systemFontScale 系统当前 fontScale */
    fun resolve(systemFontScale: Float): Float = when (this) {
        FOLLOW_SYSTEM -> systemFontScale.coerceIn(0.85f, 1.45f)
        SMALL -> 0.90f
        STANDARD -> 1.0f
        LARGE -> 1.12f
        EXTRA_LARGE -> 1.25f
    }
}

data class AppSettings(
    val notificationsEnabled: Boolean = true,
    val temperatureUnit: TemperatureUnit = TemperatureUnit.CELSIUS,
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val fontScale: AppFontScale = AppFontScale.STANDARD,
    val preferLocationOnLaunch: Boolean = true,
    val privacyAccepted: Boolean = false,
    val backgroundRefreshInterval: BackgroundRefreshInterval = BackgroundRefreshInterval.HOURS_3,
    val todayCards: List<TodayCardPref> = TodayCardLayout.DEFAULT
)

class AppSettingsStore(private val context: Context) : AppSettingsSource {
    override val settings: Flow<AppSettings> = context.appSettingsDataStore.data.map { prefs ->
        AppSettings(
            notificationsEnabled = prefs[KEY_NOTIFICATIONS] ?: true,
            temperatureUnit = prefs[KEY_TEMP_UNIT]?.let {
                runCatching { TemperatureUnit.valueOf(it) }.getOrDefault(TemperatureUnit.CELSIUS)
            } ?: TemperatureUnit.CELSIUS,
            themeMode = prefs[KEY_THEME]?.let {
                runCatching { ThemeMode.valueOf(it) }.getOrDefault(ThemeMode.SYSTEM)
            } ?: ThemeMode.SYSTEM,
            // 默认「标准」：避免系统超大字体导致布局裁切；需要时可改跟随系统
            fontScale = prefs[KEY_FONT_SCALE]?.let {
                runCatching { AppFontScale.valueOf(it) }.getOrDefault(AppFontScale.STANDARD)
            } ?: AppFontScale.STANDARD,
            preferLocationOnLaunch = prefs[KEY_PREFER_LOCATION] ?: true,
            privacyAccepted = prefs[KEY_PRIVACY] ?: false,
            backgroundRefreshInterval = BackgroundRefreshInterval.fromHours(
                prefs[KEY_BG_REFRESH_HOURS] ?: WeatherRefreshPolicy.BACKGROUND_HOURS
            ),
            todayCards = TodayCardLayout.decode(prefs[KEY_TODAY_CARDS])
        )
    }

    override suspend fun getOnce(): AppSettings = settings.first()

    suspend fun setNotificationsEnabled(enabled: Boolean) {
        context.appSettingsDataStore.edit { it[KEY_NOTIFICATIONS] = enabled }
    }

    suspend fun setTemperatureUnit(unit: TemperatureUnit) {
        context.appSettingsDataStore.edit { it[KEY_TEMP_UNIT] = unit.name }
    }

    suspend fun setThemeMode(mode: ThemeMode) {
        context.appSettingsDataStore.edit { it[KEY_THEME] = mode.name }
    }

    suspend fun setFontScale(scale: AppFontScale) {
        context.appSettingsDataStore.edit { it[KEY_FONT_SCALE] = scale.name }
    }

    suspend fun setPreferLocationOnLaunch(prefer: Boolean) {
        context.appSettingsDataStore.edit { it[KEY_PREFER_LOCATION] = prefer }
    }

    suspend fun setPrivacyAccepted(accepted: Boolean) {
        context.appSettingsDataStore.edit { it[KEY_PRIVACY] = accepted }
    }

    suspend fun setBackgroundRefreshInterval(interval: BackgroundRefreshInterval) {
        context.appSettingsDataStore.edit { it[KEY_BG_REFRESH_HOURS] = interval.hours }
    }

    suspend fun setTodayCards(prefs: List<TodayCardPref>) {
        context.appSettingsDataStore.edit {
            it[KEY_TODAY_CARDS] = TodayCardLayout.encode(prefs)
        }
    }

    suspend fun resetTodayCards() {
        context.appSettingsDataStore.edit {
            it[KEY_TODAY_CARDS] = TodayCardLayout.encode(TodayCardLayout.DEFAULT)
        }
    }

    companion object {
        private val KEY_NOTIFICATIONS = booleanPreferencesKey("notifications_enabled")
        private val KEY_TEMP_UNIT = stringPreferencesKey("temperature_unit")
        private val KEY_THEME = stringPreferencesKey("theme_mode")
        private val KEY_FONT_SCALE = stringPreferencesKey("font_scale")
        private val KEY_PREFER_LOCATION = booleanPreferencesKey("prefer_location")
        private val KEY_PRIVACY = booleanPreferencesKey("privacy_accepted")
        private val KEY_BG_REFRESH_HOURS = intPreferencesKey("bg_refresh_hours")
        private val KEY_TODAY_CARDS = stringPreferencesKey("today_cards")
    }
}

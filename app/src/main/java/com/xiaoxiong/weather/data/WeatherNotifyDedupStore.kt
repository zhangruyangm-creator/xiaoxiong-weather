package com.xiaoxiong.weather.data

import android.content.Context
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first
import kotlin.math.abs

private val Context.weatherNotifyDedupDataStore by preferencesDataStore("weather_notify_dedup")

/**
 * 后台刷新通知去重：避免 WorkManager 周期触发时反复推送同一条通知。
 *
 * 用 DataStore 而非 SharedPreferences，与 App 其余持久化保持一致；
 * Worker 在 doWork 协程内调用，挂起开销可接受。
 */
class WeatherNotifyDedupStore(
    private val context: Context,
) {
    private val store = context.weatherNotifyDedupDataStore

    suspend fun shouldNotify(
        cityName: String,
        weatherCode: Int?,
        temperatureC: Double?,
    ): Boolean {
        val prefs = store.data.first()
        val lastCity = prefs[KEY_CITY]
        val lastCode = prefs[KEY_CODE]
        val lastTemp = prefs[KEY_TEMP]
        val lastAt = prefs[KEY_AT] ?: 0L
        val now = System.currentTimeMillis()

        if (lastAt == 0L || lastCity == null) return true
        if (cityName != lastCity) return true

        // 天气码缺失（接口坏包等）时不算「变化」，避免对内容为 -- 的快照重复推送
        val codeChanged = weatherCode != null && weatherCode != lastCode
        val tempChanged =
            temperatureC != null &&
                lastTemp != null &&
                abs(temperatureC - lastTemp) >= TEMP_DELTA_C
        // 恶劣天气超过静默窗口可再提醒一次；普通天气只在码/气温变化时提醒
        val severeReminder = weatherCode in SEVERE_CODES && now - lastAt >= FORCE_NOTIFY_AFTER_MS

        return codeChanged || tempChanged || severeReminder
    }

    suspend fun rememberNotified(
        cityName: String,
        weatherCode: Int?,
        temperatureC: Double?,
    ) {
        store.edit { prefs ->
            prefs[KEY_CITY] = cityName
            prefs[KEY_CODE] = weatherCode ?: Int.MIN_VALUE
            temperatureC?.let { prefs[KEY_TEMP] = it }
            prefs[KEY_AT] = System.currentTimeMillis()
        }
    }

    /** 临近降水提醒按「城市+类型+文案」去重，签名不变时 6 小时内只发一次。 */
    suspend fun shouldNotifySoon(
        cityName: String,
        signature: String,
    ): Boolean {
        val prefs = store.data.first()
        val lastSig = prefs[KEY_SOON_SIG]
        val lastAt = prefs[KEY_SOON_AT] ?: 0L
        return signature != lastSig ||
            System.currentTimeMillis() - lastAt >= FORCE_NOTIFY_AFTER_MS
    }

    suspend fun rememberNotifiedSoon(signature: String) {
        store.edit { prefs ->
            prefs[KEY_SOON_SIG] = signature
            prefs[KEY_SOON_AT] = System.currentTimeMillis()
        }
    }

    companion object {
        private val KEY_CITY = stringPreferencesKey("city")
        private val KEY_CODE = intPreferencesKey("code")
        private val KEY_TEMP = doublePreferencesKey("temp")
        private val KEY_AT = longPreferencesKey("at")
        private val KEY_SOON_SIG = stringPreferencesKey("soon_sig")
        private val KEY_SOON_AT = longPreferencesKey("soon_at")

        private const val TEMP_DELTA_C = 2.0

        /** 恶劣天气/临近降水提醒的静默窗口；到期可再提醒 */
        private const val FORCE_NOTIFY_AFTER_MS = 6 * 60 * 60 * 1000L
        private val SEVERE_CODES = setOf(65, 67, 75, 82, 86, 95, 96, 99)
    }
}

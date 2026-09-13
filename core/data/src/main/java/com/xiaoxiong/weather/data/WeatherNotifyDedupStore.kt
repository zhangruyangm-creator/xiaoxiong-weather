package com.xiaoxiong.weather.data

import android.content.Context
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlin.math.abs

private val Context.weatherNotifyDedupDataStore by preferencesDataStore("weather_notify_dedup")

/** 恶劣天气/临近降水提醒的静默窗口；到期可再提醒。 */
private const val NOTIFY_SILENCE_WINDOW_MS = 6 * 60 * 60 * 1000L

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

    /**
     * 判定是否要推送「天气变化」通知，并在同一笔 DataStore edit 内原子落盘本轮状态。
     * 此前是 shouldNotify（读）→ 推送 → rememberNotified（写）三步，并发重叠时可能重复推送；
     * 现在判定与记录在同一次 edit 里，且仅在判定为「要推」时才改写状态（语义与旧实现一致）。
     */
    suspend fun shouldNotifyAndRemember(
        cityName: String,
        weatherCode: Int?,
        temperatureC: Double?,
    ): Boolean {
        var should = false
        // 判定与落盘在同一个 updateData 事务内：并发重叠时不会出现
        // 「A 判定要推 → B 判定要推 → A/B 各推一条」的重复推送窗口
        store.updateData { prefs ->
            val lastCity = prefs[KEY_CITY]
            val lastCode = prefs[KEY_CODE]
            val lastTemp = prefs[KEY_TEMP]
            val lastAt = prefs[KEY_AT] ?: 0L
            val now = System.currentTimeMillis()

            val firstEver = lastAt == 0L || lastCity == null
            val cityChanged = cityName != lastCity
            // 天气码缺失（接口坏包等）时不算「变化」，避免对内容为 -- 的快照重复推送
            val codeChanged = weatherCode != null && weatherCode != lastCode
            val tempChanged =
                temperatureC != null &&
                    lastTemp != null &&
                    abs(temperatureC - lastTemp) >= TEMP_DELTA_C
            // 恶劣天气超过静默窗口可再提醒一次；普通天气只在码/气温变化时提醒
            val severeReminder = weatherCode in SEVERE_CODES && now - lastAt >= NOTIFY_SILENCE_WINDOW_MS
            should = firstEver || cityChanged || codeChanged || tempChanged || severeReminder

            // 仅在判定为「要推」时才改写状态（语义与旧实现一致）
            if (!should) return@updateData prefs
            prefs.toMutablePreferences().apply {
                this[KEY_CITY] = cityName
                this[KEY_CODE] = weatherCode ?: Int.MIN_VALUE
                if (temperatureC != null) {
                    this[KEY_TEMP] = temperatureC
                } else {
                    // 温度缺失时清掉旧值，避免下一轮用过期温度误判「变化」
                    this -= KEY_TEMP
                }
                this[KEY_AT] = now
            }
        }
        return should
    }

    /**
     * 临近降水提醒按「城市+类型+文案」去重，签名不变时 6 小时内只发一次。
     * 判定与落盘在同一事务内原子进行（同 [shouldNotifyAndRemember]）。
     */
    suspend fun shouldNotifySoonAndRemember(
        cityName: String,
        signature: String,
    ): Boolean {
        var should = false
        store.updateData { prefs ->
            val lastSig = prefs[KEY_SOON_SIG]
            val lastAt = prefs[KEY_SOON_AT] ?: 0L
            val now = System.currentTimeMillis()
            should = signature != lastSig || now - lastAt >= NOTIFY_SILENCE_WINDOW_MS
            if (!should) return@updateData prefs
            prefs.toMutablePreferences().apply {
                this[KEY_SOON_SIG] = signature
                this[KEY_SOON_AT] = now
            }
        }
        return should
    }

    /**
     * 批量严重预警去重：一次原子事务内判定并记录本批签名，返回本轮应推送的签名。
     *
     * 与 [shouldNotifySoonAndRemember] 的单签名槽不同：每个签名独立记录时间戳。
     * 同一轮的多条严重预警（如高温 + 大雾）不会互相覆盖，也不会在下一轮被误判为
     * 「新预警」重复推送；返回空列表表示本批全部处于静默窗口内。
     */
    suspend fun shouldNotifyAlertsAndRemember(signatures: List<String>): List<String> {
        if (signatures.isEmpty()) return emptyList()
        var pending: List<String> = emptyList()
        store.updateData { prefs ->
            val now = System.currentTimeMillis()
            val remembered = SevereAlertNotifyMemory.parse(prefs[KEY_ALERT_AT], now)
            pending =
                signatures.filter { signature ->
                    val lastAt = remembered[signature]
                    lastAt == null || now - lastAt >= NOTIFY_SILENCE_WINDOW_MS
                }
            if (pending.isEmpty()) return@updateData prefs
            val merged = remembered.toMutableMap()
            pending.forEach { merged[it] = now }
            prefs.toMutablePreferences().apply {
                this[KEY_ALERT_AT] = SevereAlertNotifyMemory.encode(merged)
            }
        }
        return pending
    }

    companion object {
        private val KEY_CITY = stringPreferencesKey("city")
        private val KEY_CODE = intPreferencesKey("code")
        private val KEY_TEMP = doublePreferencesKey("temp")
        private val KEY_AT = longPreferencesKey("at")
        private val KEY_SOON_SIG = stringPreferencesKey("soon_sig")
        private val KEY_SOON_AT = longPreferencesKey("soon_at")
        private val KEY_ALERT_AT = stringSetPreferencesKey("alert_sigs")

        private const val TEMP_DELTA_C = 2.0
        private val SEVERE_CODES = setOf(65, 67, 75, 82, 86, 95, 96, 99)
    }
}

/**
 * 严重预警签名记忆的编解码：DataStore 中每项为 `签名\t时间戳`。
 * 解析时丢弃超过静默窗口的条目，避免集合随预警种类无限增长；纯逻辑，可 JVM 测试。
 */
internal object SevereAlertNotifyMemory {
    fun parse(
        entries: Set<String>?,
        now: Long,
    ): Map<String, Long> {
        if (entries.isNullOrEmpty()) return emptyMap()
        val parsed = mutableMapOf<String, Long>()
        entries.forEach { entry ->
            val separator = entry.lastIndexOf(ENTRY_SEPARATOR)
            if (separator <= 0) return@forEach
            val signature = entry.substring(0, separator)
            val at = entry.substring(separator + 1).toLongOrNull() ?: return@forEach
            // at > now（时钟回拨）也保留：宁可静默也不能重复轰炸
            if (at > now - NOTIFY_SILENCE_WINDOW_MS) parsed[signature] = at
        }
        return parsed
    }

    fun encode(entries: Map<String, Long>): Set<String> =
        entries.mapTo(mutableSetOf()) { (signature, at) -> "$signature$ENTRY_SEPARATOR$at" }

    private const val ENTRY_SEPARATOR = '\t'
}

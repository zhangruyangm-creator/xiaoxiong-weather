package com.xiaoxiong.weather.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.math.abs

private val Context.weatherNotifyDedupDataStore by preferencesDataStore(
    name = "weather_notify_dedup",
    corruptionHandler = corruptionHandlerLogging("weather_notify_dedup"),
)

/** 恶劣天气/临近降水提醒的静默窗口；到期可再提醒。 */
private const val NOTIFY_SILENCE_WINDOW_MS = 6 * 60 * 60 * 1000L

private const val TEMP_DELTA_C = 2.0

private val SEVERE_CODES = setOf(65, 67, 75, 82, 86, 95, 96, 99)

/**
 * 后台刷新通知去重：避免 WorkManager 周期触发时反复推送同一条通知。
 *
 * 用 DataStore 而非 SharedPreferences，与 App 其余持久化保持一致；
 * Worker 在 doWork 协程内调用，挂起开销可接受。
 */
class WeatherNotifyDedupStore internal constructor(
    private val store: DataStore<Preferences>,
) {
    constructor(context: Context) : this(context.weatherNotifyDedupDataStore)

    /** 进程内串行化「判定 → 展示 → 写记忆」整段，消除拆分后的 TOCTOU 并发窗口。 */
    private val dedupGate = NotifyDedupGate()

    /**
     * 「天气变化」通知的原子流程：锁内判定，仅当 [post] 展示成功才写记忆。
     * 系统不允许展示通知时调用方不应进入本方法（渠道关闭期间的提醒不写记忆）。
     * [post] 只做通知展示（NotificationManager IPC），不得做网络/PM 操作。
     *
     * @return 是否实际展示
     */
    suspend fun notifyWeatherChangeIfDue(
        cityName: String,
        weatherCode: Int?,
        temperatureC: Double?,
        post: suspend () -> Boolean,
    ): Boolean =
        dedupGate.run {
            if (!shouldNotifyLocked(cityName, weatherCode, temperatureC)) return@run false
            if (!post()) return@run false
            rememberNotifiedLocked(cityName, weatherCode, temperatureC)
            true
        }

    /**
     * 批量严重预警的原子流程：锁内过滤静默签名后交给 [post] 展示；
     * 仅对展示成功的签名写记忆，全部展示失败则不写任何记忆，
     * 调用方可继续走通用天气变化通知兜底（不会静默 6 小时）。
     * 展示成功后同步通用去重基线，避免同一轮再重复推一条通用通知。
     *
     * [post] 只做通知展示，不得做网络/PM 操作。
     *
     * @return 实际展示成功的签名（为空表示本轮未展示任何预警）
     */
    suspend fun notifyAlertsIfDue(
        cityName: String,
        weatherCode: Int?,
        temperatureC: Double?,
        signatures: List<String>,
        post: suspend (List<String>) -> List<String>,
    ): List<String> =
        dedupGate.run {
            if (signatures.isEmpty()) return@run emptyList()
            val pending = filterAlertsLocked(signatures)
            if (pending.isEmpty()) return@run emptyList()
            val posted = post(pending)
            if (posted.isEmpty()) return@run emptyList()
            rememberNotifiedAlertsLocked(posted)
            // 同步通用去重基线：严重预警已推送后，通用分支不再立刻重复推一条
            rememberNotifiedLocked(cityName, weatherCode, temperatureC)
            posted
        }

    /** 「天气变化」只读判定，必须在 [dedupGate] 内调用。 */
    private suspend fun shouldNotifyLocked(
        cityName: String,
        weatherCode: Int?,
        temperatureC: Double?,
    ): Boolean {
        val prefs = store.data.first()
        return shouldNotifyWeatherChange(
            cityName = cityName,
            weatherCode = weatherCode,
            temperatureC = temperatureC,
            lastCity = prefs[KEY_CITY],
            lastCode = prefs[KEY_CODE],
            lastTemp = prefs[KEY_TEMP],
            lastAt = prefs[KEY_AT] ?: 0L,
            now = System.currentTimeMillis(),
        )
    }

    /** 通用通知状态落盘，必须在 [dedupGate] 内调用。 */
    private suspend fun rememberNotifiedLocked(
        cityName: String,
        weatherCode: Int?,
        temperatureC: Double?,
    ) {
        store.updateData { prefs ->
            val now = System.currentTimeMillis()
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
    }

    /** 批量严重预警判定（只读），必须在 [dedupGate] 内调用。 */
    private suspend fun filterAlertsLocked(signatures: List<String>): List<String> {
        val now = System.currentTimeMillis()
        val remembered = SevereAlertNotifyMemory.parse(store.data.first()[KEY_ALERT_AT], now)
        return filterAlertSignaturesToNotify(signatures, remembered, now)
    }

    /** 严重预警签名落盘，必须在 [dedupGate] 内调用。 */
    private suspend fun rememberNotifiedAlertsLocked(signatures: List<String>) {
        store.updateData { prefs ->
            val now = System.currentTimeMillis()
            val remembered = SevereAlertNotifyMemory.parse(prefs[KEY_ALERT_AT], now)
            val merged = remembered.toMutableMap()
            signatures.forEach { merged[it] = now }
            prefs.toMutablePreferences().apply {
                this[KEY_ALERT_AT] = SevereAlertNotifyMemory.encode(merged)
            }
        }
    }

    companion object {
        private val KEY_CITY = stringPreferencesKey("city")
        private val KEY_CODE = intPreferencesKey("code")
        private val KEY_TEMP = doublePreferencesKey("temp")
        private val KEY_AT = longPreferencesKey("at")
        private val KEY_ALERT_AT = stringSetPreferencesKey("alert_sigs")
    }
}

/**
 * 进程内通知去重门闩：把「判定 → 展示 → 写记忆」整段串行化。
 *
 * 为保证「系统拦截通知时不写记忆」而把原子 updateData 拆成只读判定 + 成功后落盘，
 * 同时也重新引入了 check-then-act 并发窗口：两个并发调用可能同时判定通过、各推一条。
 * 此锁保证同一时刻只有一个调用在推进流程，后到者在锁内重新判定时已被先到者写下的
 * 记忆挡住，不会重复推送。
 *
 * 锁内只允许通知展示（NotificationManager IPC）；[Mutex.withLock] 在取消/异常时
 * 必定释放锁，不得在锁内做网络/PM 等长耗时操作。
 */
internal class NotifyDedupGate {
    private val mutex = Mutex()

    suspend fun <T> run(block: suspend () -> T): T = mutex.withLock { block() }
}

/**
 * 「天气变化」通知判定（纯逻辑）：首轮、换城、天气码或气温变化、恶劣天气过静默窗口时提醒。
 * 与旧实现的判定语义一致，拆出后调用方可先看系统是否允许展示再决定是否落盘。
 */
internal fun shouldNotifyWeatherChange(
    cityName: String,
    weatherCode: Int?,
    temperatureC: Double?,
    lastCity: String?,
    lastCode: Int?,
    lastTemp: Double?,
    lastAt: Long,
    now: Long,
): Boolean {
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
    return firstEver || cityChanged || codeChanged || tempChanged || severeReminder
}

/** 严重预警去重判定（纯逻辑）：保留不在静默窗口内的签名；at > now（时钟回拨）同样静默。 */
internal fun filterAlertSignaturesToNotify(
    signatures: List<String>,
    remembered: Map<String, Long>,
    now: Long,
): List<String> =
    signatures.filter { signature ->
        val lastAt = remembered[signature]
        lastAt == null || now - lastAt >= NOTIFY_SILENCE_WINDOW_MS
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

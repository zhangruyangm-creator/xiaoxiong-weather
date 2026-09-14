package com.xiaoxiong.weather.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.xiaoxiong.weather.util.indexOfCurrentHour
import kotlinx.coroutines.flow.first
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import java.time.LocalDate
import java.time.ZoneId
import java.util.Locale

private val Context.weatherCacheDataStore by preferencesDataStore(
    name = "weather_cache_v1",
    corruptionHandler = corruptionHandlerLogging("weather_cache_v1"),
)

/**
 * 天气快照缓存。
 *
 * 每个城市一个 DataStore key（`city_<savedAt>_<lat>_<lon>`）：
 * - 写入只 encode 该城市，并只清理同城旧键/过期键，payload 与耗时不再随城市数增长；
 * - 读取先按 key 前缀扫描（只解析 key 上的时间/坐标，不 decode 其他城市），命中后才 decode；
 * - 首次访问自动把旧版单键列表 `entries_json` 迁移为按城 key 并删除旧键；
 * - 保存时裁剪 hourly 到「当前小时前 6h ~ 后 48h」（缓存 24h 内回退时仍有未来 24h 数据），
 *   同时剔除过期键并限制城市数上限。
 */
class WeatherCacheStore internal constructor(
    private val dataStore: DataStore<Preferences>,
) : WeatherCache {
    constructor(context: Context) : this(context.weatherCacheDataStore)

    private val json =
        Json {
            ignoreUnknownKeys = true
            encodeDefaults = true
            // 缓存读旧格式 / 缺字段：允许 null 编码缺失字段，类型漂移归零值
            explicitNulls = false
            coerceInputValues = true
        }

    override suspend fun save(snapshot: WeatherSnapshot) {
        val now = System.currentTimeMillis()
        val entry =
            CacheEntry(
                snapshot = trimSnapshotForCache(snapshot),
                savedAtEpochMs = now,
            )
        dataStore.edit { prefs ->
            migrateLegacyLocked(prefs, now)
            // 写前清理：同城旧键 + 过期键；只按 key 元数据判断，不 decode 其他城市
            prefs
                .cityEntryKeys()
                .filter { key ->
                    !isWithinFreshnessWindow(key.savedAtEpochMs, now, MAX_AGE_MS) ||
                        sameCacheCity(
                            key.latitude,
                            key.longitude,
                            snapshot.city.latitude,
                            snapshot.city.longitude,
                        )
                }.forEach { prefs.remove(stringPreferencesKey(it.name)) }
            prefs[cacheEntryKey(now, snapshot.city)] = encodeEntry(entry)
            pruneOverflowLocked(prefs)
        }
    }

    override suspend fun get(city: City): CacheEntry? {
        val initial = dataStore.data.first()
        val prefs =
            if (initial.contains(LEGACY_KEY)) {
                val now = System.currentTimeMillis()
                dataStore.edit { mutable -> migrateLegacyLocked(mutable, now) }
                dataStore.data.first()
            } else {
                initial
            }
        val now = System.currentTimeMillis()
        // 只 decode 坐标匹配的候选（正常只有 1 个）；解出的城市再做一次容差校验兜底
        val candidates =
            prefs
                .cityEntryKeys()
                .filter {
                    isWithinFreshnessWindow(it.savedAtEpochMs, now, MAX_AGE_MS) &&
                        sameCacheCity(it.latitude, it.longitude, city.latitude, city.longitude)
                }.sortedByDescending { it.savedAtEpochMs }
        for (candidate in candidates) {
            val raw = prefs[stringPreferencesKey(candidate.name)] ?: continue
            val entry = decodeEntry(raw) ?: continue
            if (!isWithinFreshnessWindow(entry.savedAtEpochMs, now, MAX_AGE_MS)) continue
            if (!sameCacheCity(entry.snapshot.city, city)) continue
            // 离线跨天：缓存里的 daily[0] 仍是昨天，返回前按快照时区裁掉过期日
            return entry.copy(snapshot = dropStaleDaily(entry.snapshot))
        }
        return null
    }

    private fun migrateLegacyLocked(
        prefs: MutablePreferences,
        nowEpochMs: Long,
    ) {
        val raw = prefs[LEGACY_KEY] ?: return
        prefs.remove(LEGACY_KEY)
        migrateLegacyEntries(decodeEntries(raw), nowEpochMs).forEach { (name, entry) ->
            prefs[stringPreferencesKey(name)] = encodeEntry(entry)
        }
    }

    private fun pruneOverflowLocked(prefs: MutablePreferences) {
        val keys = prefs.cityEntryKeys()
        if (keys.size <= MAX_ENTRIES) return
        keys
            .sortedByDescending { it.savedAtEpochMs }
            .drop(MAX_ENTRIES)
            .forEach { prefs.remove(stringPreferencesKey(it.name)) }
    }

    private fun cacheEntryKey(
        savedAtEpochMs: Long,
        city: City,
    ) = stringPreferencesKey(cacheEntryKeyName(savedAtEpochMs, city.latitude, city.longitude))

    private fun encodeEntry(entry: CacheEntry): String = json.encodeToString(CacheEntry.serializer(), entry)

    private fun decodeEntry(raw: String): CacheEntry? = runCatching { json.decodeFromString(CacheEntry.serializer(), raw) }.getOrNull()

    private fun decodeEntries(raw: String?): List<CacheEntry> {
        if (raw.isNullOrBlank()) return emptyList()
        return runCatching {
            json.decodeFromString(ListSerializer(CacheEntry.serializer()), raw)
        }.getOrDefault(emptyList())
    }

    @Serializable
    data class CacheEntry(
        val snapshot: WeatherSnapshot,
        val savedAtEpochMs: Long,
    )

    companion object {
        private const val MAX_ENTRIES = 12

        /** 缓存条目最大存活时长：超过则 [get] 视为无缓存（离线回退不展示过期数据）。 */
        internal const val MAX_AGE_MS = 24L * 60 * 60 * 1000

        private val LEGACY_KEY = stringPreferencesKey("entries_json")
    }
}

/** 缓存 key 里的城市元数据；坐标写在 key 上，扫描时无需 decode payload。 */
internal data class CachedCityKey(
    val name: String,
    val savedAtEpochMs: Long,
    val latitude: Double,
    val longitude: Double,
)

internal const val CACHE_CITY_KEY_PREFIX = "city_"

/** 保存时保留当前小时之前的小时数（供降水回顾柱状图）。 */
internal const val CACHE_HOURLY_PAST_HOURS = 6

/** 保存时保留当前小时之后的小时数；缓存可存活 24h，仍能覆盖回退时的未来 24h。 */
internal const val CACHE_HOURLY_FUTURE_HOURS = 48

private val CACHE_CITY_KEY_REGEX =
    Regex("""^${CACHE_CITY_KEY_PREFIX}(\d+)_(-?\d+(?:\.\d+)?)_(-?\d+(?:\.\d+)?)$""")

private fun formatCoordinate(value: Double): String = String.format(Locale.US, "%.4f", value)

/** 每城 key：`city_<savedAt>_<lat>_<lon>`。 */
internal fun cacheEntryKeyName(
    savedAtEpochMs: Long,
    latitude: Double,
    longitude: Double,
): String = "$CACHE_CITY_KEY_PREFIX${savedAtEpochMs}_${formatCoordinate(latitude)}_${formatCoordinate(longitude)}"

internal fun parseCacheEntryKey(name: String): CachedCityKey? {
    val match = CACHE_CITY_KEY_REGEX.matchEntire(name) ?: return null
    val savedAt = match.groupValues[1].toLongOrNull() ?: return null
    val latitude = match.groupValues[2].toDoubleOrNull() ?: return null
    val longitude = match.groupValues[3].toDoubleOrNull() ?: return null
    return CachedCityKey(name, savedAt, latitude, longitude)
}

internal fun sameCacheCity(
    a: City,
    b: City,
): Boolean = a.isSamePlace(b)

internal fun sameCacheCity(
    latA: Double,
    lonA: Double,
    latB: Double,
    lonB: Double,
): Boolean = isSamePlace(latA, lonA, latB, lonB)

/** 落盘前裁剪 hourly：只保留当前小时前 [CACHE_HOURLY_PAST_HOURS] 到后 [CACHE_HOURLY_FUTURE_HOURS]。 */
internal fun trimSnapshotForCache(snapshot: WeatherSnapshot): WeatherSnapshot {
    val hourly = snapshot.hourly
    if (hourly.isEmpty()) return snapshot
    val currentIndex = indexOfCurrentHour(hourly.map { it.time }, snapshot.current.time)
    if (currentIndex < 0) return snapshot
    val from = (currentIndex - CACHE_HOURLY_PAST_HOURS).coerceAtLeast(0)
    val to = (currentIndex + CACHE_HOURLY_FUTURE_HOURS + 1).coerceAtMost(hourly.size)
    if (from == 0 && to == hourly.size) return snapshot
    return snapshot.copy(hourly = hourly.subList(from, to))
}

/**
 * 快照所属时区的「今天」：timezone 缺失或非法时回退 [fallbackZone]（默认系统时区）。
 * 纯逻辑，便于单测跨天/回退边界。
 */
internal fun todayInSnapshotZone(
    timezoneId: String?,
    fallbackZone: ZoneId = ZoneId.systemDefault(),
): LocalDate {
    val zone = runCatching { timezoneId?.let { ZoneId.of(it) } }.getOrNull() ?: fallbackZone
    return LocalDate.now(zone)
}

/**
 * 裁掉早于「今天」的 daily 条目（按快照时区判定）。
 *
 * 离线跨午夜后读取缓存时，daily[0] 仍是昨天，会让首页高低温、七日页「今天」、
 * UV/预警整体错位一天。全部条目都过期时保留最后一条，避免消费方拿到空列表。
 */
internal fun dropStaleDaily(
    snapshot: WeatherSnapshot,
    today: LocalDate = todayInSnapshotZone(snapshot.timezone),
): WeatherSnapshot {
    val daily = snapshot.daily
    if (daily.isEmpty()) return snapshot
    val fresh = daily.filter { it.isOnOrAfter(today) }
    if (fresh.size == daily.size) return snapshot
    return snapshot.copy(daily = fresh.ifEmpty { listOf(daily.last()) })
}

/** date 为 ISO 日期；无法解析时保守保留，不做跨天丢弃。 */
private fun DailyWeather.isOnOrAfter(today: LocalDate): Boolean {
    val parsed = runCatching { LocalDate.parse(date) }.getOrNull() ?: return true
    return !parsed.isBefore(today)
}

/**
 * 旧版单键列表迁移：按城市去重（同城保留最新）、剔除过期条目，生成按城 key → entry。
 * 返回 Map 而不是直接写 DataStore，保持纯逻辑可测。
 */
internal fun migrateLegacyEntries(
    legacy: List<WeatherCacheStore.CacheEntry>,
    nowEpochMs: Long,
): Map<String, WeatherCacheStore.CacheEntry> {
    val migrated = LinkedHashMap<String, WeatherCacheStore.CacheEntry>()
    val keptCities = mutableListOf<City>()
    legacy.sortedByDescending { it.savedAtEpochMs }.forEach { entry ->
        val city = entry.snapshot.city
        if (!isWithinFreshnessWindow(entry.savedAtEpochMs, nowEpochMs, WeatherCacheStore.MAX_AGE_MS)) return@forEach
        if (keptCities.any { sameCacheCity(it, city) }) return@forEach
        keptCities += city
        migrated[cacheEntryKeyName(entry.savedAtEpochMs, city.latitude, city.longitude)] = entry
    }
    return migrated
}

private fun Preferences.cityEntryKeys(): List<CachedCityKey> = asMap().keys.mapNotNull { parseCacheEntryKey(it.name) }

/**
 * 缓存新鲜度：年龄必须落在 [0, maxAgeMs]。
 * savedAt 在未来（时钟回拨/异常写入）视为无效，否则回拨后缓存会永久不过期。
 */
internal fun isWithinFreshnessWindow(
    savedAtEpochMs: Long,
    nowEpochMs: Long,
    maxAgeMs: Long,
): Boolean {
    val age = nowEpochMs - savedAtEpochMs
    return age in 0..maxAgeMs
}

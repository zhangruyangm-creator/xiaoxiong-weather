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

    override suspend fun save(
        snapshot: WeatherSnapshot,
        presentationStamp: String,
        fetchedAtEpochMs: Long,
    ) {
        val now = System.currentTimeMillis()
        val entry =
            CacheEntry(
                snapshot = trimSnapshotForCache(snapshot),
                savedAtEpochMs = now,
                presentationStamp = presentationStamp,
                fetchedAtEpochMs = fetchedAtEpochMs,
            )
        dataStore.edit { prefs ->
            migrateLegacyLocked(prefs, now)
            // 新旧保护必须**先于**写前清理：
            // 清理会删掉同城旧键（含尚未被覆盖的那条新数据），而 target 用当前毫秒
            // 组建、本身几乎不存在，若在清理之后才比对，既读不到可比对象（保护失效），
            // 又已经把好数据删了。
            val keys = prefs.cityEntryKeys()
            val existingFetchedAt =
                keys
                    .firstOrNull {
                        sameCacheCity(
                            it.latitude,
                            it.longitude,
                            snapshot.city.latitude,
                            snapshot.city.longitude,
                        )
                    }?.let { decodeEntry(prefs[stringPreferencesKey(it.name)])?.fetchedAtEpochMs }
                    ?.takeIf { it > 0L }
            if (isStaleFetch(incomingFetchedAt = fetchedAtEpochMs, existingFetchedAt = existingFetchedAt)) {
                // 旧数据直接放弃，且必须在清理前返回，否则会删掉新条目却又不写入
                return@edit
            }
            // 写前清理：同城旧键 + 过期键；只按 key 元数据判断，不 decode 其他城市
            keys
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

    override suspend fun get(
        city: City,
        presentationStamp: String,
    ): CacheEntry? {
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
            val trimmed = dropStaleDaily(entry.snapshot)
            // 文案是生成时按当时语言/温度单位烘焙的（预警里带 "38°C"/"100°F"）。
            // 指纹不一致说明用户换过语言或单位：数值照常离线可用，但旧语言的文案要丢掉，
            // 等联网刷新重新生成——否则会出现"中文提示 + 华氏温度"这类错配。
            val snapshot =
                if (entry.presentationStamp == presentationStamp) {
                    trimmed
                } else {
                    dropLocalizedText(trimmed)
                }
            return entry.copy(snapshot = snapshot)
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

    private fun decodeEntry(raw: String?): CacheEntry? =
        if (raw.isNullOrBlank()) null else runCatching { json.decodeFromString(CacheEntry.serializer(), raw) }.getOrNull()

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
        /** 生成文案时的语言+温度单位指纹；旧档为空串，读取时按不匹配处理。 */
        val presentationStamp: String = "",
        /**
         * 发起本次取数的时刻（epoch ms）。旧档为 0，按"不参与新旧比较"处理。
         * 用于拒绝比已存条目更旧的写入，见 [WeatherCache.save]。
         */
        val fetchedAtEpochMs: Long = 0L,
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

/**
 * 本次写入是否比已存条目更旧（是则应拒绝）。
 *
 * ViewModel / Worker / Widget 会为同一城市并发取数，必须按**取数发起时刻**比较：
 * 旧请求若后完成，按写入顺序落盘会让磁盘回退到旧天气，而时间戳看起来更新，
 * 冷启动与离线都读错。`existingFetchedAt` 为 null 表示没有可比条目（旧档 fetchedAt=0）。
 */
internal fun isStaleFetch(
    incomingFetchedAt: Long,
    existingFetchedAt: Long?,
): Boolean = existingFetchedAt != null && incomingFetchedAt in 1 until existingFetchedAt

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
fun todayInSnapshotZone(
    timezoneId: String?,
    fallbackZone: ZoneId = ZoneId.systemDefault(),
): LocalDate {
    val zone = runCatching { timezoneId?.let { ZoneId.of(it) } }.getOrNull() ?: fallbackZone
    return LocalDate.now(zone)
}

/**
 * 缓存文案的「语言 + 温度单位」指纹。
 *
 * 预警/建议/生活指数是拉取时用当时的 Resources 与温度单位生成并烘焙成字符串的
 * （预警文案里直接带 `38°C` / `100°F`）。用户换语言或换单位后，命中旧缓存会看到
 * 错配的文案，所以把两者一起折进指纹随条目落盘，读时比对。
 */
fun presentationStampOf(
    locale: Locale,
    temperatureUnit: TemperatureUnit,
): String = "${locale.toLanguageTag()}|${temperatureUnit.name}"

/**
 * 丢掉生成时烘焙的本地化文案，只保留与语言/单位无关的数值数据。
 *
 * 指纹不匹配时使用：离线仍能看到实况/逐时/逐日与空气质量（UI 渲染时才按当前单位格式化），
 * 只是暂时没有预警与建议，等下一次联网刷新重新生成。
 */
internal fun dropLocalizedText(snapshot: WeatherSnapshot): WeatherSnapshot {
    if (snapshot.alerts.isEmpty() && snapshot.advice == null && snapshot.lifeIndices.isEmpty()) {
        return snapshot
    }
    return snapshot.copy(
        alerts = emptyList(),
        advice = null,
        lifeIndices = emptyList(),
    )
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

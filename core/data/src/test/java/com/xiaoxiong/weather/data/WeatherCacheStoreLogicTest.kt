package com.xiaoxiong.weather.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/** 多数用例只关心缓存读写本身，统一用固定指纹。 */
private const val TEST_STAMP = "zh-CN|CELSIUS"

class WeatherCacheStoreLogicTest {
    @get:Rule
    val tempFolder = TemporaryFolder()

    private lateinit var scope: CoroutineScope
    private lateinit var dataStore: DataStore<Preferences>
    private lateinit var store: WeatherCacheStore

    private val json =
        Json {
            ignoreUnknownKeys = true
            encodeDefaults = true
            explicitNulls = false
            coerceInputValues = true
        }

    @Before
    fun setUp() {
        scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
        dataStore =
            PreferenceDataStoreFactory.create(scope = scope) {
                File(tempFolder.newFolder(), "cache.preferences_pb")
            }
        store = WeatherCacheStore(dataStore)
    }

    @After
    fun tearDown() {
        scope.cancel()
    }

    // ---------- 纯逻辑：key 编码 ----------

    @Test
    fun cacheKey_roundTripsTimestampAndCoordinates() {
        val name = cacheEntryKeyName(savedAtEpochMs = 1_700_000_000_000L, latitude = 36.6512, longitude = -117.1234)
        val parsed = parseCacheEntryKey(name)
        assertNotNull(parsed)
        assertEquals(1_700_000_000_000L, parsed!!.savedAtEpochMs)
        assertEquals(36.6512, parsed.latitude, 0.00001)
        assertEquals(-117.1234, parsed.longitude, 0.00001)
        assertEquals(name, parsed.name)
    }

    @Test
    fun cacheKey_ignoresLegacyAndUnknownKeys() {
        assertNull(parseCacheEntryKey("entries_json"))
        assertNull(parseCacheEntryKey("city_notanumber_36.65_117.12"))
        assertNull(parseCacheEntryKey("city_123_36.65"))
        assertNull(parseCacheEntryKey("favorite_city_123"))
    }

    @Test
    fun sameCacheCity_usesSameToleranceAsLegacy() {
        val a = City(id = 1L, name = "甲", latitude = 36.65, longitude = 117.12)
        assertTrue(sameCacheCity(a, a.copy(latitude = 36.655, longitude = 117.124)))
        assertFalse(sameCacheCity(a, a.copy(latitude = 36.67)))
    }

    // ---------- 纯逻辑：hourly 裁剪 ----------

    @Test
    fun trimSnapshot_keepsPastAndFutureWindow() {
        val snapshot = snapshotWithHourly(hours = 80, currentIndex = 30)
        val trimmed = trimSnapshotForCache(snapshot)

        val expectedFrom = 30 - CACHE_HOURLY_PAST_HOURS
        val expectedTo = 30 + CACHE_HOURLY_FUTURE_HOURS + 1
        assertEquals(expectedTo - expectedFrom, trimmed.hourly.size)
        assertEquals(snapshot.hourly[expectedFrom].time, trimmed.hourly.first().time)
        assertEquals(snapshot.hourly[expectedTo - 1].time, trimmed.hourly.last().time)
    }

    @Test
    fun trimSnapshot_keepsSmallListsUntouched() {
        val snapshot = snapshotWithHourly(hours = 10, currentIndex = 2)
        val trimmed = trimSnapshotForCache(snapshot)
        assertEquals(snapshot.hourly, trimmed.hourly)
    }

    // ---------- 纯逻辑：旧格式迁移 ----------

    @Test
    fun migrateLegacy_dropsExpiredAndKeepsNewestPerCity() {
        val now = 1_700_000_000_000L
        val freshBeijing = city("北京", 39.9, 116.4)
        val olderBeijing = freshBeijing.copy(id = 2L)
        val expiredShanghai = city("上海", 31.2, 121.5)
        val freshShanghai = city("上海", 31.2, 121.5)

        val migrated =
            migrateLegacyEntries(
                legacy =
                    listOf(
                        entry(freshBeijing, now - 60_000),
                        entry(olderBeijing, now - 120_000),
                        entry(expiredShanghai, now - WeatherCacheStore.MAX_AGE_MS - 1),
                        entry(freshShanghai, now - 30_000),
                    ),
                nowEpochMs = now,
            )

        // 同城去重保留最新 + 过期条目剔除 → 北京/上海各 1 条
        assertEquals(2, migrated.size)
        assertEquals(now - 60_000, migrated.values.first { it.snapshot.city.name == "北京" }.savedAtEpochMs)
        assertEquals(now - 30_000, migrated.values.first { it.snapshot.city.name == "上海" }.savedAtEpochMs)
    }

    @Test
    fun migrateLegacy_emptyInputProducesNoKeys() {
        assertTrue(migrateLegacyEntries(emptyList(), System.currentTimeMillis()).isEmpty())
    }

    // ---------- DataStore 集成：按城读写/迁移/过期 ----------

    @Test
    fun saveThenGet_returnsTrimmedSnapshot() =
        runBlocking {
            val city = city("济南", 36.65, 117.12)
            val snapshot = snapshotWithHourly(hours = 80, currentIndex = 30).copy(city = city)

            store.save(snapshot, TEST_STAMP)
            val loaded = store.get(city, TEST_STAMP)

            assertNotNull(loaded)
            assertEquals(city.latitude, loaded!!.snapshot.city.latitude, 0.0001)
            assertEquals(CACHE_HOURLY_FUTURE_HOURS + CACHE_HOURLY_PAST_HOURS + 1, loaded.snapshot.hourly.size)
        }

    @Test
    fun get_matchesCityWithinTolerance() =
        runBlocking {
            val savedCity = city("济南", 36.65, 117.12)
            val queriedCity = savedCity.copy(latitude = 36.655, longitude = 117.124)
            store.save(snapshotWithHourly(hours = 4, currentIndex = 1).copy(city = savedCity), TEST_STAMP)

            assertNotNull(store.get(queriedCity, TEST_STAMP))
        }

    @Test
    fun save_replacesOldKeyForSameCity() =
        runBlocking {
            val city = city("济南", 36.65, 117.12)
            store.save(snapshotWithHourly(hours = 4, currentIndex = 1).copy(city = city, elevationMeters = 1.0), TEST_STAMP)
            store.save(snapshotWithHourly(hours = 4, currentIndex = 1).copy(city = city, elevationMeters = 2.0), TEST_STAMP)

            val loaded = store.get(city, TEST_STAMP)
            assertEquals(2.0, loaded!!.snapshot.elevationMeters!!, 0.001)
            // 同城只保留一个 key，避免每次刷新堆积
            assertEquals(1, dataStoreKeys().count { it.startsWith(CACHE_CITY_KEY_PREFIX) })
        }

    @Test
    fun get_corruptPayload_returnsNullWithoutThrowing() =
        runBlocking {
            val city = city("济南", 36.65, 117.12)
            val name = cacheEntryKeyName(System.currentTimeMillis(), city.latitude, city.longitude)
            dataStore.edit { prefs ->
                prefs[stringPreferencesKey(name)] = "{not-json"
            }

            assertNull(store.get(city, TEST_STAMP))
        }

    @Test
    fun save_prunesExpiredKeysOfOtherCities() =
        runBlocking {
            val now = System.currentTimeMillis()
            val expiredName = cacheEntryKeyName(now - WeatherCacheStore.MAX_AGE_MS - 1, 31.2, 121.5)
            seedRawEntry(expiredName, entry(city("上海", 31.2, 121.5), now - WeatherCacheStore.MAX_AGE_MS - 1))

            val city = city("济南", 36.65, 117.12)
            store.save(snapshotWithHourly(hours = 4, currentIndex = 1).copy(city = city), TEST_STAMP)

            assertFalse(dataStoreKeys().contains(expiredName))
        }

    @Test
    fun get_migratesLegacyEntriesAndRemovesLegacyKey() =
        runBlocking {
            val now = System.currentTimeMillis()
            val city = city("济南", 36.65, 117.12)
            seedLegacy(listOf(entry(city, now - 60_000)))

            val loaded = store.get(city, TEST_STAMP)

            assertNotNull(loaded)
            assertEquals("济南", loaded!!.snapshot.city.name)
            assertFalse(dataStoreKeys().contains("entries_json"))
        }

    @Test
    fun get_dropsExpiredLegacyEntries() =
        runBlocking {
            val now = System.currentTimeMillis()
            val city = city("济南", 36.65, 117.12)
            seedLegacy(listOf(entry(city, now - WeatherCacheStore.MAX_AGE_MS - 1)))

            assertNull(store.get(city, TEST_STAMP))
            assertFalse(dataStoreKeys().contains("entries_json"))
        }

    @Test
    fun get_stampMismatch_keepsNumbersButDropsBakedLocalizedText() =
        runBlocking {
            val city = city("济南", 36.65, 117.12)
            val snapshot =
                snapshotWithHourly(hours = 4, currentIndex = 1).copy(
                    city = city,
                    advice = "穿外套",
                    alerts =
                        listOf(
                            WeatherAlert(
                                title = "高温预警",
                                message = "体感 38°C",
                                severity = AlertSeverity.WARNING,
                            ),
                        ),
                    lifeIndices =
                        listOf(
                            LifeIndex(
                                kind = LifeIndexKind.CLOTHING,
                                level = LifeIndexLevel.GOOD,
                                title = "穿衣",
                                summary = "短袖",
                            ),
                        ),
                )
            store.save(snapshot, "zh-CN|CELSIUS")

            // 同语言同单位：文案照常返回
            val same = store.get(city, "zh-CN|CELSIUS")
            assertNotNull(same)
            assertEquals("穿外套", same!!.snapshot.advice)
            assertEquals(1, same.snapshot.alerts.size)

            // 用户切了华氏：数值仍可离线展示，旧单位烘焙的文案必须丢弃
            val switched = store.get(city, "zh-CAN|FAHRENHEIT")
            assertNotNull(switched)
            val s = switched!!.snapshot
            assertEquals(city, s.city)
            // 数值字段与语言/单位无关，必须照常离线可用
            assertTrue(s.hourly.isNotEmpty())
            assertEquals(1.0, s.hourly[1].temperatureC ?: 0.0, 1e-6)
            assertNull(s.advice)
            assertTrue(s.alerts.isEmpty())
            assertTrue(s.lifeIndices.isEmpty())
        }

    @Test
    fun presentationStamp_differsByLocaleAndUnit() {
        val zh = presentationStampOf(java.util.Locale.SIMPLIFIED_CHINESE, TemperatureUnit.CELSIUS)
        val zhF = presentationStampOf(java.util.Locale.SIMPLIFIED_CHINESE, TemperatureUnit.FAHRENHEIT)
        val en = presentationStampOf(java.util.Locale.US, TemperatureUnit.CELSIUS)
        assertTrue(zh != zhF)
        assertTrue(zh != en)
    }

    // ---------- helpers ----------
    private fun city(
        name: String,
        latitude: Double,
        longitude: Double,
    ): City = City(id = name.hashCode().toLong(), name = name, latitude = latitude, longitude = longitude)

    private fun snapshotWithHourly(
        hours: Int,
        currentIndex: Int,
    ): WeatherSnapshot {
        val base = LocalDateTime.of(2026, 9, 12, 0, 0)
        val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm")
        val hourly =
            (0 until hours).map { index ->
                HourlyWeather(
                    time = base.plusHours(index.toLong()).format(formatter),
                    temperatureC = index.toDouble(),
                )
            }
        return WeatherSnapshot(
            city = city("测试", 36.65, 117.12),
            current = CurrentWeather(time = hourly[currentIndex].time),
            hourly = hourly,
        )
    }

    private fun entry(
        city: City,
        savedAtEpochMs: Long,
    ): WeatherCacheStore.CacheEntry =
        WeatherCacheStore.CacheEntry(
            snapshot = WeatherSnapshot(city = city, current = CurrentWeather(time = "2026-09-12T12:00")),
            savedAtEpochMs = savedAtEpochMs,
        )

    private suspend fun seedLegacy(entries: List<WeatherCacheStore.CacheEntry>) {
        val raw = json.encodeToString(ListSerializer(WeatherCacheStore.CacheEntry.serializer()), entries)
        dataStore.edit { prefs ->
            prefs[stringPreferencesKey("entries_json")] = raw
        }
    }

    private suspend fun seedRawEntry(
        name: String,
        entry: WeatherCacheStore.CacheEntry,
    ) {
        val raw = json.encodeToString(WeatherCacheStore.CacheEntry.serializer(), entry)
        dataStore.edit { prefs ->
            prefs[stringPreferencesKey(name)] = raw
        }
    }

    private suspend fun dataStoreKeys(): Set<String> =
        dataStore.data
            .first()
            .asMap()
            .keys
            .map { it.name }
            .toSet()
}

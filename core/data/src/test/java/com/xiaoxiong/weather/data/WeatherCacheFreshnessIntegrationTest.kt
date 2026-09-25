package com.xiaoxiong.weather.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import com.xiaoxiong.weather.data.SelectedCityStore.PersistedSelection
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/**
 * 天气缓存跨请求新旧保护的**接线**测试（走真实 DataStore 与 save/get）。
 *
 * 之所以单独写：`WeatherCacheFreshnessGuardTest` 只测 `isStaleFetch` 纯逻辑，
 * 无法发现"判定函数写对了但 save() 里根本没接上"。事实上有过一版实现把比对放在
 * 写前清理之后——清理已删掉同城条目、且 target 用当前毫秒组建本身不存在，
 * 于是保护恒不生效，还会把新数据删掉。这个测试就是为了锁住那个位置。
 */
class WeatherCacheFreshnessIntegrationTest {
    @get:Rule
    val tempFolder = TemporaryFolder()

    private lateinit var scope: CoroutineScope
    private lateinit var dataStore: DataStore<Preferences>
    private lateinit var store: WeatherCacheStore

    private val stamp = "zh-CN|CELSIUS"

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

    private fun city(
        name: String = "南京",
        latitude: Double = 32.06,
        longitude: Double = 118.79,
    ): City = City(id = name.hashCode().toLong(), name = name, latitude = latitude, longitude = longitude)

    private fun snapshot(
        city: City,
        time: String,
    ): WeatherSnapshot =
        WeatherSnapshot(
            city = city,
            current = CurrentWeather(time = time, temperatureC = 20.0),
        )

    /** 读回当前城市的快照时间，作为"磁盘上是哪一次取数"的凭证。 */
    private fun readTime(city: City): String? {
        val entry = runBlocking { store.get(city, stamp) }
        return entry?.snapshot?.current?.time
    }

    @Test
    fun `newer fetch overwrites older`() {
        val city = city()
        runBlocking {
            store.save(snapshot(city, "旧"), stamp, fetchedAtEpochMs = 1_000L)
            store.save(snapshot(city, "新"), stamp, fetchedAtEpochMs = 2_000L)
        }
        assertEquals("新", readTime(city))
    }

    @Test
    fun `stale fetch arriving later must not overwrite newer data`() {
        val city = city()
        runBlocking {
            store.save(snapshot(city, "新"), stamp, fetchedAtEpochMs = 2_000L)
            // 旧请求后完成：必须被丢弃，否则冷启动/离线读到旧天气却以为是新鲜的
            store.save(snapshot(city, "旧"), stamp, fetchedAtEpochMs = 1_000L)
        }
        assertEquals("新", readTime(city))
    }

    @Test
    fun `stale fetch must not delete the newer entry as a side effect`() {
        val city = city()
        runBlocking {
            store.save(snapshot(city, "新"), stamp, fetchedAtEpochMs = 2_000L)
            store.save(snapshot(city, "旧"), stamp, fetchedAtEpochMs = 1_000L)
        }
        // 若比对排在写前清理之后，旧写入会先删掉新条目再自我拒绝 → 缓存被清空
        assertEquals("被拒绝的写入不能顺手删掉好数据", "新", readTime(city))
    }

    @Test
    fun `unstamped legacy call keeps working`() {
        val city = city()
        runBlocking {
            store.save(snapshot(city, "一"), stamp, fetchedAtEpochMs = 0L)
            store.save(snapshot(city, "二"), stamp, fetchedAtEpochMs = 0L)
        }
        assertEquals("未提供取数时刻时按旧行为放行", "二", readTime(city))
    }

    @Test
    fun `different city is unaffected by another citys staleness`() {
        val nanjing = city("南京", 32.06, 118.79)
        val beijing = city("北京", 39.90, 116.41)
        runBlocking {
            store.save(snapshot(nanjing, "南京新"), stamp, fetchedAtEpochMs = 2_000L)
            store.save(snapshot(beijing, "北京"), stamp, fetchedAtEpochMs = 1_000L)
            store.save(snapshot(nanjing, "南京旧"), stamp, fetchedAtEpochMs = 1_000L)
        }
        assertEquals("南京新", readTime(nanjing))
        assertEquals("北京", readTime(beijing))
    }

    @Test
    fun `selection type is not part of cache identity but coordinates are`() {
        // 仅用于确认本测试文件引用的 PersistedSelection 与缓存城市口径一致（防止误用同名城市）
        val a = PersistedSelection(city = city(), useLocation = false)
        val b = PersistedSelection(city = city("南京", 40.0, 120.0), useLocation = false)
        assertEquals(a.city.name, b.city.name)
        assertEquals(false, sameCacheCity(a.city.latitude, a.city.longitude, b.city.latitude, b.city.longitude))
    }
}

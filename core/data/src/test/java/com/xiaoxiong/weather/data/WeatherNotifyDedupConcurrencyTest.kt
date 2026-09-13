package com.xiaoxiong.weather.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [WeatherNotifyDedupStore] 原子流程的并发测试：
 * 两个协程同时判定同一签名时，进程内门闩保证只有一个能通过并展示，修复拆分
 * `shouldNotify` + `rememberNotified` 后重现的 check-then-act 竞态。
 */
class WeatherNotifyDedupConcurrencyTest {
    @Test
    fun concurrentWeatherChange_onlyOnePosts() =
        runBlocking {
            val store = WeatherNotifyDedupStore(InMemoryPreferencesStore())
            var posts = 0

            val results =
                listOf(
                    async {
                        store.notifyWeatherChangeIfDue("青岛", 0, 20.0) {
                            delay(10)
                            posts++
                            true
                        }
                    },
                    async {
                        store.notifyWeatherChangeIfDue("青岛", 0, 20.0) {
                            delay(10)
                            posts++
                            true
                        }
                    },
                ).map { it.await() }

            assertEquals(1, results.count { it })
            assertEquals(1, posts)
        }

    @Test
    fun concurrentAlerts_onlyOnePosts() =
        runBlocking {
            val store = WeatherNotifyDedupStore(InMemoryPreferencesStore())
            val signature = "青岛|FOG|1"
            var posts = 0

            val results =
                listOf(
                    async {
                        store.notifyAlertsIfDue("青岛", 0, 20.0, listOf(signature)) { pending ->
                            delay(10)
                            posts++
                            pending
                        }
                    },
                    async {
                        store.notifyAlertsIfDue("青岛", 0, 20.0, listOf(signature)) { pending ->
                            delay(10)
                            posts++
                            pending
                        }
                    },
                ).map { it.await() }

            assertEquals(1, posts)
            assertEquals(1, results.count { it == listOf(signature) })
            assertTrue(results.any { it.isEmpty() })
        }

    @Test
    fun allSeverePostsFail_writesNoMemory_genericCanStillPost() =
        runBlocking {
            val store = WeatherNotifyDedupStore(InMemoryPreferencesStore())
            var genericPosts = 0

            // 严重预警全部展示失败：不得写记忆，否则通用兜底被静默吞掉 6 小时
            val posted =
                store.notifyAlertsIfDue("青岛", 0, 20.0, listOf("青岛|HEAT|2")) { emptyList() }
            assertTrue(posted.isEmpty())

            val generic =
                store.notifyWeatherChangeIfDue("青岛", 0, 20.0) {
                    genericPosts++
                    true
                }
            assertTrue(generic)
            assertEquals(1, genericPosts)
        }

    @Test
    fun alertPostSuccess_syncsGenericBaseline() =
        runBlocking {
            val store = WeatherNotifyDedupStore(InMemoryPreferencesStore())
            var genericPosts = 0

            val posted = store.notifyAlertsIfDue("青岛", 0, 20.0, listOf("青岛|FOG|1")) { it }
            assertEquals(listOf("青岛|FOG|1"), posted)

            // 严重预警已真实展示：同一轮通用通知不再重复推
            val generic =
                store.notifyWeatherChangeIfDue("青岛", 0, 20.0) {
                    genericPosts++
                    true
                }
            assertFalse(generic)
            assertEquals(0, genericPosts)
        }
}

/** 仅内存的 DataStore 替身：data 读快照，updateData 串行改写（模拟单进程 DataStore）。 */
private class InMemoryPreferencesStore : DataStore<Preferences> {
    private val mutex = Mutex()
    private val state = MutableStateFlow(emptyPreferences())

    override val data: Flow<Preferences> = state

    override suspend fun updateData(transform: suspend (Preferences) -> Preferences): Preferences =
        mutex.withLock {
            val updated = transform(state.value)
            state.value = updated
            updated
        }
}

package com.xiaoxiong.weather.data

import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.util.concurrent.atomic.AtomicInteger

class CitySearchCacheTest {
    private fun city(name: String) = City(id = name.hashCode().toLong(), name = name, latitude = 1.0, longitude = 2.0)

    @Test
    fun get_returnsValueWithinTtl() =
        runBlocking {
            val cache = CitySearchCache(maxEntries = 4, ttlMs = 90_000)
            cache.put("济南", listOf(city("济南")), nowEpochMs = 1_000)

            assertEquals(listOf("济南"), cache.get("济南", 1_001)?.map { it.name })
            assertEquals(listOf("济南"), cache.get("济南", 1_000 + 89_999)?.map { it.name })
        }

    @Test
    fun get_expiredEntryReturnsNullAndIsEvicted() =
        runBlocking {
            val cache = CitySearchCache(maxEntries = 4, ttlMs = 90_000)
            cache.put("济南", listOf(city("济南")), nowEpochMs = 1_000)

            assertNull(cache.get("济南", 1_000 + 90_000))
            assertNull(cache.get("济南", 1_001))
        }

    @Test
    fun get_futureTimestampAfterClockRollbackIsNotFresh() =
        runBlocking {
            val cache = CitySearchCache(maxEntries = 4, ttlMs = 90_000)
            cache.put("济南", listOf(city("济南")), nowEpochMs = 1_000)

            assertNull(cache.get("济南", 999))
        }

    @Test
    fun emptyResultsAreCached() =
        runBlocking {
            val cache = CitySearchCache(maxEntries = 4, ttlMs = 90_000)
            cache.put("不存在", emptyList(), nowEpochMs = 1_000)

            assertEquals(emptyList<City>(), cache.get("不存在", 1_001))
        }

    @Test
    fun lru_evictsLeastRecentlyUsedBeyondCapacity() =
        runBlocking {
            val cache = CitySearchCache(maxEntries = 2, ttlMs = 90_000)
            cache.put("a", listOf(city("a")), 1_000)
            cache.put("b", listOf(city("b")), 1_000)
            // 访问 a 使其成为最近使用，b 变成最旧
            cache.get("a", 1_001)
            cache.put("c", listOf(city("c")), 1_001)

            assertNull(cache.get("b", 1_002))
            assertEquals(listOf("a"), cache.get("a", 1_002)?.map { it.name })
            assertEquals(listOf("c"), cache.get("c", 1_002)?.map { it.name })
        }

    @Test
    fun singleFlight_concurrentCallersShareOneExecution() =
        runBlocking {
            val flight = SingleFlight<String, Int>()
            val calls = AtomicInteger()
            val first =
                async {
                    flight.run("k") {
                        calls.incrementAndGet()
                        delay(50)
                        42
                    }
                }
            val second =
                async {
                    flight.run("k") {
                        calls.incrementAndGet()
                        delay(50)
                        43
                    }
                }

            assertEquals(42, first.await())
            assertEquals(42, second.await())
            assertEquals(1, calls.get())
        }

    @Test
    fun singleFlight_differentKeysRunIndependently() =
        runBlocking {
            val flight = SingleFlight<String, Int>()
            val calls = AtomicInteger()
            val a =
                async {
                    flight.run("a") {
                        calls.incrementAndGet()
                        1
                    }
                }
            val b =
                async {
                    flight.run("b") {
                        calls.incrementAndGet()
                        2
                    }
                }

            assertEquals(1, a.await())
            assertEquals(2, b.await())
            assertEquals(2, calls.get())
        }

    @Test
    fun singleFlight_failurePropagatesAndNextCallCanRetry() =
        runBlocking {
            val flight = SingleFlight<String, Int>()
            val owner =
                async<String?> {
                    runCatching {
                        flight.run("k") {
                            delay(50)
                            throw IllegalStateException("boom")
                        }
                    }.exceptionOrNull()?.message
                }
            val waiter =
                async<String?> {
                    delay(10)
                    runCatching { flight.run("k") { 99 } }.exceptionOrNull()?.message
                }

            assertEquals("boom", owner.await())
            assertEquals("boom", waiter.await())
            // 失败后条目已移除，可正常重试
            assertEquals(7, flight.run("k") { 7 })
        }

    @Test
    fun singleFlight_ownerCancellationAllowsNextCall() =
        runBlocking {
            val flight = SingleFlight<String, Int>()
            val owner =
                launch {
                    flight.run("k") {
                        delay(10_000)
                        1
                    }
                }
            delay(20)
            owner.cancelAndJoin()

            assertEquals(2, flight.run("k") { 2 })
        }
}

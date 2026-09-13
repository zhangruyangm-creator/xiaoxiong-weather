package com.xiaoxiong.weather.widget

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class WidgetLoadGateTest {
    private fun sampleUi(city: String = "青岛") =
        WeatherWidgetUi(
            cityName = city,
            emoji = "☀️",
            bearRes = 0,
            temperatureText = "26°C",
            description = "晴",
            highLowText = "18 / 28",
            updatedText = "12:00 更新",
        )

    @Test
    fun withinTtl_sameCity_reusesResultAndSkipsFetch() =
        runTest {
            var now = 1_000L
            var calls = 0
            val gate = WidgetLoadGate(ttlMs = 30_000, clock = { now })

            val first =
                gate.load("loc|青岛") {
                    calls++
                    sampleUi()
                }
            now += 29_999
            val second =
                gate.load("loc|青岛") {
                    calls++
                    sampleUi("别的城市")
                }

            assertEquals(1, calls)
            assertSame(first, second)
        }

    @Test
    fun ttlExpired_sameCity_refetches() =
        runTest {
            var now = 1_000L
            var calls = 0
            val gate = WidgetLoadGate(ttlMs = 30_000, clock = { now })

            gate.load("loc|青岛") {
                calls++
                sampleUi()
            }
            now += 30_000
            gate.load("loc|青岛") {
                calls++
                sampleUi()
            }

            assertEquals(2, calls)
        }

    @Test
    fun differentCityKey_refetchesEvenWithinTtl() =
        runTest {
            var calls = 0
            val gate = WidgetLoadGate(ttlMs = 30_000, clock = { 1_000L })

            gate.load("loc|青岛") {
                calls++
                sampleUi()
            }
            gate.load("city|北京") {
                calls++
                sampleUi("北京")
            }

            assertEquals(2, calls)
        }

    @Test
    fun concurrentLoads_executeFetchOnce() =
        runTest {
            var calls = 0
            val release = CompletableDeferred<Unit>()
            val gate = WidgetLoadGate(ttlMs = 30_000, clock = { 1_000L })

            val jobs =
                List(4) {
                    async {
                        gate.load("loc|青岛") {
                            calls++
                            release.await()
                            sampleUi()
                        }
                    }
                }
            runCurrent()
            assertEquals(1, calls)

            release.complete(Unit)
            val results = jobs.awaitAll()

            assertEquals(1, calls)
            assertEquals(1, results.map { it?.cityName }.distinct().size)
            assertEquals("青岛", results.first()?.cityName)
        }

    @Test
    fun nullResult_isCachedWithinTtlToAvoidRetryStorm() =
        runTest {
            var calls = 0
            val gate = WidgetLoadGate(ttlMs = 30_000, clock = { 1_000L })

            val first =
                gate.load("loc|青岛") {
                    calls++
                    null
                }
            val second =
                gate.load("loc|青岛") {
                    calls++
                    sampleUi()
                }

            assertNull(first)
            assertNull(second)
            assertEquals(1, calls)
        }

    @Test
    fun invalidate_forcesReload() =
        runTest {
            var calls = 0
            val gate = WidgetLoadGate(ttlMs = 30_000, clock = { 1_000L })

            gate.load("loc|青岛") {
                calls++
                sampleUi()
            }
            gate.invalidate()
            gate.load("loc|青岛") {
                calls++
                sampleUi()
            }

            assertEquals(2, calls)
        }
}

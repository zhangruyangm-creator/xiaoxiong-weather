package com.xiaoxiong.weather.ui.weather

import com.xiaoxiong.weather.data.AppError
import com.xiaoxiong.weather.data.WeatherSnapshot
import com.xiaoxiong.weather.fixtures.WeatherFixtures
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.time.LocalDate

@OptIn(ExperimentalCoroutinesApi::class)
class WeatherViewModelSelectCityTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun selectCity_networkFailure_fallsBackToCacheOffline() =
        runTest {
            val city = WeatherFixtures.city("济南")
            val cachedSnapshot = snapshotFor(city, temp = 18.0)
            val cache =
                FakeWeatherCache().apply {
                    put(city, cachedSnapshot, savedAt = 1_700_000_000_000L)
                }
            val fetcher =
                RecordingFetcher(weatherBlock = { throw AppError.NetworkUnavailable() })
            val session = RecordingLocationSession()
            val vm = createTestViewModel(fetcher, cache, session).vm

            vm.selectCity(city)
            advanceUntilIdle()

            val state = vm.uiState.value
            assertEquals(city.name, state.selectedCity?.name)
            assertEquals(18.0, state.weather?.current?.temperatureC)
            assertTrue(state.isOffline)
            assertFalse(state.isLoadingWeather)
            assertEquals("网络异常，已显示缓存天气", state.errorMessage)
            assertEquals(1, session.cancelCount)
        }

    @Test
    fun networkRecovery_triggersQuietRefresh() =
        runTest {
            val city = WeatherFixtures.city("济南")
            val fetcher = RecordingFetcher()
            val network = FakeNetworkStateSource(online = true)
            val vm =
                createTestViewModel(
                    fetcher,
                    FakeWeatherCache(),
                    RecordingLocationSession(),
                    networkMonitor = network,
                ).vm

            vm.selectCity(city)
            advanceUntilIdle()
            val callsBeforeRefresh = fetcher.weatherCallCount
            assertTrue(callsBeforeRefresh >= 1)

            // 断网后恢复：应自动触发一次静默刷新，且不打断界面 loading
            network.setOnline(false)
            advanceUntilIdle()
            network.setOnline(true)
            advanceUntilIdle()

            assertTrue(fetcher.weatherCallCount > callsBeforeRefresh)
            assertFalse(vm.uiState.value.isLoadingWeather)
        }

    @Test
    fun refresh_quietDedup_withinWindowSkipsDuplicate() =
        runTest {
            val city = WeatherFixtures.city("济南")
            val fetcher = RecordingFetcher()
            val vm = createTestViewModel(fetcher).vm

            vm.selectCity(city)
            advanceUntilIdle()
            val callsAfterSelect = fetcher.weatherCallCount
            assertTrue(callsAfterSelect >= 1)

            // 第一次静默刷新（如回前台）：距上次请求已过窗口，应执行
            vm.refresh(quiet = true)
            advanceUntilIdle()
            val callsAfterFirstQuiet = fetcher.weatherCallCount
            assertTrue(callsAfterFirstQuiet > callsAfterSelect)

            // 紧接着第二次静默刷新（如网络恢复叠加）：落在去重窗口内，应被跳过
            vm.refresh(quiet = true)
            advanceUntilIdle()
            assertEquals(callsAfterFirstQuiet, fetcher.weatherCallCount)

            // 手动下拉刷新不受窗口限制，总是执行
            vm.refresh(quiet = false)
            advanceUntilIdle()
            assertTrue(fetcher.weatherCallCount > callsAfterFirstQuiet)
        }

    @Test
    fun selectCity_cancelsPendingLocateAndDropsStaleFetch() =
        runTest {
            val cityA = WeatherFixtures.city("济南", lat = 36.65, lon = 117.12)
            val cityB = WeatherFixtures.city("青岛", lat = 36.07, lon = 120.38)
            val snapA = snapshotFor(cityA, temp = 10.0)
            val snapB = snapshotFor(cityB, temp = 20.0)
            val gateA = CompletableDeferred<WeatherSnapshot>()
            val fetcher =
                RecordingFetcher(
                    weatherBlock = { city ->
                        when (city.name) {
                            "济南" -> gateA.await()
                            else -> snapB
                        }
                    },
                )
            val session = RecordingLocationSession()
            val vm = createTestViewModel(fetcher, session = session).vm

            vm.selectCity(cityA)
            advanceUntilIdle()
            assertEquals(1, session.cancelCount)
            assertEquals(
                "济南",
                vm.uiState.value.selectedCity
                    ?.name,
            )
            assertTrue(vm.uiState.value.isLoadingWeather)

            vm.selectCity(cityB)
            advanceUntilIdle()
            assertEquals(2, session.cancelCount)
            assertEquals(
                "青岛",
                vm.uiState.value.selectedCity
                    ?.name,
            )

            gateA.complete(snapA)
            advanceUntilIdle()

            val state = vm.uiState.value
            assertEquals("青岛", state.selectedCity?.name)
            assertEquals(20.0, state.weather?.current?.temperatureC)
            assertFalse(state.isOffline)
            assertFalse(state.isLoadingWeather)
            assertNull(state.errorMessage)
        }

    @Test
    fun ensurePhenologyShift_freshCache_noNetworkCall() =
        runTest {
            val city = WeatherFixtures.city("济南")
            val year = LocalDate.now().year
            val fetcher = RecordingFetcher()
            val phenologyCache =
                FakePhenologyCache().apply {
                    seed(city, year, shiftDays = -5)
                }
            val vm = createTestViewModel(fetcher, phenologyCache = phenologyCache).vm

            vm.selectCity(city)
            advanceUntilIdle()
            vm.ensurePhenologyShift()
            advanceUntilIdle()

            assertEquals(-5, vm.uiState.value.phenologyShiftDays)
            assertEquals(0, fetcher.phenologyCallCount)
        }

    @Test
    fun ensurePhenologyShift_staleCache_fetchesOnceAndPublishes() =
        runTest {
            val city = WeatherFixtures.city("济南")
            val year = LocalDate.now().year
            val fetcher =
                RecordingFetcher(
                    phenologyBlock = { _, _ -> -8 },
                )
            val phenologyCache =
                FakePhenologyCache().apply {
                    seed(city, year, shiftDays = null, fetchedToday = false)
                }
            val vm = createTestViewModel(fetcher, phenologyCache = phenologyCache).vm

            vm.selectCity(city)
            advanceUntilIdle()
            vm.ensurePhenologyShift()
            advanceUntilIdle()

            assertEquals(-8, vm.uiState.value.phenologyShiftDays)
            assertEquals(1, fetcher.phenologyCallCount)
            assertEquals(1, phenologyCache.saved.size)

            // 同日再次进入七日页：命中缓存，不再联网
            vm.ensurePhenologyShift()
            advanceUntilIdle()
            assertEquals(1, fetcher.phenologyCallCount)
        }

    @Test
    fun ensurePhenologyShift_noData_cachesNullAndKeepsNull() =
        runTest {
            val city = WeatherFixtures.city("济南")
            val year = LocalDate.now().year
            val fetcher = RecordingFetcher() // 默认返回 null（数据不足）
            val phenologyCache = FakePhenologyCache()
            val vm = createTestViewModel(fetcher, phenologyCache = phenologyCache).vm

            vm.selectCity(city)
            advanceUntilIdle()
            vm.ensurePhenologyShift()
            advanceUntilIdle()

            assertNull(vm.uiState.value.phenologyShiftDays)
            assertEquals(1, fetcher.phenologyCallCount)
            assertEquals(1, phenologyCache.saved.size)
            assertEquals(null, phenologyCache.saved.first().shiftDays)
        }

    @Test
    fun selectCity_resetsPhenologyShift() =
        runTest {
            val cityA = WeatherFixtures.city("济南", lat = 36.65, lon = 117.12)
            val cityB = WeatherFixtures.city("青岛", lat = 36.07, lon = 120.38)
            val fetcher =
                RecordingFetcher(
                    phenologyBlock = { _, _ -> -5 },
                )
            val phenologyCache = FakePhenologyCache()
            val vm = createTestViewModel(fetcher, phenologyCache = phenologyCache).vm

            vm.selectCity(cityA)
            advanceUntilIdle()
            vm.ensurePhenologyShift()
            advanceUntilIdle()
            assertEquals(-5, vm.uiState.value.phenologyShiftDays)

            vm.selectCity(cityB)
            advanceUntilIdle()
            assertNull(vm.uiState.value.phenologyShiftDays)
        }

    @Test
    fun fetchHistoryDay_success_publishesAndDedups() =
        runTest {
            val city = WeatherFixtures.city("济南")
            val date = LocalDate.now().minusDays(2)
            val fetcher =
                RecordingFetcher(
                    historyBlock = { _, _ -> testHistoryWeather(date, tempMax = 30.0) },
                )
            val vm = createTestViewModel(fetcher).vm

            vm.selectCity(city)
            advanceUntilIdle()
            vm.fetchHistoryDay(date)
            advanceUntilIdle()

            val history = vm.uiState.value.historyWeather
            assertEquals(30.0, history?.tempMaxC)
            assertEquals(1, fetcher.historyCallCount)

            // 同城同日再查：命中内存缓存，不再联网
            vm.fetchHistoryDay(date)
            advanceUntilIdle()
            assertEquals(1, fetcher.historyCallCount)
            val historyAgain = vm.uiState.value.historyWeather
            assertEquals(30.0, historyAgain?.tempMaxC)
        }

    @Test
    fun fetchHistoryDay_failure_marksFailed() =
        runTest {
            val city = WeatherFixtures.city("济南")
            val date = LocalDate.now().minusDays(2)
            val fetcher =
                RecordingFetcher(
                    historyBlock = { _, _ -> null },
                )
            val vm = createTestViewModel(fetcher).vm

            vm.selectCity(city)
            advanceUntilIdle()
            vm.fetchHistoryDay(date)
            advanceUntilIdle()

            assertNull(vm.uiState.value.historyWeather)
            assertTrue(vm.uiState.value.historyFailed)
            assertFalse(vm.uiState.value.isHistoryLoading)

            // 失败不缓存：再查会重试
            vm.fetchHistoryDay(date)
            advanceUntilIdle()
            assertEquals(2, fetcher.historyCallCount)
        }

    @Test
    fun selectCity_resetsHistory() =
        runTest {
            val cityA = WeatherFixtures.city("济南", lat = 36.65, lon = 117.12)
            val cityB = WeatherFixtures.city("青岛", lat = 36.07, lon = 120.38)
            val date = LocalDate.now().minusDays(2)
            val fetcher =
                RecordingFetcher(
                    historyBlock = { _, _ -> testHistoryWeather(date, tempMax = 30.0) },
                )
            val vm = createTestViewModel(fetcher).vm

            vm.selectCity(cityA)
            advanceUntilIdle()
            vm.fetchHistoryDay(date)
            advanceUntilIdle()
            val historyA = vm.uiState.value.historyWeather
            assertEquals(30.0, historyA?.tempMaxC)

            vm.selectCity(cityB)
            advanceUntilIdle()
            assertNull(vm.uiState.value.historyWeather)
            assertFalse(vm.uiState.value.isHistoryLoading)
        }
}

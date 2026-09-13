package com.xiaoxiong.weather.ui.weather

import com.xiaoxiong.weather.data.TemperatureUnit
import com.xiaoxiong.weather.data.WeatherSnapshot
import com.xiaoxiong.weather.fixtures.WeatherFixtures
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/**
 * 温度单位切换触发的应用内静默刷新：:app 检测到单位真实变化后向
 * `quietRefreshRequests` 发信号，WeatherViewModel 订阅并走 refresh(quiet = true)。
 *
 * 信号是边沿触发：被 loading / 5s 去重窗口拦截时不得直接丢弃，
 * 而是置 pending 并在条件满足后补发一次（连续信号合并）。
 */
@OptIn(ExperimentalCoroutinesApi::class)
class WeatherViewModelQuietRefreshTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun quietRefreshRequest_triggersOneRefreshKeepingUi() =
        runTest {
            val city = WeatherFixtures.city("济南")
            val fetcher = RecordingFetcher()
            val requests = MutableSharedFlow<Unit>()
            val vm = createTestViewModel(fetcher, quietRefreshRequests = requests).vm

            vm.selectCity(city)
            advanceUntilIdle()
            val callsAfterSelect = fetcher.weatherCallCount
            assertTrue(callsAfterSelect >= 1)

            requests.emit(Unit)
            advanceUntilIdle()

            assertEquals(callsAfterSelect + 1, fetcher.weatherCallCount)
            // 静默：不打断界面数据，也不显示 loading
            assertTrue(vm.uiState.value.weather != null)
            assertEquals(false, vm.uiState.value.isLoadingWeather)
        }

    @Test
    fun quietRefreshRequest_withinDedupWindow_mergesAndReplaysOnceAfterWindow() =
        runTest {
            val city = WeatherFixtures.city("济南")
            val fetcher = RecordingFetcher()
            val requests = MutableSharedFlow<Unit>()
            val vm =
                createTestViewModel(
                    fetcher,
                    quietRefreshRequests = requests,
                    nowEpochMs = { testScheduler.currentTime + 1_000_000L },
                ).vm

            vm.selectCity(city)
            advanceUntilIdle()
            val callsAfterSelect = fetcher.weatherCallCount

            // 第一次信号：距上次请求已过窗口，立即执行
            requests.emit(Unit)
            advanceUntilIdle()
            val callsAfterFirst = fetcher.weatherCallCount
            assertEquals(callsAfterSelect + 1, callsAfterFirst)

            // 窗口内两连信号：不立即执行，合并为一次延迟补发
            requests.emit(Unit)
            requests.emit(Unit)
            assertEquals(callsAfterFirst, fetcher.weatherCallCount)

            // 窗口过期：两个信号只补一次
            advanceUntilIdle()
            assertEquals(callsAfterFirst + 1, fetcher.weatherCallCount)
            assertTrue(vm.uiState.value.weather != null)
            assertFalse(vm.uiState.value.isLoadingWeather)
        }

    @Test
    fun quietRefreshRequest_whileLoading_replaysOnceAfterLoadCompletes() =
        runTest {
            val city = WeatherFixtures.city("济南")
            val gate = CompletableDeferred<WeatherSnapshot>()
            var served = 0
            val fetcher =
                RecordingFetcher(
                    weatherBlock = { c ->
                        served++
                        if (served == 1) gate.await() else snapshotFor(c, temp = 25.0)
                    },
                )
            val requests = MutableSharedFlow<Unit>()
            val vm =
                createTestViewModel(
                    fetcher,
                    quietRefreshRequests = requests,
                    nowEpochMs = { testScheduler.currentTime + 1_000_000L },
                ).vm

            vm.selectCity(city)
            advanceUntilIdle()
            assertTrue(vm.uiState.value.isLoadingWeather)
            assertEquals(1, fetcher.weatherCallCount)

            // loading 中两连信号：不打断当前加载，也不直接丢
            requests.emit(Unit)
            requests.emit(Unit)
            assertEquals(1, fetcher.weatherCallCount)

            gate.complete(snapshotFor(city, temp = 20.0))
            advanceUntilIdle()

            // 加载结束后只补发一次，最终界面是新单位（25.0）快照
            assertEquals(2, fetcher.weatherCallCount)
            assertEquals(
                25.0,
                vm.uiState.value.weather
                    ?.current
                    ?.temperatureC,
            )
            assertFalse(vm.uiState.value.isLoadingWeather)
        }

    @Test
    fun quietRefreshRequest_loadingEndsInsideDedupWindow_waitsForWindow() =
        runTest {
            val city = WeatherFixtures.city("济南")
            val gate = CompletableDeferred<WeatherSnapshot>()
            var served = 0
            val fetcher =
                RecordingFetcher(
                    weatherBlock = { c ->
                        served++
                        when (served) {
                            3 -> gate.await()
                            4 -> snapshotFor(c, temp = 40.0)
                            else -> snapshotFor(c, temp = 10.0)
                        }
                    },
                )
            val vm =
                createTestViewModel(
                    fetcher,
                    nowEpochMs = { testScheduler.currentTime + 1_000_000L },
                ).vm

            vm.selectCity(city)
            advanceUntilIdle()
            // 先发起一次静默刷新，占用去重窗口
            vm.refresh(quiet = true)
            advanceUntilIdle()
            assertEquals(2, fetcher.weatherCallCount)

            // 可见加载在窗口内开始，期间收到单位切换信号（loading 拦截）
            vm.selectCity(city)
            advanceUntilIdle()
            assertTrue(vm.uiState.value.isLoadingWeather)
            assertEquals(3, fetcher.weatherCallCount)
            vm.refresh(quiet = true)
            assertEquals(3, fetcher.weatherCallCount)

            gate.complete(snapshotFor(city, temp = 30.0))
            runCurrent()

            // loading 已结束但窗口未过：补发未发生
            assertFalse(vm.uiState.value.isLoadingWeather)
            assertEquals(3, fetcher.weatherCallCount)

            // 窗口过期后才补发一次
            advanceUntilIdle()
            assertEquals(4, fetcher.weatherCallCount)
            assertEquals(
                40.0,
                vm.uiState.value.weather
                    ?.current
                    ?.temperatureC,
            )
        }

    @Test
    fun unitToggleCToFToC_finalSnapshotUsesFinalUnit() =
        runTest {
            val city = WeatherFixtures.city("济南")
            var unit = TemperatureUnit.CELSIUS
            val fetcher =
                RecordingFetcher(
                    weatherBlock = { c ->
                        when (unit) {
                            TemperatureUnit.CELSIUS -> snapshotFor(c, temp = 20.0)
                            TemperatureUnit.FAHRENHEIT -> snapshotFor(c, temp = 68.0)
                        }
                    },
                )
            val requests = MutableSharedFlow<Unit>()
            val vm =
                createTestViewModel(
                    fetcher,
                    quietRefreshRequests = requests,
                    nowEpochMs = { testScheduler.currentTime + 1_000_000L },
                ).vm

            vm.selectCity(city)
            advanceUntilIdle()
            assertEquals(
                20.0,
                vm.uiState.value.weather
                    ?.current
                    ?.temperatureC,
            )

            // C→F：立即补发
            unit = TemperatureUnit.FAHRENHEIT
            requests.emit(Unit)
            advanceUntilIdle()
            assertEquals(
                68.0,
                vm.uiState.value.weather
                    ?.current
                    ?.temperatureC,
            )

            // F→C 紧随其后（去重窗口内）：合并为窗口过后的单次补发，最终回到 °C
            unit = TemperatureUnit.CELSIUS
            requests.emit(Unit)
            advanceUntilIdle()
            assertEquals(
                20.0,
                vm.uiState.value.weather
                    ?.current
                    ?.temperatureC,
            )

            // 首载 + C→F + 合并补发，共 3 次
            assertEquals(3, fetcher.weatherCallCount)
        }
}

package com.xiaoxiong.weather.ui.weather

import com.xiaoxiong.weather.data.AppError
import com.xiaoxiong.weather.data.HistoryExtreme
import com.xiaoxiong.weather.data.HistoryRangeSummary
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
class WeatherViewModelHistoryRangeTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val start = LocalDate.now().minusDays(7)
    private val end = LocalDate.now().minusDays(1)

    @Test
    fun fetchHistoryRange_success_publishesAndDedups() =
        runTest {
            val city = WeatherFixtures.city("济南")
            val fetcher =
                RecordingFetcher(
                    historyRangeBlock = { _, s, e -> summary(s, e) },
                )
            val vm = createTestViewModel(fetcher).vm

            vm.selectCity(city)
            advanceUntilIdle()
            vm.fetchHistoryRange(start, end)
            advanceUntilIdle()

            val state = vm.uiState.value
            assertEquals(start.toString(), state.historyRange?.startDate)
            assertEquals(33.0, state.historyRange?.hottest?.value)
            assertFalse(state.isHistoryRangeLoading)
            assertFalse(state.historyRangeFailed)
            assertEquals(1, fetcher.historyRangeCallCount)

            // 同城同区间再查：命中内存缓存，不再联网
            vm.fetchHistoryRange(start, end)
            advanceUntilIdle()
            assertEquals(1, fetcher.historyRangeCallCount)
        }

    @Test
    fun fetchHistoryRange_failure_marksFailed() =
        runTest {
            val city = WeatherFixtures.city("济南")
            val fetcher = RecordingFetcher() // 默认返回 null（区间无数据）
            val vm = createTestViewModel(fetcher).vm

            vm.selectCity(city)
            advanceUntilIdle()
            vm.fetchHistoryRange(start, end)
            advanceUntilIdle()

            assertNull(vm.uiState.value.historyRange)
            assertTrue(vm.uiState.value.historyRangeFailed)
            assertFalse(vm.uiState.value.historyRangeNetworkFailed)
            assertFalse(vm.uiState.value.isHistoryRangeLoading)

            // 失败不缓存：再查会重试
            vm.fetchHistoryRange(start, end)
            advanceUntilIdle()
            assertEquals(2, fetcher.historyRangeCallCount)
        }

    @Test
    fun fetchHistoryRange_networkError_marksNetworkFailed() =
        runTest {
            val city = WeatherFixtures.city("济南")
            val fetcher =
                RecordingFetcher(
                    historyRangeBlock = { _, _, _ -> throw AppError.NetworkUnavailable() },
                )
            val vm = createTestViewModel(fetcher).vm

            vm.selectCity(city)
            advanceUntilIdle()
            vm.fetchHistoryRange(start, end)
            advanceUntilIdle()

            assertNull(vm.uiState.value.historyRange)
            assertFalse(vm.uiState.value.historyRangeFailed)
            assertTrue(vm.uiState.value.historyRangeNetworkFailed)
            assertFalse(vm.uiState.value.isHistoryRangeLoading)
        }

    @Test
    fun fetchHistoryRange_switchCity_dropsStaleResult() =
        runTest {
            val cityA = WeatherFixtures.city("济南", lat = 36.65, lon = 117.12)
            val cityB = WeatherFixtures.city("青岛", lat = 36.07, lon = 120.38)
            val gate = CompletableDeferred<HistoryRangeSummary?>()
            val fetcher =
                RecordingFetcher(
                    historyRangeBlock = { _, s, e -> if (gate.isCompleted) summary(s, e) else gate.await() },
                )
            val vm = createTestViewModel(fetcher).vm

            vm.selectCity(cityA)
            advanceUntilIdle()
            vm.fetchHistoryRange(start, end)
            advanceUntilIdle()
            assertTrue(vm.uiState.value.isHistoryRangeLoading)

            // 查询期间换城：旧城市的结果不应盖回新城市
            vm.selectCity(cityB)
            advanceUntilIdle()
            assertFalse(vm.uiState.value.isHistoryRangeLoading)
            assertNull(vm.uiState.value.historyRange)

            gate.complete(summary(start, end))
            advanceUntilIdle()

            assertNull(vm.uiState.value.historyRange)
            assertFalse(vm.uiState.value.isHistoryRangeLoading)
            assertEquals(
                "青岛",
                vm.uiState.value.selectedCity
                    ?.name,
            )
        }

    @Test
    fun fetchHistoryRange_invalidRange_noop() =
        runTest {
            val city = WeatherFixtures.city("济南")
            val fetcher = RecordingFetcher()
            val vm = createTestViewModel(fetcher).vm

            vm.selectCity(city)
            advanceUntilIdle()
            vm.fetchHistoryRange(end, start)
            advanceUntilIdle()

            assertEquals(0, fetcher.historyRangeCallCount)
            assertNull(vm.uiState.value.historyRange)
            assertFalse(vm.uiState.value.isHistoryRangeLoading)
        }

    @Test
    fun fetchHistoryRange_success_thenSwitchCity_resets() =
        runTest {
            val cityA = WeatherFixtures.city("济南", lat = 36.65, lon = 117.12)
            val cityB = WeatherFixtures.city("青岛", lat = 36.07, lon = 120.38)
            val fetcher =
                RecordingFetcher(
                    historyRangeBlock = { _, s, e -> summary(s, e) },
                )
            val vm = createTestViewModel(fetcher).vm

            vm.selectCity(cityA)
            advanceUntilIdle()
            vm.fetchHistoryRange(start, end)
            advanceUntilIdle()
            assertEquals(
                start.toString(),
                vm.uiState.value.historyRange
                    ?.startDate,
            )

            vm.selectCity(cityB)
            advanceUntilIdle()

            assertNull(vm.uiState.value.historyRange)
            assertFalse(vm.uiState.value.historyRangeFailed)
            assertFalse(vm.uiState.value.isHistoryRangeLoading)
        }

    @Test
    fun clearHistoryRange_resetsState() =
        runTest {
            val city = WeatherFixtures.city("济南")
            val fetcher =
                RecordingFetcher(
                    historyRangeBlock = { _, s, e -> summary(s, e) },
                )
            val vm = createTestViewModel(fetcher).vm

            vm.selectCity(city)
            advanceUntilIdle()
            vm.fetchHistoryRange(start, end)
            advanceUntilIdle()
            assertFalse(vm.uiState.value.isHistoryRangeLoading)

            vm.clearHistoryRange()
            advanceUntilIdle()

            assertNull(vm.uiState.value.historyRange)
            assertFalse(vm.uiState.value.historyRangeFailed)
            assertFalse(vm.uiState.value.isHistoryRangeLoading)
        }

    private fun summary(
        start: LocalDate,
        end: LocalDate,
    ) = HistoryRangeSummary(
        startDate = start.toString(),
        endDate = end.toString(),
        days = listOf(testHistoryWeather(start, tempMax = 33.0)),
        hottest = HistoryExtreme(value = 33.0, date = start.toString()),
        coldest = null,
        wettest = null,
    )
}

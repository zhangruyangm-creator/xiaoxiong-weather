package com.xiaoxiong.weather.ui.weather

import com.xiaoxiong.weather.data.AppError
import com.xiaoxiong.weather.data.DebugWeatherOverride
import com.xiaoxiong.weather.fixtures.WeatherFixtures
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class WeatherViewModelDebugModeTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun enableDebugMode_entersAndFlagsState() =
        runTest {
            val city = WeatherFixtures.city("济南")
            val vm = createTestViewModel().vm

            vm.selectCity(city)
            advanceUntilIdle()
            assertFalse(vm.uiState.value.debugEnabled)

            vm.enableDebugMode()
            advanceUntilIdle()

            assertTrue(vm.uiState.value.debugEnabled)
            assertEquals("已进入调试模式", vm.uiState.value.infoMessage)
        }

    @Test
    fun applyDebugOverride_overridesPublishedWeather() =
        runTest {
            val city = WeatherFixtures.city("济南")
            val vm = createTestViewModel().vm

            vm.selectCity(city)
            advanceUntilIdle()
            assertEquals(
                20.0,
                vm.uiState.value.weather
                    ?.current
                    ?.temperatureC,
            )

            vm.applyDebugOverride(DebugWeatherOverride(temperatureC = 33.0))
            advanceUntilIdle()

            assertTrue(vm.uiState.value.debugEnabled)
            assertEquals("已应用调试覆盖", vm.uiState.value.infoMessage)
            awaitTemperature(vm, 33.0)
        }

    @Test
    fun exitDebugMode_restoresBaseWeather() =
        runTest {
            val city = WeatherFixtures.city("济南")
            val vm = createTestViewModel().vm

            vm.selectCity(city)
            advanceUntilIdle()
            vm.applyDebugOverride(DebugWeatherOverride(temperatureC = 33.0))
            advanceUntilIdle()
            awaitTemperature(vm, 33.0)

            vm.exitDebugMode()
            advanceUntilIdle()

            assertFalse(vm.uiState.value.debugEnabled)
            assertEquals("已退出调试模式", vm.uiState.value.infoMessage)
            awaitTemperature(vm, 20.0)
        }

    @Test
    fun clearDebugOverride_restoresBaseWhileStayingEnabled() =
        runTest {
            val city = WeatherFixtures.city("济南")
            val vm = createTestViewModel().vm

            vm.selectCity(city)
            advanceUntilIdle()
            vm.applyDebugOverride(DebugWeatherOverride(temperatureC = 33.0))
            advanceUntilIdle()
            awaitTemperature(vm, 33.0)

            vm.clearDebugOverride()
            advanceUntilIdle()

            awaitInfo(vm, "已清除覆盖，恢复实况字段")
            awaitTemperature(vm, 20.0)
            assertTrue(vm.uiState.value.debugEnabled)
        }

    @Test
    fun exitDebugMode_afterSwitchToFailingCity_doesNotReviveOldCityWeather() =
        runTest {
            val cityA = WeatherFixtures.city("济南", lat = 36.65, lon = 117.12)
            val cityB = WeatherFixtures.city("青岛", lat = 36.07, lon = 120.38)
            val fetcher =
                RecordingFetcher(
                    weatherBlock = { city ->
                        if (city.name == "青岛") {
                            throw AppError.NetworkUnavailable()
                        } else {
                            snapshotFor(city, temp = 20.0)
                        }
                    },
                )
            val vm = createTestViewModel(fetcher).vm

            vm.selectCity(cityA)
            advanceUntilIdle()
            assertEquals(
                "济南",
                vm.uiState.value.weather
                    ?.city
                    ?.name,
            )

            // 换城后新城市请求失败：旧城市快照不能作为调试还原数据
            vm.selectCity(cityB)
            advanceUntilIdle()
            assertNull(vm.uiState.value.weather)

            vm.exitDebugMode()
            advanceUntilIdle()

            assertEquals(
                "青岛",
                vm.uiState.value.selectedCity
                    ?.name,
            )
            assertNull(vm.uiState.value.weather)
        }

    @Test
    fun selectCity_publishesIconAndWidgetEffects() =
        runTest {
            val city = WeatherFixtures.city("济南")
            val harness = createTestViewModel()
            val vm = harness.vm

            vm.selectCity(city)
            advanceUntilIdle()

            // 成功拉取后：调度桌面图标（天气码/昼夜）+ 刷新小组件
            val last = harness.effects.iconSchedules.lastOrNull()
            assertEquals(0, last?.weatherCode)
            assertEquals(null, last?.precipMmPerHour)
            assertEquals(true, last?.isDay)
            assertTrue(harness.effects.widgetRefreshCount >= 1)
            assertNull(vm.uiState.value.errorMessage)
        }
}

/**
 * 调试回灌走 Dispatchers.Default（真实线程），advanceUntilIdle 等不到它；
 * 轮询等待天气字段真正被重发。
 */
private fun awaitTemperature(
    vm: WeatherViewModel,
    expected: Double?,
) {
    val deadline = System.currentTimeMillis() + 5_000
    while (System.currentTimeMillis() < deadline) {
        if (vm.uiState.value.weather
                ?.current
                ?.temperatureC == expected
        ) {
            return
        }
        Thread.sleep(10)
    }
    throw AssertionError("weather temperature not republished as $expected")
}

private fun awaitInfo(
    vm: WeatherViewModel,
    expected: String,
) {
    val deadline = System.currentTimeMillis() + 5_000
    while (System.currentTimeMillis() < deadline) {
        if (vm.uiState.value.infoMessage == expected) return
        Thread.sleep(10)
    }
    throw AssertionError("infoMessage still ${vm.uiState.value.infoMessage}, expected $expected")
}

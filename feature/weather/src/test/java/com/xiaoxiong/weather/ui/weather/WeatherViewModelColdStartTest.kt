package com.xiaoxiong.weather.ui.weather

import com.xiaoxiong.weather.data.AppSettings
import com.xiaoxiong.weather.data.SelectedCityStore
import com.xiaoxiong.weather.fixtures.WeatherFixtures
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class WeatherViewModelColdStartTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun ensureInitialLoad_savedCity_selectsItWithoutLocating() =
        runTest {
            val saved = WeatherFixtures.city("济南")
            val fetcher = RecordingFetcher()
            val session = RecordingLocationSession()
            val selectedCityStore = FakeSelectedCitySource().apply { seed(saved) }
            val vm =
                createTestViewModel(
                    fetcher,
                    session = session,
                    selectedCityStore = selectedCityStore,
                ).vm

            vm.ensureInitialLoad()
            advanceUntilIdle()

            assertEquals(
                "济南",
                vm.uiState.value.selectedCity
                    ?.name,
            )
            assertEquals(0, session.locateCount)
            assertEquals(1, fetcher.weatherCallCount)
            assertFalse(vm.uiState.value.usingCurrentLocation)
        }

    @Test
    fun ensureInitialLoad_noSaved_usesFavorite() =
        runTest {
            val favorite = WeatherFixtures.city("上海")
            val favoriteStore = FakeFavoriteCitySource().apply { this.favorite = favorite }
            val vm =
                createTestViewModel(
                    settings = AppSettings(preferLocationOnLaunch = false),
                    favoriteStore = favoriteStore,
                ).vm

            vm.ensureInitialLoad()
            advanceUntilIdle()

            assertEquals(
                "上海",
                vm.uiState.value.selectedCity
                    ?.name,
            )
        }

    @Test
    fun ensureInitialLoad_noSavedNoFavorite_fallsBackToBeijing() =
        runTest {
            val vm = createTestViewModel(settings = AppSettings(preferLocationOnLaunch = false)).vm

            vm.ensureInitialLoad()
            advanceUntilIdle()

            assertEquals(
                "Beijing",
                vm.uiState.value.selectedCity
                    ?.name,
            )
            assertFalse(vm.uiState.value.usingCurrentLocation)
        }

    @Test
    fun ensureInitialLoad_userPickedCityWhileReading_keepsUserChoice() =
        runTest {
            val savedA = WeatherFixtures.city("济南")
            val cityB = WeatherFixtures.city("青岛", lat = 36.07, lon = 120.38)
            val gate = CompletableDeferred<Unit>()
            val selectedCityStore =
                FakeSelectedCitySource().apply {
                    seed(savedA)
                    getOnceOverride = SelectedCityStore.PersistedSelection(savedA, useLocation = false)
                    getOnceGate = gate
                }
            val vm =
                createTestViewModel(
                    settings = AppSettings(preferLocationOnLaunch = false),
                    selectedCityStore = selectedCityStore,
                ).vm

            vm.ensureInitialLoad()
            advanceUntilIdle()
            // 首启读取仍挂起时用户手动选城
            vm.selectCity(cityB)
            advanceUntilIdle()
            assertEquals(
                "青岛",
                vm.uiState.value.selectedCity
                    ?.name,
            )

            gate.complete(Unit)
            advanceUntilIdle()

            assertEquals(
                "青岛",
                vm.uiState.value.selectedCity
                    ?.name,
            )
        }

    @Test
    fun restoreFallbackCity_noSaved_usesLocalizedFallbackName() =
        runTest {
            val session = RecordingLocationSession()
            val vm =
                createTestViewModel(
                    session = session,
                    locationPermission = true,
                    settings = AppSettings(preferLocationOnLaunch = true),
                ).vm

            vm.loadCurrentLocationWeather()
            advanceUntilIdle()
            session.emitError(RuntimeException("locate failed"))
            advanceUntilIdle()

            assertEquals(
                "Beijing",
                vm.uiState.value.selectedCity
                    ?.name,
            )
        }

    @Test
    fun restoreFallbackCity_userPickedWhileReading_keepsUserChoice() =
        runTest {
            val savedA = WeatherFixtures.city("济南")
            val cityB = WeatherFixtures.city("青岛", lat = 36.07, lon = 120.38)
            val gate = CompletableDeferred<Unit>()
            val selectedCityStore =
                FakeSelectedCitySource().apply {
                    getOnceOverride = SelectedCityStore.PersistedSelection(savedA, useLocation = false)
                    getOnceGate = gate
                }
            val session = RecordingLocationSession()
            val vm =
                createTestViewModel(
                    session = session,
                    locationPermission = true,
                    settings = AppSettings(preferLocationOnLaunch = true),
                    selectedCityStore = selectedCityStore,
                ).vm

            vm.loadCurrentLocationWeather()
            advanceUntilIdle()
            session.emitError(RuntimeException("locate failed"))
            advanceUntilIdle()
            // 回落读取仍挂起时用户手动选城
            vm.selectCity(cityB)
            advanceUntilIdle()

            gate.complete(Unit)
            advanceUntilIdle()

            assertEquals(
                "青岛",
                vm.uiState.value.selectedCity
                    ?.name,
            )
        }

    @Test
    fun ensureInitialLoad_preferLocationWithoutPermission_defersAndLocatesAfterGrant() =
        runTest {
            val saved = WeatherFixtures.city("济南")
            val located = WeatherFixtures.city("青岛", lat = 36.07, lon = 120.38)
            val selectedCityStore = FakeSelectedCitySource().apply { seed(saved, useLocation = true) }
            val session = RecordingLocationSession()
            val harness =
                createTestViewModel(
                    session = session,
                    locationPermission = false,
                    settings = AppSettings(preferLocationOnLaunch = true),
                    selectedCityStore = selectedCityStore,
                )
            val vm = harness.vm

            // 无权限：先落到已保存城市，暂不定位
            vm.ensureInitialLoad()
            advanceUntilIdle()
            assertEquals(
                "济南",
                vm.uiState.value.selectedCity
                    ?.name,
            )
            assertEquals(0, session.locateCount)

            // 首次授权后补一次定位，并切到当前位置
            harness.locationPermissionSource.granted = true
            vm.onPermissionResult(true)
            advanceUntilIdle()
            assertEquals(1, session.locateCount)

            session.emitResolved(located)
            advanceUntilIdle()
            assertEquals(
                "青岛",
                vm.uiState.value.selectedCity
                    ?.name,
            )
            assertTrue(vm.uiState.value.usingCurrentLocation)
        }

    @Test
    fun ensureInitialLoad_preferLocationWithPermission_locatesImmediately() =
        runTest {
            val located = WeatherFixtures.city("青岛", lat = 36.07, lon = 120.38)
            val session = RecordingLocationSession()
            val vm =
                createTestViewModel(
                    session = session,
                    locationPermission = true,
                    settings = AppSettings(preferLocationOnLaunch = true),
                ).vm

            vm.ensureInitialLoad()
            advanceUntilIdle()

            assertEquals(1, session.locateCount)
            session.emitResolved(located)
            advanceUntilIdle()
            assertEquals(
                "青岛",
                vm.uiState.value.selectedCity
                    ?.name,
            )
            assertTrue(vm.uiState.value.usingCurrentLocation)
        }

    @Test
    fun onAppForeground_coldStartSkipsOnce_thenQuietRefreshes() =
        runTest {
            val fetcher = RecordingFetcher()
            val vm = createTestViewModel(fetcher).vm
            vm.ensureInitialLoad()
            advanceUntilIdle()
            val callsAfterInitial = fetcher.weatherCallCount
            assertTrue(callsAfterInitial >= 1)

            // 冷启动后的第一次 ON_START 不重复拉取（避免与首启加载并发）
            vm.onAppForeground()
            runCurrent()
            assertEquals(callsAfterInitial, fetcher.weatherCallCount)

            // 之后每次回前台静默刷新
            vm.onAppForeground()
            runCurrent()
            assertTrue(fetcher.weatherCallCount > callsAfterInitial)

            vm.onAppBackground()
        }

    @Test
    fun consumeRuntimePermissionRequest_trueOnlyOncePerProcess() =
        runTest {
            val vm = createTestViewModel().vm

            assertTrue(vm.consumeRuntimePermissionRequest())
            assertFalse(vm.consumeRuntimePermissionRequest())
        }

    @Test
    fun onNotificationPermissionResult_denied_showsInfo() =
        runTest {
            val vm = createTestViewModel().vm

            vm.onNotificationPermissionResult(false)
            advanceUntilIdle()

            assertEquals(
                "未开启通知权限，天气提醒不会弹出；可在系统设置中稍后开启",
                vm.uiState.value.infoMessage,
            )
        }
}

package com.xiaoxiong.weather.ui.weather

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
class WeatherViewModelLocationTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun loadCurrentLocationWeather_withoutPermission_showsError() =
        runTest {
            val session = RecordingLocationSession()
            val vm = createTestViewModel(locationPermission = false, session = session).vm

            vm.loadCurrentLocationWeather(showRationale = true, quiet = false)

            assertEquals("请先授予定位权限", vm.uiState.value.errorMessage)
            assertEquals(0, session.locateCount)
            assertFalse(vm.uiState.value.isLocating)
        }

    @Test
    fun loadCurrentLocationWeather_quietWithoutPermission_staysSilent() =
        runTest {
            val session = RecordingLocationSession()
            val vm = createTestViewModel(locationPermission = false, session = session).vm

            vm.loadCurrentLocationWeather(showRationale = true, quiet = true)

            assertNull(vm.uiState.value.errorMessage)
            assertEquals(0, session.locateCount)
        }

    @Test
    fun loadCurrentLocationWeather_withPermission_resolvesAndSelectsCity() =
        runTest {
            val city = WeatherFixtures.city("济南")
            val session = RecordingLocationSession()
            val harness = createTestViewModel(locationPermission = true, session = session)
            val vm = harness.vm

            vm.loadCurrentLocationWeather()
            assertEquals(1, session.locateCount)
            assertTrue(vm.uiState.value.isLocating)
            assertTrue(vm.uiState.value.isLoadingWeather)

            session.emitResolved(city)
            advanceUntilIdle()

            val state = vm.uiState.value
            assertEquals(city.name, state.selectedCity?.name)
            assertTrue(state.usingCurrentLocation)
            assertEquals(20.0, state.weather?.current?.temperatureC)
            assertFalse(state.isLoadingWeather)
            assertFalse(state.isLocating)
            assertTrue(harness.effects.iconSchedules.isNotEmpty())
            assertTrue(harness.effects.widgetRefreshCount >= 1)
            val persisted = harness.selectedCityStore.saved.last()
            assertTrue(persisted.useLocation)
        }

    @Test
    fun loadCurrentLocationWeather_onError_withoutFallback_selectsBeijing() =
        runTest {
            val session = RecordingLocationSession()
            val vm = createTestViewModel(locationPermission = true, session = session).vm

            vm.loadCurrentLocationWeather()
            session.emitError(Throwable("boom"))
            advanceUntilIdle()

            // 定位失败且无任何已保存/收藏城市 → 回落到北京并展示其天气
            val state = vm.uiState.value
            assertEquals("北京", state.selectedCity?.name)
            assertEquals(20.0, state.weather?.current?.temperatureC)
            assertFalse(state.usingCurrentLocation)
            assertFalse(state.isLocating)
            assertFalse(state.isLoadingWeather)
        }

    @Test
    fun loadCurrentLocationWeather_onError_withExistingWeather_keepsError() =
        runTest {
            val cityA = WeatherFixtures.city("济南")
            val session = RecordingLocationSession()
            val vm = createTestViewModel(locationPermission = true, session = session).vm

            // 已有天气在展示（如来自缓存/上一条选城），定位失败不需要回退城市
            vm.selectCity(cityA)
            advanceUntilIdle()
            assertEquals(
                cityA.name,
                vm.uiState.value.selectedCity
                    ?.name,
            )

            vm.loadCurrentLocationWeather()
            session.emitError(Throwable("boom"))
            advanceUntilIdle()

            assertEquals("boom", vm.uiState.value.errorMessage)
            assertEquals(
                cityA.name,
                vm.uiState.value.selectedCity
                    ?.name,
            )
            assertFalse(vm.uiState.value.isLocating)
        }

    @Test
    fun loadCurrentLocationWeather_onError_withFavorite_restoresFavorite() =
        runTest {
            val favoriteCity = WeatherFixtures.city("青岛", lat = 36.07, lon = 120.38)
            val favoriteStore = FakeFavoriteCitySource()
            favoriteStore.favorite = favoriteCity
            val session = RecordingLocationSession()
            val harness =
                createTestViewModel(
                    locationPermission = true,
                    session = session,
                    favoriteStore = favoriteStore,
                )

            harness.vm.loadCurrentLocationWeather()
            session.emitError(Throwable("boom"))
            advanceUntilIdle()

            assertEquals(
                favoriteCity.name,
                harness.vm.uiState.value.selectedCity
                    ?.name,
            )
            assertFalse(harness.vm.uiState.value.isLocating)
        }

    @Test
    fun onPlaceUpdated_updatesLabel_withoutRefetch() =
        runTest {
            val resolved = WeatherFixtures.city("市中区", lat = 36.65, lon = 117.12)
            val renamed = WeatherFixtures.city("历下区", lat = 36.65, lon = 117.12)
            val session = RecordingLocationSession()
            val harness = createTestViewModel(locationPermission = true, session = session)
            val vm = harness.vm

            vm.loadCurrentLocationWeather()
            session.emitResolved(resolved)
            advanceUntilIdle()
            assertEquals(1, harness.fetcher.weatherCallCount)
            assertEquals(
                resolved.name,
                vm.uiState.value.selectedCity
                    ?.name,
            )

            session.emitPlaceUpdated(renamed)
            advanceUntilIdle()

            assertEquals(
                renamed.name,
                vm.uiState.value.selectedCity
                    ?.name,
            )
            assertEquals(
                renamed.name,
                vm.uiState.value.weather
                    ?.city
                    ?.name,
            )
            assertTrue(vm.uiState.value.usingCurrentLocation)
            // 同坐标地名刷新不重拉天气
            assertEquals(1, harness.fetcher.weatherCallCount)
            val persisted = harness.selectedCityStore.saved.last()
            assertEquals(renamed.name, persisted.city.name)
            assertTrue(persisted.useLocation)
        }

    @Test
    fun requestLocate_withPermission_locatesImmediately() =
        runTest {
            val session = RecordingLocationSession()
            val vm = createTestViewModel(locationPermission = true, session = session).vm

            assertFalse(vm.requestLocate())
            assertEquals(1, session.locateCount)
            assertTrue(vm.uiState.value.isLocating)
        }

    @Test
    fun requestLocate_withoutPermission_thenDenied_showsError() =
        runTest {
            val session = RecordingLocationSession()
            val vm = createTestViewModel(locationPermission = false, session = session).vm

            // 先完成一次冷启动初始加载（真实流程里 onStart 已触发），落到默认城市
            vm.ensureInitialLoad()
            advanceUntilIdle()
            assertEquals(
                "北京",
                vm.uiState.value.selectedCity
                    ?.name,
            )

            assertTrue(vm.requestLocate())
            assertEquals(0, session.locateCount)

            vm.onPermissionResult(granted = false)
            advanceUntilIdle()

            assertEquals("请先授予定位权限", vm.uiState.value.errorMessage)
            assertEquals(
                "北京",
                vm.uiState.value.selectedCity
                    ?.name,
            )
        }
}

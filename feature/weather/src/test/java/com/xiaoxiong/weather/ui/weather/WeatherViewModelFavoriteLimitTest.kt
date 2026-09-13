package com.xiaoxiong.weather.ui.weather

import com.xiaoxiong.weather.data.City
import com.xiaoxiong.weather.data.FavoriteCityStore
import com.xiaoxiong.weather.fixtures.WeatherFixtures
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test

/**
 * 收藏上限反馈：满 [FavoriteCityStore.MAX_FAVORITES] 后新增收藏不再静默失败，
 * 而是给出可见提示；取消已有收藏始终允许。
 */
@OptIn(ExperimentalCoroutinesApi::class)
class WeatherViewModelFavoriteLimitTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private fun favoriteCities(count: Int): List<City> = (1..count).map { WeatherFixtures.city("城市$it", lat = 30.0 + it, lon = 110.0 + it) }

    @Test
    fun toggleFavorite_atLimit_showsInfoAndSkipsToggle() =
        runTest {
            val city = WeatherFixtures.city("济南")
            val store =
                FakeFavoriteCitySource().apply {
                    seedFavorites(favoriteCities(FavoriteCityStore.MAX_FAVORITES))
                }
            val vm = createTestViewModel(favoriteStore = store).vm

            vm.selectCity(city)
            advanceUntilIdle()
            vm.toggleFavorite()
            advanceUntilIdle()

            assertEquals(0, store.toggleCount)
            assertEquals("收藏已达上限", vm.uiState.value.infoMessage)
        }

    @Test
    fun toggleFavorite_atLimit_removalStillAllowed() =
        runTest {
            val city = WeatherFixtures.city("济南")
            val store =
                FakeFavoriteCitySource().apply {
                    seedFavorites(favoriteCities(FavoriteCityStore.MAX_FAVORITES - 1) + city)
                }
            val vm = createTestViewModel(favoriteStore = store).vm

            vm.selectCity(city)
            advanceUntilIdle()
            vm.toggleFavorite()
            advanceUntilIdle()

            assertEquals(1, store.toggleCount)
            assertNull(vm.uiState.value.infoMessage)
        }

    @Test
    fun toggleFavorite_belowLimit_togglesWithoutInfo() =
        runTest {
            val city = WeatherFixtures.city("济南")
            val store = FakeFavoriteCitySource()
            val vm = createTestViewModel(favoriteStore = store).vm

            vm.selectCity(city)
            advanceUntilIdle()
            vm.toggleFavorite()
            advanceUntilIdle()

            assertEquals(1, store.toggleCount)
            assertNull(vm.uiState.value.infoMessage)
        }
}

package com.xiaoxiong.weather.ui.weather

import com.xiaoxiong.weather.data.City
import com.xiaoxiong.weather.domain.CitySearchUseCase
import com.xiaoxiong.weather.fixtures.WeatherFixtures
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.mockito.Mockito
import org.mockito.stubbing.Answer

/**
 * 搜索框与选城/静默刷新的交互：
 * - 静默刷新（轮询/网络恢复/单位切换/回前台）不得覆盖用户正在输入的搜索词与联想；
 * - queryFlow 在选城后与 UI query 同步，用户删回原词能重新触发搜索。
 */
@OptIn(ExperimentalCoroutinesApi::class)
class WeatherViewModelSearchInputTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    /** 强匹配的搜索 mock：记录 searchLocal 收到的查询词，strongMatch 避免走远端。 */
    private fun recordingSearch(
        queries: MutableList<String>,
        suggestions: List<City> = emptyList(),
    ): CitySearchUseCase =
        Mockito.mock(
            CitySearchUseCase::class.java,
            Answer { invocation ->
                when (invocation.method.name) {
                    "searchLocal" -> {
                        queries += invocation.arguments[0] as String
                        CitySearchUseCase.LocalHit(suggestions, strongMatch = true)
                    }
                    else -> null
                }
            },
        )

    @Test
    fun quietRefresh_keepsUserQueryAndSuggestions() =
        runTest {
            val city = WeatherFixtures.city("济南")
            val typed = listOf(WeatherFixtures.city("上海", lat = 31.23, lon = 121.47))
            val vm = createTestViewModel(citySearch = recordingSearch(mutableListOf(), typed)).vm

            vm.selectCity(city)
            advanceUntilIdle()
            vm.onQueryChange("上海")
            advanceUntilIdle()
            assertEquals("上海", vm.uiState.value.query)
            assertEquals(typed, vm.uiState.value.suggestions)

            // 静默刷新：输入与联想都必须保留
            vm.selectCity(city, quiet = true)
            advanceUntilIdle()
            assertEquals("上海", vm.uiState.value.query)
            assertEquals(typed, vm.uiState.value.suggestions)
        }

    @Test
    fun manualSelect_updatesQueryAndClearsSuggestions() =
        runTest {
            val cityA = WeatherFixtures.city("济南", lat = 36.65, lon = 117.12)
            val cityB = WeatherFixtures.city("青岛", lat = 36.07, lon = 120.38)
            val typed = listOf(WeatherFixtures.city("上海", lat = 31.23, lon = 121.47))
            val vm = createTestViewModel(citySearch = recordingSearch(mutableListOf(), typed)).vm

            vm.selectCity(cityA)
            advanceUntilIdle()
            vm.onQueryChange("上海")
            advanceUntilIdle()
            assertEquals(typed, vm.uiState.value.suggestions)

            // 非静默选城：仍同步城市名并清空联想
            vm.selectCity(cityB)
            advanceUntilIdle()
            assertEquals(cityB.displayName, vm.uiState.value.query)
            assertTrue(
                vm.uiState.value.suggestions
                    .isEmpty(),
            )
        }

    @Test
    fun quietRefresh_whenQueryStillFollowsCity_syncsNewCityName() =
        runTest {
            val cityA = WeatherFixtures.city("济南", lat = 36.65, lon = 117.12)
            val cityB = WeatherFixtures.city("青岛", lat = 36.07, lon = 120.38)
            val vm = createTestViewModel().vm

            vm.selectCity(cityA)
            advanceUntilIdle()
            assertEquals(cityA.displayName, vm.uiState.value.query)

            // 输入未偏离城市名（用户未在搜索）：静默换城时跟随同步新城市名
            vm.selectCity(cityB, quiet = true)
            advanceUntilIdle()
            assertEquals(cityB.displayName, vm.uiState.value.query)
        }

    @Test
    fun placeRefined_thenBackspaceToOldLabel_triggersSearch() =
        runTest {
            val initial = WeatherFixtures.city("北京", lat = 39.9, lon = 116.4)
            val refined = initial.copy(name = "北京市")
            val queries = mutableListOf<String>()
            val session = RecordingLocationSession()
            val vm =
                createTestViewModel(
                    session = session,
                    citySearch = recordingSearch(queries),
                    locationPermission = true,
                ).vm

            vm.loadCurrentLocationWeather(showRationale = false)
            session.emitResolved(initial)
            advanceUntilIdle()
            session.emitPlaceUpdated(refined)
            advanceUntilIdle()
            assertEquals(refined.displayName, vm.uiState.value.query)

            // 地名精化把 UI query 改成新地名：queryFlow 应一并同步，
            // 用户退格回旧地名（单次文本变化）时才会重新触发搜索
            queries.clear()
            vm.onQueryChange(initial.displayName)
            advanceUntilIdle()
            assertEquals(listOf(initial.displayName), queries)
        }

    @Test
    fun clearQueryKeepLocation_preservesLocationMode_quietRefreshKeepsEmptyQuery() =
        runTest {
            val city = WeatherFixtures.city("北京")
            val vm = createTestViewModel().vm

            vm.selectCity(city, fromLocation = true)
            advanceUntilIdle()
            assertTrue(vm.uiState.value.usingCurrentLocation)

            vm.clearQueryKeepLocation()
            advanceUntilIdle()
            assertEquals("", vm.uiState.value.query)
            assertTrue(
                vm.uiState.value.suggestions
                    .isEmpty(),
            )
            assertTrue(
                "清空搜索不应退出「当前位置」模式",
                vm.uiState.value.usingCurrentLocation,
            )

            // 清空后静默刷新：空查询/定位模式都不被覆盖
            vm.selectCity(city, fromLocation = true, quiet = true)
            advanceUntilIdle()
            assertEquals("", vm.uiState.value.query)
            assertTrue(vm.uiState.value.usingCurrentLocation)
        }

    @Test
    fun selectCity_sameTextReenteredBeforeDebounce_doesNotSearchCityFullName() =
        runTest {
            val city =
                City(
                    id = 1,
                    name = "上海",
                    latitude = 31.23,
                    longitude = 121.47,
                    country = "中国",
                    admin1 = "上海市",
                )
            val queries = mutableListOf<String>()
            val vm = createTestViewModel(citySearch = recordingSearch(queries)).vm

            vm.onQueryChange("上海")
            advanceUntilIdle()
            queries.clear()

            // 选城同步值还未被防抖消费（<200ms），此时 IME 以相同文本回调一次
            vm.selectCity(city)
            vm.onQueryChange(city.displayName)
            advanceUntilIdle()

            // 同值输入未改变 queryFlow，不应把选城跳过标记作废，否则会误搜一遍城市全名
            assertEquals(emptyList<String>(), queries)
        }

    @Test
    fun selectCity_thenRetypePreviousQuery_triggersSearchAgain() =
        runTest {
            val city = WeatherFixtures.city("济南")
            val queries = mutableListOf<String>()
            val vm = createTestViewModel(citySearch = recordingSearch(queries)).vm

            vm.selectCity(city)
            advanceUntilIdle()
            vm.onQueryChange("上海")
            advanceUntilIdle()
            assertEquals(listOf("上海"), queries)

            // 选城把 UI query 改为城市名，queryFlow 应同步（且不再自动搜一轮城市全名）
            vm.selectCity(city)
            advanceUntilIdle()
            assertEquals(city.displayName, vm.uiState.value.query)
            assertEquals(listOf("上海"), queries)

            // 用户删回原词：queryFlow 已同步过，重新触发搜索
            queries.clear()
            vm.onQueryChange("上海")
            advanceUntilIdle()
            assertEquals(listOf("上海"), queries)
        }
}

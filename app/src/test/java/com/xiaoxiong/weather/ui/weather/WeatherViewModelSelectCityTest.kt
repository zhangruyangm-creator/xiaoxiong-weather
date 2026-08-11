package com.xiaoxiong.weather.ui.weather

import android.app.Application
import android.content.res.Resources
import com.xiaoxiong.weather.R
import com.xiaoxiong.weather.data.AppError
import com.xiaoxiong.weather.data.AppSettings
import com.xiaoxiong.weather.data.AppSettingsSource
import com.xiaoxiong.weather.data.City
import com.xiaoxiong.weather.data.DebugWeatherOverride
import com.xiaoxiong.weather.data.DebugWeatherSource
import com.xiaoxiong.weather.data.DebugWeatherState
import com.xiaoxiong.weather.data.FavoriteCitySource
import com.xiaoxiong.weather.data.HistoryWeather
import com.xiaoxiong.weather.data.LocationPermissionSource
import com.xiaoxiong.weather.data.LocationSession
import com.xiaoxiong.weather.data.LocationSessionFactory
import com.xiaoxiong.weather.data.PhenologyCache
import com.xiaoxiong.weather.data.PhenologyCacheStore
import com.xiaoxiong.weather.data.RadarMapRepository
import com.xiaoxiong.weather.data.RecentCitySource
import com.xiaoxiong.weather.data.SelectedCitySource
import com.xiaoxiong.weather.data.SelectedCityStore
import com.xiaoxiong.weather.data.WeatherCache
import com.xiaoxiong.weather.data.WeatherCacheStore
import com.xiaoxiong.weather.data.WeatherFetcher
import com.xiaoxiong.weather.data.WeatherPresentationEffects
import com.xiaoxiong.weather.data.WeatherSnapshot
import com.xiaoxiong.weather.domain.CitySearchUseCase
import com.xiaoxiong.weather.fixtures.WeatherFixtures
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TestWatcher
import org.junit.runner.Description
import org.mockito.Mockito
import java.time.LocalDate

@OptIn(ExperimentalCoroutinesApi::class)
class MainDispatcherRule : TestWatcher() {
    private val dispatcher = UnconfinedTestDispatcher()

    override fun starting(description: Description) {
        Dispatchers.setMain(dispatcher)
    }

    override fun finished(description: Description) {
        Dispatchers.resetMain()
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class WeatherViewModelSelectCityTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun selectCity_networkFailure_fallsBackToCacheOffline() =
        runTest {
            val city = WeatherFixtures.city("济南")
            val cachedSnapshot = snapshot(city, temp = 18.0)
            val cache =
                FakeWeatherCache().apply {
                    put(city, cachedSnapshot, savedAt = 1_700_000_000_000L)
                }
            val fetcher = FakeWeatherFetcher { throw AppError.NetworkUnavailable() }
            val session = RecordingLocationSession()
            val vm = createViewModel(fetcher, cache, session)

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
    fun selectCity_cancelsPendingLocateAndDropsStaleFetch() =
        runTest {
            val cityA = WeatherFixtures.city("济南", lat = 36.65, lon = 117.12)
            val cityB = WeatherFixtures.city("青岛", lat = 36.07, lon = 120.38)
            val snapA = snapshot(cityA, temp = 10.0)
            val snapB = snapshot(cityB, temp = 20.0)
            val gateA = CompletableDeferred<WeatherSnapshot>()
            val fetcher =
                FakeWeatherFetcher { city ->
                    when (city.name) {
                        "济南" -> gateA.await()
                        else -> snapB
                    }
                }
            val cache = FakeWeatherCache()
            val session = RecordingLocationSession()
            val vm = createViewModel(fetcher, cache, session)

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
            val fetcher = RecordingPhenologyFetcher()
            val phenologyCache =
                FakePhenologyCache().apply {
                    seed(city, year, shiftDays = -5)
                }
            val vm = createViewModel(fetcher, FakeWeatherCache(), RecordingLocationSession(), phenologyCache)

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
                RecordingPhenologyFetcher(
                    phenologyBlock = { _, _ -> -8 },
                )
            val phenologyCache =
                FakePhenologyCache().apply {
                    seed(city, year, shiftDays = null, fetchedToday = false)
                }
            val vm = createViewModel(fetcher, FakeWeatherCache(), RecordingLocationSession(), phenologyCache)

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
            val fetcher = RecordingPhenologyFetcher() // 默认返回 null（数据不足）
            val phenologyCache = FakePhenologyCache()
            val vm = createViewModel(fetcher, FakeWeatherCache(), RecordingLocationSession(), phenologyCache)

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
            val year = LocalDate.now().year
            val fetcher = RecordingPhenologyFetcher(phenologyBlock = { _, _ -> -5 })
            val phenologyCache = FakePhenologyCache()
            val vm = createViewModel(fetcher, FakeWeatherCache(), RecordingLocationSession(), phenologyCache)

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
                RecordingPhenologyFetcher(
                    historyBlock = { _, _ -> historyWeather(date, tempMax = 30.0) },
                )
            val vm = createViewModel(fetcher, FakeWeatherCache(), RecordingLocationSession())

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
                RecordingPhenologyFetcher(
                    historyBlock = { _, _ -> null },
                )
            val vm = createViewModel(fetcher, FakeWeatherCache(), RecordingLocationSession())

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
                RecordingPhenologyFetcher(
                    historyBlock = { _, _ -> historyWeather(date, tempMax = 30.0) },
                )
            val vm = createViewModel(fetcher, FakeWeatherCache(), RecordingLocationSession())

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

    private fun createViewModel(
        fetcher: WeatherFetcher,
        cache: WeatherCache,
        session: RecordingLocationSession,
        phenologyCache: PhenologyCache = FakePhenologyCache(),
    ): WeatherViewModel {
        val app = mockApplication()
        val deps =
            WeatherViewModelDeps(
                weatherRepository = fetcher,
                locationHelper =
                    object : LocationPermissionSource {
                        override fun hasLocationPermission() = false
                    },
                favoriteStore = FakeFavoriteCitySource(),
                recentCityStore = FakeRecentCitySource(),
                selectedCityStore = FakeSelectedCitySource(),
                weatherCacheStore = cache,
                phenologyCache = phenologyCache,
                citySearch = Mockito.mock(CitySearchUseCase::class.java),
                settingsStore = FakeAppSettingsSource(),
                debugWeatherStore = FakeDebugWeatherSource(),
                radarMapRepository = Mockito.mock(RadarMapRepository::class.java),
                locationSessionFactory = LocationSessionFactory { session },
                presentationEffects = WeatherPresentationEffects.NoOp,
                popularCitiesLoader = { emptyList() },
            )
        return WeatherViewModel(app, deps)
    }

    private fun mockApplication(): Application {
        val resources = Mockito.mock(Resources::class.java)
        Mockito.`when`(resources.getString(Mockito.anyInt())).thenAnswer { inv ->
            messageForRes(inv.getArgument(0))
        }
        Mockito.`when`(resources.getString(Mockito.anyInt(), Mockito.any())).thenAnswer { inv ->
            messageForRes(inv.getArgument(0))
        }
        val app = Mockito.mock(Application::class.java)
        Mockito.`when`(app.resources).thenReturn(resources)
        Mockito.`when`(app.getString(Mockito.anyInt())).thenAnswer { inv ->
            messageForRes(inv.getArgument(0))
        }
        Mockito.`when`(app.getString(Mockito.anyInt(), Mockito.any())).thenAnswer { inv ->
            messageForRes(inv.getArgument(0))
        }
        return app
    }

    private fun messageForRes(id: Int): String =
        when (id) {
            R.string.error_network_cached -> "网络异常，已显示缓存天气"
            else -> "error"
        }

    private fun snapshot(
        city: City,
        temp: Double,
    ) = WeatherSnapshot(
        city = city,
        current = WeatherFixtures.current(temperatureC = temp),
        hourly = emptyList(),
        daily = emptyList(),
    )
}

private class FakeWeatherFetcher(
    private val block: suspend (City) -> WeatherSnapshot,
) : WeatherFetcher {
    override suspend fun fetchWeather(city: City): WeatherSnapshot = block(city)
}

/** 记录物候/历史拉取次数；默认返回 null（数据不足），可注入具体返回值。 */
private class RecordingPhenologyFetcher(
    private val weatherBlock: (City) -> WeatherSnapshot = { city ->
        WeatherSnapshot(
            city = city,
            current = WeatherFixtures.current(temperatureC = 20.0),
        )
    },
    private val phenologyBlock: (City, LocalDate) -> Int? = { _, _ -> null },
    private val historyBlock: (City, LocalDate) -> HistoryWeather? = { _, _ -> null },
) : WeatherFetcher {
    var phenologyCallCount = 0
        private set
    var historyCallCount = 0
        private set

    override suspend fun fetchWeather(city: City): WeatherSnapshot = weatherBlock(city)

    override suspend fun fetchPhenologyShiftDays(
        city: City,
        today: LocalDate,
    ): Int? {
        phenologyCallCount++
        return phenologyBlock(city, today)
    }

    override suspend fun fetchHistoryDay(
        city: City,
        date: LocalDate,
    ): HistoryWeather? {
        historyCallCount++
        return historyBlock(city, date)
    }
}

private fun historyWeather(
    date: LocalDate,
    tempMax: Double? = null,
): HistoryWeather =
    HistoryWeather(
        date = date.toString(),
        weatherCode = 0,
        tempMaxC = tempMax,
        tempMinC = null,
        tempMeanC = null,
        precipSumMm = null,
        humidityMeanPercent = null,
        windMaxKmh = null,
        sunrise = null,
        sunset = null,
    )

private class FakePhenologyCache : PhenologyCache {
    val saved = mutableListOf<PhenologyCacheStore.PhenologyEntry>()
    private var seeded: PhenologyCacheStore.PhenologyEntry? = null

    fun seed(
        city: City,
        year: Int,
        shiftDays: Int?,
        fetchedToday: Boolean = true,
    ) {
        seeded =
            PhenologyCacheStore.PhenologyEntry(
                cityLat = city.latitude,
                cityLon = city.longitude,
                year = year,
                shiftDays = shiftDays,
                fetchedAtEpochMs =
                    if (fetchedToday) {
                        System.currentTimeMillis()
                    } else {
                        System.currentTimeMillis() - 2L * 24 * 3600_000L
                    },
            )
    }

    override suspend fun get(
        city: City,
        year: Int,
    ): PhenologyCacheStore.PhenologyEntry? {
        val entry = seeded ?: return null
        return if (entry.year == year && entry.sameCity(city) && entry.isFreshToday()) {
            entry
        } else {
            null
        }
    }

    override suspend fun save(
        city: City,
        year: Int,
        shiftDays: Int?,
    ) {
        val entry =
            PhenologyCacheStore.PhenologyEntry(
                cityLat = city.latitude,
                cityLon = city.longitude,
                year = year,
                shiftDays = shiftDays,
                fetchedAtEpochMs = System.currentTimeMillis(),
            )
        saved += entry
        seeded = entry
    }
}

private class FakeWeatherCache : WeatherCache {
    private val map = mutableMapOf<String, WeatherCacheStore.CacheEntry>()

    fun put(
        city: City,
        snapshot: WeatherSnapshot,
        savedAt: Long,
    ) {
        map[key(city)] = WeatherCacheStore.CacheEntry(snapshot, savedAt)
    }

    override suspend fun get(city: City): WeatherCacheStore.CacheEntry? = map[key(city)]

    override suspend fun save(snapshot: WeatherSnapshot) {
        map[key(snapshot.city)] =
            WeatherCacheStore.CacheEntry(
                snapshot,
                System.currentTimeMillis(),
            )
    }

    private fun key(city: City) = "${"%.2f".format(city.latitude)},${"%.2f".format(city.longitude)}"
}

private class RecordingLocationSession : LocationSession {
    var cancelCount = 0
        private set

    override fun cancelPending() {
        cancelCount++
    }

    override fun locate(
        onResolved: (City) -> Unit,
        onError: (Throwable) -> Unit,
        onPlaceUpdated: (City) -> Unit,
    ) = Unit
}

private class FakeFavoriteCitySource : FavoriteCitySource {
    override val favorites: Flow<List<City>> = flowOf(emptyList())

    override suspend fun toggle(city: City) = Unit

    override fun isFavorite(
        favorites: List<City>,
        city: City?,
    ) = false

    override suspend fun firstOrNull(): City? = null
}

private class FakeRecentCitySource : RecentCitySource {
    override val recent: Flow<List<City>> = flowOf(emptyList())

    override suspend fun add(city: City) = Unit

    override suspend fun clear() = Unit
}

private class FakeSelectedCitySource : SelectedCitySource {
    override suspend fun save(
        city: City,
        useLocation: Boolean,
    ) = Unit

    override suspend fun getOnce(): SelectedCityStore.PersistedSelection? = null
}

private class FakeAppSettingsSource : AppSettingsSource {
    private val state = MutableStateFlow(AppSettings())
    override val settings: Flow<AppSettings> = state

    override suspend fun getOnce(): AppSettings = state.value
}

private class FakeDebugWeatherSource : DebugWeatherSource {
    private val stateFlow = MutableStateFlow(DebugWeatherState())
    override val state: Flow<DebugWeatherState> = stateFlow

    override suspend fun getOnce(): DebugWeatherState = stateFlow.value

    override suspend fun setEnabled(enabled: Boolean) {
        stateFlow.value =
            stateFlow.value.copy(
                enabled = enabled,
                override = if (enabled) stateFlow.value.override else DebugWeatherOverride(),
            )
    }

    override suspend fun saveOverride(override: DebugWeatherOverride) {
        stateFlow.value = DebugWeatherState(enabled = true, override = override)
    }

    override suspend fun clearOverride() {
        stateFlow.value = stateFlow.value.copy(override = DebugWeatherOverride())
    }
}

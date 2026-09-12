package com.xiaoxiong.weather.ui.weather

import android.app.Application
import android.content.res.Resources
import androidx.lifecycle.ViewModel
import com.xiaoxiong.weather.R
import com.xiaoxiong.weather.data.AppSettings
import com.xiaoxiong.weather.data.AppSettingsSource
import com.xiaoxiong.weather.data.City
import com.xiaoxiong.weather.data.DebugWeatherOverride
import com.xiaoxiong.weather.data.DebugWeatherSource
import com.xiaoxiong.weather.data.DebugWeatherState
import com.xiaoxiong.weather.data.FavoriteCitySource
import com.xiaoxiong.weather.data.HistoryRangeSummary
import com.xiaoxiong.weather.data.HistoryWeather
import com.xiaoxiong.weather.data.LocationPermissionSource
import com.xiaoxiong.weather.data.LocationSession
import com.xiaoxiong.weather.data.LocationSessionFactory
import com.xiaoxiong.weather.data.NetworkStateSource
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
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.setMain
import org.junit.rules.TestWatcher
import org.junit.runner.Description
import org.mockito.Mockito
import java.time.LocalDate

@OptIn(ExperimentalCoroutinesApi::class)
class MainDispatcherRule : TestWatcher() {
    override fun starting(description: Description) {
        MainTestDispatchers.ensureInstalled()
    }

    override fun finished(description: Description) {
        // 取消测试创建的 ViewModel 协程，避免泄漏的 withContext(Default) 续体污染后续测试。
        // 不 resetMain：Default 续体的取消 resume 仍需要落在有效的 Main 上，
        // 卸载后 resume 会抛 "Dispatchers.Main accessed after resetMain"。
        TestViewModelRegistry.cancelAll()
    }
}

/** 测试 Main dispatcher：整个测试 JVM 只安装一次、不卸载。 */
@OptIn(ExperimentalCoroutinesApi::class)
private object MainTestDispatchers {
    @Volatile
    private var installed = false

    fun ensureInstalled() {
        if (!installed) {
            Dispatchers.setMain(UnconfinedTestDispatcher())
            installed = true
        }
    }
}

/**
 * 登记测试中创建的 ViewModel 以便在测试结束时取消其协程。
 * ViewModel.viewModelScope 无公开取消入口，用 ViewModel.clear()（package-private）反射调用。
 */
internal object TestViewModelRegistry {
    private val viewModels = java.util.concurrent.CopyOnWriteArrayList<WeatherViewModel>()

    fun register(viewModel: WeatherViewModel) {
        viewModels += viewModel
    }

    fun cancelAll() {
        viewModels.forEach { vm ->
            runCatching {
                ViewModel::class.java.getDeclaredMethod("clear").apply {
                    isAccessible = true
                    invoke(vm)
                }
            }
        }
        viewModels.clear()
    }
}

/** 可编程定位会话：捕获回调，测试手动触发 onResolved / onError / onPlaceUpdated。 */
class RecordingLocationSession : LocationSession {
    var cancelCount = 0
        private set
    var locateCount = 0
        private set
    private var onResolved: ((City) -> Unit)? = null
    private var onError: ((Throwable) -> Unit)? = null
    private var onPlaceUpdated: ((City) -> Unit)? = null

    override fun cancelPending() {
        cancelCount++
    }

    override fun locate(
        onResolved: (City) -> Unit,
        onError: (Throwable) -> Unit,
        onPlaceUpdated: (City) -> Unit,
    ) {
        locateCount++
        this.onResolved = onResolved
        this.onError = onError
        this.onPlaceUpdated = onPlaceUpdated
    }

    fun emitResolved(city: City) {
        onResolved?.invoke(city)
    }

    fun emitError(error: Throwable) {
        onError?.invoke(error)
    }

    fun emitPlaceUpdated(city: City) {
        onPlaceUpdated?.invoke(city)
    }
}

/**
 * 可编程 + 计数的 WeatherFetcher：天气/物候/单日历史/区间历史各带计数与可注入实现。
 */
class RecordingFetcher(
    private val weatherBlock: suspend (City) -> WeatherSnapshot =
        { city -> snapshotFor(city, temp = 20.0) },
    private val phenologyBlock: suspend (City, LocalDate) -> Int? = { _, _ -> null },
    private val historyBlock: suspend (City, LocalDate) -> HistoryWeather? = { _, _ -> null },
    private val historyRangeBlock:
        suspend (City, LocalDate, LocalDate) -> HistoryRangeSummary? = { _, _, _ -> null },
) : WeatherFetcher {
    var weatherCallCount = 0
        private set
    var phenologyCallCount = 0
        private set
    var historyCallCount = 0
        private set
    var historyRangeCallCount = 0
        private set

    override suspend fun fetchWeather(city: City): WeatherSnapshot {
        weatherCallCount++
        return weatherBlock(city)
    }

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

    override suspend fun fetchHistoryRange(
        city: City,
        start: LocalDate,
        end: LocalDate,
    ): HistoryRangeSummary? {
        historyRangeCallCount++
        return historyRangeBlock(city, start, end)
    }
}

class FakeWeatherCache : WeatherCache {
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

class FakePhenologyCache : PhenologyCache {
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

/** 记录小组件/图标副作用调用，供断言（NoOp 替代）。 */
class RecordingPresentationEffects : WeatherPresentationEffects {
    data class IconSchedule(
        val weatherCode: Int?,
        val precipMmPerHour: Double?,
        val isDay: Boolean?,
    )

    val iconSchedules = mutableListOf<IconSchedule>()
    var widgetRefreshCount = 0
        private set

    override fun scheduleLauncherIcon(
        context: android.content.Context,
        weatherCode: Int?,
        precipMmPerHour: Double?,
        isDay: Boolean?,
    ) {
        iconSchedules += IconSchedule(weatherCode, precipMmPerHour, isDay)
    }

    override suspend fun refreshWidgets(context: android.content.Context) {
        widgetRefreshCount++
    }
}

class FakeFavoriteCitySource : FavoriteCitySource {
    var toggleCount = 0
        private set
    var favorite: City? = null

    override val favorites: Flow<List<City>> =
        MutableStateFlow(favorite?.let { listOf(it) } ?: emptyList())

    override suspend fun toggle(city: City) {
        toggleCount++
        favorite = city
    }

    override fun isFavorite(
        favorites: List<City>,
        city: City?,
    ): Boolean =
        city?.let { c ->
            favorites.any {
                it.latitude == c.latitude && it.longitude == c.longitude
            }
        } ?: false

    override suspend fun firstOrNull(): City? = favorite
}

class FakeRecentCitySource : RecentCitySource {
    var clearCount = 0
        private set
    private val state = MutableStateFlow<List<City>>(emptyList())

    override val recent: Flow<List<City>> = state

    override suspend fun add(city: City) {
        state.value = listOf(city) + state.value.filterNot { it.id == city.id }
    }

    override suspend fun clear() {
        clearCount++
        state.value = emptyList()
    }
}

class FakeSelectedCitySource : SelectedCitySource {
    val saved = mutableListOf<SelectedCityStore.PersistedSelection>()

    /** 非空时 [getOnce] 先挂起，模拟冷启动/回落读取缓慢。 */
    var getOnceGate: CompletableDeferred<Unit>? = null

    /** 非空时 [getOnce] 固定返回该值（模拟读取到的旧城市，不随保存变化）。 */
    var getOnceOverride: SelectedCityStore.PersistedSelection? = null

    fun seed(
        city: City,
        useLocation: Boolean = false,
    ) {
        saved += SelectedCityStore.PersistedSelection(city, useLocation)
    }

    override suspend fun save(
        city: City,
        useLocation: Boolean,
    ) {
        saved += SelectedCityStore.PersistedSelection(city, useLocation)
    }

    override suspend fun getOnce(): SelectedCityStore.PersistedSelection? {
        getOnceGate?.await()
        return getOnceOverride ?: saved.lastOrNull()
    }
}

class FakeAppSettingsSource(
    initial: AppSettings = AppSettings(),
) : AppSettingsSource {
    private val state = MutableStateFlow(initial)
    override val settings: Flow<AppSettings> = state

    override suspend fun getOnce(): AppSettings = state.value
}

class FakeDebugWeatherSource : DebugWeatherSource {
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

/** 可编程在线状态源，模拟断网/恢复。 */
class FakeNetworkStateSource(
    online: Boolean,
) : NetworkStateSource {
    private val flow = MutableStateFlow(online)
    override val isOnline: StateFlow<Boolean> = flow

    fun setOnline(value: Boolean) {
        flow.value = value
    }
}

/** 可在测试中动态改变授权状态，模拟「运行时授权后 hasLocationPermission 变 true」。 */
class FakeLocationPermissionSource(
    var granted: Boolean,
) : LocationPermissionSource {
    override fun hasLocationPermission(): Boolean = granted
}

class ViewModelTestHarness(
    val vm: WeatherViewModel,
    val effects: RecordingPresentationEffects,
    val session: RecordingLocationSession,
    val favoriteStore: FakeFavoriteCitySource,
    val selectedCityStore: FakeSelectedCitySource,
    val fetcher: RecordingFetcher,
    val locationPermissionSource: FakeLocationPermissionSource,
)

fun createTestViewModel(
    fetcher: RecordingFetcher = RecordingFetcher(),
    cache: WeatherCache = FakeWeatherCache(),
    session: RecordingLocationSession = RecordingLocationSession(),
    phenologyCache: PhenologyCache = FakePhenologyCache(),
    networkMonitor: NetworkStateSource? = null,
    locationPermission: Boolean = false,
    settings: AppSettings = AppSettings(),
    favoriteStore: FakeFavoriteCitySource = FakeFavoriteCitySource(),
    selectedCityStore: FakeSelectedCitySource = FakeSelectedCitySource(),
    presentationEffects: RecordingPresentationEffects = RecordingPresentationEffects(),
    citySearch: CitySearchUseCase = Mockito.mock(CitySearchUseCase::class.java),
): ViewModelTestHarness {
    val app = mockApplication()
    val permissionSource = FakeLocationPermissionSource(locationPermission)
    val deps =
        WeatherViewModelDeps(
            weatherRepository = fetcher,
            locationHelper = permissionSource,
            favoriteStore = favoriteStore,
            recentCityStore = FakeRecentCitySource(),
            selectedCityStore = selectedCityStore,
            weatherCacheStore = cache,
            phenologyCache = phenologyCache,
            citySearch = citySearch,
            settingsStore = FakeAppSettingsSource(settings),
            debugWeatherStore = FakeDebugWeatherSource(),
            radarMapRepository = Mockito.mock(RadarMapRepository::class.java),
            locationSessionFactory = LocationSessionFactory { session },
            presentationEffects = presentationEffects,
            popularCitiesLoader = { emptyList() },
            networkMonitor = networkMonitor,
        )
    return ViewModelTestHarness(
        vm = WeatherViewModel(app, deps).also { TestViewModelRegistry.register(it) },
        effects = presentationEffects,
        session = session,
        favoriteStore = favoriteStore,
        selectedCityStore = selectedCityStore,
        fetcher = fetcher as? RecordingFetcher ?: RecordingFetcher(),
        locationPermissionSource = permissionSource,
    )
}

fun snapshotFor(
    city: City,
    temp: Double,
) = WeatherSnapshot(
    city = city,
    current = WeatherFixtures.current(temperatureC = temp),
    hourly = emptyList(),
    daily = emptyList(),
)

fun testHistoryWeather(
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

fun mockApplication(): Application {
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

fun messageForRes(id: Int): String =
    when (id) {
        R.string.error_network_cached -> "网络异常，已显示缓存天气"
        R.string.error_network_unavailable -> "网络不可用，请稍后重试"
        R.string.error_need_location_permission -> "请先授予定位权限"
        R.string.error_locate_failed -> "定位失败"
        R.string.info_location_denied -> "未授权定位，可手动搜索城市查看天气"
        R.string.info_notification_denied -> "未开启通知权限，天气提醒不会弹出；可在系统设置中稍后开启"
        R.string.debug_entered -> "已进入调试模式"
        R.string.debug_exited -> "已退出调试模式"
        R.string.debug_applied -> "已应用调试覆盖"
        R.string.debug_override_cleared -> "已清除覆盖，恢复实况字段"
        R.string.cold_start_fallback_city -> "Beijing"
        else -> "error"
    }

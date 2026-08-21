package com.xiaoxiong.weather.ui.weather

import android.app.Application
import androidx.annotation.StringRes
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.xiaoxiong.weather.R
import com.xiaoxiong.weather.data.AppSettings
import com.xiaoxiong.weather.data.City
import com.xiaoxiong.weather.data.DebugWeatherOverride
import com.xiaoxiong.weather.data.DebugWeatherState
import com.xiaoxiong.weather.data.DebugWeatherStore
import com.xiaoxiong.weather.data.HistoryRangeSummary
import com.xiaoxiong.weather.data.HistoryWeather
import com.xiaoxiong.weather.data.RadarMapRepository
import com.xiaoxiong.weather.data.WeatherCacheStore
import com.xiaoxiong.weather.data.WeatherSnapshot
import com.xiaoxiong.weather.util.WeatherCodes
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.concurrent.atomic.AtomicInteger

data class WeatherUiState(
    val query: String = "",
    val suggestions: List<City> = emptyList(),
    val isSearching: Boolean = false,
    val isLoadingWeather: Boolean = false,
    val isLocating: Boolean = false,
    val selectedCity: City? = null,
    val weather: WeatherSnapshot? = null,
    val usingCurrentLocation: Boolean = false,
    val lastUpdatedText: String? = null,
    val isOffline: Boolean = false,
    val errorMessage: String? = null,
    val infoMessage: String? = null,
    /** 隐藏调试模式已开启 */
    val debugEnabled: Boolean = false,
    /**
     * 物候偏移天数（正 = 推迟，负 = 提前），进入七日页时按需拉取；
     * 独立于天气快照，主天气刷新不影响该值；null = 无数据/尚未拉取。
     */
    val phenologyShiftDays: Int? = null,
    /**
     * 历史天气查询结果（用户主动选日）；null = 尚未查询或该日期无数据。
     * 网络/服务端错误时置 [historyNetworkFailed]。
     */
    val historyWeather: HistoryWeather? = null,
    val isHistoryLoading: Boolean = false,
    /** 该日期确实无数据（请求成功但 archive 无该日数据）。 */
    val historyFailed: Boolean = false,
    /** 网络/服务端不可用导致查询失败（区别于无数据，可重试）。 */
    val historyNetworkFailed: Boolean = false,
    /**
     * 历史日期范围统计结果（最高/最低温、最多降水等）；
     * null = 尚未查询或区间内无数据。网络/服务端错误置 [historyRangeNetworkFailed]。
     */
    val historyRange: HistoryRangeSummary? = null,
    val isHistoryRangeLoading: Boolean = false,
    /** 区间内确实无可用数据。 */
    val historyRangeFailed: Boolean = false,
    /** 网络/服务端不可用导致区间查询失败（区别于无数据，可重试）。 */
    val historyRangeNetworkFailed: Boolean = false,
)

class WeatherViewModel(
    application: Application,
    private val deps: WeatherViewModelDeps,
) : AndroidViewModel(application) {
    private val repository = deps.weatherRepository
    private val locationHelper = deps.locationHelper
    private val favoriteStore = deps.favoriteStore
    private val recentCityStore = deps.recentCityStore
    private val selectedCityStore = deps.selectedCityStore
    private val weatherCacheStore = deps.weatherCacheStore
    private val phenologyCache = deps.phenologyCache
    val settingsStore = deps.settingsStore
    private val debugWeatherStore = deps.debugWeatherStore
    val radarMapRepository: RadarMapRepository = deps.radarMapRepository

    private val _uiState = MutableStateFlow(WeatherUiState())
    val uiState: StateFlow<WeatherUiState> = _uiState.asStateFlow()

    val favorites: StateFlow<List<City>> =
        favoriteStore.favorites
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val recentCities: StateFlow<List<City>> =
        recentCityStore.recent
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _popularCities = MutableStateFlow<List<City>>(emptyList())
    val popularCities: StateFlow<List<City>> = _popularCities.asStateFlow()

    val appSettings: StateFlow<AppSettings> =
        settingsStore.settings
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AppSettings())

    private val debugController =
        DebugModeController(
            scope = viewModelScope,
            app = getApplication(),
            store = deps.debugWeatherStore,
            onState = { transform -> _uiState.update(transform) },
            republishWeather = { republishBaseWeather() },
        )
    val debugState: StateFlow<DebugWeatherState> = debugController.state

    private val searchController =
        CitySearchController(
            scope = viewModelScope,
            app = getApplication(),
            citySearch = deps.citySearch,
            onState = { transform -> _uiState.update(transform) },
            currentState = { _uiState.value },
            onDebugMagicQuery = { debugController.enable() },
        )

    /** 未经调试覆盖的原始快照，供编辑器与退出调试还原。 */
    private val _baseWeather = MutableStateFlow<WeatherSnapshot?>(null)
    val baseWeather: StateFlow<WeatherSnapshot?> = _baseWeather.asStateFlow()

    private var weatherJob: Job? = null
    private val weatherRequestId = AtomicInteger(0)

    /** 上一次发起天气请求的墙钟时间；用于静默刷新去重，防止多来源叠加重复拉取 */
    private var lastRefreshAtEpochMs = 0L

    /** 静默刷新去重窗口：窗口内同城静默刷新直接跳过 */
    private val refreshDedupWindowMs = 5_000L
    private val locationCoordinator = deps.locationSessionFactory.create(viewModelScope)
    private val presentationEffects = deps.presentationEffects

    private val coldStart =
        ColdStartController(
            scope = viewModelScope,
            settingsStore = settingsStore,
            selectedCityStore = selectedCityStore,
            favoriteStore = favoriteStore,
            locationHelper = locationHelper,
            selectCity = { city, fromLocation, quiet -> selectCity(city, fromLocation, quiet) },
            loadCurrentLocationWeather = { showRationale, quiet ->
                loadCurrentLocationWeather(showRationale, quiet)
            },
            refresh = { quiet -> refresh(quiet) },
            updateState = { transform -> _uiState.update(transform) },
            currentSelectedCity = { _uiState.value.selectedCity },
            hasWeather = { _uiState.value.weather != null },
            str = { id -> getApplication<Application>().str(id) },
        )

    init {
        viewModelScope.launch {
            _popularCities.value =
                withContext(Dispatchers.Default) {
                    deps.popularCitiesLoader(getApplication())
                }
        }
        debugController.start()
        searchController.start()
        startNetworkRecoveryRefresh()
    }

    /** 断网期间失败后，网络一恢复就静默刷新，把界面拉回最新数据。 */
    private fun startNetworkRecoveryRefresh() {
        val monitor = deps.networkMonitor ?: return
        var wasOnline = monitor.isOnline.value
        viewModelScope.launch {
            monitor.isOnline.collect { online ->
                if (online && !wasOnline) {
                    refresh(quiet = true)
                }
                wasOnline = online
            }
        }
    }

    // region 前台/后台与权限：委托给 ColdStartController

    fun onAppForeground() = coldStart.onAppForeground()

    fun onAppBackground() = coldStart.onAppBackground()

    fun consumeRuntimePermissionRequest(): Boolean = coldStart.consumeRuntimePermissionRequest()

    fun hasLocationPermission(): Boolean = coldStart.hasLocationPermission()

    fun onPermissionResult(granted: Boolean) = coldStart.onPermissionResult(granted)

    fun onNotificationPermissionResult(granted: Boolean) = coldStart.onNotificationPermissionResult(granted)

    fun ensureInitialLoad() = coldStart.ensureInitialLoad()

    fun requestLocate(): Boolean = coldStart.requestLocate()

    // endregion

    fun onQueryChange(value: String) = searchController.onQueryChange(value)

    /** 彩蛋口令进入游戏等场景：清空搜索框但不退出「当前位置」模式。 */
    fun clearQueryKeepLocation() = searchController.clearQueryKeepLocation()

    fun selectCity(
        city: City,
        fromLocation: Boolean = false,
        quiet: Boolean = false,
    ) {
        // 手动选城时作废进行中的定位，避免 GPS 晚到抢回城市
        if (!fromLocation) {
            locationCoordinator.cancelPending()
        }

        val useLocation = fromLocation || city.isCurrentLocation
        val requestId = weatherRequestId.incrementAndGet()

        _uiState.update {
            val samePlace = roughlySameCity(it.selectedCity, city)
            // 同城刷新 / 静默刷新保留旧数据，避免下拉或自动刷新把界面刷空
            val keepWeather = quiet || samePlace
            it.copy(
                selectedCity = city,
                query = city.displayName,
                suggestions = emptyList(),
                weather = if (keepWeather) it.weather else null,
                isOffline = if (keepWeather) it.isOffline else false,
                // 静默刷新不打断可见的 loading；手动刷新才显示进度
                isLoadingWeather = if (quiet) it.isLoadingWeather else true,
                isLocating = if (quiet) it.isLocating else false,
                usingCurrentLocation = useLocation,
                // 换城后物候/历史天气属于旧城市，等再进入相关页面按需补齐
                phenologyShiftDays = null,
                historyWeather = null,
                isHistoryLoading = false,
                historyFailed = false,
                historyNetworkFailed = false,
                historyRange = null,
                isHistoryRangeLoading = false,
                historyRangeFailed = false,
                historyRangeNetworkFailed = false,
                errorMessage = null,
            )
        }
        viewModelScope.launch {
            selectedCityStore.save(city, useLocation)
        }
        if (!fromLocation && !quiet) {
            viewModelScope.launch {
                recentCityStore.add(city)
            }
        }
        weatherJob?.cancel()
        weatherJob =
            viewModelScope.launch {
                if (!quiet) {
                    val cached = readCacheSafely(city)
                    if (cached != null && requestId == weatherRequestId.get()) {
                        publishWeather(
                            requestId = requestId,
                            snapshot = cached.snapshot,
                            selectedCity = city,
                            isLoadingWeather = true,
                            isOffline = false,
                            lastUpdatedText = formatCacheTime(cached.savedAtEpochMs),
                            updateIcon = true,
                            refreshWidgets = false,
                        )
                    }
                }

                try {
                    val snapshot = repository.fetchWeather(city)
                    if (requestId != weatherRequestId.get()) return@launch
                    weatherCacheStore.save(snapshot)
                    publishWeather(
                        requestId = requestId,
                        snapshot = snapshot,
                        selectedCity = city,
                        isLoadingWeather = false,
                        isOffline = false,
                        lastUpdatedText = nowText(),
                        errorMessage = null,
                        updateIcon = true,
                        refreshWidgets = true,
                    )
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    if (requestId != weatherRequestId.get()) return@launch
                    val cached = readCacheSafely(city)
                    if (cached != null) {
                        publishWeather(
                            requestId = requestId,
                            snapshot = cached.snapshot,
                            selectedCity = city,
                            isLoadingWeather = false,
                            isOffline = true,
                            lastUpdatedText = formatCacheTime(cached.savedAtEpochMs),
                            errorMessage = if (quiet) null else str(R.string.error_network_cached),
                            updateIcon = true,
                            refreshWidgets = true,
                        )
                    } else {
                        _uiState.update {
                            it.copy(
                                isLoadingWeather = false,
                                isOffline = false,
                                errorMessage =
                                    if (quiet) {
                                        null
                                    } else {
                                        userFacingError(e, R.string.error_fetch_weather)
                                    },
                            )
                        }
                    }
                }
            }
    }

    fun loadCurrentLocationWeather(
        showRationale: Boolean = true,
        quiet: Boolean = false,
    ) {
        if (!locationHelper.hasLocationPermission()) {
            if (showRationale && !quiet) {
                _uiState.update { it.copy(errorMessage = str(R.string.error_need_location_permission)) }
            }
            return
        }

        weatherJob?.cancel()
        weatherJob = null
        weatherRequestId.incrementAndGet()

        if (!quiet) {
            _uiState.update {
                it.copy(
                    isLocating = true,
                    isLoadingWeather = true,
                    // 保留旧天气，定位期间不把界面刷空
                    errorMessage = null,
                )
            }
        }

        locationCoordinator.locate(
            onResolved = { city ->
                selectCity(city, fromLocation = true, quiet = quiet)
            },
            onError = { e ->
                _uiState.update {
                    it.copy(
                        isLocating = false,
                        isLoadingWeather = false,
                        errorMessage =
                            if (quiet) {
                                null
                            } else {
                                userFacingError(e, R.string.error_locate_failed)
                            },
                    )
                }
                if (_uiState.value.weather == null) {
                    coldStart.restoreFallbackCity()
                }
            },
            onPlaceUpdated = { city ->
                updateCurrentLocationLabel(city)
            },
        )
    }

    /** 逆地理完成后只改地名，不重拉天气。 */
    private fun updateCurrentLocationLabel(city: City) {
        _uiState.update { state ->
            if (!state.usingCurrentLocation) return@update state
            val current = state.selectedCity ?: return@update state
            if (!roughlySameCity(current, city)) return@update state
            state.copy(
                selectedCity = city,
                query = city.displayName,
            )
        }
        viewModelScope.launch {
            selectedCityStore.save(city, useLocation = true)
        }
    }

    fun toggleFavorite() {
        val city = _uiState.value.selectedCity ?: return
        viewModelScope.launch {
            favoriteStore.toggle(city.copy(isCurrentLocation = false))
        }
    }

    fun clearRecentCities() {
        viewModelScope.launch {
            recentCityStore.clear()
        }
    }

    fun isFavorite(
        city: City?,
        favorites: List<City>,
    ): Boolean = favoriteStore.isFavorite(favorites, city)

    fun refresh(quiet: Boolean = false) {
        val state = _uiState.value
        // 静默刷新不打断正在进行的可见加载，避免把 spinner 清掉导致短暂空白
        if (quiet && (state.isLoadingWeather || state.isLocating)) return
        val city = state.selectedCity
        // 既无城市也非定位模式（冷启动异常态）：无事可刷，别占用去重窗口
        if (city == null && !state.usingCurrentLocation) return
        // 静默刷新去重：距上一次天气请求不足窗口则跳过。
        // 下拉刷新 / 回前台 / 网络恢复等来源会叠加触发，统一收敛为最近一次
        val now = System.currentTimeMillis()
        if (quiet && now - lastRefreshAtEpochMs < refreshDedupWindowMs) return
        lastRefreshAtEpochMs = now
        if (city != null) {
            // 下拉/自动刷新只重拉天气，复用已有坐标；完整 GPS 留给右上角「定位」
            selectCity(
                city = city,
                fromLocation = state.usingCurrentLocation || city.isCurrentLocation,
                quiet = quiet,
            )
        } else {
            loadCurrentLocationWeather(showRationale = !quiet, quiet = quiet)
        }
    }

    fun clearError() {
        _uiState.update { it.copy(errorMessage = null) }
    }

    fun clearInfo() {
        _uiState.update { it.copy(infoMessage = null) }
    }

    private var phenologyJob: Job? = null

    /** 单日历史查询内存缓存：键 = "城市坐标|日期"；FIFO 上限防会话内无限增长。 */
    private val maxHistoryCacheEntries = 30
    private val historyCache = mutableMapOf<String, HistoryWeather>()

    private fun cacheHistory(
        key: String,
        value: HistoryWeather,
    ) {
        if (historyCache.size >= maxHistoryCacheEntries) {
            historyCache.keys.firstOrNull()?.let { historyCache.remove(it) }
        }
        historyCache[key] = value
    }

    /** 区间历史查询内存缓存：键 = "城市坐标|起|止"。 */
    private val historyRangeCache = mutableMapOf<String, HistoryRangeSummary>()
    private val maxHistoryRangeCacheEntries = 30

    private var historyRangeJob: Job? = null

    /**
     * 日期范围历史查询：按「城市|起|止」内存缓存去重，同城同区间只查一次；
     * 区间内无数据置 historyRangeFailed，网络/服务端错误置 historyRangeNetworkFailed。
     */
    fun fetchHistoryRange(
        start: LocalDate,
        end: LocalDate,
    ) {
        if (end.isBefore(start)) return
        val city = _uiState.value.selectedCity ?: return
        val cacheKey =
            "%.4f,%.4f|%s|%s".format(city.latitude, city.longitude, start, end)
        historyRangeCache[cacheKey]?.let { cached ->
            _uiState.update {
                it.copy(
                    historyRange = cached,
                    isHistoryRangeLoading = false,
                    historyRangeFailed = false,
                    historyRangeNetworkFailed = false,
                )
            }
            return
        }
        historyRangeJob?.cancel()
        historyRangeJob =
            viewModelScope.launch {
                _uiState.update {
                    it.copy(
                        isHistoryRangeLoading = true,
                        historyRange = null,
                        historyRangeFailed = false,
                        historyRangeNetworkFailed = false,
                    )
                }
                var networkFailed = false
                val result =
                    try {
                        repository.fetchHistoryRange(city, start, end)
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        networkFailed = true
                        null
                    }
                if (result != null) {
                    if (historyRangeCache.size >= maxHistoryRangeCacheEntries) {
                        historyRangeCache.keys.firstOrNull()?.let { historyRangeCache.remove(it) }
                    }
                    historyRangeCache[cacheKey] = result
                }
                // 查询期间换城则丢弃结果（不覆盖新城市）
                if (stillOnCity(city)) {
                    _uiState.update {
                        it.copy(
                            historyRange = result,
                            isHistoryRangeLoading = false,
                            historyRangeFailed = result == null && !networkFailed,
                            historyRangeNetworkFailed = networkFailed,
                        )
                    }
                } else {
                    _uiState.update { it.copy(isHistoryRangeLoading = false) }
                }
            }
    }

    fun clearHistoryRange() {
        historyRangeJob?.cancel()
        _uiState.update {
            it.copy(
                historyRange = null,
                isHistoryRangeLoading = false,
                historyRangeFailed = false,
                historyRangeNetworkFailed = false,
            )
        }
    }

    /**
     * 进入「七日」页时按需补齐物候偏移：
     * 同城同年当日已拉过则直接用缓存，否则才联网（一天最多一次）；
     * 跨天/跨年/换城自然失效，会在下次进入页面时重拉。
     * 独立于主天气加载，失败时静默保留空（时令卡退化为纯经验推算）。
     */
    fun ensurePhenologyShift() {
        val city = _uiState.value.selectedCity ?: return
        if (phenologyJob?.isActive == true) return
        phenologyJob =
            viewModelScope.launch {
                val today = LocalDate.now()
                val cached = phenologyCache.get(city, today.year)
                val shift =
                    if (cached != null) {
                        cached.shiftDays
                    } else {
                        val fetched =
                            try {
                                repository.fetchPhenologyShiftDays(city, today)
                            } catch (e: CancellationException) {
                                throw e
                            } catch (e: Exception) {
                                null
                            }
                        // 无论成功还是无数据都缓存当日结果，保证一天最多拉一次
                        phenologyCache.save(city, today.year, fetched)
                        fetched
                    }
                // 拉取期间换城则不覆盖新城市的显示
                if (stillOnCity(city)) {
                    _uiState.update { it.copy(phenologyShiftDays = shift) }
                }
            }
    }

    private var historyJob: Job? = null

    /**
     * 单日历史查询：按「城市|日期」做内存缓存去重，同城同日只查一次；
     * 换城后缓存键不同，自然不串城。该日无数据置 historyFailed，
     * 网络/服务端错误置 historyNetworkFailed。
     */
    fun fetchHistoryDay(date: LocalDate) {
        val city = _uiState.value.selectedCity ?: return
        val cacheKey = "%.4f,%.4f|%s".format(city.latitude, city.longitude, date)
        historyCache[cacheKey]?.let { cached ->
            _uiState.update {
                it.copy(historyWeather = cached, isHistoryLoading = false, historyFailed = false, historyNetworkFailed = false)
            }
            return
        }
        historyJob?.cancel()
        historyJob =
            viewModelScope.launch {
                _uiState.update {
                    it.copy(isHistoryLoading = true, historyWeather = null, historyFailed = false, historyNetworkFailed = false)
                }
                var networkFailed = false
                val result =
                    try {
                        repository.fetchHistoryDay(city, date)
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        networkFailed = true
                        null
                    }
                if (result != null) {
                    cacheHistory(cacheKey, result)
                }
                // 查询期间换城则丢弃结果（不覆盖新城市）
                if (stillOnCity(city)) {
                    _uiState.update {
                        it.copy(
                            historyWeather = result,
                            isHistoryLoading = false,
                            historyFailed = result == null && !networkFailed,
                            historyNetworkFailed = networkFailed,
                        )
                    }
                } else {
                    _uiState.update { it.copy(isHistoryLoading = false) }
                }
            }
    }

    fun clearHistoryDay() {
        historyJob?.cancel()
        _uiState.update {
            it.copy(historyWeather = null, isHistoryLoading = false, historyFailed = false, historyNetworkFailed = false)
        }
    }

    fun enableDebugMode() = debugController.enable()

    fun exitDebugMode() = debugController.exit()

    fun applyDebugOverride(override: DebugWeatherOverride) = debugController.applyOverride(override)

    fun clearDebugOverride() = debugController.clearOverride()

    private suspend fun republishBaseWeather() {
        val base = _baseWeather.value ?: return
        val shown = presentWeather(base)
        _uiState.update {
            it.copy(
                weather = shown,
                debugEnabled = debugWeatherStore.getOnce().enabled,
            )
        }
    }

    private suspend fun presentWeather(base: WeatherSnapshot): WeatherSnapshot {
        val debug = debugWeatherStore.getOnce()
        return if (debug.enabled) {
            withContext(Dispatchers.Default) {
                DebugWeatherStore.apply(
                    base,
                    debug.override,
                    getApplication<Application>().resources,
                )
            }
        } else {
            base
        }
    }

    private fun publishWeather(
        requestId: Int,
        snapshot: WeatherSnapshot,
        selectedCity: City,
        isLoadingWeather: Boolean,
        isOffline: Boolean,
        lastUpdatedText: String,
        errorMessage: String? = null,
        updateIcon: Boolean = false,
        refreshWidgets: Boolean = false,
    ) {
        // 入口即弃过期请求：presentWeather 会挂起读 DataStore，期间可能已有更新的请求
        if (requestId != weatherRequestId.get()) return
        _baseWeather.value = snapshot
        viewModelScope.launch {
            val shown = presentWeather(snapshot)
            // 续体再校验一次，否则快速切城时旧快照会盖回新城市
            if (requestId != weatherRequestId.get()) return@launch
            if (updateIcon) {
                presentationEffects.scheduleLauncherIcon(
                    getApplication(),
                    shown.current.weatherCode,
                    WeatherCodes.intensityPrecipMmPerHour(shown.current),
                    shown.current.isDay,
                )
            }
            // 先出界面再刷小组件，避免 widget 更新拖住 loading
            _uiState.update {
                it.copy(
                    weather = shown,
                    selectedCity = selectedCity,
                    isLoadingWeather = isLoadingWeather,
                    isOffline = isOffline,
                    lastUpdatedText = lastUpdatedText,
                    errorMessage = errorMessage,
                    debugEnabled = debugWeatherStore.getOnce().enabled,
                )
            }
            if (refreshWidgets) {
                presentationEffects.refreshWidgets(getApplication())
            }
        }
    }

    private fun roughlySameCity(
        a: City?,
        b: City,
    ): Boolean {
        if (a == null) return false
        return kotlin.math.abs(a.latitude - b.latitude) < 0.01 &&
            kotlin.math.abs(a.longitude - b.longitude) < 0.01
    }

    /** 异步查询携带的 [city] 是否仍是当前选中城市；非本城市的结果应在写回前丢弃。 */
    private fun stillOnCity(city: City): Boolean {
        val current = _uiState.value
        return current.selectedCity != null && roughlySameCity(current.selectedCity, city)
    }

    private fun nowText(): String = LocalDateTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss"))

    /**
     * 读本地天气缓存。缓存只是「应急展示」用的，读失败不应影响主流程：
     * 若 DataStore 偶发 IO 异常直接向上抛，会让协程卡在 loading 且不回退（昨晚全城加载不出）。
     * 这里吞掉异常按无缓存处理，让外层 catch 走错误分支而非卡死。
     */
    private suspend fun readCacheSafely(city: City): WeatherCacheStore.CacheEntry? =
        try {
            weatherCacheStore.get(city)
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            null
        }

    private fun formatCacheTime(epochMs: Long): String =
        LocalDateTime
            .ofInstant(Instant.ofEpochMilli(epochMs), ZoneId.systemDefault())
            .format(DateTimeFormatter.ofPattern("MM-dd HH:mm"))

    private fun str(
        @StringRes id: Int,
        vararg args: Any,
    ): String = getApplication<Application>().str(id, *args)

    private fun userFacingError(
        e: Throwable,
        @StringRes fallback: Int,
    ): String = e.userFacingMessage(getApplication(), fallback)
}

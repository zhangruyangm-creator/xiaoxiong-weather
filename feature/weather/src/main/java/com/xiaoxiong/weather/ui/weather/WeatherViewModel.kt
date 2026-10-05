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
import com.xiaoxiong.weather.data.FavoriteCityStore
import com.xiaoxiong.weather.data.HistoryRangeSummary
import com.xiaoxiong.weather.data.HistoryWeather
import com.xiaoxiong.weather.data.RadarMapRepository
import com.xiaoxiong.weather.data.WeatherCacheStore
import com.xiaoxiong.weather.data.WeatherSnapshot
import com.xiaoxiong.weather.data.isSamePlace
import com.xiaoxiong.weather.data.presentationStampOf
import com.xiaoxiong.weather.util.WeatherCodes
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
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
    /**
     * 最近一次拉取失败的原因，**不随 Snackbar 播完而清空**。
     *
     * [errorMessage] 用于一次性提示，播完即清；无数据时页面会退化成「暂无今天的
     * 天气数据」，用户既看不到原因也没有恢复入口。这里保留一份给错误态常驻展示，
     * 下一次成功刷新时清空。
     */
    val lastErrorMessage: String? = null,
    val infoMessage: String? = null,
    /** 隐藏调试模式已开启 */
    val debugEnabled: Boolean = false,
    /** 物候偏移拉取中（Archive 积温）；与 null shift 区分，避免 UI 误判为「无数据」。 */
    val phenologyShiftLoading: Boolean = false,
    /**
     * 物候偏移天数（正 = 推迟，负 = 提前），天气加载成功后后台预拉；进入七日页也会补齐；
     * 独立于天气快照；null = 无数据且未在加载。
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

    /**
     * 发布序号：同一 requestId 下可能先发缓存快照、后发网络快照，
     * 而两次发布各自会挂起（presentWeather 读 DataStore），完成顺序不保证。
     * 用单调序号保证晚到的旧快照（缓存）不会盖掉已成功的较新快照（网络）。
     */
    private val weatherPublishSeq = AtomicInteger(0)

    /** 上一次发起天气请求的墙钟时间；用于静默刷新去重，防止多来源叠加重复拉取 */
    private var lastRefreshAtEpochMs = 0L

    /** 静默刷新去重窗口：窗口内同城静默刷新直接跳过 */
    private val refreshDedupWindowMs = 5_000L

    /**
     * 被 loading / 去重窗口拦截的静默刷新信号：信号是边沿触发，丢一次不会重试，
     * 因此拦截时置位，等 loading 结束或窗口过期后补发一次；连续多次信号只补一次。
     */
    private var pendingQuietRefresh = false
    private var pendingQuietRefreshJob: Job? = null
    private val locationCoordinator = deps.locationSessionFactory.create(viewModelScope)
    private val presentationEffects = deps.presentationEffects

    /** 历史查询与物候偏移：独立控制器，避免 ViewModel 承载过多职责。 */
    private val historyController =
        HistoryAndPhenologyController(
            scope = viewModelScope,
            repository = repository,
            phenologyCache = phenologyCache,
            updateState = { transform -> _uiState.update(transform) },
            currentCity = { _uiState.value.selectedCity },
        )

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
            // 回退城市的展示名走本地化字符串：英文用户冷启动看到的是 "Beijing" 而非硬编码中文；
            // admin/country 一并清空，避免 displayName 拼出 "Beijing, 北京, 中国"
            fallbackCity = {
                val app = getApplication<Application>()
                localizedFallbackCity(
                    fallbackBeijing(),
                    app.getString(R.string.cold_start_fallback_city),
                )
            },
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
        startQuietRefreshRequests()
        startPendingQuietRefreshWatcher()
    }

    /**
     * 应用内静默刷新信号（温度单位切换后预警文案按旧单位生成，需重拉）：
     * 与网络恢复刷新同款入口 `refresh(quiet = true)`，不改变既有节流语义。
     */
    private fun startQuietRefreshRequests() {
        val requests = deps.quietRefreshRequests ?: return
        viewModelScope.launch {
            requests.collect { refresh(quiet = true) }
        }
    }

    /**
     * 静默刷新补发观察者：`pendingQuietRefresh` 可能在可见 loading 期间置位
     * （此时不能立即刷新，否则会清掉 spinner），在 loading 结束的边沿补发。
     */
    private fun startPendingQuietRefreshWatcher() {
        viewModelScope.launch {
            _uiState
                .map { it.isLoadingWeather || it.isLocating }
                .distinctUntilChanged()
                .collect { loading -> if (!loading) flushPendingQuietRefresh() }
        }
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

    // region 历史 / 物候：委托给 HistoryAndPhenologyController

    fun ensurePhenologyShift() = historyController.ensurePhenologyShift()

    fun fetchHistoryDay(date: LocalDate) = historyController.fetchHistoryDay(date)

    fun clearHistoryDay() = historyController.clearHistoryDay()

    fun fetchHistoryRange(
        start: LocalDate,
        end: LocalDate,
    ) = historyController.fetchHistoryRange(start, end)

    fun clearHistoryRange() = historyController.clearHistoryRange()

    // endregion

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
        val previousState = _uiState.value
        val samePlace = previousState.selectedCity?.isSamePlace(city) == true
        // 静默刷新时用户输入是否仍停留在旧城市名（未进行搜索）；是则跟随新城市名同步
        val queryFollowsCity = previousState.query == previousState.selectedCity?.displayName
        // 手动选城：作废在途搜索，防止旧建议在选城后复活；静默刷新不动用户正在进行的搜索
        if (!quiet) {
            searchController.cancelPending()
        }

        _uiState.update {
            // 同城刷新 / 静默刷新保留旧数据，避免下拉或自动刷新把界面刷空
            val keepWeather = quiet || samePlace
            it.copy(
                selectedCity = city,
                // 静默刷新保留用户正在输入的搜索词与联想；仅当输入还等于旧城市名时才同步城市名
                query = if (quiet && !queryFollowsCity) it.query else city.displayName,
                suggestions = if (quiet) it.suggestions else emptyList(),
                weather = if (keepWeather) it.weather else null,
                isOffline = if (keepWeather) it.isOffline else false,
                // 换城后旧城市的更新时间不再显示（顶栏副标题），等新数据到达再写
                lastUpdatedText = if (keepWeather) it.lastUpdatedText else null,
                // 静默刷新不打断可见的 loading；手动刷新才显示进度
                isLoadingWeather = if (quiet) it.isLoadingWeather else true,
                isLocating = if (quiet) it.isLocating else false,
                usingCurrentLocation = useLocation,
                // 换城后物候/历史天气属于旧城市，等再进入相关页面按需补齐；
                // 同城刷新保留已有结果，避免静默刷新把刚查到的东西清空
                phenologyShiftDays = if (samePlace) it.phenologyShiftDays else null,
                phenologyShiftLoading = if (samePlace) it.phenologyShiftLoading else false,
                historyWeather = if (samePlace) it.historyWeather else null,
                isHistoryLoading = if (samePlace) it.isHistoryLoading else false,
                historyFailed = if (samePlace) it.historyFailed else false,
                historyNetworkFailed = if (samePlace) it.historyNetworkFailed else false,
                historyRange = if (samePlace) it.historyRange else null,
                isHistoryRangeLoading = if (samePlace) it.isHistoryRangeLoading else false,
                historyRangeFailed = if (samePlace) it.historyRangeFailed else false,
                historyRangeNetworkFailed = if (samePlace) it.historyRangeNetworkFailed else false,
                errorMessage = null,
                // 换城后旧城市的常驻错误不能盖到新城市：否则 weather=null 时
                // 今天页会用上一城的失败文案画 ErrorRetryCard。
                lastErrorMessage = if (samePlace) it.lastErrorMessage else null,
            )
        }
        // UI query 被改成城市名时同步搜索基线（不触发重复搜索），
        // 否则 queryFlow 仍停在旧输入，用户删回原词时不会再触发搜索
        if (!quiet || queryFollowsCity) {
            searchController.onCitySelected(city)
        }
        if (!samePlace) {
            // 换城即作废旧城市快照，避免退出调试模式时把上一城市天气回灌
            _baseWeather.value = null
        }
        // 进程重建后历史页可能先于城市就绪发起查询：城市确定后补发
        historyController.retryPending()
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

                // 记录取数发起时刻：并发刷新（手动 + Worker + Widget）时，
                // 后发先至的旧数据不能以"更新"的时间戳覆盖新数据
                val fetchStartedAt = System.currentTimeMillis()
                try {
                    val snapshot = repository.fetchWeather(city)
                    if (requestId != weatherRequestId.get()) return@launch
                    // 缓存写失败（磁盘满等）不应把一次成功的网络拉取误判成网络错误：
                    // 数据已到手，先出界面；下次刷新再重试写缓存。
                    // CancellationException 必须透传，不能被 runCatching 吞掉。
                    try {
                        weatherCacheStore.save(snapshot, currentPresentationStamp(), fetchStartedAt)
                    } catch (e: CancellationException) {
                        throw e
                    } catch (_: Exception) {
                    }
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
                                // 无缓存且拉取失败：常驻错误态，给用户原因与重试入口
                                lastErrorMessage =
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
        var labelApplied = false
        _uiState.update { state ->
            // update 的 lambda 可能因 CAS 失败重跑：每次先复位，避免上一轮的标记泄漏
            labelApplied = false
            if (!state.usingCurrentLocation) return@update state
            val current = state.selectedCity ?: return@update state
            if (!current.isSamePlace(city)) return@update state
            labelApplied = true
            val weather =
                state.weather?.takeIf { it.city.isSamePlace(city) }?.copy(city = city)
                    ?: state.weather
            state.copy(
                selectedCity = city,
                query = city.displayName,
                weather = weather,
            )
        }
        // UI query 总是被改写成精化后的地名：同步搜索基线，避免 queryFlow 停在旧地名，
        // 用户退格一位后 StateFlow 值未变、搜索不再触发（与 onCitySelected 同一约定）。
        if (labelApplied) searchController.onCitySelected(city)
        viewModelScope.launch {
            selectedCityStore.save(city, useLocation = true)
        }
    }

    fun toggleFavorite() {
        val city = _uiState.value.selectedCity ?: return
        viewModelScope.launch {
            val favorites = favoriteStore.favorites.first()
            val alreadyFavorite = favoriteStore.isFavorite(favorites, city)
            // 收藏满额且本次是新增：给出提示而不是静默失败（底层 toggle 会拒绝新增）
            if (!FavoriteCityStore.canAddFavorite(favorites.size, alreadyFavorite)) {
                _uiState.update {
                    it.copy(
                        infoMessage =
                            str(
                                R.string.search_favorite_limit_reached,
                                FavoriteCityStore.MAX_FAVORITES,
                            ),
                    )
                }
                return@launch
            }
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
        // 静默刷新不打断正在进行的可见加载，避免把 spinner 清掉导致短暂空白；
        // 但信号不能丢：置 pending，等 loading 结束再补发一次
        if (quiet && (state.isLoadingWeather || state.isLocating)) {
            markPendingQuietRefresh()
            return
        }
        val city = state.selectedCity
        // 既无城市也非定位模式（冷启动异常态）：无事可刷，别占用去重窗口
        if (city == null && !state.usingCurrentLocation) return
        // 静默刷新去重：距上一次天气请求不足窗口则本应跳过；
        // 但单位切换等边沿信号丢一次不重试，改为挂起并在窗口过期后补发一次。
        // 下拉刷新 / 回前台 / 网络恢复等来源会叠加触发，统一收敛为最近一次
        val now = deps.nowEpochMs()
        if (quiet && now - lastRefreshAtEpochMs < refreshDedupWindowMs) {
            markPendingQuietRefresh()
            return
        }
        // 本次静默刷新成功发起：pending 使命已达成，清位并取消待补发任务
        if (quiet) {
            pendingQuietRefresh = false
            pendingQuietRefreshJob?.cancel()
            pendingQuietRefreshJob = null
        }
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

    /** 静默刷新被拦截：置位 pending 并尝试补发（loading 中则等 loading 结束）。 */
    private fun markPendingQuietRefresh() {
        pendingQuietRefresh = true
        flushPendingQuietRefresh()
    }

    /**
     * 条件满足（不在 loading 且已超出 5s 去重窗口）时补发一次静默刷新；
     * 否则留给 loading 结束观察者或窗口过期延迟任务触发。
     */
    private fun flushPendingQuietRefresh() {
        if (!pendingQuietRefresh) return
        val state = _uiState.value
        if (state.isLoadingWeather || state.isLocating) return
        val remainingMs = refreshDedupWindowMs - (deps.nowEpochMs() - lastRefreshAtEpochMs)
        if (remainingMs > 0) {
            if (pendingQuietRefreshJob?.isActive != true) {
                pendingQuietRefreshJob =
                    viewModelScope.launch {
                        delay(remainingMs)
                        pendingQuietRefreshJob = null
                        flushPendingQuietRefresh()
                    }
            }
            return
        }
        pendingQuietRefreshJob?.cancel()
        pendingQuietRefreshJob = null
        refresh(quiet = true)
    }

    fun clearError() {
        _uiState.update { it.copy(errorMessage = null) }
    }

    fun clearInfo() {
        _uiState.update { it.copy(infoMessage = null) }
    }

    fun enableDebugMode() = debugController.enable()

    fun exitDebugMode() = debugController.exit()

    fun applyDebugOverride(override: DebugWeatherOverride) = debugController.applyOverride(override)

    fun clearDebugOverride() = debugController.clearOverride()

    private suspend fun republishBaseWeather() {
        val base = _baseWeather.value ?: return
        // 快照属于旧城市时不回灌（如换城后新城市尚未成功拉取）
        val selectedCity = _uiState.value.selectedCity ?: return
        if (!base.city.isSamePlace(selectedCity)) return
        val presented = presentWeather(base)
        // presentWeather 会挂起读 DataStore：恢复后在同一同步段复核快照与城市，
        // 期间换城或新一轮刷新已发布时直接丢弃，旧快照不得回灌覆盖新数据。
        if (_baseWeather.value !== base) return
        val currentCity = _uiState.value.selectedCity ?: return
        if (!base.city.isSamePlace(currentCity)) return
        _uiState.update {
            it.copy(
                weather = presented.snapshot,
                debugEnabled = presented.debugEnabled,
            )
        }
    }

    /** 发布用展示态：快照 + 本次读取到的调试开关，供调用方一次写回。 */
    private data class PresentedWeather(
        val snapshot: WeatherSnapshot,
        val debugEnabled: Boolean,
    )

    /**
     * 只在入口读取一次调试状态并返回全部所需信息：调用方恢复后即可在同一同步段完成
     * 校验与写回，发布路径不再有第二个挂起点，晚到的旧快照也就没有可乘之机。
     */
    private suspend fun presentWeather(base: WeatherSnapshot): PresentedWeather {
        val debug = debugWeatherStore.getOnce()
        val shown =
            if (debug.enabled) {
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
        return PresentedWeather(shown, debug.enabled)
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
        // 本次发布的单调序号：值更大者更新，旧序号完成后直接丢弃
        val publishSeq = weatherPublishSeq.incrementAndGet()
        _baseWeather.value = snapshot
        viewModelScope.launch {
            val presented = presentWeather(snapshot)
            // 挂起恢复后在同一同步段完成全部校验与写回：presentWeather 只读一次 DataStore，
            // 返回后不再有第二个挂起点，因此序号/城市校验与 _uiState.update、图标/小组件副作用
            // 之间不会被其它发布穿插。
            // 续体再校验一次，否则快速切城时旧快照会盖回新城市
            if (requestId != weatherRequestId.get()) return@launch
            // 同 requestId 下缓存发布可能晚于网络发布返回：较旧快照不得反向覆盖新数据
            if (publishSeq != weatherPublishSeq.get()) return@launch
            // 发布目标已不是当前选中城市（切城取消不到本协程）时直接丢弃
            if (_uiState.value.selectedCity?.isSamePlace(selectedCity) != true) return@launch
            val shown = presented.snapshot
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
                // 定位精化可能先于天气返回：同坐标时保留已精化地名，避免「当前位置」盖回去
                val cityToShow =
                    if (it.usingCurrentLocation &&
                        it.selectedCity != null &&
                        it.selectedCity.isSamePlace(selectedCity)
                    ) {
                        it.selectedCity
                    } else {
                        selectedCity
                    }
                val weatherToShow =
                    if (cityToShow != null &&
                        shown.city.isSamePlace(cityToShow) &&
                        (
                            shown.city.name != cityToShow.name ||
                                shown.city.admin1 != cityToShow.admin1 ||
                                shown.city.admin2 != cityToShow.admin2
                        )
                    ) {
                        shown.copy(city = cityToShow)
                    } else {
                        shown
                    }
                it.copy(
                    weather = weatherToShow,
                    selectedCity = cityToShow,
                    isLoadingWeather = isLoadingWeather,
                    isOffline = isOffline,
                    lastUpdatedText = lastUpdatedText,
                    errorMessage = errorMessage,
                    // 有新快照到达即视为恢复：清掉常驻错误态，让页面回到正常内容
                    lastErrorMessage = null,
                    debugEnabled = presented.debugEnabled,
                )
            }
            if (refreshWidgets) {
                presentationEffects.refreshWidgets(getApplication())
            }
            if (!isLoadingWeather) {
                ensurePhenologyShift()
            }
        }
    }

    private fun nowText(): String = LocalDateTime.now().format(NowTimeFormatter)

    /**
     * 读本地天气缓存。缓存只是「应急展示」用的，读失败不应影响主流程：
     * 若 DataStore 偶发 IO 异常直接向上抛，会让协程卡在 loading 且不回退（昨晚全城加载不出）。
     * 这里吞掉异常按无缓存处理，让外层 catch 走错误分支而非卡死。
     */
    private suspend fun readCacheSafely(city: City): WeatherCacheStore.CacheEntry? =
        try {
            weatherCacheStore.get(city, currentPresentationStamp())
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            null
        }

    /**
     * 当前「语言 + 温度单位」指纹：缓存里的预警/建议是按生成时的语言与单位烘焙的
     * 字符串，换设置后必须靠指纹识别旧文案。单位取内存里已在收集的 [appSettings]，
     * 既不多一次 DataStore 读，也保证与界面当前显示的单位一致。
     */
    private fun currentPresentationStamp(): String = presentationStampOf(currentLocale(), appSettings.value.temperatureUnit)

    private fun currentLocale(): Locale =
        runCatching {
            val config = getApplication<Application>().resources.configuration
            @Suppress("DEPRECATION")
            config?.locales?.get(0) ?: config?.locale ?: Locale.getDefault()
        }.getOrDefault(Locale.getDefault())

    private fun formatCacheTime(epochMs: Long): String =
        LocalDateTime
            .ofInstant(Instant.ofEpochMilli(epochMs), ZoneId.systemDefault())
            .format(CacheTimeFormatter)

    private fun str(
        @StringRes id: Int,
        vararg args: Any,
    ): String = getApplication<Application>().str(id, *args)

    private fun userFacingError(
        e: Throwable,
        @StringRes fallback: Int,
    ): String = e.userFacingMessage(getApplication(), fallback)
}

/** 顶栏更新时间文本（HH:mm:ss）。 */
private val NowTimeFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm:ss", Locale.US)

/** 缓存时间戳展示（MM-dd HH:mm）。 */
private val CacheTimeFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("MM-dd HH:mm", Locale.US)

package com.xiaoxiong.weather.ui.weather

import com.xiaoxiong.weather.R
import com.xiaoxiong.weather.data.AppSettingsSource
import com.xiaoxiong.weather.data.City
import com.xiaoxiong.weather.data.FavoriteCitySource
import com.xiaoxiong.weather.data.LocationPermissionSource
import com.xiaoxiong.weather.data.SelectedCitySource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

/**
 * 冷启动与权限状态机：
 *  - 首启按 settings/saved/favorite/permission 决定落到哪个城市
 *  - 运行时权限申请进程内只自动弹一次
 *  - 权限同意后补一次定位；拒绝则回落到已保存/收藏/北京
 *  - 前台整点轮询 + 回到前台静默刷新；退后台停轮询
 *
 * 把这块从 ViewModel 抽出来，ViewModel 只保留天气拉取/缓存/发布核心。
 */
class ColdStartController(
    private val scope: CoroutineScope,
    private val settingsStore: AppSettingsSource,
    private val selectedCityStore: SelectedCitySource,
    private val favoriteStore: FavoriteCitySource,
    private val locationHelper: LocationPermissionSource,
    private val selectCity: (City, fromLocation: Boolean, quiet: Boolean) -> Unit,
    private val loadCurrentLocationWeather: (showRationale: Boolean, quiet: Boolean) -> Unit,
    private val refresh: (quiet: Boolean) -> Unit,
    private val updateState: ((WeatherUiState) -> WeatherUiState) -> Unit,
    private val currentSelectedCity: () -> City?,
    private val hasWeather: () -> Boolean,
    private val str: (Int) -> String,
    /** 无任何历史/收藏/定位时的回退城市；由调用方按本地化资源构造。 */
    private val fallbackCity: () -> City = ::fallbackBeijing,
) {
    private var initialLoadStarted = false
    private var pendingLocateAfterPermission = false

    /** 冷启动时因尚无权限而没能定位；首次授权后应补一次定位 */
    private var coldStartDeferredLocation = false

    /**
     * 冷启动首次 ON_START 由 [ensureInitialLoad] 负责拉数，跳过一次前台刷新，
     * 避免与首启加载重复请求。
     */
    private var skipNextForegroundRefresh = true

    /** 进程内只自动申请一次运行时权限（不跨进程持久化，避免杀进程后永远不再弹）*/
    private var runtimePermissionsRequested = false

    private val foregroundRefresh =
        ForegroundAutoRefresh(
            scope = scope,
            hasCity = { currentSelectedCity() != null },
            onQuietRefresh = { refresh(true) },
        )

    /** Activity 进入前台（ON_START）：启动整点轮询，并在非冷启动时立即静默刷新。 */
    fun onAppForeground() {
        foregroundRefresh.enterForeground()
        if (skipNextForegroundRefresh) {
            skipNextForegroundRefresh = false
            return
        }
        if (!initialLoadStarted) return
        if (currentSelectedCity() == null) return
        refresh(true)
    }

    /** Activity 退到后台（ON_STOP）：停掉整点轮询，后台只留给 WorkManager。 */
    fun onAppBackground() {
        foregroundRefresh.leaveForeground()
    }

    /**
     * @return true 表示本次应弹出运行时权限；进程内只会返回一次 true。
     */
    fun consumeRuntimePermissionRequest(): Boolean {
        if (runtimePermissionsRequested) return false
        runtimePermissionsRequested = true
        return true
    }

    fun hasLocationPermission(): Boolean = locationHelper.hasLocationPermission()

    fun ensureInitialLoad() {
        startInitialLoadIfNeeded()
    }

    /**
     * @return true 表示 UI 需要弹出权限申请；false 表示已直接开始定位
     */
    fun requestLocate(): Boolean {
        if (locationHelper.hasLocationPermission()) {
            loadCurrentLocationWeather(true, false)
            return false
        }
        pendingLocateAfterPermission = true
        return true
    }

    fun onPermissionResult(granted: Boolean) {
        if (granted) {
            if (pendingLocateAfterPermission) {
                pendingLocateAfterPermission = false
                coldStartDeferredLocation = false
                initialLoadStarted = true
                loadCurrentLocationWeather(false, false)
                return
            }
            if (!initialLoadStarted) {
                startInitialLoadIfNeeded()
            } else if (coldStartDeferredLocation) {
                // 权限对话框期间已先落到北京/缓存城市；首次同意定位后切到当前位置
                coldStartDeferredLocation = false
                loadCurrentLocationWeather(false, false)
            }
        } else {
            if (pendingLocateAfterPermission) {
                pendingLocateAfterPermission = false
                updateState { it.copy(errorMessage = str(R.string.error_need_location_permission)) }
            } else {
                updateState { it.copy(infoMessage = str(R.string.info_location_denied)) }
            }
            coldStartDeferredLocation = false
            startInitialLoadIfNeeded()
        }
    }

    fun onNotificationPermissionResult(granted: Boolean) {
        if (!granted) {
            updateState { it.copy(infoMessage = str(R.string.info_notification_denied)) }
        }
    }

    /** 定位失败后回落到已保存/收藏/北京，避免界面一直空着。 */
    fun restoreFallbackCity() {
        // 用户已手动选城时不要再覆盖
        if (currentSelectedCity() != null) return
        scope.launch {
            val saved = selectedCityStore.getOnce()?.city
            val favorite = favoriteStore.firstOrNull()
            // 异步读取期间用户可能已手动选城，同样不能覆盖
            if (currentSelectedCity() != null) return@launch
            when {
                saved != null -> selectCity(saved, false, false)
                favorite != null -> selectCity(favorite, false, false)
                else -> selectCity(fallbackCity(), false, false)
            }
        }
    }

    private fun startInitialLoadIfNeeded() {
        if (initialLoadStarted) return
        initialLoadStarted = true
        scope.launch {
            val (settings, saved, favorite) =
                coroutineScope {
                    val settingsDeferred = async { settingsStore.getOnce() }
                    val savedDeferred = async { selectedCityStore.getOnce() }
                    val favoriteDeferred = async { favoriteStore.firstOrNull() }
                    Triple(
                        settingsDeferred.await(),
                        savedDeferred.await(),
                        favoriteDeferred.await(),
                    )
                }
            // 读取期间用户已手动选城：首启结果作废，避免把选择盖回去
            if (currentSelectedCity() != null) return@launch
            val hasPermission = locationHelper.hasLocationPermission()
            val preferLocate = settings.preferLocationOnLaunch
            when {
                preferLocate && saved?.useLocation == true && hasPermission -> {
                    loadCurrentLocationWeather(false, false)
                }
                preferLocate && saved?.useLocation == true && !hasPermission -> {
                    coldStartDeferredLocation = true
                    selectCity(saved.city, false, false)
                }
                saved != null -> {
                    selectCity(saved.city, false, false)
                }
                favorite != null -> {
                    selectCity(favorite, false, false)
                }
                preferLocate && hasPermission -> {
                    loadCurrentLocationWeather(false, false)
                }
                else -> {
                    if (preferLocate) coldStartDeferredLocation = true
                    selectCity(fallbackCity(), false, false)
                }
            }
        }
    }
}

/** 无任何历史/收藏/定位时的兜底城市（北京坐标）；名字通常由调用方按本地化覆盖。 */
internal fun fallbackBeijing() =
    City(
        id = 1816670,
        name = "北京",
        latitude = 39.9042,
        longitude = 116.4074,
        country = "中国",
        admin1 = "北京",
    )

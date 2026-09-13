package com.xiaoxiong.weather.di

import android.content.Context
import com.xiaoxiong.weather.BuildConfig
import com.xiaoxiong.weather.WeatherApp
import com.xiaoxiong.weather.data.AppSettingsStore
import com.xiaoxiong.weather.data.DebugWeatherStore
import com.xiaoxiong.weather.data.FavoriteCityStore
import com.xiaoxiong.weather.data.ForecastFetcher
import com.xiaoxiong.weather.data.LocationHelper
import com.xiaoxiong.weather.data.NetworkMonitor
import com.xiaoxiong.weather.data.PerformanceInterceptor
import com.xiaoxiong.weather.data.PhenologyCacheStore
import com.xiaoxiong.weather.data.RadarMapRepository
import com.xiaoxiong.weather.data.RecentCityStore
import com.xiaoxiong.weather.data.RetryInterceptor
import com.xiaoxiong.weather.data.SelectedCityStore
import com.xiaoxiong.weather.data.WeatherCacheStore
import com.xiaoxiong.weather.data.WeatherNotifyDedupStore
import com.xiaoxiong.weather.data.WeatherRepository
import com.xiaoxiong.weather.data.remote.OpenMeteoHttp
import com.xiaoxiong.weather.domain.CitySearchUseCase
import com.xiaoxiong.weather.game.GameEntry
import com.xiaoxiong.weather.game.loadGameEntryProviders
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import okhttp3.Cache
import okhttp3.OkHttpClient
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * 轻量手动 DI：共用 OkHttp 连接池 / 磁盘缓存 / Repository / Store。
 */
class AppContainer(
    context: Context,
) {
    private val appContext = context.applicationContext

    private val httpCache by lazy {
        Cache(File(appContext.cacheDir, "http_cache"), 20L * 1024 * 1024)
    }

    /** 共享连接池 + 磁盘缓存 + GET 重试；搜索/雷达用 newBuilder 派生 */
    val httpClient by lazy {
        OkHttpClient
            .Builder()
            .cache(httpCache)
            .addInterceptor(PerformanceInterceptor())
            .addInterceptor(RetryInterceptor())
            .connectTimeout(20, TimeUnit.SECONDS)
            // 网络切换后旧连接可能半死，10s 响应等待避免「卡住 1 分钟才失败」
            .readTimeout(10, TimeUnit.SECONDS)
            // 总耗时兜底：VPN/半死连接黑洞下 connectTimeout+readTimeout+重试可能累计 30-40s
            // 期间 UI 一直 loading 且不显示离线缓存。callTimeout 强制整条调用（含重试）15s 内失败，
            // 让 ViewModel 尽快回退到本地缓存 + 离线标识。
            .callTimeout(15, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .build()
    }

    /** 监听网络变化：清空死连接池 + 暴露在线状态，随进程常驻。 */
    val networkMonitor by lazy {
        NetworkMonitor(appContext, httpClient.connectionPool)
            .also { it.start() }
    }

    val searchHttpClient by lazy {
        httpClient
            .newBuilder()
            .connectTimeout(5, TimeUnit.SECONDS)
            .readTimeout(5, TimeUnit.SECONDS)
            .callTimeout(6, TimeUnit.SECONDS)
            .build()
    }

    val radarHttpClient by lazy {
        httpClient
            .newBuilder()
            .connectTimeout(8, TimeUnit.SECONDS)
            .readTimeout(12, TimeUnit.SECONDS)
            .build()
    }

    val json by lazy { WeatherRepository.createJson() }

    val weatherRepository by lazy {
        // OpenMeteoHttp 是 object 单例，启动时注入版本号用于 User-Agent
        OpenMeteoHttp.versionName = BuildConfig.VERSION_NAME
        WeatherRepository(
            resources = appContext.resources,
            client = httpClient,
            searchClient = searchHttpClient,
            json = json,
        )
    }

    val radarMapRepository by lazy {
        RadarMapRepository(client = radarHttpClient, json = json, versionName = BuildConfig.VERSION_NAME)
    }

    val locationHelper by lazy { LocationHelper(appContext) }
    val favoriteStore by lazy { FavoriteCityStore(appContext) }
    val recentCityStore by lazy { RecentCityStore(appContext) }
    val selectedCityStore by lazy { SelectedCityStore(appContext) }
    val weatherCacheStore by lazy { WeatherCacheStore(appContext) }
    val phenologyCacheStore by lazy { PhenologyCacheStore(appContext) }
    val settingsStore by lazy { AppSettingsStore(appContext) }
    val debugWeatherStore by lazy { DebugWeatherStore(appContext) }
    val notifyDedupStore by lazy { WeatherNotifyDedupStore(appContext) }

    init {
        // 后台独立进程（Worker/小组件）可能不经过 DebugWeatherStore 就直接拉取写缓存：
        // 温度单位 provider 在容器构建时安装，provider 内部读 settingsStore 当前单位，
        // 保证任何路径生成的预警文案都跟随设置；DebugWeatherStore 保留同样安装（幂等）。
        ForecastFetcher.installTemperatureUnitProvider(settingsStore)
    }

    val citySearchUseCase by lazy {
        CitySearchUseCase(
            repository = weatherRepository,
            locationHelper = locationHelper,
        )
    }

    private val _gameEntries = MutableStateFlow<List<GameEntry>>(emptyList())

    /** ServiceLoader 装配的游戏入口；由 [preloadGameEntries] 在后台填充，WeatherScreen 订阅读取。 */
    val gameEntries: StateFlow<List<GameEntry>> = _gameEntries.asStateFlow()

    /**
     * 后台预载游戏入口（保持 `-Pgames` 语义：未选中的模块不在 classpath，结果自然为空）。
     * 避免 MainActivity 首帧在主线程做 ServiceLoader 扫描。
     */
    suspend fun preloadGameEntries() {
        _gameEntries.value = loadGameEntryProviders()
    }

    /**
     * 应用内静默天气刷新信号：温度单位切换后预警文案等派生字符串按旧单位生成，
     * 需重新拉取（`refresh(quiet = true)`，保留已有界面数据、不打断 loading）。
     * 由 [com.xiaoxiong.weather.WeatherApp] 在检测到单位真实变化后触发，
     * WeatherViewModel 订阅；无订阅者（无天气页存活）时信号自然丢弃，下次拉取本就用新单位。
     */
    private val _quietWeatherRefreshRequests =
        MutableSharedFlow<Unit>(extraBufferCapacity = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)

    val quietWeatherRefreshRequests: SharedFlow<Unit> = _quietWeatherRefreshRequests.asSharedFlow()

    fun requestQuietWeatherRefresh() {
        _quietWeatherRefreshRequests.tryEmit(Unit)
    }
}

fun Context.appContainer(): AppContainer = (applicationContext as WeatherApp).container

package com.xiaoxiong.weather.di

import android.content.Context
import com.xiaoxiong.weather.WeatherApp
import com.xiaoxiong.weather.data.AppSettingsStore
import com.xiaoxiong.weather.data.DebugWeatherStore
import com.xiaoxiong.weather.data.FavoriteCityStore
import com.xiaoxiong.weather.data.LocationHelper
import com.xiaoxiong.weather.data.NetworkMonitor
import com.xiaoxiong.weather.data.PhenologyCacheStore
import com.xiaoxiong.weather.data.RadarMapRepository
import com.xiaoxiong.weather.data.RecentCityStore
import com.xiaoxiong.weather.data.RetryInterceptor
import com.xiaoxiong.weather.data.SelectedCityStore
import com.xiaoxiong.weather.data.WeatherCacheStore
import com.xiaoxiong.weather.data.WeatherNotifyDedupStore
import com.xiaoxiong.weather.data.WeatherRepository
import com.xiaoxiong.weather.domain.CitySearchUseCase
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
            .addInterceptor(RetryInterceptor())
            .connectTimeout(20, TimeUnit.SECONDS)
            // 网络切换后旧连接可能半死，10s 响应等待避免「卡住 1 分钟才失败」
            .readTimeout(10, TimeUnit.SECONDS)
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
        WeatherRepository(
            resources = appContext.resources,
            client = httpClient,
            searchClient = searchHttpClient,
            json = json,
        )
    }

    val radarMapRepository by lazy {
        RadarMapRepository(client = radarHttpClient, json = json)
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

    val citySearchUseCase by lazy {
        CitySearchUseCase(
            repository = weatherRepository,
            locationHelper = locationHelper,
        )
    }
}

fun Context.appContainer(): AppContainer = (applicationContext as WeatherApp).container

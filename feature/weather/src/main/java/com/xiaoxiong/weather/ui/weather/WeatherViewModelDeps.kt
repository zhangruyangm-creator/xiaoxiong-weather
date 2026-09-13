package com.xiaoxiong.weather.ui.weather

import android.app.Application
import com.xiaoxiong.weather.data.AppSettingsSource
import com.xiaoxiong.weather.data.ChinaCityCatalog
import com.xiaoxiong.weather.data.City
import com.xiaoxiong.weather.data.DebugWeatherSource
import com.xiaoxiong.weather.data.FavoriteCitySource
import com.xiaoxiong.weather.data.LocationPermissionSource
import com.xiaoxiong.weather.data.LocationSessionFactory
import com.xiaoxiong.weather.data.NetworkStateSource
import com.xiaoxiong.weather.data.NoOpWeatherPresentationEffects
import com.xiaoxiong.weather.data.PhenologyCache
import com.xiaoxiong.weather.data.RadarMapRepository
import com.xiaoxiong.weather.data.RecentCitySource
import com.xiaoxiong.weather.data.SelectedCitySource
import com.xiaoxiong.weather.data.WeatherCache
import com.xiaoxiong.weather.data.WeatherFetcher
import com.xiaoxiong.weather.data.WeatherPresentationEffects
import com.xiaoxiong.weather.domain.CitySearchUseCase
import kotlinx.coroutines.flow.Flow

data class WeatherViewModelDeps(
    val weatherRepository: WeatherFetcher,
    val locationHelper: LocationPermissionSource,
    val favoriteStore: FavoriteCitySource,
    val recentCityStore: RecentCitySource,
    val selectedCityStore: SelectedCitySource,
    val weatherCacheStore: WeatherCache,
    val phenologyCache: PhenologyCache,
    val citySearch: CitySearchUseCase,
    val settingsStore: AppSettingsSource,
    val debugWeatherStore: DebugWeatherSource,
    val radarMapRepository: RadarMapRepository,
    val locationSessionFactory: LocationSessionFactory,
    val presentationEffects: WeatherPresentationEffects = NoOpWeatherPresentationEffects,
    /** 网络在线状态；null（如测试）则不做网络恢复自动刷新。 */
    val networkMonitor: NetworkStateSource? = null,
    /**
     * 应用内静默刷新信号（温度单位切换后预警文案需按新单位重拉）；
     * null（如测试）则不订阅。收到的信号统一走 `refresh(quiet = true)`，
     * 保留既有 loading/界面数据与 5s 去重节流语义。
     */
    val quietRefreshRequests: Flow<Unit>? = null,
    /** 墙钟毫秒（默认系统时钟）；测试注入虚拟时钟，让去重窗口可确定性推进。 */
    val nowEpochMs: () -> Long = System::currentTimeMillis,
    val popularCitiesLoader: suspend (Application) -> List<City> = { app ->
        ChinaCityCatalog.popular(app, limit = 12)
    },
)

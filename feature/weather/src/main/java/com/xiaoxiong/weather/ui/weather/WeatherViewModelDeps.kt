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
    val popularCitiesLoader: suspend (Application) -> List<City> = { app ->
        ChinaCityCatalog.popular(app, limit = 12)
    },
)

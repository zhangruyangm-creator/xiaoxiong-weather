package com.xiaoxiong.weather.ui.weather

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.xiaoxiong.weather.data.AppSettingsSource
import com.xiaoxiong.weather.data.ChinaCityCatalog
import com.xiaoxiong.weather.data.City
import com.xiaoxiong.weather.data.DebugWeatherSource
import com.xiaoxiong.weather.data.FavoriteCitySource
import com.xiaoxiong.weather.data.LocationPermissionSource
import com.xiaoxiong.weather.data.LocationSessionFactory
import com.xiaoxiong.weather.data.RadarMapRepository
import com.xiaoxiong.weather.data.RecentCitySource
import com.xiaoxiong.weather.data.SelectedCitySource
import com.xiaoxiong.weather.data.WeatherCache
import com.xiaoxiong.weather.data.WeatherFetcher
import com.xiaoxiong.weather.data.WeatherPresentationEffects
import com.xiaoxiong.weather.di.appContainer
import com.xiaoxiong.weather.domain.CitySearchUseCase

data class WeatherViewModelDeps(
    val weatherRepository: WeatherFetcher,
    val locationHelper: LocationPermissionSource,
    val favoriteStore: FavoriteCitySource,
    val recentCityStore: RecentCitySource,
    val selectedCityStore: SelectedCitySource,
    val weatherCacheStore: WeatherCache,
    val citySearch: CitySearchUseCase,
    val settingsStore: AppSettingsSource,
    val debugWeatherStore: DebugWeatherSource,
    val radarMapRepository: RadarMapRepository,
    val locationSessionFactory: LocationSessionFactory,
    val presentationEffects: WeatherPresentationEffects = WeatherPresentationEffects.Default,
    val popularCitiesLoader: suspend (Application) -> List<City> = { app ->
        ChinaCityCatalog.popular(app, limit = 12)
    }
)

class WeatherViewModelFactory(
    private val application: Application
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        require(modelClass.isAssignableFrom(WeatherViewModel::class.java)) {
            "Unknown ViewModel: ${modelClass.name}"
        }
        val c = application.appContainer()
        return WeatherViewModel(
            application = application,
            deps = WeatherViewModelDeps(
                weatherRepository = c.weatherRepository,
                locationHelper = c.locationHelper,
                favoriteStore = c.favoriteStore,
                recentCityStore = c.recentCityStore,
                selectedCityStore = c.selectedCityStore,
                weatherCacheStore = c.weatherCacheStore,
                citySearch = c.citySearchUseCase,
                settingsStore = c.settingsStore,
                debugWeatherStore = c.debugWeatherStore,
                radarMapRepository = c.radarMapRepository,
                locationSessionFactory = LocationSessionFactory { scope ->
                    LocationWeatherCoordinator(c.locationHelper, scope)
                }
            )
        ) as T
    }
}

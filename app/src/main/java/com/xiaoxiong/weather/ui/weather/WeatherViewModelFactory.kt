package com.xiaoxiong.weather.ui.weather

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.xiaoxiong.weather.data.DefaultWeatherPresentationEffects
import com.xiaoxiong.weather.data.LocationSessionFactory
import com.xiaoxiong.weather.di.appContainer

class WeatherViewModelFactory(
    private val application: Application,
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        require(modelClass.isAssignableFrom(WeatherViewModel::class.java)) {
            "Unknown ViewModel: ${modelClass.name}"
        }
        val c = application.appContainer()
        return WeatherViewModel(
            application = application,
            deps =
                WeatherViewModelDeps(
                    weatherRepository = c.weatherRepository,
                    locationHelper = c.locationHelper,
                    favoriteStore = c.favoriteStore,
                    recentCityStore = c.recentCityStore,
                    selectedCityStore = c.selectedCityStore,
                    weatherCacheStore = c.weatherCacheStore,
                    phenologyCache = c.phenologyCacheStore,
                    citySearch = c.citySearchUseCase,
                    settingsStore = c.settingsStore,
                    debugWeatherStore = c.debugWeatherStore,
                    radarMapRepository = c.radarMapRepository,
                    locationSessionFactory =
                        LocationSessionFactory { scope ->
                            LocationWeatherCoordinator(c.locationHelper, scope)
                        },
                    presentationEffects = DefaultWeatherPresentationEffects,
                    networkMonitor = c.networkMonitor,
                    quietRefreshRequests = c.quietWeatherRefreshRequests,
                ),
        ) as T
    }
}

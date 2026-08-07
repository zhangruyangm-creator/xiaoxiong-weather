package com.xiaoxiong.weather.data

import android.content.Context
import com.xiaoxiong.weather.util.WeatherLauncherIcon
import com.xiaoxiong.weather.widget.WeatherWidgetUpdater
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow

/** 拉取天气快照（便于 ViewModel 单测替换）。 */
fun interface WeatherFetcher {
    suspend fun fetchWeather(city: City): WeatherSnapshot
}

interface WeatherCache {
    suspend fun get(city: City): WeatherCacheStore.CacheEntry?
    suspend fun save(snapshot: WeatherSnapshot)
}

interface FavoriteCitySource {
    val favorites: Flow<List<City>>
    suspend fun toggle(city: City)
    fun isFavorite(favorites: List<City>, city: City?): Boolean
    suspend fun firstOrNull(): City?
}

interface RecentCitySource {
    val recent: Flow<List<City>>
    suspend fun add(city: City)
    suspend fun clear()
}

interface SelectedCitySource {
    suspend fun save(city: City, useLocation: Boolean)
    suspend fun getOnce(): SelectedCityStore.PersistedSelection?
}

interface AppSettingsSource {
    val settings: Flow<AppSettings>
    suspend fun getOnce(): AppSettings
}

interface DebugWeatherSource {
    val state: Flow<DebugWeatherState>
    suspend fun getOnce(): DebugWeatherState
    suspend fun setEnabled(enabled: Boolean)
    suspend fun saveOverride(override: DebugWeatherOverride)
    suspend fun clearOverride()
}

interface LocationPermissionSource {
    fun hasLocationPermission(): Boolean
}

/** 定位会话：可取消过期请求，也可发起一次定位。 */
interface LocationSession {
    fun cancelPending()
    /**
     * @param onResolved 坐标就绪（可立刻拉天气；地名可能仍是占位/缓存）
     * @param onPlaceUpdated 逆地理完成后的地名刷新（同坐标，勿再整局重拉）
     */
    fun locate(
        onResolved: (City) -> Unit,
        onError: (Throwable) -> Unit,
        onPlaceUpdated: (City) -> Unit = {}
    )
}

fun interface LocationSessionFactory {
    fun create(scope: CoroutineScope): LocationSession
}

interface WeatherPresentationEffects {
    fun scheduleLauncherIcon(
        context: Context,
        weatherCode: Int?,
        precipMmPerHour: Double? = null,
        isDay: Boolean? = true
    )
    suspend fun refreshWidgets(context: Context)

    companion object {
        val Default: WeatherPresentationEffects = object : WeatherPresentationEffects {
            override fun scheduleLauncherIcon(
                context: Context,
                weatherCode: Int?,
                precipMmPerHour: Double?,
                isDay: Boolean?
            ) {
                WeatherLauncherIcon.scheduleForWeatherCode(
                    context,
                    weatherCode,
                    precipMmPerHour,
                    isDay
                )
            }

            override suspend fun refreshWidgets(context: Context) {
                WeatherWidgetUpdater.updateAll(context)
            }
        }

        val NoOp: WeatherPresentationEffects = object : WeatherPresentationEffects {
            override fun scheduleLauncherIcon(
                context: Context,
                weatherCode: Int?,
                precipMmPerHour: Double?,
                isDay: Boolean?
            ) = Unit
            override suspend fun refreshWidgets(context: Context) = Unit
        }
    }
}

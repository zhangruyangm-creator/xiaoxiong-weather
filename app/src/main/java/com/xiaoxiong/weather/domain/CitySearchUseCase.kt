package com.xiaoxiong.weather.domain

import android.content.Context
import com.xiaoxiong.weather.data.ChinaCityCatalog
import com.xiaoxiong.weather.data.City
import com.xiaoxiong.weather.data.LocationHelper
import com.xiaoxiong.weather.data.WeatherRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

/**
 * 城市搜索：本地目录优先，再限时合并远程 / Geocoder。
 */
class CitySearchUseCase(
    private val repository: WeatherRepository,
    private val locationHelper: LocationHelper,
) {
    data class LocalHit(
        val cities: List<City>,
        val strongMatch: Boolean,
    )

    suspend fun searchLocal(
        query: String,
        context: Context,
    ): LocalHit {
        val catalog =
            withContext(Dispatchers.Default) {
                ChinaCityCatalog.search(query, context = context)
            }
        return LocalHit(
            cities = catalog,
            strongMatch = ChinaCityCatalog.hasStrongMatch(query, catalog),
        )
    }

    suspend fun enrichRemote(
        query: String,
        catalog: List<City>,
    ): List<City> {
        val extras =
            withTimeoutOrNull(SEARCH_TIMEOUT_MS) {
                coroutineScope {
                    val remoteDeferred =
                        async {
                            runCatching { repository.searchCities(query) }.getOrDefault(emptyList())
                        }
                    val geocoderDeferred =
                        async {
                            // 本地无结果，或仅有弱匹配时，用系统地理编码补海外/生僻地名
                            val needGeocoder =
                                WeatherRepository.containsHan(query) &&
                                    (catalog.isEmpty() || !ChinaCityCatalog.hasStrongMatch(query, catalog))
                            if (needGeocoder) {
                                runCatching {
                                    locationHelper.searchPlaces(
                                        WeatherRepository.normalizePlaceQuery(query),
                                    )
                                }.getOrDefault(emptyList())
                            } else {
                                emptyList()
                            }
                        }
                    remoteDeferred.await() to geocoderDeferred.await()
                }
            }
        val (remote, geocoder) = extras ?: (emptyList<City>() to emptyList())
        val merged =
            WeatherRepository.mergeCityResults(
                catalog,
                WeatherRepository.mergeCityResults(remote, geocoder),
            )
        return WeatherRepository.rankCityResults(query, merged)
    }

    companion object {
        private const val SEARCH_TIMEOUT_MS = 4_500L
    }
}

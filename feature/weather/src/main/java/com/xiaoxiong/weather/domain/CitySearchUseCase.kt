package com.xiaoxiong.weather.domain

import android.content.Context
import com.xiaoxiong.weather.data.AppError
import com.xiaoxiong.weather.data.ChinaCityCatalog
import com.xiaoxiong.weather.data.City
import com.xiaoxiong.weather.data.LocationHelper
import com.xiaoxiong.weather.data.WeatherRepository
import com.xiaoxiong.weather.util.runCatchingCancellable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout

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
            try {
                withTimeout(SEARCH_TIMEOUT_MS) {
                    coroutineScope {
                        val remoteDeferred =
                            async {
                                runCatchingCancellable { repository.searchCities(query) }
                            }
                        val geocoderDeferred =
                            async {
                                // 本地无结果，或仅有弱匹配时，用系统地理编码补海外/生僻地名
                                val needGeocoder =
                                    WeatherRepository.containsHan(query) &&
                                        (catalog.isEmpty() || !ChinaCityCatalog.hasStrongMatch(query, catalog))
                                if (needGeocoder) {
                                    runCatchingCancellable {
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
            } catch (e: TimeoutCancellationException) {
                return mergeRemoteCitySearch(
                    query = query,
                    catalog = catalog,
                    remote = Result.failure(AppError.NetworkUnavailable(e)),
                    geocoder = emptyList(),
                    timedOut = true,
                )
            }
        val (remoteResult, geocoder) = extras
        return mergeRemoteCitySearch(
            query = query,
            catalog = catalog,
            remote = remoteResult,
            geocoder = geocoder,
            timedOut = false,
        )
    }

    companion object {
        private const val SEARCH_TIMEOUT_MS = 4_500L
    }
}

/**
 * 合并本地目录、远端地理编码与系统 Geocoder。
 *
 * 网络失败 / 超时不能再被当成「搜不到」：没有本地结果时必须把异常抛给 UI
 *（显示网络错误而不是「未找到城市」）。已有本地弱匹配时保留目录结果。
 */
internal fun mergeRemoteCitySearch(
    query: String,
    catalog: List<City>,
    remote: Result<List<City>>,
    geocoder: List<City>,
    timedOut: Boolean = false,
): List<City> {
    val remoteList = remote.getOrDefault(emptyList())
    val merged =
        WeatherRepository.mergeCityResults(
            catalog,
            WeatherRepository.mergeCityResults(remoteList, geocoder),
        )
    if (merged.isEmpty()) {
        if (timedOut) throw AppError.NetworkUnavailable(remote.exceptionOrNull())
        remote.exceptionOrNull()?.let { throw it }
    }
    return WeatherRepository.rankCityResults(query, merged)
}

package com.xiaoxiong.weather.ui.weather

import com.xiaoxiong.weather.data.City
import com.xiaoxiong.weather.data.HistoryRangeSummary
import com.xiaoxiong.weather.data.HistoryWeather
import com.xiaoxiong.weather.data.PhenologyCache
import com.xiaoxiong.weather.data.WeatherFetcher
import com.xiaoxiong.weather.data.isSamePlace
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.time.LocalDate

/**
 * 历史天气（单日/区间）与物候偏移查询控制器。
 *
 * 三块查询各自带独立请求 job、内存缓存去重，以及「查询期间换城则丢弃结果」的写回保护：
 *  - 单日历史按「城市|日期」缓存，同城同日只查一次；
 *  - 区间历史按「城市|起|止」缓存，同城同区间只查一次；
 *  - 物候偏移按日缓存，一天最多向网络拉一次。
 *
 * 与原 ViewModel 内嵌实现行为完全等价，抽出来只为缩减 WeatherViewModel 的职责面。
 */
class HistoryAndPhenologyController(
    private val scope: CoroutineScope,
    private val repository: WeatherFetcher,
    private val phenologyCache: PhenologyCache,
    private val updateState: ((WeatherUiState) -> WeatherUiState) -> Unit,
    private val currentCity: () -> City?,
) {
    private var phenologyJob: Job? = null
    private var phenologyCity: City? = null
    private var historyJob: Job? = null
    private var historyRangeJob: Job? = null

    private val maxHistoryCacheEntries = 30
    private val historyCache = mutableMapOf<String, HistoryWeather>()

    private val maxHistoryRangeCacheEntries = 30
    private val historyRangeCache = mutableMapOf<String, HistoryRangeSummary>()

    /** 进程重建时历史页可能先于城市加载完成发起查询：先记下，城市就绪后补发。 */
    private var pendingHistoryDay: LocalDate? = null
    private var pendingHistoryRange: Pair<LocalDate, LocalDate>? = null

    /** 城市就绪（冷启动完成/用户选城）后补发此前因无城市而挂起的查询。 */
    fun retryPending() {
        if (currentCity() == null) return
        pendingHistoryDay?.let { date ->
            pendingHistoryDay = null
            fetchHistoryDay(date)
        }
        pendingHistoryRange?.let { (start, end) ->
            pendingHistoryRange = null
            fetchHistoryRange(start, end)
        }
    }

    /**
     * 进入「七日」页时按需补齐物候偏移：
     * 同城同年当日已拉过则直接用缓存，否则才联网（一天最多一次）；
     * 跨天/跨年/换城自然失效，会在下次进入页面时重拉。
     * 独立于主天气加载，失败时静默保留空（时令卡退化为纯经验推算）。
     */
    fun ensurePhenologyShift() {
        val city = currentCity() ?: return
        if (phenologyJob?.isActive == true) {
            // 同城在途才复用；换城后旧 job 的结果已无意义，取消并重新发起
            if (phenologyCity?.isSamePlace(city) == true) return
            phenologyJob?.cancel()
        }
        phenologyCity = city
        phenologyJob =
            scope.launch {
                if (stillOnCity(city)) {
                    updateState { it.copy(phenologyShiftLoading = true) }
                }
                val today = LocalDate.now()
                val cached = phenologyCache.get(city, today.year)
                val shift =
                    if (cached != null) {
                        cached.shiftDays
                    } else {
                        val fetched =
                            try {
                                repository.fetchPhenologyShiftDays(city, today)
                            } catch (e: CancellationException) {
                                throw e
                            } catch (e: Exception) {
                                if (stillOnCity(city)) {
                                    updateState { it.copy(phenologyShiftLoading = false) }
                                }
                                return@launch
                            }
                        phenologyCache.save(city, today.year, fetched)
                        fetched
                    }
                if (stillOnCity(city)) {
                    updateState {
                        it.copy(
                            phenologyShiftDays = shift,
                            phenologyShiftLoading = false,
                        )
                    }
                }
            }
    }

    /**
     * 单日历史查询：按「城市|日期」做内存缓存去重，同城同日只查一次；
     * 换城后缓存键不同，自然不串城。该日无数据置 historyFailed，
     * 网络/服务端错误置 historyNetworkFailed。
     */
    fun fetchHistoryDay(date: LocalDate) {
        val city = currentCity()
        if (city == null) {
            // 进程重建时历史页先于城市加载完成发起查询：城市就绪后由 retryPending 补发
            pendingHistoryDay = date
            return
        }
        pendingHistoryDay = null
        val cacheKey = "%.4f,%.4f|%s".format(city.latitude, city.longitude, date)
        historyCache[cacheKey]?.let { cached ->
            // 缓存命中即作废在途请求，避免旧请求晚到覆盖当前选择
            historyJob?.cancel()
            updateState {
                it.copy(
                    historyWeather = cached,
                    isHistoryLoading = false,
                    historyFailed = false,
                    historyNetworkFailed = false,
                )
            }
            return
        }
        historyJob?.cancel()
        historyJob =
            scope.launch {
                updateState {
                    it.copy(
                        isHistoryLoading = true,
                        historyWeather = null,
                        historyFailed = false,
                        historyNetworkFailed = false,
                    )
                }
                var networkFailed = false
                val result =
                    try {
                        repository.fetchHistoryDay(city, date)
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        networkFailed = true
                        null
                    }
                if (result != null) {
                    cacheHistory(cacheKey, result)
                }
                // 查询期间换城则丢弃结果（不覆盖新城市）
                if (stillOnCity(city)) {
                    updateState {
                        it.copy(
                            historyWeather = result,
                            isHistoryLoading = false,
                            historyFailed = result == null && !networkFailed,
                            historyNetworkFailed = networkFailed,
                        )
                    }
                } else {
                    updateState { it.copy(isHistoryLoading = false) }
                }
            }
    }

    fun clearHistoryDay() {
        historyJob?.cancel()
        pendingHistoryDay = null
        updateState {
            it.copy(
                historyWeather = null,
                isHistoryLoading = false,
                historyFailed = false,
                historyNetworkFailed = false,
            )
        }
    }

    /**
     * 日期范围历史查询：按「城市|起|止」内存缓存去重，同城同区间只查一次；
     * 区间内无数据置 historyRangeFailed，网络/服务端错误置 historyRangeNetworkFailed。
     */
    fun fetchHistoryRange(
        start: LocalDate,
        end: LocalDate,
    ) {
        if (end.isBefore(start)) return
        val city = currentCity()
        if (city == null) {
            // 进程重建时历史页先于城市加载完成发起查询：城市就绪后由 retryPending 补发
            pendingHistoryRange = start to end
            return
        }
        pendingHistoryRange = null
        val cacheKey =
            "%.4f,%.4f|%s|%s".format(city.latitude, city.longitude, start, end)
        historyRangeCache[cacheKey]?.let { cached ->
            // 缓存命中即作废在途请求，避免旧请求晚到覆盖当前选择
            historyRangeJob?.cancel()
            updateState {
                it.copy(
                    historyRange = cached,
                    isHistoryRangeLoading = false,
                    historyRangeFailed = false,
                    historyRangeNetworkFailed = false,
                )
            }
            return
        }
        historyRangeJob?.cancel()
        historyRangeJob =
            scope.launch {
                updateState {
                    it.copy(
                        isHistoryRangeLoading = true,
                        historyRange = null,
                        historyRangeFailed = false,
                        historyRangeNetworkFailed = false,
                    )
                }
                var networkFailed = false
                val result =
                    try {
                        repository.fetchHistoryRange(city, start, end)
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        networkFailed = true
                        null
                    }
                if (result != null) {
                    if (historyRangeCache.size >= maxHistoryRangeCacheEntries) {
                        historyRangeCache.keys.firstOrNull()?.let { historyRangeCache.remove(it) }
                    }
                    historyRangeCache[cacheKey] = result
                }
                // 查询期间换城则丢弃结果（不覆盖新城市）
                if (stillOnCity(city)) {
                    updateState {
                        it.copy(
                            historyRange = result,
                            isHistoryRangeLoading = false,
                            historyRangeFailed = result == null && !networkFailed,
                            historyRangeNetworkFailed = networkFailed,
                        )
                    }
                } else {
                    updateState { it.copy(isHistoryRangeLoading = false) }
                }
            }
    }

    fun clearHistoryRange() {
        historyRangeJob?.cancel()
        pendingHistoryRange = null
        updateState {
            it.copy(
                historyRange = null,
                isHistoryRangeLoading = false,
                historyRangeFailed = false,
                historyRangeNetworkFailed = false,
            )
        }
    }

    private fun cacheHistory(
        key: String,
        value: HistoryWeather,
    ) {
        if (historyCache.size >= maxHistoryCacheEntries) {
            historyCache.keys.firstOrNull()?.let { historyCache.remove(it) }
        }
        historyCache[key] = value
    }

    private fun stillOnCity(city: City): Boolean = currentCity()?.isSamePlace(city) == true
}

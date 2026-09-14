package com.xiaoxiong.weather.ui.weather

import android.app.Application
import com.xiaoxiong.weather.R
import com.xiaoxiong.weather.data.City
import com.xiaoxiong.weather.data.DebugWeatherStore
import com.xiaoxiong.weather.domain.CitySearchUseCase
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

/**
 * 城市搜索防抖管道：本地目录优先，弱匹配再请求远端补全。
 * 从 WeatherViewModel 抽出，只通过 onState/currentState 读写 UI 状态。
 */
@OptIn(FlowPreview::class)
internal class CitySearchController(
    private val scope: CoroutineScope,
    private val app: Application,
    private val citySearch: CitySearchUseCase,
    private val onState: ((WeatherUiState) -> WeatherUiState) -> Unit,
    private val currentState: () -> WeatherUiState,
    private val onDebugMagicQuery: () -> Unit,
) {
    private val queryFlow = MutableStateFlow("")

    /**
     * 选城同步的 query 基线：置入后由 [handleQuery] 消费并跳过搜索；
     * 任何用户输入/清空都会作废它，避免过期标记误吞后续合法搜索。
     */
    private var syncedCityQuery: String? = null

    /**
     * 搜索世代：选城等场景调用 [cancelPending] 后，在途请求的回写全部作废，
     * 避免旧建议在选城后复活。
     */
    private var generation = 0

    fun start() {
        scope.launch {
            queryFlow
                .debounce(200)
                .distinctUntilChanged()
                .collectLatest { query -> handleQuery(query) }
        }
    }

    fun cancelPending() {
        generation++
        onState { it.copy(suggestions = emptyList(), isSearching = false) }
    }

    /**
     * 选城后同步搜索基线：ViewModel 已把 UI query 改为城市名，这里把 queryFlow 一并同步，
     * 否则 queryFlow 停在旧输入，用户删回原词时 StateFlow 值未变、搜索不再触发。
     * 同步值会被 [handleQuery] 识别并跳过，避免选城后自动搜一轮城市全名。
     */
    fun onCitySelected(city: City) {
        val displayName = city.displayName
        // 值未变时 StateFlow 不发射，无需留待消费的跳过标记
        if (queryFlow.value == displayName) return
        syncedCityQuery = displayName
        queryFlow.value = displayName
    }

    fun onQueryChange(value: String) {
        // 同值输入（IME 重复提交等）不会改变 queryFlow，也不应作废选城跳过标记：
        // 否则待防抖消费的同步值会被当成用户输入，选城后自动搜一轮城市全名。
        if (value != syncedCityQuery) syncedCityQuery = null
        onState {
            // 仅在用户真正改动了定位城市文案时才退出「当前位置」模式，避免误触
            val keepLocation =
                it.usingCurrentLocation &&
                    value == it.selectedCity?.displayName
            it.copy(
                query = value,
                errorMessage = null,
                usingCurrentLocation = keepLocation,
            )
        }
        queryFlow.value = value
    }

    /**
     * 彩蛋口令等场景清空搜索框：不退出「当前位置」模式。
     * 常规输入走 [onQueryChange]（清空时会按文本内容决定是否保持定位）。
     */
    fun clearQueryKeepLocation() {
        syncedCityQuery = null
        onState {
            it.copy(
                query = "",
                suggestions = emptyList(),
                isSearching = false,
                errorMessage = null,
                usingCurrentLocation = it.usingCurrentLocation,
            )
        }
        queryFlow.value = ""
    }

    private suspend fun handleQuery(query: String) {
        // 选城同步值：只同步基线，不发起搜索
        if (query == syncedCityQuery) {
            syncedCityQuery = null
            return
        }
        val gen = generation
        val trimmed = query.trim()
        if (DebugWeatherStore.isMagicQuery(trimmed)) {
            onDebugMagicQuery()
            if (gen != generation) return
            onState {
                it.copy(
                    query = it.selectedCity?.displayName.orEmpty(),
                    suggestions = emptyList(),
                    isSearching = false,
                    infoMessage = app.str(R.string.debug_entered),
                )
            }
            return
        }
        if (trimmed.length < 2) {
            if (gen != generation) return
            onState { it.copy(suggestions = emptyList(), isSearching = false) }
            return
        }
        val state = currentState()
        if (state.usingCurrentLocation && query == state.selectedCity?.displayName) {
            return
        }
        if (gen != generation) return
        onState { it.copy(isSearching = true, errorMessage = null) }
        try {
            val local = citySearch.searchLocal(query, app)
            if (gen != generation) return
            onState {
                it.copy(
                    suggestions = local.cities,
                    isSearching = !local.strongMatch,
                    errorMessage = null,
                )
            }
            if (local.strongMatch) {
                if (gen != generation) return
                onState { it.copy(isSearching = false) }
                return
            }

            val cities = citySearch.enrichRemote(query, local.cities)
            if (gen != generation) return
            onState {
                it.copy(
                    suggestions = cities,
                    isSearching = false,
                    errorMessage =
                        if (cities.isEmpty()) {
                            app.str(R.string.error_city_not_found, trimmed)
                        } else {
                            null
                        },
                )
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            if (gen != generation) return
            onState {
                it.copy(
                    isSearching = false,
                    suggestions = emptyList(),
                    errorMessage = e.userFacingMessage(app, R.string.error_search_failed),
                )
            }
        }
    }
}

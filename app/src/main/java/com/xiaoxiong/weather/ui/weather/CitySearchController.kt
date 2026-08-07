package com.xiaoxiong.weather.ui.weather

import android.app.Application
import com.xiaoxiong.weather.R
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
    private val onDebugMagicQuery: () -> Unit
) {
    private val queryFlow = MutableStateFlow("")

    fun start() {
        scope.launch {
            queryFlow
                .debounce(200)
                .distinctUntilChanged()
                .collectLatest { query -> handleQuery(query) }
        }
    }

    fun onQueryChange(value: String) {
        onState {
            // 仅在用户真正改动了定位城市文案时才退出「当前位置」模式，避免误触
            val keepLocation = it.usingCurrentLocation &&
                value == it.selectedCity?.displayName
            it.copy(
                query = value,
                errorMessage = null,
                usingCurrentLocation = keepLocation
            )
        }
        queryFlow.value = value
    }

    private suspend fun handleQuery(query: String) {
        val trimmed = query.trim()
        if (DebugWeatherStore.isMagicQuery(trimmed)) {
            onDebugMagicQuery()
            onState {
                it.copy(
                    query = it.selectedCity?.displayName.orEmpty(),
                    suggestions = emptyList(),
                    isSearching = false,
                    infoMessage = app.str(R.string.debug_entered)
                )
            }
            return
        }
        if (trimmed.length < 2) {
            onState { it.copy(suggestions = emptyList(), isSearching = false) }
            return
        }
        val state = currentState()
        if (state.usingCurrentLocation && query == state.selectedCity?.displayName) {
            return
        }
        onState { it.copy(isSearching = true, errorMessage = null) }
        try {
            val local = citySearch.searchLocal(query, app)
            onState {
                it.copy(
                    suggestions = local.cities,
                    isSearching = !local.strongMatch,
                    errorMessage = null
                )
            }
            if (local.strongMatch) {
                onState { it.copy(isSearching = false) }
                return
            }

            val cities = citySearch.enrichRemote(query, local.cities)
            onState {
                it.copy(
                    suggestions = cities,
                    isSearching = false,
                    errorMessage = if (cities.isEmpty()) {
                        app.str(R.string.error_city_not_found, trimmed)
                    } else {
                        null
                    }
                )
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            onState {
                it.copy(
                    isSearching = false,
                    suggestions = emptyList(),
                    errorMessage = e.userFacingMessage(app, R.string.error_search_failed)
                )
            }
        }
    }
}

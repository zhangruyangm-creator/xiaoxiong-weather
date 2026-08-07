package com.xiaoxiong.weather.ui.weather

import android.app.Application
import com.xiaoxiong.weather.R
import com.xiaoxiong.weather.data.DebugWeatherOverride
import com.xiaoxiong.weather.data.DebugWeatherSource
import com.xiaoxiong.weather.data.DebugWeatherState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * 隐藏调试模式：状态流暴露 + 覆盖应用/还原，每个动作后回灌天气展示。
 * 从 WeatherViewModel 抽出，天气回灌通过 republishWeather 回调完成。
 */
internal class DebugModeController(
    private val scope: CoroutineScope,
    private val app: Application,
    private val store: DebugWeatherSource,
    private val onState: ((WeatherUiState) -> WeatherUiState) -> Unit,
    private val republishWeather: suspend () -> Unit
) {
    val state: StateFlow<DebugWeatherState> = store.state
        .stateIn(scope, SharingStarted.WhileSubscribed(5_000), DebugWeatherState())

    /** 调试开关/覆盖变化时同步 debugEnabled，并按最新覆盖重新发布天气。 */
    fun start() {
        scope.launch {
            store.state.collectLatest { debug ->
                onState { it.copy(debugEnabled = debug.enabled) }
                republishWeather()
            }
        }
    }

    fun enable() {
        scope.launch {
            store.setEnabled(true)
            onState {
                it.copy(debugEnabled = true, infoMessage = app.str(R.string.debug_entered))
            }
            republishWeather()
        }
    }

    fun exit() {
        scope.launch {
            store.setEnabled(false)
            onState {
                it.copy(debugEnabled = false, infoMessage = app.str(R.string.debug_exited))
            }
            republishWeather()
        }
    }

    fun applyOverride(override: DebugWeatherOverride) {
        scope.launch {
            store.saveOverride(override)
            onState {
                it.copy(debugEnabled = true, infoMessage = app.str(R.string.debug_applied))
            }
            republishWeather()
        }
    }

    fun clearOverride() {
        scope.launch {
            store.clearOverride()
            onState { it.copy(infoMessage = app.str(R.string.debug_override_cleared)) }
            republishWeather()
        }
    }
}

package com.xiaoxiong.weather.ui.weather

import androidx.compose.foundation.pager.PagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalFocusManager
import com.xiaoxiong.weather.ui.game.BearFarmScreen
import com.xiaoxiong.weather.ui.game.BearGameScreen
import com.xiaoxiong.weather.ui.game.BearMazeScreen
import com.xiaoxiong.weather.ui.game.BearStarJumpScreen
import com.xiaoxiong.weather.ui.game.EASTER_EGG_BEAR_FARM
import com.xiaoxiong.weather.ui.game.EASTER_EGG_BEAR_GAME
import com.xiaoxiong.weather.ui.game.EASTER_EGG_BEAR_MAZE
import com.xiaoxiong.weather.ui.game.EASTER_EGG_BEAR_STAR
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * 彩蛋口令触发与 4 个小熊游戏弹窗的统一容器。
 * 口令在搜索框输入后立即触发对应游戏，并清空搜索框/收起键盘；
 * 清空搜索框时保留「当前位置」模式（经 [onClearQuery] 回调，不走常规输入）。
 * 退出游戏时回到首页第一页。
 */
@Composable
internal fun BearGameOverlay(
    query: String,
    onClearQuery: () -> Unit,
    sceneCode: Int?,
    isDay: Boolean?,
    cityName: String?,
    pagerState: PagerState,
    scope: CoroutineScope,
) {
    val focusManager = LocalFocusManager.current
    var showBearGame by remember { mutableStateOf(false) }
    var showStarGame by remember { mutableStateOf(false) }
    var showMazeGame by remember { mutableStateOf(false) }
    var showFarmGame by remember { mutableStateOf(false) }

    LaunchedEffect(query) {
        when (query.trim()) {
            EASTER_EGG_BEAR_GAME -> {
                onClearQuery()
                focusManager.clearFocus()
                showBearGame = true
            }
            EASTER_EGG_BEAR_STAR -> {
                onClearQuery()
                focusManager.clearFocus()
                showStarGame = true
            }
            EASTER_EGG_BEAR_MAZE -> {
                onClearQuery()
                focusManager.clearFocus()
                showMazeGame = true
            }
            EASTER_EGG_BEAR_FARM -> {
                onClearQuery()
                focusManager.clearFocus()
                showFarmGame = true
            }
        }
    }

    val onExit: () -> Unit = {
        scope.launch { pagerState.animateScrollToPage(0) }
    }

    if (showBearGame) {
        BearGameScreen(
            sceneCode = sceneCode,
            isDay = isDay,
            onExit = {
                showBearGame = false
                onExit()
            },
            cityName = cityName,
        )
    }
    if (showStarGame) {
        BearStarJumpScreen(
            sceneCode = sceneCode,
            isDay = isDay,
            onExit = {
                showStarGame = false
                onExit()
            },
            cityName = cityName,
        )
    }
    if (showMazeGame) {
        BearMazeScreen(
            sceneCode = sceneCode,
            isDay = isDay,
            onExit = {
                showMazeGame = false
                onExit()
            },
        )
    }
    if (showFarmGame) {
        BearFarmScreen(
            sceneCode = sceneCode,
            isDay = isDay,
            onExit = {
                showFarmGame = false
                onExit()
            },
        )
    }
}

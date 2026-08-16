package com.xiaoxiong.weather.ui.weather

import androidx.compose.foundation.pager.PagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalFocusManager
import com.xiaoxiong.weather.game.GameEntry
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * 彩蛋口令触发与小熊游戏 / 小屋弹窗的统一容器。
 * 口令在搜索框输入后立即触发对应内容，并清空搜索框/收起键盘（经 [onClearQuery]）。
 *
 * 游戏从 [entries] 注册表按口令匹配（装配方提供，见阶段 5 模块化约定）。
 * 同一时刻只激活一个游戏（与历史行为一致）；小熊的家枢纽内打开其它游戏即切换到该游戏，
 * 退出子游戏时直接回到天气首页。
 */
@Composable
fun BearGameOverlay(
    query: String,
    onClearQuery: () -> Unit,
    sceneCode: Int?,
    isDay: Boolean?,
    cityName: String?,
    pagerState: PagerState,
    scope: CoroutineScope,
    entries: List<GameEntry>,
) {
    val focusManager = LocalFocusManager.current
    // 口令注册表直接来自装配方（ServiceLoader），无需过滤
    val enabledEntries = entries

    // 当前激活的游戏口令；null 表示未打开任何游戏
    var activePassword by remember { mutableStateOf<String?>(null) }

    val launchGame: (GameEntry) -> Unit = { entry ->
        onClearQuery()
        focusManager.clearFocus()
        activePassword = entry.password
    }

    LaunchedEffect(query) {
        enabledEntries.firstOrNull { it.password == query.trim() }?.let(launchGame)
    }

    val exitToHome: () -> Unit = {
        scope.launch { pagerState.animateScrollToPage(0) }
    }

    val activeEntry = enabledEntries.firstOrNull { it.password == activePassword } ?: return
    activeEntry.Content(
        sceneCode = sceneCode,
        isDay = isDay,
        cityName = cityName,
        onExit = {
            activePassword = null
            exitToHome()
        },
        onOpenGame = { password ->
            enabledEntries.firstOrNull { it.password == password }?.let(launchGame)
        },
        otherGames = enabledEntries.filter { it.password != activePassword },
    )
}

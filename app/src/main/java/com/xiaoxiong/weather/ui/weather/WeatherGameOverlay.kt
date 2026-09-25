package com.xiaoxiong.weather.ui.weather

import androidx.compose.foundation.pager.PagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalFocusManager
import com.xiaoxiong.weather.game.GameEntry
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * 彩蛋口令触发与小熊游戏 / 小屋弹窗的统一容器。
 * 口令在搜索框输入后立即触发对应内容，并清空搜索框/收起键盘（经 [onClearQuery]）。
 *
 * 除搜索框口令外，还支持 [requestPassword] 作为「外部直接激活」信号（如标题连点彩蛋跳转
 * 至游戏枢纽）：`(nonce, password)` 中的 nonce 自增保证相同口令也能重复触发。
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
    requestPassword: Pair<Int, String>? = null,
    onActiveChange: (Boolean) -> Unit = {},
    onRequestPasswordConsumed: () -> Unit = {},
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

    // keys 含 enabledEntries：ServiceLoader 预载完成前输入口令/连点标题不会丢信号，
    // 入口由空变有值时 effect 会重跑并用最新 query/requestPassword 重新匹配
    LaunchedEffect(query, enabledEntries) {
        enabledEntries.firstOrNull { it.password == query.trim() }?.let(launchGame)
    }

    // 外部直接激活信号（如标题连点彩蛋）：nonce 变化即重新尝试，口令相同也生效
    //
    // 同一对 (nonce, password) 只能消费一次：entries 由空→非空时 LaunchedEffect 会因
    // keys 变化重跑，若不消费会把已经触发过的口令再触发一次，导致「从设置返回后
    // 突然弹出小熊的家」之类延迟副作用。消费成功/失败都通知父组件清空信号。
    val currentOnConsumed by rememberUpdatedState(onRequestPasswordConsumed)
    LaunchedEffect(requestPassword, enabledEntries) {
        val nonce = requestPassword?.first
        val password = requestPassword?.second
        if (nonce != null && password != null) {
            enabledEntries.firstOrNull { it.password == password }?.let(launchGame)
        }
        if (nonce != null) currentOnConsumed()
    }

    // transitionSpec/回调内不是 composable 上下文，先在组合期读出
    val reduceMotion = com.xiaoxiong.weather.ui.LocalReduceMotion.current
    val exitToHome: () -> Unit = {
        scope.launch {
            // 减弱动画：直接跳回今天页，不做横向滑动
            if (reduceMotion) {
                pagerState.scrollToPage(0)
            } else {
                pagerState.animateScrollToPage(0)
            }
        }
    }

    val activeEntry = enabledEntries.firstOrNull { it.password == activePassword }
    val currentOnActiveChange by rememberUpdatedState(onActiveChange)
    LaunchedEffect(activeEntry) {
        currentOnActiveChange(activeEntry != null)
    }
    val entry = activeEntry ?: return
    val otherGames =
        remember(activePassword, enabledEntries) {
            enabledEntries.filter { it.password != activePassword }
        }
    key(activePassword) {
        entry.Content(
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
            otherGames = otherGames,
        )
    }
}

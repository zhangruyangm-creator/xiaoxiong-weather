package com.xiaoxiong.weather.ui.game

import androidx.compose.runtime.Composable

/**
 * 小熊迷宫未选入编译时的门面：口令置为不可达，入口为空实现。
 * 符号与真实实现保持一致的签名，保证 main 的入口始终可编译。
 */
const val EASTER_EGG_BEAR_MAZE = "BEAR_GAME_DISABLED"

@Composable
fun BearMazeScreen(
    @Suppress("UNUSED_PARAMETER") sceneCode: Int?,
    @Suppress("UNUSED_PARAMETER") isDay: Boolean?,
    @Suppress("UNUSED_PARAMETER") onExit: () -> Unit,
) = Unit

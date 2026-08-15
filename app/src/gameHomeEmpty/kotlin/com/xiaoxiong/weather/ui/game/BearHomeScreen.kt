package com.xiaoxiong.weather.ui.game

import androidx.compose.runtime.Composable

/**
 * 小熊的家未选入编译时的门面：口令置为不可达，入口为空实现。
 * 符号与真实实现保持一致的签名，保证 main 的入口始终可编译。
 */
const val EASTER_EGG_BEAR_HOME = "BEAR_GAME_DISABLED"

@Composable
fun BearHomeScreen(
    @Suppress("UNUSED_PARAMETER") sceneCode: Int?,
    @Suppress("UNUSED_PARAMETER") isDay: Boolean?,
    @Suppress("UNUSED_PARAMETER") onExit: () -> Unit,
    @Suppress("UNUSED_PARAMETER") onOpenJump: () -> Unit = {},
    @Suppress("UNUSED_PARAMETER") onOpenStar: () -> Unit = {},
    @Suppress("UNUSED_PARAMETER") onOpenMaze: () -> Unit = {},
    @Suppress("UNUSED_PARAMETER") onOpenFarm: () -> Unit = {},
) = Unit

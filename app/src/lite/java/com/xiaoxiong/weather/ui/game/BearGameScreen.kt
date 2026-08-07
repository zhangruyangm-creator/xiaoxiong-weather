package com.xiaoxiong.weather.ui.game

import androidx.compose.runtime.Composable

/**
 * lite 版不含小熊彩蛋游戏：口令对用户不可达，入口为空实现。
 * 符号与 full 源集保持一致，release 下 R8 会把空实现直接内联消除。
 */
const val EASTER_EGG_BEAR_GAME = "BEAR_GAME_DISABLED"
const val EASTER_EGG_BEAR_STAR = "BEAR_GAME_DISABLED"
const val EASTER_EGG_BEAR_MAZE = "BEAR_GAME_DISABLED"
const val EASTER_EGG_BEAR_FARM = "BEAR_GAME_DISABLED"

@Composable
fun BearGameScreen(
    @Suppress("UNUSED_PARAMETER") sceneCode: Int?,
    @Suppress("UNUSED_PARAMETER") isDay: Boolean?,
    @Suppress("UNUSED_PARAMETER") onExit: () -> Unit,
) = Unit

@Composable
fun BearStarJumpScreen(
    @Suppress("UNUSED_PARAMETER") sceneCode: Int?,
    @Suppress("UNUSED_PARAMETER") isDay: Boolean?,
    @Suppress("UNUSED_PARAMETER") onExit: () -> Unit,
) = Unit

@Composable
fun BearMazeScreen(
    @Suppress("UNUSED_PARAMETER") sceneCode: Int?,
    @Suppress("UNUSED_PARAMETER") isDay: Boolean?,
    @Suppress("UNUSED_PARAMETER") onExit: () -> Unit,
) = Unit

@Composable
fun BearFarmScreen(
    @Suppress("UNUSED_PARAMETER") sceneCode: Int?,
    @Suppress("UNUSED_PARAMETER") isDay: Boolean?,
    @Suppress("UNUSED_PARAMETER") onExit: () -> Unit,
) = Unit

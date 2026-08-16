package com.xiaoxiong.weather.ui.game

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import com.xiaoxiong.weather.R
import com.xiaoxiong.weather.game.EASTER_EGG_BEAR_MAZE
import com.xiaoxiong.weather.game.GameEntry

/** 小熊迷宫（截图口令 [EASTER_EGG_BEAR_MAZE]）。 */
object BearMazeEntry : GameEntry {
    override val password: String = EASTER_EGG_BEAR_MAZE

    @get:StringRes
    override val titleRes: Int = R.string.bear_maze_title

    @Composable
    override fun Content(
        sceneCode: Int?,
        isDay: Boolean?,
        cityName: String?,
        onExit: () -> Unit,
        @Suppress("UNUSED_PARAMETER") onOpenGame: ((password: String) -> Unit)?,
        @Suppress("UNUSED_PARAMETER") otherGames: List<GameEntry>,
    ) {
        BearMazeScreen(
            sceneCode = sceneCode,
            isDay = isDay,
            onExit = onExit,
        )
    }
}

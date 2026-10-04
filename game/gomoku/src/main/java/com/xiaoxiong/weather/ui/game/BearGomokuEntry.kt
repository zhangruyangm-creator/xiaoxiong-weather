package com.xiaoxiong.weather.ui.game

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import com.xiaoxiong.weather.R
import com.xiaoxiong.weather.game.EASTER_EGG_BEAR_GOMOKU
import com.xiaoxiong.weather.game.GameEntry

/** 小熊五子棋（口令 [EASTER_EGG_BEAR_GOMOKU]）：15×15 连五，人机对弈。 */
object BearGomokuEntry : GameEntry {
    override val password: String = EASTER_EGG_BEAR_GOMOKU

    @get:StringRes
    override val titleRes: Int = R.string.gomoku_title

    @Composable
    override fun Content(
        sceneCode: Int?,
        isDay: Boolean?,
        cityName: String?,
        onExit: () -> Unit,
        onOpenGame: ((String) -> Unit)?,
        onOpenBearAlbum: (() -> Unit)?,
        bearHomeResumeRoom: String?,
        onBearHomeResumeRoomConsumed: (() -> Unit)?,
        otherGames: List<GameEntry>,
    ) {
        BearGomokuScreen(
            sceneCode = sceneCode,
            onExit = onExit,
            cityName = cityName,
        )
    }
}

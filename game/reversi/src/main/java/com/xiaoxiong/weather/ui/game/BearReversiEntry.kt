package com.xiaoxiong.weather.ui.game

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import com.xiaoxiong.weather.R
import com.xiaoxiong.weather.game.EASTER_EGG_BEAR_REVERSI
import com.xiaoxiong.weather.game.GameEntry

/** 小熊黑白棋（口令 [EASTER_EGG_BEAR_REVERSI]）：8×8 奥赛罗，人机对弈。 */
object BearReversiEntry : GameEntry {
    override val password: String = EASTER_EGG_BEAR_REVERSI

    @get:StringRes
    override val titleRes: Int = R.string.reversi_title

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
        BearReversiScreen(
            sceneCode = sceneCode,
            onExit = onExit,
            cityName = cityName,
        )
    }
}

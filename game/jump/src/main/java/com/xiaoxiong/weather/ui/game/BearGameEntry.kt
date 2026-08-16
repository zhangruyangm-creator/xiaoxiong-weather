package com.xiaoxiong.weather.ui.game

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import com.xiaoxiong.weather.R
import com.xiaoxiong.weather.game.EASTER_EGG_BEAR_GAME
import com.xiaoxiong.weather.game.GameEntry

/** 小熊跳跳（截图口令 [EASTER_EGG_BEAR_GAME]）。 */
object BearGameEntry : GameEntry {
    override val password: String = EASTER_EGG_BEAR_GAME

    @get:StringRes
    override val titleRes: Int = R.string.bear_game_title

    @Composable
    override fun Content(
        sceneCode: Int?,
        isDay: Boolean?,
        cityName: String?,
        onExit: () -> Unit,
        @Suppress("UNUSED_PARAMETER") onOpenGame: ((password: String) -> Unit)?,
        @Suppress("UNUSED_PARAMETER") otherGames: List<GameEntry>,
    ) {
        BearGameScreen(
            sceneCode = sceneCode,
            isDay = isDay,
            onExit = onExit,
            cityName = cityName,
        )
    }
}

package com.xiaoxiong.weather.ui.game

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import com.xiaoxiong.weather.R
import com.xiaoxiong.weather.game.EASTER_EGG_BEAR_TILES
import com.xiaoxiong.weather.game.GameEntry

/** 小熊方块（口令 [EASTER_EGG_BEAR_TILES]）：别踩白块儿 + 小熊块 / 双连点块。 */
object BearRainEntry : GameEntry {
    override val password: String = EASTER_EGG_BEAR_TILES

    @get:StringRes
    override val titleRes: Int = R.string.rain_title

    @Composable
    override fun Content(
        sceneCode: Int?,
        isDay: Boolean?,
        cityName: String?,
        onExit: () -> Unit,
        @Suppress("UNUSED_PARAMETER") onOpenGame: ((password: String) -> Unit)?,
        @Suppress("UNUSED_PARAMETER") onOpenBearAlbum: (() -> Unit)?,
        @Suppress("UNUSED_PARAMETER") bearHomeResumeRoom: String?,
        @Suppress("UNUSED_PARAMETER") onBearHomeResumeRoomConsumed: (() -> Unit)?,
        @Suppress("UNUSED_PARAMETER") otherGames: List<GameEntry>,
    ) {
        BearRainScreen(
            sceneCode = sceneCode,
            onExit = onExit,
            cityName = cityName,
        )
    }
}

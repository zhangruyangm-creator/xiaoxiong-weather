package com.xiaoxiong.weather.ui.game

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import com.xiaoxiong.weather.R
import com.xiaoxiong.weather.game.EASTER_EGG_BEAR_FARM
import com.xiaoxiong.weather.game.GameEntry

/** 小熊农场（截图口令 [EASTER_EGG_BEAR_FARM]）。 */
object BearFarmEntry : GameEntry {
    override val password: String = EASTER_EGG_BEAR_FARM

    @get:StringRes
    override val titleRes: Int = R.string.bear_farm_title

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
        BearFarmScreen(
            sceneCode = sceneCode,
            isDay = isDay,
            onExit = onExit,
        )
    }
}

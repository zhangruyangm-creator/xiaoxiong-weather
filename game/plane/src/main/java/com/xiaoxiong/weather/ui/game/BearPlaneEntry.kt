package com.xiaoxiong.weather.ui.game

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import com.xiaoxiong.weather.R
import com.xiaoxiong.weather.game.EASTER_EGG_BEAR_PLANE
import com.xiaoxiong.weather.game.GameEntry

/** 小熊战机（口令 [EASTER_EGG_BEAR_PLANE]）。 */
object BearPlaneEntry : GameEntry {
    override val password: String = EASTER_EGG_BEAR_PLANE

    @get:StringRes
    override val titleRes: Int = R.string.bear_plane_title

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
        BearPlaneScreen(
            sceneCode = sceneCode,
            isDay = isDay,
            onExit = onExit,
            cityName = cityName,
        )
    }
}

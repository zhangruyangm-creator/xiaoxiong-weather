package com.xiaoxiong.weather.ui.game

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import com.xiaoxiong.weather.R
import com.xiaoxiong.weather.game.EASTER_EGG_BEAR_HOME
import com.xiaoxiong.weather.game.GameEntry

/** 小熊的家（截图口令 [EASTER_EGG_BEAR_HOME]）。
 *  游乐间入口来自装配方注入的 [otherGames]（随 `-Pgames` 裁剪自动增减），
 *  点击后经 [onOpenGame] 切换其它游戏；卧室菜单经 [onOpenBearAlbum] 打开全屏相册。 */
object BearHomeEntry : GameEntry {
    override val password: String = EASTER_EGG_BEAR_HOME

    @get:StringRes
    override val titleRes: Int = R.string.bear_home_title

    @Composable
    override fun Content(
        sceneCode: Int?,
        isDay: Boolean?,
        cityName: String?,
        onExit: () -> Unit,
        onOpenGame: ((password: String) -> Unit)?,
        onOpenBearAlbum: (() -> Unit)?,
        bearHomeResumeRoom: String?,
        onBearHomeResumeRoomConsumed: (() -> Unit)?,
        otherGames: List<GameEntry>,
    ) {
        BearHomeScreen(
            sceneCode = sceneCode,
            isDay = isDay,
            onExit = onExit,
            otherGames = otherGames,
            onOpenGame = { game ->
                onOpenGame?.invoke(game.password)
            },
            onOpenBearAlbum = { onOpenBearAlbum?.invoke() },
            bearHomeResumeRoom = bearHomeResumeRoom,
            onBearHomeResumeRoomConsumed = { onBearHomeResumeRoomConsumed?.invoke() },
        )
    }
}

package com.xiaoxiong.weather.ui.game

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import com.xiaoxiong.weather.R
import com.xiaoxiong.weather.game.EASTER_EGG_BEAR_HOME
import com.xiaoxiong.weather.game.GameEntry

/** 小熊的家（截图口令 [EASTER_EGG_BEAR_HOME]）。
 *  枢纽内的游乐间入口来自装配方注入的 [otherGames]（随 `-Pgames` 裁剪自动增减），
 *  点击后经 [onOpenGame] 按口令切换其它游戏，由装配方统一路由。 */
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
        )
    }
}

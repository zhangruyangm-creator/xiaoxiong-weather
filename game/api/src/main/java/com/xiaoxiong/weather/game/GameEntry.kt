package com.xiaoxiong.weather.game

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable

/**
 * 游戏入口契约：口令 → 游戏内容。
 *
 * - [password]：搜索框口令（如「#小熊跳跳」），由游戏各自定义并注册。
 * - [titleRes]：游戏标题（Home 枢纽、排行榜等共用）。
 * - [Content]：游戏主内容；[sceneCode]/[isDay]/[cityName] 是天气联动的运行期值
 *   （见 MODULARIZATION.md「天气—游戏联动契约」），游戏侧只消费值、不 import feature 类型。
 * - [otherGames]：装配方注入的其它已启用游戏（用于「小熊的家」枢纽动态展示入口；
 *   随 `-Pgames` 裁剪自动增减，游戏侧不得自行依赖具体游戏模块）。
 * - [onOpenBearAlbum]：非游戏彩蛋「小熊相册」，仅枢纽卧室等由装配方路由到天气页全屏相册。
 * - [bearHomeResumeRoom] / [onBearHomeResumeRoomConsumed]：从相册返回枢纽时恢复房间（见 [BEAR_HOME_ROOM_BEDROOM]）。
 */
interface GameEntry {
    val password: String

    @get:StringRes
    val titleRes: Int

    @Composable
    fun Content(
        sceneCode: Int?,
        isDay: Boolean?,
        cityName: String?,
        onExit: () -> Unit,
        onOpenGame: ((password: String) -> Unit)? = null,
        onOpenBearAlbum: (() -> Unit)? = null,
        bearHomeResumeRoom: String? = null,
        onBearHomeResumeRoomConsumed: (() -> Unit)? = null,
        otherGames: List<GameEntry> = emptyList(),
    )
}

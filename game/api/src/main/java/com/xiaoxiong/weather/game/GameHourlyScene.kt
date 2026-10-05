package com.xiaoxiong.weather.game

import androidx.compose.runtime.compositionLocalOf

/**
 * 小时天气切片：给需要按时间追赶的游戏（农场离线 tick）用。
 * [epochMs] 为该小时起点的 epoch 毫秒；[sceneCode]/[isDay] 与 [GameEntry.Content] 同口径。
 */
data class GameHourlyScene(
    val epochMs: Long,
    val sceneCode: Int?,
    val isDay: Boolean?,
)

/** 装配方注入的过去+未来小时序列；缺省空列表，游戏回退到当前 [sceneCode]。 */
val LocalGameHourlyForecast = compositionLocalOf { emptyList<GameHourlyScene>() }

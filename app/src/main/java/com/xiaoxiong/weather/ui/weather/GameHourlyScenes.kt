package com.xiaoxiong.weather.ui.weather

import com.xiaoxiong.weather.data.WeatherSnapshot
import com.xiaoxiong.weather.game.GameHourlyScene
import com.xiaoxiong.weather.util.WeatherCodes
import com.xiaoxiong.weather.util.isDaytimeHour
import com.xiaoxiong.weather.util.parseFlexibleDateTime
import java.time.ZoneId

/** 把天气快照的小时序列编成游戏侧切片（过去+未来，按 epoch 排序）。 */
internal fun gameHourlyScenesOf(snapshot: WeatherSnapshot?): List<GameHourlyScene> {
    if (snapshot == null) return emptyList()
    val hourly = snapshot.hourly
    if (hourly.isEmpty()) return emptyList()
    val zone =
        snapshot.timezone?.let { runCatching { ZoneId.of(it) }.getOrNull() }
            ?: ZoneId.systemDefault()
    return hourly
        .mapNotNull { hour ->
            val local = parseFlexibleDateTime(hour.time) ?: return@mapNotNull null
            GameHourlyScene(
                epochMs = local.atZone(zone).toInstant().toEpochMilli(),
                sceneCode = WeatherCodes.displayCode(hour.weatherCode, hour.precipitationMm),
                isDay = isDaytimeHour(local.hour),
            )
        }.sortedBy { it.epochMs }
}

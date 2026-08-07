package com.xiaoxiong.weather.ui.weather

import com.xiaoxiong.weather.data.DailyWeather

/**
 * 七日温度条在整周刻度上的起止比例（0–1）。
 * [start] 对应当日最低温在周最低–周最高轴上的位置，[end] 对应最高温。
 */
data class DailyTempBarRange(
    val start: Float,
    val end: Float,
) {
    val width: Float get() = (end - start).coerceAtLeast(0f)
}

fun weekTemperatureBounds(days: List<DailyWeather>): Pair<Double, Double>? {
    val mins = days.mapNotNull { it.tempMinC }
    val maxs = days.mapNotNull { it.tempMaxC }
    if (mins.isEmpty() || maxs.isEmpty()) return null
    val weekMin = mins.minOrNull() ?: return null
    val weekMax = maxs.maxOrNull() ?: return null
    return weekMin to weekMax.coerceAtLeast(weekMin + 1.0)
}

/**
 * @param minVisibleWidth 色块最小宽度比例，避免温差极小时看不见
 */
fun weekAlignedTempBarRange(
    dayMinC: Double?,
    dayMaxC: Double?,
    weekMinC: Double,
    weekMaxC: Double,
    minVisibleWidth: Float = 0.06f,
): DailyTempBarRange {
    val span = (weekMaxC - weekMinC).coerceAtLeast(1.0)
    val dayMin = dayMinC ?: weekMinC
    val dayMax = (dayMaxC ?: dayMin).coerceAtLeast(dayMin)
    var start = ((dayMin - weekMinC) / span).toFloat().coerceIn(0f, 1f)
    var end = ((dayMax - weekMinC) / span).toFloat().coerceIn(0f, 1f)
    if (end - start < minVisibleWidth) {
        val mid = (start + end) / 2f
        start = (mid - minVisibleWidth / 2f).coerceAtLeast(0f)
        end = (start + minVisibleWidth).coerceAtMost(1f)
        start = (end - minVisibleWidth).coerceAtLeast(0f)
    }
    return DailyTempBarRange(start = start, end = end)
}

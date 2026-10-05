package com.xiaoxiong.weather.ui.weather

import com.xiaoxiong.weather.data.DailyWeather
import java.time.LocalDate

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
    // 单趟扫描，避免两次 mapNotNull 的临时列表分配
    var weekMin: Double? = null
    var weekMax: Double? = null
    for (day in days) {
        val low = day.tempMinC
        if (low != null && (weekMin == null || low < weekMin)) weekMin = low
        val high = day.tempMaxC
        if (high != null && (weekMax == null || high > weekMax)) weekMax = high
    }
    val min = weekMin ?: return null
    val max = weekMax ?: return null
    return min to max.coerceAtLeast(min + 1.0)
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

/**
 * 七日页「今天」标签：用日历日比对，而不是列表下标 0。
 * 缓存裁剪失败或时区跨天时，daily[0] 可能仍是昨天/明天。
 */
fun isDailyCalendarToday(
    date: String,
    today: LocalDate,
): Boolean {
    val parsed = runCatching { LocalDate.parse(date) }.getOrNull() ?: return false
    return parsed == today
}

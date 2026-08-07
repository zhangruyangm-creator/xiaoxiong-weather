package com.xiaoxiong.weather.util

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.time.temporal.ChronoUnit
import kotlin.math.abs

fun hourKey(isoTime: String): String = isoTime.take(13) // yyyy-MM-ddTHH

fun isSameHour(a: String, b: String): Boolean = hourKey(a) == hourKey(b)

fun indexOfCurrentHour(times: List<String>, currentTime: String): Int {
    if (times.isEmpty()) return -1

    val key = hourKey(currentTime)
    val exact = times.indexOfFirst { hourKey(it) == key }
    if (exact >= 0) return exact

    val current = parseFlexibleDateTime(currentTime) ?: return 0
    return times.indices.minByOrNull { index ->
        val t = parseFlexibleDateTime(times[index]) ?: return@minByOrNull Long.MAX_VALUE
        abs(ChronoUnit.MINUTES.between(t, current))
    } ?: 0
}

/** 从当前小时起向未来取 [count] 条（含当前），用于降雨提醒/穿衣建议窗口。 */
fun <T> upcomingFromCurrent(
    items: List<T>,
    timeOf: (T) -> String,
    currentTime: String,
    count: Int = 12
): List<T> {
    if (items.isEmpty()) return emptyList()
    val idx = indexOfCurrentHour(items.map(timeOf), currentTime).coerceAtLeast(0)
    return items.drop(idx).take(count)
}

/** 取截止当前小时（含）的最近 [count] 条，用于雪后路滑等回顾窗口。 */
fun <T> recentHoursUpToCurrent(
    items: List<T>,
    timeOf: (T) -> String,
    currentTime: String,
    count: Int = 12
): List<T> {
    if (items.isEmpty() || count <= 0) return emptyList()
    val idx = indexOfCurrentHour(items.map(timeOf), currentTime).coerceAtLeast(0)
    val from = (idx - count + 1).coerceAtLeast(0)
    return items.subList(from, idx + 1)
}

/**
 * 今日从 0 点到当前小时（含）各整点小时桶累计。
 * 与图表同一套 hourly 标准（当前小时为该钟点合计，可能仍含预报成分）。
 */
fun <T> precipFallenTodayMm(
    items: List<T>,
    timeOf: (T) -> String,
    precipOf: (T) -> Double?,
    currentTime: String
): Double {
    if (items.isEmpty()) return 0.0
    val current = parseFlexibleDateTime(currentTime) ?: return 0.0
    val day = current.toLocalDate()
    val idx = indexOfCurrentHour(items.map(timeOf), currentTime)
    if (idx < 0) return 0.0
    return items.take(idx + 1).sumOf { item ->
        val t = parseFlexibleDateTime(timeOf(item)) ?: return@sumOf 0.0
        if (t.toLocalDate() == day) precipOf(item) ?: 0.0 else 0.0
    }
}

fun hoursBetween(fromIso: String, toIso: String): Long {
    val from = parseFlexibleDateTime(fromIso) ?: return Long.MAX_VALUE
    val to = parseFlexibleDateTime(toIso) ?: return Long.MAX_VALUE
    return ChronoUnit.HOURS.between(from, to).coerceAtLeast(0)
}

fun parseFlexibleDateTime(value: String): LocalDateTime? {
    val candidates = listOf(
        DateTimeFormatter.ISO_LOCAL_DATE_TIME,
        DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm"),
        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"),
        DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss")
    )
    for (formatter in candidates) {
        try {
            return LocalDateTime.parse(value, formatter)
        } catch (_: DateTimeParseException) {
            // try next
        }
    }
    // 仅日期时补零点
    return try {
        LocalDateTime.parse("${value.take(10)}T00:00")
    } catch (_: Exception) {
        null
    }
}

/**
 * 小时明细用：今天/明天/具体日期 + 时刻（相对 [referenceIsoTime] 的日期）。
 * [otherDateLabel] 接收月、日，例如「8月5日」或「8/5」。
 */
fun formatHourDetailLabel(
    isoTime: String,
    referenceIsoTime: String,
    todayLabel: String,
    tomorrowLabel: String,
    otherDateLabel: (month: Int, dayOfMonth: Int) -> String
): String {
    val dt = parseFlexibleDateTime(isoTime)
        ?: return formatHourLabel(isoTime)
    val hour = "%02d:%02d".format(dt.hour, dt.minute)
    val refDate = parseFlexibleDateTime(referenceIsoTime)?.toLocalDate()
        ?: LocalDate.now()
    val dayLabel = when (dt.toLocalDate()) {
        refDate -> todayLabel
        refDate.plusDays(1) -> tomorrowLabel
        else -> otherDateLabel(dt.monthValue, dt.dayOfMonth)
    }
    return "$dayLabel $hour"
}

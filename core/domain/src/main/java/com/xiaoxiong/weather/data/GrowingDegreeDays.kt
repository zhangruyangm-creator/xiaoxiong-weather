package com.xiaoxiong.weather.data

import java.time.LocalDate
import java.time.MonthDay
import kotlin.math.roundToInt

/**
 * 积温（Growing Degree Days）物候推算：用「今年已累计积温」对比「常年同期累计积温」，
 * 把温差换算为物候提前/推迟的天数。物候学中开花等物候事件主要受有效积温驱动。
 *
 * 约定：积温越高 → 物候越早；返回偏移为「正 = 推迟，负 = 提前」，与 [SeasonTipGenerator.reliefShiftDays] 同方向。
 */
object GrowingDegreeDays {
    /** 有效积温基准温度：多数温带植物以 ≥5°C 计有效积温。 */
    private const val DEFAULT_BASE_TEMP_C = 5.0

    /** 数据太少时（< 此天数）没有足够依据推算偏移，返回 null。 */
    const val MIN_DAYS = 30

    /**
     * 偏移天数上限（±30 天）：近期积温增速偏低时 diff / rate 会被放大到 ±70 天，
     * 远超真实物候年际波动（通常 ±2 周），钳制避免时令卡给出耸动结论。
     */
    const val MAX_SHIFT_DAYS = 30

    /** 单日有效积温 = max(0, 日均温 - 基准)。 */
    fun dailyGdd(
        meanTempC: Double,
        baseTempC: Double = DEFAULT_BASE_TEMP_C,
    ): Double = (meanTempC - baseTempC).coerceAtLeast(0.0)

    /** 逐日累计积温曲线（长度与输入一致）。 */
    fun cumulativeGdd(
        dailyTemps: List<Double>,
        baseTempC: Double = DEFAULT_BASE_TEMP_C,
    ): List<Double> {
        var acc = 0.0
        return dailyTemps.map { temp ->
            acc += dailyGdd(temp, baseTempC)
            acc
        }
    }

    /**
     * 物候偏移天数：正 = 今年更冷 / 推迟，负 = 今年更暖 / 提前。
     *
     * 算法：以「常年今日累计积温 − 今年今日累计积温」为差值，除以近期（近一周）积温增速，
     * 把积温差换算成天数。数据不足或近期无有效积温增长时返回 null。
     */
    fun shiftDays(
        thisYearDailyTemps: List<Double>,
        normalDailyTemps: List<Double>,
        baseTempC: Double = DEFAULT_BASE_TEMP_C,
    ): Int? {
        val n = minOf(thisYearDailyTemps.size, normalDailyTemps.size)
        if (n < MIN_DAYS) return null

        val thisYear = cumulativeGdd(thisYearDailyTemps.take(n), baseTempC)
        val normal = cumulativeGdd(normalDailyTemps.take(n), baseTempC)

        val diff = normal.last() - thisYear.last()
        val recentRate = (thisYear.last() - thisYear[n - 8]) / 7.0
        if (recentRate <= 0.05) return null
        // 增速偏低时换算结果会被放大，钳制到 ±MAX_SHIFT_DAYS 内
        return (diff / recentRate).roundToInt().coerceIn(-MAX_SHIFT_DAYS, MAX_SHIFT_DAYS)
    }

    /**
     * 把多年逐日温度按「月-日」对齐求平均（2/29 忽略，并入 2/28 前统计），
     * 得到「常年」温度曲线。返回以 [MonthDay] 为键的多年日均温。
     */
    fun averageByDayOfYear(records: List<Pair<LocalDate, Double?>>): Map<MonthDay, Double> {
        val sums = linkedMapOf<MonthDay, Double>()
        val counts = linkedMapOf<MonthDay, Int>()
        for ((date, temp) in records) {
            if (temp == null) continue
            val key = MonthDay.from(date)
            // 2/29 并入 2/28，避免闰年多一天导致常年曲线断裂
            val bucket = if (key.monthValue == 2 && key.dayOfMonth == 29) MonthDay.of(2, 28) else key
            sums[bucket] = sums.getOrDefault(bucket, 0.0) + temp
            counts[bucket] = counts.getOrDefault(bucket, 0) + 1
        }
        return sums.mapValues { (bucket, sum) -> sum / counts.getValue(bucket) }
    }

    /** 取 [onDate] 之前（含当日）每天的常年温度序列。 */
    fun normalTempsUntil(
        normalByDay: Map<MonthDay, Double>,
        onDate: LocalDate,
        fromDate: LocalDate,
    ): List<Double> {
        var current = fromDate
        val result = mutableListOf<Double>()
        while (!current.isAfter(onDate)) {
            tempOn(normalByDay, current)?.let { result += it }
            current = current.plusDays(1)
        }
        return result
    }

    /**
     * 按日历日对齐「今年」与「常年」日均温。
     *
     * 缺测日、乱序、闰日都不能按下标 zip：否则后面每一天都会对错常年曲线，
     * 积温差被放大成完全不实的提前/推迟。双边都缺的日子跳过。
     */
    fun alignedDailyTemps(
        thisYearRecords: List<Pair<LocalDate, Double>>,
        normalByDay: Map<MonthDay, Double>,
        fromDate: LocalDate,
        throughDate: LocalDate,
    ): Pair<List<Double>, List<Double>> {
        if (throughDate.isBefore(fromDate)) return emptyList<Double>() to emptyList()
        val thisByDay = LinkedHashMap<MonthDay, Double>(thisYearRecords.size)
        for ((date, temp) in thisYearRecords) {
            thisByDay[MonthDay.from(date)] = temp
        }
        val thisTemps = mutableListOf<Double>()
        val normalTemps = mutableListOf<Double>()
        var current = fromDate
        while (!current.isAfter(throughDate)) {
            val thisTemp = tempOn(thisByDay, current)
            val normalTemp = tempOn(normalByDay, current)
            if (thisTemp != null && normalTemp != null) {
                thisTemps += thisTemp
                normalTemps += normalTemp
            }
            current = current.plusDays(1)
        }
        return thisTemps to normalTemps
    }

    private fun tempOn(
        byDay: Map<MonthDay, Double>,
        date: LocalDate,
    ): Double? {
        val key = MonthDay.from(date)
        return byDay[key]
            ?: if (key.monthValue == 2 && key.dayOfMonth == 29) {
                byDay[MonthDay.of(2, 28)]
            } else {
                null
            }
    }
}

package com.xiaoxiong.weather.ui.weather

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.temporal.ChronoUnit

/**
 * 日出–日落弧上的进度。
 * [progress]：白天沿上弧 0（日出）→ 1（日落）；夜晚沿下弧 0（日落）→ 1（日出）。
 */
data class SunPathPhase(
    val isDay: Boolean,
    val progress: Float,
)

fun isWeekend(date: LocalDate): Boolean = date.dayOfWeek == DayOfWeek.SATURDAY || date.dayOfWeek == DayOfWeek.SUNDAY

/**
 * 同一天、同一场景索引固定，避免重组时乱跳。
 *
 * 哈希乘子刻意避开常见池大小（31 等），否则会与 size 形成噪声互消、
 * 导致某些池（如 weekend 旅行插画）日期严重重复。1000003 为质数乘子，
 * 对各尺寸均匀分布且无周期退火。
 */
fun dailyVariantIndex(
    date: LocalDate,
    salt: String,
    size: Int,
): Int {
    if (size <= 1) return 0
    val h = date.toEpochDay() * 1_000_003L + salt.hashCode()
    return ((h % size) + size).toInt() % size
}

fun sunPathPhase(
    now: LocalDateTime,
    sunrise: LocalDateTime,
    sunset: LocalDateTime,
): SunPathPhase? {
    if (!sunset.isAfter(sunrise)) return null
    val dayTotal = ChronoUnit.SECONDS.between(sunrise, sunset).coerceAtLeast(1)
    if (!now.isBefore(sunrise) && !now.isAfter(sunset)) {
        val elapsed = ChronoUnit.SECONDS.between(sunrise, now).coerceIn(0, dayTotal)
        return SunPathPhase(isDay = true, progress = elapsed.toFloat() / dayTotal)
    }
    val nightStart = if (!now.isBefore(sunset)) sunset else sunset.minusDays(1)
    val nightEnd = if (now.isBefore(sunrise)) sunrise else sunrise.plusDays(1)
    val nightTotal = ChronoUnit.SECONDS.between(nightStart, nightEnd).coerceAtLeast(1)
    val elapsed = ChronoUnit.SECONDS.between(nightStart, now).coerceIn(0, nightTotal)
    return SunPathPhase(isDay = false, progress = elapsed.toFloat() / nightTotal)
}

/**
 * 小熊生活片刻按**钟点**划分（午饭/晚饭不跟着日照进度跑，避免夏天 10 点就吃午饭）。
 * [phase] 只用来区分白天/夜晚；白天与夜晚各自用本地时间切段。
 */
fun sunPathBearMoment(
    phase: SunPathPhase?,
    date: LocalDate = LocalDate.now(),
    time: LocalTime = LocalTime.now(),
    sunrise: LocalTime? = null,
): SunPathBearMoment {
    if (phase == null) {
        return if (isWeekend(date)) SunPathBearMoment.WEEKEND_MORNING else SunPathBearMoment.MORNING_EXERCISE
    }
    val minutes = time.hour * 60 + time.minute
    return if (phase.isDay) {
        dayBearMoment(date, minutes)
    } else {
        nightBearMoment(date, minutes, phase.progress, sunrise)
    }
}

/**
 * 白天（工作日）：晨练(日出~07:15；若 07:15 尚未日出则跳过直接吃早餐)
 * → 早餐(07:15–08:15) → 上班通勤(08:15–09:00) → 上午忙
 * → 午饭(11:30–12:30) → 下午忙 → 下午茶(15:00–15:45) → 下午忙(至18:00)
 * → 回家通勤(18:00–18:45) → 傍晚锻炼(18:45–19:00) → 晚饭(19:00–20:00)。
 * 周末：上午出门(日出~09:00) → 旅行(09:00–11:30 与 12:30–15:00) → 午饭
 * → 下午玩乐(15:00–18:00) → 归途(18:00 起)；不拆通勤/锻炼/早餐。
 */
private fun dayBearMoment(
    date: LocalDate,
    minutes: Int,
): SunPathBearMoment {
    val weekend = isWeekend(date)
    return when {
        minutes < 7 * 60 + 15 ->
            if (weekend) SunPathBearMoment.WEEKEND_MORNING else SunPathBearMoment.MORNING_EXERCISE
        minutes < 8 * 60 + 15 ->
            if (weekend) SunPathBearMoment.WEEKEND_MORNING else SunPathBearMoment.BREAKFAST
        minutes < 9 * 60 ->
            if (weekend) SunPathBearMoment.WEEKEND_MORNING else SunPathBearMoment.MORNING_OUT
        minutes < 11 * 60 + 30 ->
            if (weekend) SunPathBearMoment.WEEKEND_EXPLORE else SunPathBearMoment.WORK_MORNING
        minutes < 12 * 60 + 30 ->
            SunPathBearMoment.LUNCH
        minutes < 15 * 60 ->
            if (weekend) SunPathBearMoment.WEEKEND_EXPLORE else SunPathBearMoment.WORK_AFTERNOON
        !weekend && minutes in (15 * 60) until (15 * 60 + 45) ->
            SunPathBearMoment.AFTERNOON_TEA
        minutes < 18 * 60 ->
            if (weekend) SunPathBearMoment.WEEKEND_AFTERNOON else SunPathBearMoment.WORK_AFTERNOON
        minutes < 18 * 60 + 45 ->
            if (weekend) SunPathBearMoment.WEEKEND_RETURN else SunPathBearMoment.HEAD_HOME
        minutes < 19 * 60 ->
            if (weekend) SunPathBearMoment.WEEKEND_RETURN else SunPathBearMoment.EVENING_EXERCISE
        else ->
            if (weekend) SunPathBearMoment.WEEKEND_RETURN else SunPathBearMoment.DINNER
    }
}

/**
 * 夜晚：工作日若日落早，用黄昏补上回家通勤(~18:45前)与傍晚锻炼(~19:00前)，
 * 再晚饭(19:00–20:00) → 歇一会儿(~23:00前) → 入睡准备(23:00–24:00) → 睡觉 → 临近天亮（日出前 30 分钟）。
 * 夏天日落晚时，通勤/锻炼已在白天完成，入夜会较快落到晚饭。
 */
private fun nightBearMoment(
    date: LocalDate,
    minutes: Int,
    nightProgress: Float = 0f,
    sunrise: LocalTime? = null,
): SunPathBearMoment {
    val weekend = isWeekend(date)
    // 快天亮：日出前 30 分钟 ~ 日出。有 sunrise 时精确计算，否则按 progress 兜底。
    val isAlmostDawn =
        if (sunrise != null) {
            val sunriseMin = sunrise.hour * 60 + sunrise.minute
            val dawnStart = sunriseMin - 30
            minutes in dawnStart until sunriseMin
        } else {
            // progress ≈ 1.0 时即日出；30min / 典型夜长 12h ≈ 0.042
            nightProgress > 0.96f
        }
    return when {
        isAlmostDawn -> SunPathBearMoment.ALMOST_DAWN
        minutes < 5 * 60 -> SunPathBearMoment.SLEEP
        minutes >= 23 * 60 -> SunPathBearMoment.BEDTIME_PREP
        !weekend && minutes < 18 * 60 + 45 -> SunPathBearMoment.HEAD_HOME
        !weekend && minutes < 19 * 60 -> SunPathBearMoment.EVENING_EXERCISE
        minutes < 20 * 60 -> SunPathBearMoment.DINNER
        else -> SunPathBearMoment.EVENING
    }
}

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
 * 实现说明：原版用 `(epochDay * 1_000_003 + salt.hashCode()) % size`，对
 * size=60/31/8 等池都接近完美均匀；但当 size 与乘子互质性弱时，连续日期
 * 模 size 的步长可能很小，用户感觉"周末早晨总是同一张"。
 *
 * 改进两点：
 * 1. 乘子改为 1_000_033（质数，与常见池大小都互质且离 1_000_003 足够远），
 *    让相邻日期的步长更分散。
 * 2. salt.hashCode() × 31 作为加扰，让不同 salt 序列与日期步长独立，
 *    避免不同 moment 在同一天"撞上"同一索引偏移。
 *
 * 仍是确定性映射：同一天 + 同一 salt + 同一 size 始终落在同一索引，
 * 避免 App 重组/Widget 刷新时插画跳变。
 */
fun dailyVariantIndex(
    date: LocalDate,
    salt: String,
    size: Int,
): Int {
    if (size <= 1) return 0
    val h = date.toEpochDay() * 1_000_033L + salt.hashCode() * 31L
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
    val minutes = time.hour * 60 + time.minute
    // 无日出日落数据时按本地钟点近似昼夜（6:00–18:00 为白天），
    // 否则深夜会被当成早晨，显示晨练/周末早晨等不符合作息的画面
    val isDay = phase?.isDay ?: (minutes in 6 * 60 until 18 * 60)
    return if (isDay) {
        dayBearMoment(date, minutes, sunrise)
    } else {
        nightBearMoment(date, minutes, phase?.progress ?: 0f, sunrise)
    }
}

/**
 * 白天（工作日）：晨练(日出起最多 45 分钟，且不晚于 06:30)
 * → 晨间逛逛(至 07:15；复用探索插画池) → 早餐(07:15–08:15)
 * → 上班通勤(08:15–09:00) → 上午忙
 * → 午饭(11:30–12:30) → 下午忙 → 下午茶(15:00–15:45) → 下午忙(至18:00)
 * → 回家通勤(18:00–18:45) → 傍晚锻炼(18:45–19:00) → 晚饭(19:00–20:00)。
 * 周末：上午出门(日出~09:00) → 旅行(09:00–11:30 与 12:30–15:00) → 午饭
 * → 下午玩乐(15:00–18:00) → 归途(18:00 起)；不拆通勤/锻炼/早餐。
 */
private fun dayBearMoment(
    date: LocalDate,
    minutes: Int,
    sunrise: LocalTime? = null,
): SunPathBearMoment {
    val weekend = isWeekend(date)
    // 无 sunrise 时按晨练结束点封顶，与小屋作息近似一致。
    val exerciseEnd =
        if (weekend) {
            BearRoutineTimes.MORNING_STROLL_END_MINUTE
        } else {
            val cap = BearRoutineTimes.MORNING_EXERCISE_END_MINUTE
            if (sunrise != null) {
                val sunriseMin = sunrise.hour * 60 + sunrise.minute
                minOf(sunriseMin + 45, cap)
            } else {
                cap
            }
        }
    return when {
        // 凌晨段：日界（5:00）到日出前（默认取 MORNING_STROLL_END_MINUTE 作为兜底），
        // 周末也走 ALMOST_DAWN，与卧室作息时间表语义对齐，避免 5–7 点就显示「出门玩」
        minutes < exerciseEnd ->
            if (isWeekend(date)) SunPathBearMoment.ALMOST_DAWN else SunPathBearMoment.MORNING_EXERCISE
        minutes < BearRoutineTimes.MORNING_STROLL_END_MINUTE ->
            if (isWeekend(date)) SunPathBearMoment.ALMOST_DAWN else SunPathBearMoment.MORNING_STROLL
        // 7:15–8:15 工作日是早餐；周末继续「周末早晨/准备出门」
        minutes < BearRoutineTimes.BREAKFAST_END_MINUTE ->
            if (isWeekend(date)) SunPathBearMoment.WEEKEND_MORNING else SunPathBearMoment.BREAKFAST
        minutes < BearRoutineTimes.MORNING_OUT_END_MINUTE ->
            if (isWeekend(date)) SunPathBearMoment.WEEKEND_MORNING else SunPathBearMoment.MORNING_OUT
        // 周末 9:00–9:45 加一段简短的早午餐，与卧室作息时间表对齐
        weekend && minutes < BearRoutineTimes.WEEKEND_BREAKFAST_END_MINUTE ->
            SunPathBearMoment.BREAKFAST
        // 周末上午旅行：9:45–11:30；工作日是上午工作
        minutes < BearRoutineTimes.LUNCH_START_MINUTE ->
            if (weekend) SunPathBearMoment.WEEKEND_EXPLORE else SunPathBearMoment.WORK_MORNING
        minutes < BearRoutineTimes.LUNCH_END_MINUTE ->
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
 * 再晚饭(19:00–20:00) → 歇一会儿(~23:00前) → 入睡准备(23:00–24:00) → 睡觉
 * → 日界后至日出前为临近天亮（避免「分钟小于 18:45」把凌晨误判成回家通勤/晚饭）。
 * 夏天日落晚时，通勤/锻炼已在白天完成，入夜会较快落到晚饭。
 */
private fun nightBearMoment(
    date: LocalDate,
    minutes: Int,
    nightProgress: Float = 0f,
    sunrise: LocalTime? = null,
): SunPathBearMoment {
    val weekend = isWeekend(date)
    return when {
        minutes < BearRoutineTimes.DAY_BOUNDARY_MINUTE -> SunPathBearMoment.SLEEP
        // 日界(5:00)～日出：黎明。须放在黄昏通勤分支之前。
        isPreSunriseDawn(minutes, sunrise, nightProgress) -> SunPathBearMoment.ALMOST_DAWN
        minutes >= 23 * 60 -> SunPathBearMoment.BEDTIME_PREP
        !weekend && minutes < 18 * 60 + 45 -> SunPathBearMoment.HEAD_HOME
        !weekend && minutes < 19 * 60 -> SunPathBearMoment.EVENING_EXERCISE
        minutes < 20 * 60 -> SunPathBearMoment.DINNER
        else -> SunPathBearMoment.EVENING
    }
}

/**
 * 日出前的凌晨段是否算「临近天亮」。
 * 有 sunrise：[日界, 日出)；无 sunrise：progress 末段，或日界～晨段结束兜底。
 */
private fun isPreSunriseDawn(
    minutes: Int,
    sunrise: LocalTime?,
    nightProgress: Float,
): Boolean {
    if (sunrise != null) {
        val sunriseMin = sunrise.hour * 60 + sunrise.minute
        return minutes >= BearRoutineTimes.DAY_BOUNDARY_MINUTE && minutes < sunriseMin
    }
    if (nightProgress > 0.96f) return true
    return minutes >= BearRoutineTimes.DAY_BOUNDARY_MINUTE &&
        minutes < BearRoutineTimes.MORNING_STROLL_END_MINUTE
}

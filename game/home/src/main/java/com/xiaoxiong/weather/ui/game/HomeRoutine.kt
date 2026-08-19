package com.xiaoxiong.weather.ui.game

import com.xiaoxiong.weather.R
import com.xiaoxiong.weather.ui.weather.SunPathBearMoment
import com.xiaoxiong.weather.ui.weather.isWeekend
import java.time.LocalDate
import java.time.LocalTime

/** 卧室作息时间线条目（分钟区间左闭右开）。 */
data class HomeRoutineSlot(
    val moment: SunPathBearMoment,
    val startMinute: Int,
    val endMinute: Int,
)

enum class HomeRoutineStatus {
    DONE,
    NOW,
    SOON,
}

fun homeRoutineSlots(date: LocalDate): List<HomeRoutineSlot> =
    if (isWeekend(date)) {
        listOf(
            HomeRoutineSlot(SunPathBearMoment.ALMOST_DAWN, 5 * 60, 7 * 60 + 15),
            HomeRoutineSlot(SunPathBearMoment.WEEKEND_MORNING, 7 * 60 + 15, 9 * 60),
            HomeRoutineSlot(SunPathBearMoment.WEEKEND_EXPLORE, 9 * 60, 11 * 60 + 30),
            HomeRoutineSlot(SunPathBearMoment.LUNCH, 11 * 60 + 30, 12 * 60 + 30),
            HomeRoutineSlot(SunPathBearMoment.WEEKEND_AFTERNOON, 12 * 60 + 30, 18 * 60),
            HomeRoutineSlot(SunPathBearMoment.WEEKEND_RETURN, 18 * 60, 19 * 60),
            HomeRoutineSlot(SunPathBearMoment.DINNER, 19 * 60, 20 * 60),
            HomeRoutineSlot(SunPathBearMoment.EVENING, 20 * 60, 23 * 60),
            HomeRoutineSlot(SunPathBearMoment.BEDTIME_PREP, 23 * 60, 24 * 60),
            HomeRoutineSlot(SunPathBearMoment.SLEEP, 0, 5 * 60),
        )
    } else {
        listOf(
            HomeRoutineSlot(SunPathBearMoment.ALMOST_DAWN, 5 * 60, 6 * 60),
            HomeRoutineSlot(SunPathBearMoment.MORNING_EXERCISE, 6 * 60, 7 * 60 + 15),
            HomeRoutineSlot(SunPathBearMoment.BREAKFAST, 7 * 60 + 15, 8 * 60 + 15),
            HomeRoutineSlot(SunPathBearMoment.MORNING_OUT, 8 * 60 + 15, 9 * 60),
            HomeRoutineSlot(SunPathBearMoment.WORK_MORNING, 9 * 60, 11 * 60 + 30),
            HomeRoutineSlot(SunPathBearMoment.LUNCH, 11 * 60 + 30, 12 * 60 + 30),
            HomeRoutineSlot(SunPathBearMoment.WORK_AFTERNOON, 12 * 60 + 30, 15 * 60),
            HomeRoutineSlot(SunPathBearMoment.AFTERNOON_TEA, 15 * 60, 15 * 60 + 45),
            HomeRoutineSlot(SunPathBearMoment.WORK_AFTERNOON, 15 * 60 + 45, 18 * 60),
            HomeRoutineSlot(SunPathBearMoment.HEAD_HOME, 18 * 60, 18 * 60 + 45),
            HomeRoutineSlot(SunPathBearMoment.EVENING_EXERCISE, 18 * 60 + 45, 19 * 60),
            HomeRoutineSlot(SunPathBearMoment.DINNER, 19 * 60, 20 * 60),
            HomeRoutineSlot(SunPathBearMoment.EVENING, 20 * 60, 23 * 60),
            HomeRoutineSlot(SunPathBearMoment.BEDTIME_PREP, 23 * 60, 24 * 60),
            HomeRoutineSlot(SunPathBearMoment.SLEEP, 0, 5 * 60),
        )
    }

/**
 * 作息以凌晨 5:00 为日界：在 [00:00, 05:00) 的“凌晨段”仍属于前一天的作息日，
 * 因此取前一天为展示日期，避免把新一天尚未开始的时段误判为已结束（DONE）。
 */
fun homeRoutineDay(
    date: LocalDate,
    now: LocalTime,
): LocalDate {
    val m = now.hour * 60 + now.minute
    return if (m < 5 * 60) date.minusDays(1) else date
}

/**
 * 以凌晨 5:00 为日界，把钟点映射到可比较的时间轴，便于判断已过/未到。
 * SLEEP 的 [0, 5:00) 映射到轴末端。
 */
private fun toHomeTimeline(minuteOfDay: Int): Int {
    val m = minuteOfDay.coerceIn(0, 24 * 60)
    return if (m < 5 * 60) m + 24 * 60 else m
}

fun homeRoutineStatus(
    slot: HomeRoutineSlot,
    now: LocalTime,
): HomeRoutineStatus {
    val m = now.hour * 60 + now.minute
    val axisNow = toHomeTimeline(m)
    val axisStart = toHomeTimeline(slot.startMinute)
    val axisEnd =
        when {
            // 睡觉跨日：把 [0, 5:00) 映射到轴末端；入睡准备等 24:00 前的时段随后判为已过
            slot.startMinute < 5 * 60 && slot.endMinute <= 5 * 60 -> 24 * 60 + 5 * 60
            else -> toHomeTimeline(slot.endMinute)
        }
    return when {
        axisNow in axisStart until axisEnd -> HomeRoutineStatus.NOW
        axisEnd <= axisNow -> HomeRoutineStatus.DONE
        else -> HomeRoutineStatus.SOON
    }
}

fun formatHomeMinuteRange(
    startMinute: Int,
    endMinute: Int,
): String {
    fun fmt(min: Int): String {
        if (min >= 24 * 60) return "24:00"
        val h = (min.coerceAtLeast(0) / 60) % 24
        val m = min.coerceAtLeast(0) % 60
        return "%02d:%02d".format(h, m)
    }
    return "${fmt(startMinute)}–${fmt(endMinute)}"
}

fun homeRoutineStatusLabel(status: HomeRoutineStatus): Int =
    when (status) {
        HomeRoutineStatus.DONE -> R.string.bear_home_routine_done
        HomeRoutineStatus.NOW -> R.string.bear_home_routine_now
        HomeRoutineStatus.SOON -> R.string.bear_home_routine_soon
    }

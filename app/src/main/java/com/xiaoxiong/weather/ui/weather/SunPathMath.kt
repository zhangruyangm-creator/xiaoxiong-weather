package com.xiaoxiong.weather.ui.weather

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import com.xiaoxiong.weather.R
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

/** 轨迹旁小熊生活片刻（专用活动插画）。 */
enum class SunPathBearMoment(
    @param:StringRes val captionRes: Int,
    @param:DrawableRes private vararg val bearVariants: Int,
) {
    /** 工作日晨练：多项运动按日独立轮换（与傍晚锻炼互不绑定）。 */
    MORNING_EXERCISE(
        R.string.sun_bear_morning_exercise,
        R.drawable.ic_sun_bear_exercise_run,
        R.drawable.ic_sun_bear_exercise_basketball,
        R.drawable.ic_sun_bear_exercise_pingpong,
        R.drawable.ic_sun_bear_exercise_bar,
        R.drawable.ic_sun_bear_exercise_badminton,
        R.drawable.ic_sun_bear_exercise_jumprope,
        R.drawable.ic_sun_bear_exercise_bike,
        R.drawable.ic_sun_bear_exercise_soccer,
        R.drawable.ic_sun_bear_exercise_yoga,
        R.drawable.ic_sun_bear_exercise_taichi,
    ),
    MORNING_OUT(
        R.string.sun_bear_morning_out,
        R.drawable.ic_sun_bear_morning_out,
        R.drawable.ic_sun_bear_morning_metro,
        R.drawable.ic_sun_bear_morning_bus,
        R.drawable.ic_sun_bear_morning_taxi,
        R.drawable.ic_sun_bear_morning_drive,
    ),

    /** 插画由 [workDayBearRes] 按工种选取，此处仅占位。 */
    WORK_MORNING(R.string.sun_bear_work_morning, R.drawable.ic_sun_bear_work_morning),
    LUNCH(
        R.string.sun_bear_lunch,
        R.drawable.ic_sun_bear_lunch,
        R.drawable.ic_sun_bear_lunch_2,
        R.drawable.ic_sun_bear_lunch_3,
        R.drawable.ic_sun_bear_lunch_4,
        R.drawable.ic_sun_bear_lunch_5,
        R.drawable.ic_sun_bear_lunch_6,
        R.drawable.ic_sun_bear_lunch_7,
        R.drawable.ic_sun_bear_lunch_8,
        R.drawable.ic_sun_bear_lunch_9,
        R.drawable.ic_sun_bear_lunch_10,
    ),

    /** 插画由 [workDayBearRes] 按工种选取，此处仅占位。 */
    WORK_AFTERNOON(R.string.sun_bear_work_afternoon, R.drawable.ic_sun_bear_work_afternoon),

    /** 工作日下午茶：咖啡 / 茶 / 奶茶 / 果汁 / 热可可 / 点心，按日轮换。 */
    AFTERNOON_TEA(
        R.string.sun_bear_afternoon_tea,
        R.drawable.ic_sun_bear_tea_coffee,
        R.drawable.ic_sun_bear_tea_tea,
        R.drawable.ic_sun_bear_tea_milk,
        R.drawable.ic_sun_bear_tea_juice,
        R.drawable.ic_sun_bear_tea_cocoa,
        R.drawable.ic_sun_bear_tea_dessert,
    ),
    HEAD_HOME(
        R.string.sun_bear_head_home,
        R.drawable.ic_sun_bear_head_home,
        R.drawable.ic_sun_bear_home_metro,
        R.drawable.ic_sun_bear_home_bus,
        R.drawable.ic_sun_bear_home_taxi,
        R.drawable.ic_sun_bear_home_drive,
    ),

    /** 工作日傍晚锻炼：与晨练共用运动池，但按日独立抽取。 */
    EVENING_EXERCISE(
        R.string.sun_bear_evening_exercise,
        R.drawable.ic_sun_bear_exercise_run,
        R.drawable.ic_sun_bear_exercise_basketball,
        R.drawable.ic_sun_bear_exercise_pingpong,
        R.drawable.ic_sun_bear_exercise_bar,
        R.drawable.ic_sun_bear_exercise_badminton,
        R.drawable.ic_sun_bear_exercise_jumprope,
        R.drawable.ic_sun_bear_exercise_bike,
        R.drawable.ic_sun_bear_exercise_soccer,
        R.drawable.ic_sun_bear_exercise_yoga,
        R.drawable.ic_sun_bear_exercise_taichi,
    ),
    WEEKEND_MORNING(
        R.string.sun_bear_weekend_morning,
        R.drawable.ic_sun_bear_weekend_morning_1,
        R.drawable.ic_sun_bear_weekend_morning_2,
        R.drawable.ic_sun_bear_weekend_morning_3,
        R.drawable.ic_sun_bear_weekend_morning_4,
        R.drawable.ic_sun_bear_weekend_morning_5,
    ),
    WEEKEND_EXPLORE(
        R.string.sun_bear_weekend_explore,
        R.drawable.ic_sun_bear_weekend_explore_1,
        R.drawable.ic_sun_bear_weekend_explore_2,
        R.drawable.ic_sun_bear_weekend_explore_3,
        R.drawable.ic_sun_bear_weekend_explore_4,
        R.drawable.ic_sun_bear_weekend_explore_5,
    ),
    WEEKEND_AFTERNOON(
        R.string.sun_bear_weekend_afternoon,
        R.drawable.ic_sun_bear_weekend_afternoon_1,
        R.drawable.ic_sun_bear_weekend_afternoon_2,
        R.drawable.ic_sun_bear_weekend_afternoon_3,
        R.drawable.ic_sun_bear_weekend_afternoon_4,
        R.drawable.ic_sun_bear_weekend_afternoon_5,
    ),
    WEEKEND_RETURN(
        R.string.sun_bear_weekend_return,
        R.drawable.ic_sun_bear_weekend_return_1,
        R.drawable.ic_sun_bear_weekend_return_2,
        R.drawable.ic_sun_bear_weekend_return_3,
        R.drawable.ic_sun_bear_weekend_return_4,
        R.drawable.ic_sun_bear_weekend_return_5,
    ),
    DINNER(
        R.string.sun_bear_dinner,
        R.drawable.ic_sun_bear_dinner,
        R.drawable.ic_sun_bear_dinner_2,
        R.drawable.ic_sun_bear_dinner_3,
        R.drawable.ic_sun_bear_dinner_4,
        R.drawable.ic_sun_bear_dinner_5,
        R.drawable.ic_sun_bear_dinner_6,
        R.drawable.ic_sun_bear_dinner_7,
        R.drawable.ic_sun_bear_dinner_8,
        R.drawable.ic_sun_bear_dinner_9,
        R.drawable.ic_sun_bear_dinner_10,
    ),
    EVENING(
        R.string.sun_bear_evening,
        R.drawable.ic_sun_bear_evening,
        R.drawable.ic_sun_bear_evening_game,
        R.drawable.ic_sun_bear_evening_book,
        R.drawable.ic_sun_bear_evening_phone,
        R.drawable.ic_sun_bear_evening_tv,
        R.drawable.ic_sun_bear_evening_music,
        R.drawable.ic_sun_bear_evening_knit,
        R.drawable.ic_sun_bear_evening_diary,
        R.drawable.ic_sun_bear_evening_pet,
    ),
    SLEEP(
        R.string.sun_bear_sleep,
        R.drawable.ic_sun_bear_sleep,
        R.drawable.ic_sun_bear_sleep_bed_pink,
        R.drawable.ic_sun_bear_sleep_bed_yellow,
        R.drawable.ic_sun_bear_sleep_bed_purple,
        R.drawable.ic_sun_bear_sleep_tatami,
        R.drawable.ic_sun_bear_sleep_tatami_orange,
        R.drawable.ic_sun_bear_sleep_kang,
        R.drawable.ic_sun_bear_sleep_hammock,
        R.drawable.ic_sun_bear_sleep_tent,
        R.drawable.ic_sun_bear_sleep_sofa,
        R.drawable.ic_sun_bear_sleep_train,
        R.drawable.ic_sun_bear_sleep_igloo,
        R.drawable.ic_sun_bear_sleep_treehouse,
    ),
    ALMOST_DAWN(
        R.string.sun_bear_almost_dawn,
        R.drawable.ic_sun_bear_almost_dawn,
        R.drawable.ic_sun_bear_almost_dawn_2,
        R.drawable.ic_sun_bear_almost_dawn_3,
    ),
    ;

    /** 按自然日稳定选取插画，当天不变、隔天可换。 */
    @DrawableRes
    fun bearResFor(date: LocalDate): Int {
        when (this) {
            WORK_MORNING -> return workDayBearRes(date, afternoon = false)
            WORK_AFTERNOON -> return workDayBearRes(date, afternoon = true)
            MORNING_OUT, HEAD_HOME -> return commuteBearRes(this, date)
            MORNING_EXERCISE, EVENING_EXERCISE ->
                return EXERCISE_DRAWABLES[exerciseIndex(date, this)]
            else -> Unit
        }
        if (bearVariants.isEmpty()) return R.drawable.ic_sun_bear_morning_out
        if (bearVariants.size == 1) return bearVariants[0]
        return bearVariants[dailyVariantIndex(date, name, bearVariants.size)]
    }

    /** 通勤/下午茶按变体匹配文案；其余片刻用固定 caption。 */
    @StringRes
    fun captionResFor(date: LocalDate): Int {
        when (this) {
            AFTERNOON_TEA -> {
                val captions =
                    intArrayOf(
                        R.string.sun_bear_tea_coffee,
                        R.string.sun_bear_tea_tea,
                        R.string.sun_bear_tea_milk,
                        R.string.sun_bear_tea_juice,
                        R.string.sun_bear_tea_cocoa,
                        R.string.sun_bear_tea_dessert,
                    )
                return captions[dailyVariantIndex(date, name, captions.size)]
            }
            EVENING -> {
                val captions =
                    intArrayOf(
                        R.string.sun_bear_evening_tea,
                        R.string.sun_bear_evening_game,
                        R.string.sun_bear_evening_book,
                        R.string.sun_bear_evening_phone,
                        R.string.sun_bear_evening_tv,
                        R.string.sun_bear_evening_music,
                        R.string.sun_bear_evening_knit,
                        R.string.sun_bear_evening_diary,
                        R.string.sun_bear_evening_pet,
                    )
                return captions[dailyVariantIndex(date, name, captions.size)]
            }
            MORNING_OUT -> return COMMUTE_OUT_CAPTIONS[commuteIndex(date)]
            HEAD_HOME -> return COMMUTE_HOME_CAPTIONS[commuteIndex(date)]
            MORNING_EXERCISE -> return EXERCISE_MORNING_CAPTIONS[exerciseIndex(date, this)]
            EVENING_EXERCISE -> return EXERCISE_EVENING_CAPTIONS[exerciseIndex(date, this)]
            else -> return captionRes
        }
    }
}

/** 走路 / 地铁 / 公交 / 打车 / 开车；上下班共用同一天索引。 */
private val COMMUTE_OUT_DRAWABLES =
    intArrayOf(
        R.drawable.ic_sun_bear_morning_out,
        R.drawable.ic_sun_bear_morning_metro,
        R.drawable.ic_sun_bear_morning_bus,
        R.drawable.ic_sun_bear_morning_taxi,
        R.drawable.ic_sun_bear_morning_drive,
    )
private val COMMUTE_HOME_DRAWABLES =
    intArrayOf(
        R.drawable.ic_sun_bear_head_home,
        R.drawable.ic_sun_bear_home_metro,
        R.drawable.ic_sun_bear_home_bus,
        R.drawable.ic_sun_bear_home_taxi,
        R.drawable.ic_sun_bear_home_drive,
    )
private val COMMUTE_OUT_CAPTIONS =
    intArrayOf(
        R.string.sun_bear_commute_walk_out,
        R.string.sun_bear_commute_metro_out,
        R.string.sun_bear_commute_bus_out,
        R.string.sun_bear_commute_taxi_out,
        R.string.sun_bear_commute_drive_out,
    )
private val COMMUTE_HOME_CAPTIONS =
    intArrayOf(
        R.string.sun_bear_commute_walk_home,
        R.string.sun_bear_commute_metro_home,
        R.string.sun_bear_commute_bus_home,
        R.string.sun_bear_commute_taxi_home,
        R.string.sun_bear_commute_drive_home,
    )

private fun commuteIndex(date: LocalDate): Int = dailyVariantIndex(date, "COMMUTE", COMMUTE_OUT_DRAWABLES.size)

@DrawableRes
private fun commuteBearRes(
    moment: SunPathBearMoment,
    date: LocalDate,
): Int {
    val i = commuteIndex(date)
    return when (moment) {
        SunPathBearMoment.HEAD_HOME -> COMMUTE_HOME_DRAWABLES[i]
        else -> COMMUTE_OUT_DRAWABLES[i]
    }
}

/** 跑步 / 篮球 / 乒乓 / 单杠 / 羽毛球 / 跳绳 / 骑行 / 足球 / 瑜伽 / 太极；晨练与傍晚各自按日抽取。 */
private val EXERCISE_DRAWABLES =
    intArrayOf(
        R.drawable.ic_sun_bear_exercise_run,
        R.drawable.ic_sun_bear_exercise_basketball,
        R.drawable.ic_sun_bear_exercise_pingpong,
        R.drawable.ic_sun_bear_exercise_bar,
        R.drawable.ic_sun_bear_exercise_badminton,
        R.drawable.ic_sun_bear_exercise_jumprope,
        R.drawable.ic_sun_bear_exercise_bike,
        R.drawable.ic_sun_bear_exercise_soccer,
        R.drawable.ic_sun_bear_exercise_yoga,
        R.drawable.ic_sun_bear_exercise_taichi,
    )
private val EXERCISE_MORNING_CAPTIONS =
    intArrayOf(
        R.string.sun_bear_exercise_run_morning,
        R.string.sun_bear_exercise_basketball_morning,
        R.string.sun_bear_exercise_pingpong_morning,
        R.string.sun_bear_exercise_bar_morning,
        R.string.sun_bear_exercise_badminton_morning,
        R.string.sun_bear_exercise_jumprope_morning,
        R.string.sun_bear_exercise_bike_morning,
        R.string.sun_bear_exercise_soccer_morning,
        R.string.sun_bear_exercise_yoga_morning,
        R.string.sun_bear_exercise_taichi_morning,
    )
private val EXERCISE_EVENING_CAPTIONS =
    intArrayOf(
        R.string.sun_bear_exercise_run_evening,
        R.string.sun_bear_exercise_basketball_evening,
        R.string.sun_bear_exercise_pingpong_evening,
        R.string.sun_bear_exercise_bar_evening,
        R.string.sun_bear_exercise_badminton_evening,
        R.string.sun_bear_exercise_jumprope_evening,
        R.string.sun_bear_exercise_bike_evening,
        R.string.sun_bear_exercise_soccer_evening,
        R.string.sun_bear_exercise_yoga_evening,
        R.string.sun_bear_exercise_taichi_evening,
    )

private fun exerciseIndex(
    date: LocalDate,
    moment: SunPathBearMoment,
): Int = dailyVariantIndex(date, moment.name, EXERCISE_DRAWABLES.size)

/** 办公室上午/下午各一张；其他工种当天上下午同一张。 */
private val WORK_FIELD_JOBS =
    intArrayOf(
        R.drawable.ic_sun_bear_work_brick,
        R.drawable.ic_sun_bear_work_delivery,
        R.drawable.ic_sun_bear_work_factory,
        R.drawable.ic_sun_bear_work_chef,
        R.drawable.ic_sun_bear_work_farm,
        R.drawable.ic_sun_bear_work_courier,
        R.drawable.ic_sun_bear_work_shop,
        R.drawable.ic_sun_bear_work_mechanic,
        R.drawable.ic_sun_bear_work_teacher,
        R.drawable.ic_sun_bear_work_nurse,
        R.drawable.ic_sun_bear_work_firefighter,
        R.drawable.ic_sun_bear_work_driver,
        R.drawable.ic_sun_bear_work_barista,
        R.drawable.ic_sun_bear_work_fisher,
        R.drawable.ic_sun_bear_work_warehouse,
        R.drawable.ic_sun_bear_work_cleaner,
        R.drawable.ic_sun_bear_work_barber,
        R.drawable.ic_sun_bear_work_scientist,
        R.drawable.ic_sun_bear_work_gardener,
        R.drawable.ic_sun_bear_work_painter,
        R.drawable.ic_sun_bear_work_baker,
        R.drawable.ic_sun_bear_work_guard,
    )

/**
 * 按天抽工种：0 = 办公室（上午/下午换图），其余为现场工种（全天同一张）。
 */
@DrawableRes
internal fun workDayBearRes(
    date: LocalDate,
    afternoon: Boolean,
): Int {
    val kindCount = 1 + WORK_FIELD_JOBS.size
    val kind = dailyVariantIndex(date, "WORK_DAY", kindCount)
    return if (kind == 0) {
        if (afternoon) {
            R.drawable.ic_sun_bear_work_afternoon
        } else {
            R.drawable.ic_sun_bear_work_morning
        }
    } else {
        WORK_FIELD_JOBS[kind - 1]
    }
}

fun isWeekend(date: LocalDate): Boolean = date.dayOfWeek == DayOfWeek.SATURDAY || date.dayOfWeek == DayOfWeek.SUNDAY

/**
 * 小熊生活片刻按**钟点**划分（午饭/晚饭不跟着日照进度跑，避免夏天 10 点就吃午饭）。
 * [phase] 只用来区分白天/夜晚；白天与夜晚各自用本地时间切段。
 */
fun sunPathBearMoment(
    phase: SunPathPhase?,
    date: LocalDate = LocalDate.now(),
    time: LocalTime = LocalTime.now(),
): SunPathBearMoment {
    if (phase == null) {
        return if (isWeekend(date)) SunPathBearMoment.WEEKEND_MORNING else SunPathBearMoment.MORNING_EXERCISE
    }
    val minutes = time.hour * 60 + time.minute
    return if (phase.isDay) {
        dayBearMoment(date, minutes)
    } else {
        nightBearMoment(date, minutes)
    }
}

/**
 * 白天（工作日）：晨练(~08:00前) → 上班通勤(08:00–09:00) → 上午忙 → 午饭(11:30–13:30)
 * → 下午忙；另插下午茶(15:00–16:30) → 回家通勤(17:30–18:30) → 傍晚锻炼(18:30后至日落)。
 * 周末仍用周末片刻，不拆通勤/锻炼。
 */
private fun dayBearMoment(
    date: LocalDate,
    minutes: Int,
): SunPathBearMoment {
    val weekend = isWeekend(date)
    return when {
        minutes < 8 * 60 ->
            if (weekend) SunPathBearMoment.WEEKEND_MORNING else SunPathBearMoment.MORNING_EXERCISE
        minutes < 9 * 60 ->
            if (weekend) SunPathBearMoment.WEEKEND_MORNING else SunPathBearMoment.MORNING_OUT
        minutes < 11 * 60 + 30 ->
            if (weekend) SunPathBearMoment.WEEKEND_EXPLORE else SunPathBearMoment.WORK_MORNING
        minutes < 13 * 60 + 30 ->
            SunPathBearMoment.LUNCH
        !weekend && minutes in (15 * 60) until (16 * 60 + 30) ->
            SunPathBearMoment.AFTERNOON_TEA
        minutes < 17 * 60 + 30 ->
            if (weekend) SunPathBearMoment.WEEKEND_AFTERNOON else SunPathBearMoment.WORK_AFTERNOON
        minutes < 18 * 60 + 30 ->
            if (weekend) SunPathBearMoment.WEEKEND_RETURN else SunPathBearMoment.HEAD_HOME
        else ->
            if (weekend) SunPathBearMoment.WEEKEND_RETURN else SunPathBearMoment.EVENING_EXERCISE
    }
}

/**
 * 夜晚：工作日若日落早，用黄昏补上回家通勤(~18:30前)与傍晚锻炼(~19:30前)，
 * 再晚饭(~20:00前) → 歇一会儿(~23:00前) → 睡觉 → 临近天亮。
 * 夏天日落晚时，通勤/锻炼多半已在白天完成，入夜会较快落到晚饭。
 */
private fun nightBearMoment(
    date: LocalDate,
    minutes: Int,
): SunPathBearMoment {
    val weekend = isWeekend(date)
    return when {
        minutes in (5 * 60) until (9 * 60) -> SunPathBearMoment.ALMOST_DAWN
        minutes >= 23 * 60 || minutes < 5 * 60 -> SunPathBearMoment.SLEEP
        !weekend && minutes < 18 * 60 + 30 -> SunPathBearMoment.HEAD_HOME
        !weekend && minutes < 19 * 60 + 30 -> SunPathBearMoment.EVENING_EXERCISE
        minutes < 20 * 60 -> SunPathBearMoment.DINNER
        else -> SunPathBearMoment.EVENING
    }
}

/** 同一天、同一场景索引固定，避免重组时乱跳。 */
internal fun dailyVariantIndex(
    date: LocalDate,
    salt: String,
    size: Int,
): Int {
    if (size <= 1) return 0
    var h = date.toEpochDay()
    h = h * 31L + salt.hashCode()
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

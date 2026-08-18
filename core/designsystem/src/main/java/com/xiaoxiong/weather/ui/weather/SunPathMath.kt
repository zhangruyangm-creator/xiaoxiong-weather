package com.xiaoxiong.weather.ui.weather

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import com.xiaoxiong.weather.R
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.temporal.ChronoUnit
import com.xiaoxiong.weather.resillustrations.R as IllustR

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
        IllustR.drawable.ic_sun_bear_exercise_run,
        IllustR.drawable.ic_sun_bear_exercise_basketball,
        IllustR.drawable.ic_sun_bear_exercise_pingpong,
        IllustR.drawable.ic_sun_bear_exercise_bar,
        IllustR.drawable.ic_sun_bear_exercise_badminton,
        IllustR.drawable.ic_sun_bear_exercise_jumprope,
        IllustR.drawable.ic_sun_bear_exercise_bike,
        IllustR.drawable.ic_sun_bear_exercise_soccer,
        IllustR.drawable.ic_sun_bear_exercise_yoga,
        IllustR.drawable.ic_sun_bear_exercise_taichi,
        IllustR.drawable.ic_sun_bear_exercise_baduanjin,
        IllustR.drawable.ic_sun_bear_exercise_squaredance,
        IllustR.drawable.ic_sun_bear_exercise_jianzi,
        IllustR.drawable.ic_sun_bear_exercise_taichisword,
        IllustR.drawable.ic_sun_bear_exercise_dogwalk,
        IllustR.drawable.ic_sun_bear_exercise_briskwalk,
        IllustR.drawable.ic_sun_bear_exercise_diabolo,
        IllustR.drawable.ic_sun_bear_exercise_tennis,
        IllustR.drawable.ic_sun_bear_exercise_hulahoop,
    ),

    /** 工作日通勤前早餐：中式 / 西式 / 阿拉伯式 / 韩式 / 东南亚独立插画池，按日轮换。 */
    BREAKFAST(
        R.string.sun_bear_breakfast,
        // 中式
        IllustR.drawable.ic_sun_bear_breakfast_youtiao,
        IllustR.drawable.ic_sun_bear_breakfast_baozi,
        IllustR.drawable.ic_sun_bear_breakfast_congee,
        IllustR.drawable.ic_sun_bear_breakfast_jianbing,
        IllustR.drawable.ic_sun_bear_breakfast_milkbread,
        IllustR.drawable.ic_sun_bear_breakfast_eggtoast,
        IllustR.drawable.ic_sun_bear_breakfast_noodles,
        IllustR.drawable.ic_sun_bear_breakfast_sandwich,
        IllustR.drawable.ic_sun_bear_breakfast_oatmeal,
        IllustR.drawable.ic_sun_bear_breakfast_changfen,
        // 西式
        IllustR.drawable.ic_sun_bear_breakfast_bacon_eggs,
        IllustR.drawable.ic_sun_bear_breakfast_waffle,
        IllustR.drawable.ic_sun_bear_breakfast_bagel,
        IllustR.drawable.ic_sun_bear_breakfast_frenchtoast,
        IllustR.drawable.ic_sun_bear_breakfast_omelette,
        IllustR.drawable.ic_sun_bear_breakfast_cereal,
        IllustR.drawable.ic_sun_bear_breakfast_english,
        // 阿拉伯 / 中东式
        IllustR.drawable.ic_sun_bear_breakfast_hummus,
        IllustR.drawable.ic_sun_bear_breakfast_arabtea,
        IllustR.drawable.ic_sun_bear_breakfast_falafel,
        IllustR.drawable.ic_sun_bear_breakfast_shakshuka,
        IllustR.drawable.ic_sun_bear_breakfast_dates,
        IllustR.drawable.ic_sun_bear_breakfast_manakish,
        // 韩式
        IllustR.drawable.ic_sun_bear_breakfast_kimbap,
        IllustR.drawable.ic_sun_bear_breakfast_koreantoast,
        IllustR.drawable.ic_sun_bear_breakfast_juk,
        IllustR.drawable.ic_sun_bear_breakfast_gyeranbap,
        IllustR.drawable.ic_sun_bear_breakfast_banchan,
        // 东南亚
        IllustR.drawable.ic_sun_bear_breakfast_kayatoast,
        IllustR.drawable.ic_sun_bear_breakfast_nasilemak,
        IllustR.drawable.ic_sun_bear_breakfast_banhmi,
        IllustR.drawable.ic_sun_bear_breakfast_roticanai,
        IllustR.drawable.ic_sun_bear_breakfast_pho,
    ),
    MORNING_OUT(
        R.string.sun_bear_morning_out,
        IllustR.drawable.ic_sun_bear_morning_out,
        IllustR.drawable.ic_sun_bear_morning_metro,
        IllustR.drawable.ic_sun_bear_morning_bus,
        IllustR.drawable.ic_sun_bear_morning_taxi,
        IllustR.drawable.ic_sun_bear_morning_drive,
        IllustR.drawable.ic_sun_bear_morning_ebike,
        IllustR.drawable.ic_sun_bear_morning_cycle,
        IllustR.drawable.ic_sun_bear_morning_ferry,
    ),

    /** 插画由 [workDayBearRes] 按工种选取，此处仅占位。 */
    WORK_MORNING(R.string.sun_bear_work_morning, IllustR.drawable.ic_sun_bear_work_morning),

    /** 午饭：中式 / 西式 / 阿拉伯式 / 韩式 / 东南亚独立插画池，按日轮换。 */
    LUNCH(
        R.string.sun_bear_lunch,
        // 中式
        IllustR.drawable.ic_sun_bear_lunch_bento,
        IllustR.drawable.ic_sun_bear_lunch_ramen,
        IllustR.drawable.ic_sun_bear_lunch_jiaozi,
        IllustR.drawable.ic_sun_bear_lunch_friedrice,
        IllustR.drawable.ic_sun_bear_lunch_sushi,
        // 西式
        IllustR.drawable.ic_sun_bear_lunch_burger,
        IllustR.drawable.ic_sun_bear_lunch_picnic,
        IllustR.drawable.ic_sun_bear_lunch_takeout,
        IllustR.drawable.ic_sun_bear_lunch_pokebowl,
        IllustR.drawable.ic_sun_bear_lunch_burgerfries,
        // 阿拉伯 / 中东式
        IllustR.drawable.ic_sun_bear_lunch_shawarma,
        IllustR.drawable.ic_sun_bear_lunch_kebab,
        IllustR.drawable.ic_sun_bear_lunch_mezze,
        IllustR.drawable.ic_sun_bear_lunch_kabsa,
        IllustR.drawable.ic_sun_bear_lunch_falafel,
        IllustR.drawable.ic_sun_bear_lunch_fattoush,
        // 韩式
        IllustR.drawable.ic_sun_bear_lunch_bibimbap,
        IllustR.drawable.ic_sun_bear_lunch_kbbq,
        IllustR.drawable.ic_sun_bear_lunch_tteokbokki,
        IllustR.drawable.ic_sun_bear_lunch_kimchijjigae,
        IllustR.drawable.ic_sun_bear_lunch_ramyeon,
        // 东南亚
        IllustR.drawable.ic_sun_bear_lunch_padthai,
        IllustR.drawable.ic_sun_bear_lunch_greencurry,
        IllustR.drawable.ic_sun_bear_lunch_nasigoreng,
        IllustR.drawable.ic_sun_bear_lunch_tomyum,
        IllustR.drawable.ic_sun_bear_lunch_satay,
    ),

    /** 插画由 [workDayBearRes] 按工种选取，此处仅占位。 */
    WORK_AFTERNOON(R.string.sun_bear_work_afternoon, IllustR.drawable.ic_sun_bear_work_afternoon),

    /** 工作日下午茶：咖啡 / 茶 / 奶茶 / 果汁 / 热可可 / 点心，按日轮换。 */
    AFTERNOON_TEA(
        R.string.sun_bear_afternoon_tea,
        IllustR.drawable.ic_sun_bear_tea_coffee,
        IllustR.drawable.ic_sun_bear_tea_tea,
        IllustR.drawable.ic_sun_bear_tea_milk,
        IllustR.drawable.ic_sun_bear_tea_juice,
        IllustR.drawable.ic_sun_bear_tea_cocoa,
        IllustR.drawable.ic_sun_bear_tea_dessert,
    ),
    HEAD_HOME(
        R.string.sun_bear_head_home,
        IllustR.drawable.ic_sun_bear_head_home,
        IllustR.drawable.ic_sun_bear_home_metro,
        IllustR.drawable.ic_sun_bear_home_bus,
        IllustR.drawable.ic_sun_bear_home_taxi,
        IllustR.drawable.ic_sun_bear_home_drive,
        IllustR.drawable.ic_sun_bear_home_ebike,
        IllustR.drawable.ic_sun_bear_home_cycle,
        IllustR.drawable.ic_sun_bear_home_ferry,
    ),

    /** 工作日傍晚锻炼：与晨练共用运动池，但按日独立抽取。 */
    EVENING_EXERCISE(
        R.string.sun_bear_evening_exercise,
        IllustR.drawable.ic_sun_bear_exercise_run,
        IllustR.drawable.ic_sun_bear_exercise_basketball,
        IllustR.drawable.ic_sun_bear_exercise_pingpong,
        IllustR.drawable.ic_sun_bear_exercise_bar,
        IllustR.drawable.ic_sun_bear_exercise_badminton,
        IllustR.drawable.ic_sun_bear_exercise_jumprope,
        IllustR.drawable.ic_sun_bear_exercise_bike,
        IllustR.drawable.ic_sun_bear_exercise_soccer,
        IllustR.drawable.ic_sun_bear_exercise_yoga,
        IllustR.drawable.ic_sun_bear_exercise_taichi,
        IllustR.drawable.ic_sun_bear_exercise_baduanjin,
        IllustR.drawable.ic_sun_bear_exercise_squaredance,
        IllustR.drawable.ic_sun_bear_exercise_jianzi,
        IllustR.drawable.ic_sun_bear_exercise_taichisword,
        IllustR.drawable.ic_sun_bear_exercise_dogwalk,
        IllustR.drawable.ic_sun_bear_exercise_briskwalk,
        IllustR.drawable.ic_sun_bear_exercise_diabolo,
        IllustR.drawable.ic_sun_bear_exercise_tennis,
        IllustR.drawable.ic_sun_bear_exercise_hulahoop,
    ),
    WEEKEND_MORNING(
        R.string.sun_bear_weekend_morning,
        // 插画由 [WEEKEND_DAY_DRAWABLES] 与下午共用，此处仅占位。
        IllustR.drawable.ic_sun_bear_weekend_station,
    ),
    WEEKEND_EXPLORE(
        R.string.sun_bear_weekend_explore,
        IllustR.drawable.ic_sun_bear_weekend_explore_tokyotower,
        IllustR.drawable.ic_sun_bear_weekend_explore_fuji,
        IllustR.drawable.ic_sun_bear_weekend_explore_museum,
        IllustR.drawable.ic_sun_bear_weekend_explore_boat,
        IllustR.drawable.ic_sun_bear_weekend_explore_hiking,
        IllustR.drawable.ic_sun_bear_weekend_explore_ginza,
        IllustR.drawable.ic_sun_bear_weekend_explore_shibuya,
        IllustR.drawable.ic_sun_bear_weekend_explore_shinjuku,
        IllustR.drawable.ic_sun_bear_weekend_explore_akihabara,
        IllustR.drawable.ic_sun_bear_weekend_explore_ikebukuro,
        IllustR.drawable.ic_sun_bear_weekend_explore_timessquare,
        IllustR.drawable.ic_sun_bear_weekend_explore_fifthavenue,
        IllustR.drawable.ic_sun_bear_weekend_explore_champselysees,
        IllustR.drawable.ic_sun_bear_weekend_explore_gangnam,
        IllustR.drawable.ic_sun_bear_weekend_explore_causewaybay,
        IllustR.drawable.ic_sun_bear_weekend_explore_qingdao,
        IllustR.drawable.ic_sun_bear_weekend_explore_qushuiting,
        IllustR.drawable.ic_sun_bear_weekend_explore_fuzimiao,
        IllustR.drawable.ic_sun_bear_weekend_explore_weihai,
        IllustR.drawable.ic_sun_bear_weekend_explore_sapporo,
        IllustR.drawable.ic_sun_bear_weekend_explore_nanjingroad,
        IllustR.drawable.ic_sun_bear_weekend_explore_baoan,
        IllustR.drawable.ic_sun_bear_weekend_explore_shenzhenbay,
        IllustR.drawable.ic_sun_bear_weekend_explore_tangye,
        IllustR.drawable.ic_sun_bear_weekend_explore_luomashi,
        IllustR.drawable.ic_sun_bear_weekend_explore_qianmen,
        IllustR.drawable.ic_sun_bear_weekend_explore_gulangyu,
        IllustR.drawable.ic_sun_bear_weekend_explore_hongyadong,
        IllustR.drawable.ic_sun_bear_weekend_explore_rio,
        IllustR.drawable.ic_sun_bear_weekend_explore_yellowstone,
    ),
    WEEKEND_AFTERNOON(
        R.string.sun_bear_weekend_afternoon,
        // 插画由 [WEEKEND_DAY_DRAWABLES] 与上午共用，此处仅占位。
        IllustR.drawable.ic_sun_bear_weekend_beach,
    ),
    WEEKEND_RETURN(
        R.string.sun_bear_weekend_return,
        IllustR.drawable.ic_sun_bear_weekend_return_luggage,
        IllustR.drawable.ic_sun_bear_weekend_return_train,
        IllustR.drawable.ic_sun_bear_weekend_return_souvenir,
        IllustR.drawable.ic_sun_bear_weekend_return_scooter,
        IllustR.drawable.ic_sun_bear_weekend_return_bags,
    ),

    /** 晚饭：中式 / 西式 / 阿拉伯式 / 韩式 / 东南亚独立插画池，按日轮换。 */
    DINNER(
        R.string.sun_bear_dinner,
        // 中式
        IllustR.drawable.ic_sun_bear_dinner_teishoku,
        IllustR.drawable.ic_sun_bear_dinner_hotpot,
        IllustR.drawable.ic_sun_bear_dinner_sushi,
        IllustR.drawable.ic_sun_bear_dinner_yakitori,
        IllustR.drawable.ic_sun_bear_dinner_shabu,
        // 西式
        IllustR.drawable.ic_sun_bear_dinner_spaghetti,
        IllustR.drawable.ic_sun_bear_dinner_bbq,
        IllustR.drawable.ic_sun_bear_dinner_pizza,
        IllustR.drawable.ic_sun_bear_dinner_steak,
        IllustR.drawable.ic_sun_bear_dinner_italian,
        // 阿拉伯 / 中东式
        IllustR.drawable.ic_sun_bear_dinner_shawarma,
        IllustR.drawable.ic_sun_bear_dinner_tagine,
        IllustR.drawable.ic_sun_bear_dinner_kebab,
        IllustR.drawable.ic_sun_bear_dinner_mansaf,
        IllustR.drawable.ic_sun_bear_dinner_mezze,
        IllustR.drawable.ic_sun_bear_dinner_couscous,
        // 韩式
        IllustR.drawable.ic_sun_bear_dinner_galbi,
        IllustR.drawable.ic_sun_bear_dinner_budaejjigae,
        IllustR.drawable.ic_sun_bear_dinner_jajangmyeon,
        IllustR.drawable.ic_sun_bear_dinner_soondubu,
        IllustR.drawable.ic_sun_bear_dinner_chimaek,
        // 东南亚
        IllustR.drawable.ic_sun_bear_dinner_laksa,
        IllustR.drawable.ic_sun_bear_dinner_massaman,
        IllustR.drawable.ic_sun_bear_dinner_hainanese,
        IllustR.drawable.ic_sun_bear_dinner_lau,
        IllustR.drawable.ic_sun_bear_dinner_banhxeo,
    ),
    EVENING(
        R.string.sun_bear_evening,
        IllustR.drawable.ic_sun_bear_evening,
        IllustR.drawable.ic_sun_bear_evening_game,
        IllustR.drawable.ic_sun_bear_evening_book,
        IllustR.drawable.ic_sun_bear_evening_phone,
        IllustR.drawable.ic_sun_bear_evening_tv,
        IllustR.drawable.ic_sun_bear_evening_music,
        IllustR.drawable.ic_sun_bear_evening_knit,
        IllustR.drawable.ic_sun_bear_evening_diary,
        IllustR.drawable.ic_sun_bear_evening_pet,
        IllustR.drawable.ic_sun_bear_evening_guitar,
        IllustR.drawable.ic_sun_bear_evening_treadmill,
        IllustR.drawable.ic_sun_bear_evening_yoga,
        IllustR.drawable.ic_sun_bear_evening_draw,
        IllustR.drawable.ic_sun_bear_evening_balcony,
        IllustR.drawable.ic_sun_bear_evening_cityview,
        IllustR.drawable.ic_sun_bear_evening_lounge,
        IllustR.drawable.ic_sun_bear_evening_videocall,
        IllustR.drawable.ic_sun_bear_evening_boardgame,
        IllustR.drawable.ic_sun_bear_evening_lego,
        IllustR.drawable.ic_sun_bear_evening_study,
    ),

    /** 入睡准备（23:00–24:00）：换睡衣 / 泡澡 / 夜灯等，按日轮换。 */
    BEDTIME_PREP(
        R.string.sun_bear_bedtime,
        IllustR.drawable.ic_sun_bear_bedtime_pajamas,
        IllustR.drawable.ic_sun_bear_bedtime_bath,
        IllustR.drawable.ic_sun_bear_bedtime_nightlight,
        IllustR.drawable.ic_sun_bear_bedtime_makebed,
        IllustR.drawable.ic_sun_bear_bedtime_story,
        IllustR.drawable.ic_sun_bear_bedtime_plush,
        IllustR.drawable.ic_sun_bear_bedtime_alarm,
        IllustR.drawable.ic_sun_bear_bedtime_footbath,
        IllustR.drawable.ic_sun_bear_bedtime_book,
    ),
    SLEEP(
        R.string.sun_bear_sleep,
        IllustR.drawable.ic_sun_bear_sleep,
        IllustR.drawable.ic_sun_bear_sleep_bed_pink,
        IllustR.drawable.ic_sun_bear_sleep_bed_yellow,
        IllustR.drawable.ic_sun_bear_sleep_bed_purple,
        IllustR.drawable.ic_sun_bear_sleep_tatami,
        IllustR.drawable.ic_sun_bear_sleep_tatami_orange,
        IllustR.drawable.ic_sun_bear_sleep_kang,
        IllustR.drawable.ic_sun_bear_sleep_hammock,
        IllustR.drawable.ic_sun_bear_sleep_tent,
        IllustR.drawable.ic_sun_bear_sleep_sofa,
        IllustR.drawable.ic_sun_bear_sleep_train,
        IllustR.drawable.ic_sun_bear_sleep_igloo,
        IllustR.drawable.ic_sun_bear_sleep_treehouse,
        IllustR.drawable.ic_sun_bear_sleep_sleepingbag,
        IllustR.drawable.ic_sun_bear_sleep_bunk,
        IllustR.drawable.ic_sun_bear_sleep_campcot,
        IllustR.drawable.ic_sun_bear_sleep_lounge,
        IllustR.drawable.ic_sun_bear_sleep_crib,
        IllustR.drawable.ic_sun_bear_sleep_petpile,
        IllustR.drawable.ic_sun_bear_sleep_sofa_lie,
        IllustR.drawable.ic_sun_bear_sleep_dorm,
    ),
    ALMOST_DAWN(
        R.string.sun_bear_almost_dawn,
        IllustR.drawable.ic_sun_bear_almost_dawn,
        IllustR.drawable.ic_sun_bear_almost_dawn_2,
        IllustR.drawable.ic_sun_bear_almost_dawn_3,
    ),
    ;

    /** 图鉴/小屋用：该片刻全部插画变体（不含按日抽取的工种/通勤特例逻辑）。 */
    fun bearVariantDrawables(): IntArray =
        when (this) {
            WEEKEND_MORNING, WEEKEND_AFTERNOON -> WEEKEND_DAY_DRAWABLES
            else -> bearVariants
        }

    /** 按自然日稳定选取插画，当天不变、隔天可换。 */
    @DrawableRes
    fun bearResFor(date: LocalDate): Int {
        when (this) {
            WORK_MORNING -> return workDayBearRes(date, afternoon = false)
            WORK_AFTERNOON -> return workDayBearRes(date, afternoon = true)
            MORNING_OUT, HEAD_HOME -> return commuteBearRes(this, date)
            MORNING_EXERCISE, EVENING_EXERCISE ->
                return EXERCISE_DRAWABLES[exerciseIndex(date, this)]
            WEEKEND_MORNING, WEEKEND_AFTERNOON ->
                return WEEKEND_DAY_DRAWABLES[
                    dailyVariantIndex(date, name, WEEKEND_DAY_DRAWABLES.size),
                ]
            else -> Unit
        }
        if (bearVariants.isEmpty()) return IllustR.drawable.ic_sun_bear_morning_out
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
                        R.string.sun_bear_evening_guitar,
                        R.string.sun_bear_evening_treadmill,
                        R.string.sun_bear_evening_yoga,
                        R.string.sun_bear_evening_draw,
                        R.string.sun_bear_evening_balcony,
                        R.string.sun_bear_evening_cityview,
                        R.string.sun_bear_evening_lounge,
                        R.string.sun_bear_evening_videocall,
                        R.string.sun_bear_evening_boardgame,
                        R.string.sun_bear_evening_lego,
                        R.string.sun_bear_evening_study,
                    )
                return captions[dailyVariantIndex(date, name, captions.size)]
            }
            BEDTIME_PREP -> {
                val captions =
                    intArrayOf(
                        R.string.sun_bear_bedtime_pajamas,
                        R.string.sun_bear_bedtime_bath,
                        R.string.sun_bear_bedtime_nightlight,
                        R.string.sun_bear_bedtime_makebed,
                        R.string.sun_bear_bedtime_story,
                        R.string.sun_bear_bedtime_plush,
                        R.string.sun_bear_bedtime_alarm,
                        R.string.sun_bear_bedtime_footbath,
                        R.string.sun_bear_bedtime_book,
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

/** 走路 / 地铁 / 公交 / 打车 / 开车 / 电瓶车 / 自行车 / 轮渡；上下班共用同一天索引。 */
private val COMMUTE_OUT_DRAWABLES =
    intArrayOf(
        IllustR.drawable.ic_sun_bear_morning_out,
        IllustR.drawable.ic_sun_bear_morning_metro,
        IllustR.drawable.ic_sun_bear_morning_bus,
        IllustR.drawable.ic_sun_bear_morning_taxi,
        IllustR.drawable.ic_sun_bear_morning_drive,
        IllustR.drawable.ic_sun_bear_morning_ebike,
        IllustR.drawable.ic_sun_bear_morning_cycle,
        IllustR.drawable.ic_sun_bear_morning_ferry,
    )
private val COMMUTE_HOME_DRAWABLES =
    intArrayOf(
        IllustR.drawable.ic_sun_bear_head_home,
        IllustR.drawable.ic_sun_bear_home_metro,
        IllustR.drawable.ic_sun_bear_home_bus,
        IllustR.drawable.ic_sun_bear_home_taxi,
        IllustR.drawable.ic_sun_bear_home_drive,
        IllustR.drawable.ic_sun_bear_home_ebike,
        IllustR.drawable.ic_sun_bear_home_cycle,
        IllustR.drawable.ic_sun_bear_home_ferry,
    )
private val COMMUTE_OUT_CAPTIONS =
    intArrayOf(
        R.string.sun_bear_commute_walk_out,
        R.string.sun_bear_commute_metro_out,
        R.string.sun_bear_commute_bus_out,
        R.string.sun_bear_commute_taxi_out,
        R.string.sun_bear_commute_drive_out,
        R.string.sun_bear_commute_ebike_out,
        R.string.sun_bear_commute_cycle_out,
        R.string.sun_bear_commute_ferry_out,
    )
private val COMMUTE_HOME_CAPTIONS =
    intArrayOf(
        R.string.sun_bear_commute_walk_home,
        R.string.sun_bear_commute_metro_home,
        R.string.sun_bear_commute_bus_home,
        R.string.sun_bear_commute_taxi_home,
        R.string.sun_bear_commute_drive_home,
        R.string.sun_bear_commute_ebike_home,
        R.string.sun_bear_commute_cycle_home,
        R.string.sun_bear_commute_ferry_home,
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

/** 运动池；晨练与傍晚共用插画、各自按日抽取。 */
private val EXERCISE_DRAWABLES =
    intArrayOf(
        IllustR.drawable.ic_sun_bear_exercise_run,
        IllustR.drawable.ic_sun_bear_exercise_basketball,
        IllustR.drawable.ic_sun_bear_exercise_pingpong,
        IllustR.drawable.ic_sun_bear_exercise_bar,
        IllustR.drawable.ic_sun_bear_exercise_badminton,
        IllustR.drawable.ic_sun_bear_exercise_jumprope,
        IllustR.drawable.ic_sun_bear_exercise_bike,
        IllustR.drawable.ic_sun_bear_exercise_soccer,
        IllustR.drawable.ic_sun_bear_exercise_yoga,
        IllustR.drawable.ic_sun_bear_exercise_taichi,
        IllustR.drawable.ic_sun_bear_exercise_baduanjin,
        IllustR.drawable.ic_sun_bear_exercise_squaredance,
        IllustR.drawable.ic_sun_bear_exercise_jianzi,
        IllustR.drawable.ic_sun_bear_exercise_taichisword,
        IllustR.drawable.ic_sun_bear_exercise_dogwalk,
        IllustR.drawable.ic_sun_bear_exercise_briskwalk,
        IllustR.drawable.ic_sun_bear_exercise_diabolo,
        IllustR.drawable.ic_sun_bear_exercise_tennis,
        IllustR.drawable.ic_sun_bear_exercise_hulahoop,
    )

/**
 * 周末白天玩乐共享池（上午出门 + 下午逛逛 + 室内休闲等）。
 * [WEEKEND_MORNING] / [WEEKEND_AFTERNOON] 各自按日从该池抽一张，互不绑定。
 */
private val WEEKEND_DAY_DRAWABLES =
    intArrayOf(
        IllustR.drawable.ic_sun_bear_weekend_station,
        IllustR.drawable.ic_sun_bear_weekend_bike,
        IllustR.drawable.ic_sun_bear_weekend_airport,
        IllustR.drawable.ic_sun_bear_weekend_roadtrip,
        IllustR.drawable.ic_sun_bear_weekend_brunch,
        IllustR.drawable.ic_sun_bear_weekend_beach,
        IllustR.drawable.ic_sun_bear_weekend_bazaar,
        IllustR.drawable.ic_sun_bear_weekend_kayak,
        IllustR.drawable.ic_sun_bear_weekend_kite,
        IllustR.drawable.ic_sun_bear_weekend_park_kite,
        IllustR.drawable.ic_sun_bear_weekend_mall,
        IllustR.drawable.ic_sun_bear_weekend_furniture,
        IllustR.drawable.ic_sun_bear_weekend_soccer,
        IllustR.drawable.ic_sun_bear_weekend_cafe,
        IllustR.drawable.ic_sun_bear_weekend_bookstore,
        IllustR.drawable.ic_sun_bear_weekend_cinema,
        IllustR.drawable.ic_sun_bear_weekend_boardgame,
        IllustR.drawable.ic_sun_bear_weekend_pottery,
        IllustR.drawable.ic_sun_bear_weekend_bakeclass,
        IllustR.drawable.ic_sun_bear_weekend_swim,
        IllustR.drawable.ic_sun_bear_weekend_karaoke,
        IllustR.drawable.ic_sun_bear_weekend_camp,
        IllustR.drawable.ic_sun_bear_weekend_nightmarket,
        IllustR.drawable.ic_sun_bear_weekend_dessert,
        IllustR.drawable.ic_sun_bear_weekend_flowermarket,
        IllustR.drawable.ic_sun_bear_weekend_wetmarket,
        IllustR.drawable.ic_sun_bear_weekend_petcafe,
        IllustR.drawable.ic_sun_bear_weekend_library,
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
        R.string.sun_bear_exercise_baduanjin_morning,
        R.string.sun_bear_exercise_squaredance_morning,
        R.string.sun_bear_exercise_jianzi_morning,
        R.string.sun_bear_exercise_taichisword_morning,
        R.string.sun_bear_exercise_dogwalk_morning,
        R.string.sun_bear_exercise_briskwalk_morning,
        R.string.sun_bear_exercise_diabolo_morning,
        R.string.sun_bear_exercise_tennis_morning,
        R.string.sun_bear_exercise_hulahoop_morning,
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
        R.string.sun_bear_exercise_baduanjin_evening,
        R.string.sun_bear_exercise_squaredance_evening,
        R.string.sun_bear_exercise_jianzi_evening,
        R.string.sun_bear_exercise_taichisword_evening,
        R.string.sun_bear_exercise_dogwalk_evening,
        R.string.sun_bear_exercise_briskwalk_evening,
        R.string.sun_bear_exercise_diabolo_evening,
        R.string.sun_bear_exercise_tennis_evening,
        R.string.sun_bear_exercise_hulahoop_evening,
    )

private fun exerciseIndex(
    date: LocalDate,
    moment: SunPathBearMoment,
): Int = dailyVariantIndex(date, moment.name, EXERCISE_DRAWABLES.size)

/** 通用办公室：上午/下午各一张。 */
private val WORK_OFFICE_GENERIC_MORNING = IllustR.drawable.ic_sun_bear_work_morning
private val WORK_OFFICE_GENERIC_AFTERNOON = IllustR.drawable.ic_sun_bear_work_afternoon

/** 办公室坐班工种（全天同一张）：程序员、会计等。 */
private val WORK_OFFICE_JOBS =
    intArrayOf(
        IllustR.drawable.ic_sun_bear_work_programmer,
        IllustR.drawable.ic_sun_bear_work_accountant,
        IllustR.drawable.ic_sun_bear_work_lawyer,
        IllustR.drawable.ic_sun_bear_work_librarian,
        IllustR.drawable.ic_sun_bear_work_architect,
        IllustR.drawable.ic_sun_bear_work_receptionist,
    )

/** 现场 / 外出工种：当天上下午同一张。 */
private val WORK_FIELD_JOBS =
    intArrayOf(
        IllustR.drawable.ic_sun_bear_work_brick,
        IllustR.drawable.ic_sun_bear_work_delivery,
        IllustR.drawable.ic_sun_bear_work_factory,
        IllustR.drawable.ic_sun_bear_work_chef,
        IllustR.drawable.ic_sun_bear_work_farm,
        IllustR.drawable.ic_sun_bear_work_courier,
        IllustR.drawable.ic_sun_bear_work_shop,
        IllustR.drawable.ic_sun_bear_work_stocker,
        IllustR.drawable.ic_sun_bear_work_streetfood,
        IllustR.drawable.ic_sun_bear_work_zhuchengpork,
        IllustR.drawable.ic_sun_bear_work_grilledfish,
        IllustR.drawable.ic_sun_bear_work_stinkytofu,
        IllustR.drawable.ic_sun_bear_work_grilledsausage,
        IllustR.drawable.ic_sun_bear_work_mechanic,
        IllustR.drawable.ic_sun_bear_work_phonerepair,
        IllustR.drawable.ic_sun_bear_work_teacher,
        IllustR.drawable.ic_sun_bear_work_nurse,
        IllustR.drawable.ic_sun_bear_work_firefighter,
        IllustR.drawable.ic_sun_bear_work_driver,
        IllustR.drawable.ic_sun_bear_work_truck,
        IllustR.drawable.ic_sun_bear_work_barista,
        IllustR.drawable.ic_sun_bear_work_fisher,
        IllustR.drawable.ic_sun_bear_work_harbor,
        IllustR.drawable.ic_sun_bear_work_warehouse,
        IllustR.drawable.ic_sun_bear_work_cleaner,
        IllustR.drawable.ic_sun_bear_work_barber,
        IllustR.drawable.ic_sun_bear_work_scientist,
        IllustR.drawable.ic_sun_bear_work_gardener,
        IllustR.drawable.ic_sun_bear_work_painter,
        IllustR.drawable.ic_sun_bear_work_baker,
        IllustR.drawable.ic_sun_bear_work_guard,
        IllustR.drawable.ic_sun_bear_work_doctor,
        IllustR.drawable.ic_sun_bear_work_police,
        IllustR.drawable.ic_sun_bear_work_pilot,
        IllustR.drawable.ic_sun_bear_work_streamer,
        IllustR.drawable.ic_sun_bear_work_designer,
        IllustR.drawable.ic_sun_bear_work_carpenter,
        IllustR.drawable.ic_sun_bear_work_electrician,
        IllustR.drawable.ic_sun_bear_work_miner,
        IllustR.drawable.ic_sun_bear_work_oilworker,
        IllustR.drawable.ic_sun_bear_work_vet,
        IllustR.drawable.ic_sun_bear_work_photographer,
        IllustR.drawable.ic_sun_bear_work_musician,
        IllustR.drawable.ic_sun_bear_work_welder,
        IllustR.drawable.ic_sun_bear_work_blacksmith,
        IllustR.drawable.ic_sun_bear_work_fitter,
        IllustR.drawable.ic_sun_bear_work_lathe,
        IllustR.drawable.ic_sun_bear_work_forklift,
        IllustR.drawable.ic_sun_bear_work_crane,
        IllustR.drawable.ic_sun_bear_work_excavator,
        IllustR.drawable.ic_sun_bear_work_tractor,
        IllustR.drawable.ic_sun_bear_work_harvester,
        IllustR.drawable.ic_sun_bear_work_sorter,
        IllustR.drawable.ic_sun_bear_work_journalist,
        IllustR.drawable.ic_sun_bear_work_astronaut,
        IllustR.drawable.ic_sun_bear_work_athlete,
        IllustR.drawable.ic_sun_bear_work_pharmacist,
        IllustR.drawable.ic_sun_bear_work_tailor,
        IllustR.drawable.ic_sun_bear_work_archaeologist,
    )

/**
 * 按天抽工种：
 * 0 = 通用办公室（上午/下午换图）；
 * 其后为办公室坐班工种，再后为现场工种（全天同一张）。
 */
@DrawableRes
internal fun workDayBearRes(
    date: LocalDate,
    afternoon: Boolean,
): Int {
    val kindCount = 1 + WORK_OFFICE_JOBS.size + WORK_FIELD_JOBS.size
    val kind = dailyVariantIndex(date, "WORK_DAY", kindCount)
    return when {
        kind == 0 ->
            if (afternoon) WORK_OFFICE_GENERIC_AFTERNOON else WORK_OFFICE_GENERIC_MORNING
        kind <= WORK_OFFICE_JOBS.size -> WORK_OFFICE_JOBS[kind - 1]
        else -> WORK_FIELD_JOBS[kind - 1 - WORK_OFFICE_JOBS.size]
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
    val isAlmostDawn = if (sunrise != null) {
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

/** 同一天、同一场景索引固定，避免重组时乱跳。 */
fun dailyVariantIndex(
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

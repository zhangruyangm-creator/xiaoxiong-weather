package com.xiaoxiong.weather.ui.weather

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import com.xiaoxiong.weather.R
import java.time.LocalDate
import com.xiaoxiong.weather.resillustrations.R as IllustR

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

    /**
     * 工作日晨练后、早餐前：插画由 [EXPLORE_DRAWABLES] 与周末旅行共用，此处仅占位。
     */
    MORNING_STROLL(
        R.string.sun_bear_morning_stroll,
        IllustR.drawable.ic_sun_bear_weekend_explore_tokyotower,
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
        IllustR.drawable.ic_sun_bear_tea_lemontea,
        IllustR.drawable.ic_sun_bear_tea_icecream,
        IllustR.drawable.ic_sun_bear_tea_eggtart,
        IllustR.drawable.ic_sun_bear_tea_fruit,
        IllustR.drawable.ic_sun_bear_tea_instantnoodles,
        IllustR.drawable.ic_sun_bear_tea_conveniencesnack,
        IllustR.drawable.ic_sun_bear_tea_yogurt,
        IllustR.drawable.ic_sun_bear_tea_croissant,
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

    /** 周末旅行：插画由 [EXPLORE_DRAWABLES] 与工作日晨间逛逛共用，此处仅占位。 */
    WEEKEND_EXPLORE(
        R.string.sun_bear_weekend_explore,
        IllustR.drawable.ic_sun_bear_weekend_explore_tokyotower,
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
        IllustR.drawable.ic_sun_bear_weekend_return_metro,
        IllustR.drawable.ic_sun_bear_weekend_return_taxi,
        IllustR.drawable.ic_sun_bear_weekend_return_nap,
        IllustR.drawable.ic_sun_bear_weekend_return_bike,
        IllustR.drawable.ic_sun_bear_weekend_return_takeout,
        IllustR.drawable.ic_sun_bear_weekend_return_ferry,
        IllustR.drawable.ic_sun_bear_weekend_return_plane,
        IllustR.drawable.ic_sun_bear_weekend_return_collapse,
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
        IllustR.drawable.ic_sun_bear_evening_balconysnack,
        IllustR.drawable.ic_sun_bear_evening_dramacry,
        IllustR.drawable.ic_sun_bear_evening_balconymoon,
        IllustR.drawable.ic_sun_bear_evening_balconyhang,
        IllustR.drawable.ic_sun_bear_evening_balconyphoto,
        IllustR.drawable.ic_sun_bear_evening_balconybreeze,
        IllustR.drawable.ic_sun_bear_evening_balconybbq,
        IllustR.drawable.ic_sun_bear_evening_livingroom,
        IllustR.drawable.ic_sun_bear_evening_projector,
        IllustR.drawable.ic_sun_bear_evening_bookshelf,
        IllustR.drawable.ic_sun_bear_evening_puzzle,
        IllustR.drawable.ic_sun_bear_evening_karaoke,
        IllustR.drawable.ic_sun_bear_evening_toycars,
        IllustR.drawable.ic_sun_bear_evening_plushplay,
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
            WEEKEND_EXPLORE, MORNING_STROLL -> EXPLORE_DRAWABLES
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
            WEEKEND_EXPLORE, MORNING_STROLL ->
                return EXPLORE_DRAWABLES[
                    dailyVariantIndex(date, name, EXPLORE_DRAWABLES.size),
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
                        R.string.sun_bear_tea_lemontea,
                        R.string.sun_bear_tea_icecream,
                        R.string.sun_bear_tea_eggtart,
                        R.string.sun_bear_tea_fruit,
                        R.string.sun_bear_tea_instantnoodles,
                        R.string.sun_bear_tea_conveniencesnack,
                        R.string.sun_bear_tea_yogurt,
                        R.string.sun_bear_tea_croissant,
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
                        R.string.sun_bear_evening_balconysnack,
                        R.string.sun_bear_evening_dramacry,
                        R.string.sun_bear_evening_balconymoon,
                        R.string.sun_bear_evening_balconyhang,
                        R.string.sun_bear_evening_balconyphoto,
                        R.string.sun_bear_evening_balconybreeze,
                        R.string.sun_bear_evening_balconybbq,
                        R.string.sun_bear_evening_livingroom,
                        R.string.sun_bear_evening_projector,
                        R.string.sun_bear_evening_bookshelf,
                        R.string.sun_bear_evening_puzzle,
                        R.string.sun_bear_evening_karaoke,
                        R.string.sun_bear_evening_toycars,
                        R.string.sun_bear_evening_plushplay,
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

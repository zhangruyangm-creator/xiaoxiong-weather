package com.xiaoxiong.weather.ui.weather

import androidx.annotation.DrawableRes
import com.xiaoxiong.weather.R
import java.time.LocalDate
import com.xiaoxiong.weather.resillustrations.R as IllustR

// 各片刻的插画池与按日抽取逻辑。
// 池声明为 internal，供 SunPathBearMoment 枚举跨文件引用；其余为模块内私有实现细节。

/** 走路 / 地铁 / 公交 / 打车 / 开车 / 电瓶车 / 自行车 / 轮渡 / 磁悬浮 / 高铁 / 轻轨 / 网约车 / 索道 + 雨天走路 / 赶时间小跑 / 拎早餐 / 地铁看手机 / 雨天开车；上下班共用同一天索引。 */
internal val COMMUTE_OUT_DRAWABLES =
    intArrayOf(
        IllustR.drawable.ic_sun_bear_morning_out,
        IllustR.drawable.ic_sun_bear_morning_metro,
        IllustR.drawable.ic_sun_bear_morning_bus,
        IllustR.drawable.ic_sun_bear_morning_taxi,
        IllustR.drawable.ic_sun_bear_morning_drive,
        IllustR.drawable.ic_sun_bear_morning_ebike,
        IllustR.drawable.ic_sun_bear_morning_cycle,
        IllustR.drawable.ic_sun_bear_morning_ferry,
        IllustR.drawable.ic_sun_bear_morning_maglev,
        IllustR.drawable.ic_sun_bear_morning_hsr,
        IllustR.drawable.ic_sun_bear_morning_tram,
        IllustR.drawable.ic_sun_bear_morning_rideshare,
        IllustR.drawable.ic_sun_bear_morning_cablecar,
        IllustR.drawable.ic_sun_bear_morning_walkrain,
        IllustR.drawable.ic_sun_bear_morning_walkrun,
        IllustR.drawable.ic_sun_bear_morning_walkbreakfast,
        IllustR.drawable.ic_sun_bear_morning_metrophone,
        IllustR.drawable.ic_sun_bear_morning_driverain,
    )
internal val COMMUTE_HOME_DRAWABLES =
    intArrayOf(
        IllustR.drawable.ic_sun_bear_head_home,
        IllustR.drawable.ic_sun_bear_home_metro,
        IllustR.drawable.ic_sun_bear_home_bus,
        IllustR.drawable.ic_sun_bear_home_taxi,
        IllustR.drawable.ic_sun_bear_home_drive,
        IllustR.drawable.ic_sun_bear_home_ebike,
        IllustR.drawable.ic_sun_bear_home_cycle,
        IllustR.drawable.ic_sun_bear_home_ferry,
        IllustR.drawable.ic_sun_bear_home_maglev,
        IllustR.drawable.ic_sun_bear_home_hsr,
        IllustR.drawable.ic_sun_bear_home_tram,
        IllustR.drawable.ic_sun_bear_home_rideshare,
        IllustR.drawable.ic_sun_bear_home_cablecar,
        IllustR.drawable.ic_sun_bear_home_walkrain,
        IllustR.drawable.ic_sun_bear_home_walkrun,
        IllustR.drawable.ic_sun_bear_home_walkbreakfast,
        IllustR.drawable.ic_sun_bear_home_metrophone,
        IllustR.drawable.ic_sun_bear_home_driverain,
    )
internal val COMMUTE_OUT_CAPTIONS =
    intArrayOf(
        R.string.sun_bear_commute_walk_out,
        R.string.sun_bear_commute_metro_out,
        R.string.sun_bear_commute_bus_out,
        R.string.sun_bear_commute_taxi_out,
        R.string.sun_bear_commute_drive_out,
        R.string.sun_bear_commute_ebike_out,
        R.string.sun_bear_commute_cycle_out,
        R.string.sun_bear_commute_ferry_out,
        R.string.sun_bear_commute_maglev_out,
        R.string.sun_bear_commute_hsr_out,
        R.string.sun_bear_commute_tram_out,
        R.string.sun_bear_commute_rideshare_out,
        R.string.sun_bear_commute_cablecar_out,
        R.string.sun_bear_commute_walkrain_out,
        R.string.sun_bear_commute_walkrun_out,
        R.string.sun_bear_commute_walkbreakfast_out,
        R.string.sun_bear_commute_metrophone_out,
        R.string.sun_bear_commute_driverain_out,
    )
internal val COMMUTE_HOME_CAPTIONS =
    intArrayOf(
        R.string.sun_bear_commute_walk_home,
        R.string.sun_bear_commute_metro_home,
        R.string.sun_bear_commute_bus_home,
        R.string.sun_bear_commute_taxi_home,
        R.string.sun_bear_commute_drive_home,
        R.string.sun_bear_commute_ebike_home,
        R.string.sun_bear_commute_cycle_home,
        R.string.sun_bear_commute_ferry_home,
        R.string.sun_bear_commute_maglev_home,
        R.string.sun_bear_commute_hsr_home,
        R.string.sun_bear_commute_tram_home,
        R.string.sun_bear_commute_rideshare_home,
        R.string.sun_bear_commute_cablecar_home,
        R.string.sun_bear_commute_walkrain_home,
        R.string.sun_bear_commute_walkrun_home,
        R.string.sun_bear_commute_walkbreakfast_home,
        R.string.sun_bear_commute_metrophone_home,
        R.string.sun_bear_commute_driverain_home,
    )

internal fun commuteIndex(date: LocalDate): Int = dailyVariantIndex(date, "COMMUTE", COMMUTE_OUT_DRAWABLES.size)

@DrawableRes
internal fun commuteBearRes(
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
internal val EXERCISE_DRAWABLES =
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
        IllustR.drawable.ic_sun_bear_exercise_baseball,
    )

/**
 * 周末白天玩乐共享池（上午出门 + 下午逛逛 + 室内休闲等）。
 * [WEEKEND_MORNING] / [WEEKEND_AFTERNOON] 各自按日从该池抽一张，互不绑定。
 */
internal val WEEKEND_DAY_DRAWABLES =
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
        IllustR.drawable.ic_sun_bear_weekend_themepark,
        IllustR.drawable.ic_sun_bear_weekend_hotairballoon,
        IllustR.drawable.ic_sun_bear_weekend_arcade,
        IllustR.drawable.ic_sun_bear_weekend_festivestreet,
    )

/**
 * 探索/旅行插画池；[WEEKEND_EXPLORE] 与工作日 [MORNING_STROLL] 共用，各自按日独立抽取。
 */
internal val EXPLORE_DRAWABLES =
    intArrayOf(
        IllustR.drawable.ic_sun_bear_weekend_explore_tokyotower,
        IllustR.drawable.ic_sun_bear_weekend_explore_fuji,
        IllustR.drawable.ic_sun_bear_weekend_explore_museum,
        IllustR.drawable.ic_sun_bear_weekend_explore_boat,
        IllustR.drawable.ic_sun_bear_weekend_explore_hiking,
        IllustR.drawable.ic_sun_bear_weekend_explore_ginza,
        IllustR.drawable.ic_sun_bear_weekend_explore_roppongi,
        IllustR.drawable.ic_sun_bear_weekend_explore_shibuya,
        IllustR.drawable.ic_sun_bear_weekend_explore_shinjuku,
        IllustR.drawable.ic_sun_bear_weekend_explore_akihabara,
        IllustR.drawable.ic_sun_bear_weekend_explore_ikebukuro,
        IllustR.drawable.ic_sun_bear_weekend_explore_timessquare,
        IllustR.drawable.ic_sun_bear_weekend_explore_fifthavenue,
        IllustR.drawable.ic_sun_bear_weekend_explore_champselysees,
        IllustR.drawable.ic_sun_bear_weekend_explore_gangnam,
        IllustR.drawable.ic_sun_bear_weekend_explore_causewaybay,
        IllustR.drawable.ic_sun_bear_weekend_explore_singapore,
        IllustR.drawable.ic_sun_bear_weekend_explore_bangkok,
        IllustR.drawable.ic_sun_bear_weekend_explore_bali,
        IllustR.drawable.ic_sun_bear_weekend_explore_kualalumpur,
        IllustR.drawable.ic_sun_bear_weekend_explore_phuket,
        IllustR.drawable.ic_sun_bear_weekend_explore_chiangmai,
        IllustR.drawable.ic_sun_bear_weekend_explore_hochiminh,
        IllustR.drawable.ic_sun_bear_weekend_explore_hanoi,
        IllustR.drawable.ic_sun_bear_weekend_explore_qingdao,
        IllustR.drawable.ic_sun_bear_weekend_explore_qushuiting,
        IllustR.drawable.ic_sun_bear_weekend_explore_fuzimiao,
        IllustR.drawable.ic_sun_bear_weekend_explore_weihai,
        IllustR.drawable.ic_sun_bear_weekend_explore_wuxi,
        IllustR.drawable.ic_sun_bear_weekend_explore_weifang,
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
        IllustR.drawable.ic_sun_bear_weekend_explore_chicago,
        IllustR.drawable.ic_sun_bear_weekend_explore_jinwan,
        IllustR.drawable.ic_sun_bear_weekend_explore_jiuzhaigou,
        IllustR.drawable.ic_sun_bear_weekend_explore_mumbai,
        IllustR.drawable.ic_sun_bear_weekend_explore_lujiazui,
        IllustR.drawable.ic_sun_bear_weekend_explore_waitan,
        IllustR.drawable.ic_sun_bear_weekend_explore_jingan,
        IllustR.drawable.ic_sun_bear_weekend_explore_huaihaizhonglu,
        IllustR.drawable.ic_sun_bear_weekend_explore_wukanglu,
        IllustR.drawable.ic_sun_bear_weekend_explore_xujiahui,
        IllustR.drawable.ic_sun_bear_weekend_explore_hengshanroad,
        IllustR.drawable.ic_sun_bear_weekend_explore_huanlegangwan,
        IllustR.drawable.ic_sun_bear_weekend_explore_huanlehaian,
        IllustR.drawable.ic_sun_bear_weekend_explore_dongmen,
        IllustR.drawable.ic_sun_bear_weekend_explore_huaqiangbei,
        IllustR.drawable.ic_sun_bear_weekend_explore_futian,
        IllustR.drawable.ic_sun_bear_weekend_explore_seaworld,
        IllustR.drawable.ic_sun_bear_weekend_explore_datangbuyecheng,
        IllustR.drawable.ic_sun_bear_weekend_explore_shamian,
        IllustR.drawable.ic_sun_bear_weekend_explore_xihu,
        IllustR.drawable.ic_sun_bear_weekend_explore_wuhanjiangtan,
        IllustR.drawable.ic_sun_bear_weekend_explore_pingjianglu,
    )

internal val EXERCISE_MORNING_CAPTIONS =
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
        R.string.sun_bear_exercise_baseball_morning,
    )
internal val EXERCISE_EVENING_CAPTIONS =
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
        R.string.sun_bear_exercise_baseball_evening,
    )

internal fun exerciseIndex(
    date: LocalDate,
    moment: SunPathBearMoment,
): Int = dailyVariantIndex(date, moment.name, EXERCISE_DRAWABLES.size)

/** 通用办公室：上午/下午各一张。 */
internal val WORK_OFFICE_GENERIC_MORNING = IllustR.drawable.ic_sun_bear_work_morning
internal val WORK_OFFICE_GENERIC_AFTERNOON = IllustR.drawable.ic_sun_bear_work_afternoon

/** 办公室坐班工种（全天同一张）：程序员、会计等。 */
internal val WORK_OFFICE_JOBS =
    intArrayOf(
        IllustR.drawable.ic_sun_bear_work_programmer,
        IllustR.drawable.ic_sun_bear_work_accountant,
        IllustR.drawable.ic_sun_bear_work_lawyer,
        IllustR.drawable.ic_sun_bear_work_librarian,
        IllustR.drawable.ic_sun_bear_work_architect,
        IllustR.drawable.ic_sun_bear_work_receptionist,
    )

/** 现场 / 外出工种：当天上下午同一张。 */
internal val WORK_FIELD_JOBS =
    intArrayOf(
        IllustR.drawable.ic_sun_bear_work_brick,
        IllustR.drawable.ic_sun_bear_work_delivery,
        IllustR.drawable.ic_sun_bear_work_milkdelivery,
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
        IllustR.drawable.ic_sun_bear_work_lambskewer,
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
        IllustR.drawable.ic_sun_bear_work_gasstation,
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

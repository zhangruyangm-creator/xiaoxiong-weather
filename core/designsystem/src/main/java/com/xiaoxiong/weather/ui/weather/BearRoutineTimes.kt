package com.xiaoxiong.weather.ui.weather

/**
 * 小熊作息共用时间线边界（分钟，左闭右开）。
 * 轨迹「生活片刻」与小屋「作息时间线」共用，避免两处各自维护魔法数字导致错位。
 */
object BearRoutineTimes {
    /** 日界线：凌晨 5:00 之前仍归属前一天作息日。 */
    const val DAY_BOUNDARY_MINUTE: Int = 5 * 60

    /** 工作日晨练结束（=晨间逛逛开始）；无日出数据时晨练封顶在此。 */
    const val MORNING_EXERCISE_END_MINUTE: Int = 6 * 60 + 30

    /** 晨间逛逛结束（=早餐开始）；周末晨段合并为 [DAY_BOUNDARY_MINUTE, 此值)。 */
    const val MORNING_STROLL_END_MINUTE: Int = 7 * 60 + 15

    /** 早餐结束（=出门/通勤开始）。 */
    const val BREAKFAST_END_MINUTE: Int = 8 * 60 + 15

    /** 出门/通勤结束（=上午忙开始）。 */
    const val MORNING_OUT_END_MINUTE: Int = 9 * 60

    /** 上午忙结束（=午饭开始）。 */
    const val LUNCH_START_MINUTE: Int = 11 * 60 + 30

    /** 午饭结束（=下午开始）。 */
    const val LUNCH_END_MINUTE: Int = 12 * 60 + 30
}

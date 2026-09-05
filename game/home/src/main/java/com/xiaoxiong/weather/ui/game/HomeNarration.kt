package com.xiaoxiong.weather.ui.game

import androidx.annotation.StringRes
import com.xiaoxiong.weather.R
import com.xiaoxiong.weather.ui.weather.SunPathBearMoment

/**
 * 家内旁白：短句、不叠「小熊」前缀，适合客厅/小结/作息列表。
 * 天气页太阳弧仍用 [SunPathBearMoment.captionResFor] 的旁白语气。
 */
@StringRes
fun homeMomentNarrationRes(moment: SunPathBearMoment): Int =
    when (moment) {
        SunPathBearMoment.ALMOST_DAWN -> R.string.bear_home_narration_almost_dawn
        SunPathBearMoment.MORNING_EXERCISE -> R.string.bear_home_narration_morning_exercise
        SunPathBearMoment.MORNING_STROLL -> R.string.bear_home_narration_morning_stroll
        SunPathBearMoment.BREAKFAST -> R.string.bear_home_narration_breakfast
        SunPathBearMoment.MORNING_OUT -> R.string.bear_home_narration_morning_out
        SunPathBearMoment.WORK_MORNING -> R.string.bear_home_narration_work_morning
        SunPathBearMoment.LUNCH -> R.string.bear_home_narration_lunch
        SunPathBearMoment.WORK_AFTERNOON -> R.string.bear_home_narration_work_afternoon
        SunPathBearMoment.AFTERNOON_TEA -> R.string.bear_home_narration_afternoon_tea
        SunPathBearMoment.HEAD_HOME -> R.string.bear_home_narration_head_home
        SunPathBearMoment.EVENING_EXERCISE -> R.string.bear_home_narration_evening_exercise
        SunPathBearMoment.WEEKEND_MORNING -> R.string.bear_home_narration_weekend_morning
        SunPathBearMoment.WEEKEND_EXPLORE -> R.string.bear_home_narration_weekend_explore
        SunPathBearMoment.WEEKEND_AFTERNOON -> R.string.bear_home_narration_weekend_afternoon
        SunPathBearMoment.WEEKEND_RETURN -> R.string.bear_home_narration_weekend_return
        SunPathBearMoment.DINNER -> R.string.bear_home_narration_dinner
        SunPathBearMoment.EVENING -> R.string.bear_home_narration_evening
        SunPathBearMoment.BEDTIME_PREP -> R.string.bear_home_narration_bedtime_prep
        SunPathBearMoment.SLEEP -> R.string.bear_home_narration_sleep
    }

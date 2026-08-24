package com.xiaoxiong.weather.ui.weather

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import com.xiaoxiong.weather.R

/**
 * 插画相册（口令 `#小熊相册`）的分组数据。
 *
 * 数据完全派生自 [SunPathBearMoment] 与 `SunPathBearDrawables` 的插画池，不维护第二份清单——
 * 以后新增插画只改各片刻池，相册自动收录，无需配任何文案。
 *
 * 分组按「插画池」聚合而非按片刻，且全局去重：一张图只出现在最先包含它的分组里
 * （如探索池被晨间逛逛与周末旅行共用，只收录在「旅行·探索」节）。
 */
data class BearAlbumSection(
    @param:StringRes val titleRes: Int,
    @param:DrawableRes val drawableRes: List<Int>,
)

/** 相册节标题：按片刻语义词合并，避免出现「晨间逛逛=周末旅行」割裂感。 */
private fun sectionTitleFor(moment: SunPathBearMoment): Int =
    when (moment) {
        SunPathBearMoment.MORNING_STROLL,
        SunPathBearMoment.WEEKEND_EXPLORE,
        -> R.string.sun_bear_morning_stroll

        SunPathBearMoment.WORK_MORNING,
        SunPathBearMoment.WORK_AFTERNOON,
        -> R.string.sun_bear_work_morning

        SunPathBearMoment.MORNING_OUT,
        SunPathBearMoment.HEAD_HOME,
        -> R.string.sun_bear_morning_out

        SunPathBearMoment.MORNING_EXERCISE,
        SunPathBearMoment.EVENING_EXERCISE,
        -> R.string.sun_bear_morning_exercise

        SunPathBearMoment.WEEKEND_MORNING,
        SunPathBearMoment.WEEKEND_AFTERNOON,
        -> R.string.sun_bear_weekend_morning

        else -> moment.captionRes
    }

/**
 * 生成相册全部分组。
 *
 * 顺序 = [SunPathBearMoment.values()] 定义序（即生活片刻时间线序），
 * 特例片刻（工作/通勤/运动/周末/探索）用对应真实池展开。
 */
fun bearAlbumSections(): List<BearAlbumSection> {
    val seen = HashSet<Int>()
    val sections = ArrayList<BearAlbumSection>()
    for (moment in SunPathBearMoment.values()) {
        val pool =
            when (moment) {
                SunPathBearMoment.WORK_MORNING,
                SunPathBearMoment.WORK_AFTERNOON,
                -> workAlbumDrawables()

                SunPathBearMoment.MORNING_OUT,
                SunPathBearMoment.HEAD_HOME,
                -> commuteAlbumDrawables()

                SunPathBearMoment.MORNING_STROLL,
                SunPathBearMoment.WEEKEND_EXPLORE,
                -> EXPLORE_DRAWABLES.toList()

                SunPathBearMoment.MORNING_EXERCISE,
                SunPathBearMoment.EVENING_EXERCISE,
                -> EXERCISE_DRAWABLES.toList()

                SunPathBearMoment.WEEKEND_MORNING,
                SunPathBearMoment.WEEKEND_AFTERNOON,
                -> WEEKEND_DAY_DRAWABLES.toList()

                else -> moment.bearVariantDrawables().toList()
            }
        // 全局去重：该片刻池里已出现在更早分组的图不再重复收录
        val fresh = pool.filter { seen.add(it) }
        if (fresh.isEmpty()) continue
        sections += BearAlbumSection(sectionTitleFor(moment), fresh)
    }
    return sections
}

/** 工作：办公室通用 + 坐班工种 + 现场工种。 */
private fun workAlbumDrawables(): List<Int> =
    buildList {
        addAll(listOf(WORK_OFFICE_GENERIC_MORNING, WORK_OFFICE_GENERIC_AFTERNOON))
        addAll(WORK_OFFICE_JOBS.toList())
        addAll(WORK_FIELD_JOBS.toList())
    }

/** 通勤：上班 + 回家。 */
private fun commuteAlbumDrawables(): List<Int> =
    buildList {
        addAll(COMMUTE_OUT_DRAWABLES.toList())
        addAll(COMMUTE_HOME_DRAWABLES.toList())
    }

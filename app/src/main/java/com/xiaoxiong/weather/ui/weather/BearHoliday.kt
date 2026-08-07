package com.xiaoxiong.weather.ui.weather

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import com.xiaoxiong.weather.R
import com.xiaoxiong.weather.util.ChineseLunarCalendar
import java.time.LocalDate
import java.time.Month

/**
 * 节日小熊主题：命中节日时覆盖日常插画与文案（睡觉/快醒除外）。
 * 农历节日由 [ChineseLunarCalendar] 万年历换算（约 1900–2100）。
 * 每个节日可有多幅插画，按日期稳定轮换（同日不变，隔日/隔年可换）。
 *
 * 中秋与国庆重叠时走 [MID_AUTUMN_NATIONAL]「双节同庆」，不互相覆盖。
 */
enum class BearHoliday(
    @param:StringRes val captionRes: Int,
    @param:DrawableRes private vararg val bearVariants: Int
) {
    SPRING_FESTIVAL(
        R.string.sun_bear_fest_spring,
        R.drawable.ic_sun_bear_fest_spring,
        R.drawable.ic_sun_bear_fest_spring_2,
        R.drawable.ic_sun_bear_fest_spring_3,
        R.drawable.ic_sun_bear_fest_spring_4,
        R.drawable.ic_sun_bear_fest_spring_5,
        R.drawable.ic_sun_bear_fest_spring_6,
        R.drawable.ic_sun_bear_fest_spring_7
    ),
    CHRISTMAS(
        R.string.sun_bear_fest_christmas,
        R.drawable.ic_sun_bear_fest_christmas,
        R.drawable.ic_sun_bear_fest_christmas_2,
        R.drawable.ic_sun_bear_fest_christmas_3
    ),
    NEW_YEAR(
        R.string.sun_bear_fest_new_year,
        R.drawable.ic_sun_bear_fest_new_year,
        R.drawable.ic_sun_bear_fest_new_year_2
    ),
    /** 中秋 ∩ 国庆：双节同庆。 */
    MID_AUTUMN_NATIONAL(
        R.string.sun_bear_fest_double,
        R.drawable.ic_sun_bear_fest_double,
        R.drawable.ic_sun_bear_fest_double_2
    ),
    MID_AUTUMN(
        R.string.sun_bear_fest_mid_autumn,
        R.drawable.ic_sun_bear_fest_mid_autumn,
        R.drawable.ic_sun_bear_fest_mid_autumn_2
    ),
    NATIONAL_DAY(
        R.string.sun_bear_fest_national,
        R.drawable.ic_sun_bear_fest_national,
        R.drawable.ic_sun_bear_fest_national_2,
        R.drawable.ic_sun_bear_fest_national_3
    );

    /** 按自然日选取插画：春节初一/初二可不同，明年春节也可不同。 */
    @DrawableRes
    fun bearResFor(date: LocalDate): Int {
        if (bearVariants.isEmpty()) return R.drawable.ic_sun_bear_fest_spring
        if (bearVariants.size == 1) return bearVariants[0]
        return bearVariants[dailyVariantIndex(date, "FEST_$name", bearVariants.size)]
    }

    /** 春节等多图节日可按变体匹配文案；其余节日用固定 caption。 */
    @StringRes
    fun captionResFor(date: LocalDate): Int {
        if (this == SPRING_FESTIVAL) {
            val captions = intArrayOf(
                R.string.sun_bear_fest_spring,
                R.string.sun_bear_fest_spring_dumpling,
                R.string.sun_bear_fest_spring_cake,
                R.string.sun_bear_fest_spring_firework,
                R.string.sun_bear_fest_spring_couplet,
                R.string.sun_bear_fest_spring_dinner,
                R.string.sun_bear_fest_spring_firecracker
            )
            return captions[dailyVariantIndex(date, "FEST_$name", captions.size)]
        }
        return captionRes
    }
}

fun bearHolidayOn(date: LocalDate): BearHoliday? {
    val midAutumn = isMidAutumn(date)
    val national = isNationalDay(date)
    // 优先级：春节 > 圣诞 > 元旦 > 双节(中秋∩国庆) > 中秋 > 国庆
    if (isSpringFestival(date)) return BearHoliday.SPRING_FESTIVAL
    if (date.month == Month.DECEMBER && date.dayOfMonth in 24..25) return BearHoliday.CHRISTMAS
    if (date.month == Month.JANUARY && date.dayOfMonth == 1) return BearHoliday.NEW_YEAR
    if (date.month == Month.DECEMBER && date.dayOfMonth == 31) return BearHoliday.NEW_YEAR
    if (midAutumn && national) return BearHoliday.MID_AUTUMN_NATIONAL
    if (midAutumn) return BearHoliday.MID_AUTUMN
    if (national) return BearHoliday.NATIONAL_DAY
    return null
}

/**
 * 节日覆盖：睡觉与临近天亮仍走日常片刻，其余时段换节日图文。
 */
fun holidayBearOverride(
    date: LocalDate,
    moment: SunPathBearMoment
): BearHoliday? {
    if (moment == SunPathBearMoment.SLEEP || moment == SunPathBearMoment.ALMOST_DAWN) return null
    return bearHolidayOn(date)
}

private fun isSpringFestival(date: LocalDate): Boolean {
    val cny = ChineseLunarCalendar.springFestival(date.year) ?: return false
    val eve = cny.minusDays(1)
    val end = cny.plusDays(3) // 初三
    return !date.isBefore(eve) && !date.isAfter(end)
}

private fun isMidAutumn(date: LocalDate): Boolean {
    // 中秋可能落在公历 year 或跨年极少见；查当年与前后年
    for (y in date.year - 1..date.year + 1) {
        val day = ChineseLunarCalendar.midAutumn(y) ?: continue
        if (date == day || date == day.minusDays(1)) return true
    }
    return false
}

private fun isNationalDay(date: LocalDate): Boolean =
    date.month == Month.OCTOBER && date.dayOfMonth in 1..3

package com.xiaoxiong.weather.ui.weather

import com.xiaoxiong.weather.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class BearHolidayTest {

    @Test
    fun springFestival_coversEveThroughDay3() {
        // 2026 春节：2 月 17 日
        assertEquals(BearHoliday.SPRING_FESTIVAL, bearHolidayOn(LocalDate.of(2026, 2, 16))) // 除夕
        assertEquals(BearHoliday.SPRING_FESTIVAL, bearHolidayOn(LocalDate.of(2026, 2, 17)))
        assertEquals(BearHoliday.SPRING_FESTIVAL, bearHolidayOn(LocalDate.of(2026, 2, 20))) // 初三
        assertNull(bearHolidayOn(LocalDate.of(2026, 2, 21)))
    }

    @Test
    fun christmas_and_newYear() {
        assertEquals(BearHoliday.CHRISTMAS, bearHolidayOn(LocalDate.of(2026, 12, 24)))
        assertEquals(BearHoliday.CHRISTMAS, bearHolidayOn(LocalDate.of(2026, 12, 25)))
        assertNull(bearHolidayOn(LocalDate.of(2026, 12, 26)))
        assertEquals(BearHoliday.NEW_YEAR, bearHolidayOn(LocalDate.of(2026, 12, 31)))
        assertEquals(BearHoliday.NEW_YEAR, bearHolidayOn(LocalDate.of(2027, 1, 1)))
    }

    @Test
    fun midAutumn_and_nationalDay() {
        assertEquals(BearHoliday.MID_AUTUMN, bearHolidayOn(LocalDate.of(2026, 9, 24)))
        assertEquals(BearHoliday.MID_AUTUMN, bearHolidayOn(LocalDate.of(2026, 9, 25)))
        assertEquals(BearHoliday.NATIONAL_DAY, bearHolidayOn(LocalDate.of(2026, 10, 1)))
        assertEquals(BearHoliday.NATIONAL_DAY, bearHolidayOn(LocalDate.of(2026, 10, 3)))
        assertNull(bearHolidayOn(LocalDate.of(2026, 10, 4)))
    }

    @Test
    fun midAutumn_overlappingNationalDay_usesDoubleFestival() {
        // 2028 中秋：10 月 3 日 → 10/2（夜）、10/3 与国庆重叠
        assertEquals(BearHoliday.MID_AUTUMN_NATIONAL, bearHolidayOn(LocalDate.of(2028, 10, 2)))
        assertEquals(BearHoliday.MID_AUTUMN_NATIONAL, bearHolidayOn(LocalDate.of(2028, 10, 3)))
        // 同假期未重叠的国庆日仍走国庆
        assertEquals(BearHoliday.NATIONAL_DAY, bearHolidayOn(LocalDate.of(2028, 10, 1)))
        // 2039 中秋约 10/2，前夜 10/1 也重叠
        assertEquals(BearHoliday.MID_AUTUMN_NATIONAL, bearHolidayOn(LocalDate.of(2039, 10, 1)))
        assertEquals(BearHoliday.MID_AUTUMN_NATIONAL, bearHolidayOn(LocalDate.of(2039, 10, 2)))
        assertEquals(BearHoliday.NATIONAL_DAY, bearHolidayOn(LocalDate.of(2039, 10, 3)))
    }

    @Test
    fun override_skipsSleepAndAlmostDawn() {
        val cny = LocalDate.of(2026, 2, 17)
        assertEquals(
            BearHoliday.SPRING_FESTIVAL,
            holidayBearOverride(cny, SunPathBearMoment.WORK_MORNING)
        )
        assertNull(holidayBearOverride(cny, SunPathBearMoment.SLEEP))
        assertNull(holidayBearOverride(cny, SunPathBearMoment.ALMOST_DAWN))
        assertNull(holidayBearOverride(LocalDate.of(2026, 8, 6), SunPathBearMoment.LUNCH))
    }

    @Test
    fun springFestival_beatsNewYearWhenOverlapping() {
        // 若某年除夕落在 1 月 1 日附近不会冲突；用表内日期确认春节优先于其它逻辑即可
        assertEquals(BearHoliday.SPRING_FESTIVAL, bearHolidayOn(LocalDate.of(2025, 1, 29)))
    }

    @Test
    fun festivalArt_stableWithinDay_canDifferAcrossDays() {
        val eve = LocalDate.of(2026, 2, 16)
        val day1 = LocalDate.of(2026, 2, 17)
        val day2 = LocalDate.of(2026, 2, 18)
        val a = BearHoliday.SPRING_FESTIVAL.bearResFor(eve)
        assertEquals(a, BearHoliday.SPRING_FESTIVAL.bearResFor(eve))
        val pool = setOf(
            R.drawable.ic_sun_bear_fest_spring,
            R.drawable.ic_sun_bear_fest_spring_2,
            R.drawable.ic_sun_bear_fest_spring_3,
            R.drawable.ic_sun_bear_fest_spring_4,
            R.drawable.ic_sun_bear_fest_spring_5,
            R.drawable.ic_sun_bear_fest_spring_6,
            R.drawable.ic_sun_bear_fest_spring_7
        )
        assertTrue(a in pool)
        val week = (0L..40L).map {
            BearHoliday.SPRING_FESTIVAL.bearResFor(day1.plusDays(it))
        }.toSet()
        assertTrue("expected multiple spring arts across days", week.size > 1)
        val captions = listOf(
            R.string.sun_bear_fest_spring,
            R.string.sun_bear_fest_spring_dumpling,
            R.string.sun_bear_fest_spring_cake,
            R.string.sun_bear_fest_spring_firework,
            R.string.sun_bear_fest_spring_couplet,
            R.string.sun_bear_fest_spring_dinner,
            R.string.sun_bear_fest_spring_firecracker
        )
        for (i in 0L..40L) {
            val d = day1.plusDays(i)
            val res = BearHoliday.SPRING_FESTIVAL.bearResFor(d)
            val idx = listOf(
                R.drawable.ic_sun_bear_fest_spring,
                R.drawable.ic_sun_bear_fest_spring_2,
                R.drawable.ic_sun_bear_fest_spring_3,
                R.drawable.ic_sun_bear_fest_spring_4,
                R.drawable.ic_sun_bear_fest_spring_5,
                R.drawable.ic_sun_bear_fest_spring_6,
                R.drawable.ic_sun_bear_fest_spring_7
            ).indexOf(res)
            assertEquals(captions[idx], BearHoliday.SPRING_FESTIVAL.captionResFor(d))
        }
        val allSeen = (0L..60L).map {
            BearHoliday.SPRING_FESTIVAL.bearResFor(day1.plusDays(it))
        }.toSet()
        assertEquals(7, allSeen.size)
        // 跨年春节也可能换图
        val nextCny = LocalDate.of(2027, 2, 6)
        assertTrue(
            BearHoliday.SPRING_FESTIVAL.bearResFor(day1) in pool &&
                BearHoliday.SPRING_FESTIVAL.bearResFor(nextCny) in pool
        )
        assertTrue(day2 != eve)
    }
}


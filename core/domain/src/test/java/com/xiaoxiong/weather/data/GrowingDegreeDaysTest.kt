package com.xiaoxiong.weather.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.MonthDay

class GrowingDegreeDaysTest {
    @Test
    fun dailyGdd_ignoresTempsBelowBase() {
        assertEquals(0.0, GrowingDegreeDays.dailyGdd(4.0), 1e-9)
        assertEquals(0.0, GrowingDegreeDays.dailyGdd(5.0), 1e-9)
        assertEquals(2.5, GrowingDegreeDays.dailyGdd(7.5), 1e-9)
    }

    @Test
    fun cumulativeGdd_sumsDaily() {
        val cum = GrowingDegreeDays.cumulativeGdd(listOf(10.0, 6.0, 4.0, 8.0))
        // 10-5=5, 6-5=1, 4→0, 8-5=3 → 5,6,6,9
        assertEquals(listOf(5.0, 6.0, 6.0, 9.0), cum)
    }

    @Test
    fun shiftDays_warmerYear_returnsNegative() {
        val thisYear = List(120) { 20.0 } // 日均 20°C
        val normal = List(120) { 10.0 } // 常年 10°C
        val shift = GrowingDegreeDays.shiftDays(thisYear, normal)
        assertTrue("expected earlier (negative), got $shift", shift != null && shift < 0)
    }

    @Test
    fun shiftDays_colderYear_returnsPositive() {
        val thisYear = List(120) { 8.0 }
        val normal = List(120) { 15.0 }
        val shift = GrowingDegreeDays.shiftDays(thisYear, normal)
        assertTrue("expected later (positive), got $shift", shift != null && shift > 0)
    }

    @Test
    fun shiftDays_sameYear_returnsZeroOrNull() {
        val temps = List(120) { 12.0 }
        val shift = GrowingDegreeDays.shiftDays(temps, temps)
        assertTrue(shift == 0 || shift == null)
    }

    @Test
    fun shiftDays_tooFewDays_returnsNull() {
        assertEquals(null, GrowingDegreeDays.shiftDays(listOf(20.0, 21.0), listOf(10.0, 11.0)))
    }

    @Test
    fun shiftDays_noRecentGrowth_returnsNull() {
        // 积温无增长（如深冬），无法推算偏移
        val frozen = List(60) { 0.0 }
        assertNull(GrowingDegreeDays.shiftDays(frozen, frozen))
    }

    @Test
    fun shiftDays_extremeDiff_isClampedToBounds() {
        val n = 120
        // 前期暴暖、近一周几乎停滞：diff/rate 会被放大到数千天，必须钳到 -30
        val burstThenFlat = List(n - 7) { 30.0 } + List(7) { 5.4 }
        assertEquals(
            -GrowingDegreeDays.MAX_SHIFT_DAYS,
            GrowingDegreeDays.shiftDays(burstThenFlat, List(n) { 0.0 }),
        )

        // 常年显著更暖、今年增速极低：正向偏移同样钳到 +30
        val slowGrowth = List(n) { 5.08 }
        assertEquals(
            GrowingDegreeDays.MAX_SHIFT_DAYS,
            GrowingDegreeDays.shiftDays(slowGrowth, List(n) { 30.0 }),
        )
    }

    @Test
    fun averageByDayOfYear_averagesAcrossYearsAndMergesLeapDay() {
        val records =
            listOf(
                LocalDate.of(2024, 2, 28) to 10.0,
                LocalDate.of(2024, 2, 29) to 20.0, // 闰日应并入 2/28
                LocalDate.of(2025, 2, 28) to 14.0,
                LocalDate.of(2025, 3, 1) to 12.0,
            )
        val avg = GrowingDegreeDays.averageByDayOfYear(records)
        assertEquals(2, avg.size)
        // (10+20+14)/3 = 14.67
        assertEquals(14.667, avg.getValue(MonthDay.of(2, 28)), 0.001)
        assertEquals(12.0, avg.getValue(MonthDay.of(3, 1)), 1e-9)
    }

    @Test
    fun normalTempsUntil_leapYearUsesFeb28ValueForFeb29() {
        val normal =
            mapOf(
                MonthDay.of(2, 26) to 5.0,
                MonthDay.of(2, 27) to 6.0,
                MonthDay.of(2, 28) to 7.0,
                MonthDay.of(3, 1) to 8.0,
                MonthDay.of(3, 2) to 9.0,
            )
        val leap =
            GrowingDegreeDays.normalTempsUntil(
                normal,
                LocalDate.of(2024, 3, 2),
                LocalDate.of(2024, 2, 26),
            )
        // 闰年含 2/29：缺失的 2/29 复用 2/28 的值，序列长度与日期范围一致
        assertEquals(listOf(5.0, 6.0, 7.0, 7.0, 8.0, 9.0), leap)

        val nonLeap =
            GrowingDegreeDays.normalTempsUntil(
                normal,
                LocalDate.of(2025, 3, 2),
                LocalDate.of(2025, 2, 26),
            )
        assertEquals(listOf(5.0, 6.0, 7.0, 8.0, 9.0), nonLeap)
    }

    @Test
    fun normalTempsUntil_returnsWindowInOrder() {
        val normal =
            mapOf(
                MonthDay.of(3, 1) to 8.0,
                MonthDay.of(3, 2) to 9.0,
                MonthDay.of(3, 3) to 10.0,
                MonthDay.of(3, 4) to 11.0,
            )
        val temps =
            GrowingDegreeDays.normalTempsUntil(
                normal,
                LocalDate.of(2026, 3, 3),
                LocalDate.of(2026, 3, 1),
            )
        assertEquals(listOf(8.0, 9.0, 10.0), temps)
    }
}

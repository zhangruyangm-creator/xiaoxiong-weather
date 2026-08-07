package com.xiaoxiong.weather.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class ChineseLunarCalendarTest {

    @Test
    fun springFestival_knownDates() {
        assertEquals(LocalDate.of(1900, 1, 31), ChineseLunarCalendar.springFestival(1900))
        assertEquals(LocalDate.of(2000, 2, 5), ChineseLunarCalendar.springFestival(2000))
        assertEquals(LocalDate.of(2025, 1, 29), ChineseLunarCalendar.springFestival(2025))
        assertEquals(LocalDate.of(2026, 2, 17), ChineseLunarCalendar.springFestival(2026))
        assertEquals(LocalDate.of(2027, 2, 6), ChineseLunarCalendar.springFestival(2027))
        assertEquals(LocalDate.of(2030, 2, 3), ChineseLunarCalendar.springFestival(2030))
        assertEquals(LocalDate.of(2037, 2, 15), ChineseLunarCalendar.springFestival(2037))
        assertEquals(LocalDate.of(2040, 2, 12), ChineseLunarCalendar.springFestival(2040))
        assertEquals(LocalDate.of(2050, 1, 23), ChineseLunarCalendar.springFestival(2050))
        assertEquals(LocalDate.of(2060, 1, 28), ChineseLunarCalendar.springFestival(2060))
        assertEquals(LocalDate.of(2100, 2, 4), ChineseLunarCalendar.springFestival(2100))
    }

    @Test
    fun midAutumn_knownDates() {
        assertEquals(LocalDate.of(2025, 10, 6), ChineseLunarCalendar.midAutumn(2025))
        assertEquals(LocalDate.of(2026, 9, 25), ChineseLunarCalendar.midAutumn(2026))
        assertEquals(LocalDate.of(2028, 10, 3), ChineseLunarCalendar.midAutumn(2028))
        assertEquals(LocalDate.of(2040, 9, 20), ChineseLunarCalendar.midAutumn(2040))
    }

    @Test
    fun outOfRange_returnsNull() {
        assertNull(ChineseLunarCalendar.springFestival(1899))
        assertNull(ChineseLunarCalendar.springFestival(2101))
        assertNull(ChineseLunarCalendar.midAutumn(1899))
    }

    @Test
    fun supportedRange_everyYearHasSpringFestival() {
        assertTrue(ChineseLunarCalendar.isSupported(1900))
        assertTrue(ChineseLunarCalendar.isSupported(2100))
        for (y in 1900..2100) {
            val d = ChineseLunarCalendar.springFestival(y)
            assertNotNull(d)
            assertTrue("CNY $y should be Jan/Feb", d!!.monthValue in 1..2)
        }
    }

    @Test
    fun leapMonth_treatsNibbleFAsNone() {
        // 2037 表项低四位为 0xF，表示无闰月
        assertEquals(0, ChineseLunarCalendar.leapMonth(2037))
    }
}

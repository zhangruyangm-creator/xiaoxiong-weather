package com.xiaoxiong.weather.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HourlyUtilsTest {
    @Test
    fun hourKey_takesYearMonthDayHour() {
        assertEquals("2026-08-02T14", hourKey("2026-08-02T14:35:00"))
        assertEquals("2026-08-02T09", hourKey("2026-08-02T09:00"))
    }

    @Test
    fun isSameHour_comparesHourBucket() {
        assertTrue(isSameHour("2026-08-02T12:05", "2026-08-02T12:59"))
        assertEquals(false, isSameHour("2026-08-02T12:00", "2026-08-02T13:00"))
    }

    @Test
    fun indexOfCurrentHour_exactMatch() {
        val times =
            listOf(
                "2026-08-02T10:00",
                "2026-08-02T11:00",
                "2026-08-02T12:00",
                "2026-08-02T13:00",
            )
        assertEquals(2, indexOfCurrentHour(times, "2026-08-02T12:30:00"))
    }

    @Test
    fun indexOfCurrentHour_emptyAndUnparseable() {
        assertEquals(-1, indexOfCurrentHour(emptyList(), "2026-08-02T12:00"))
        assertEquals(0, indexOfCurrentHour(listOf("2026-08-02T10:00"), "not-a-time"))
    }

    @Test
    fun indexOfCurrentHour_nearestWhenNoExact() {
        val times = listOf("2026-08-02T10:00", "2026-08-02T13:00")
        // 11:40 closer to 13:00? 10:00 is 100min away, 13:00 is 80min → 13:00
        assertEquals(1, indexOfCurrentHour(times, "2026-08-02T11:40"))
    }

    @Test
    fun upcomingFromCurrent_dropsPastAndLimits() {
        data class Item(
            val time: String,
        )
        val items =
            listOf(
                Item("2026-08-02T10:00"),
                Item("2026-08-02T11:00"),
                Item("2026-08-02T12:00"),
                Item("2026-08-02T13:00"),
                Item("2026-08-02T14:00"),
            )
        val upcoming = upcomingFromCurrent(items, { it.time }, "2026-08-02T12:00", count = 2)
        assertEquals(
            listOf("2026-08-02T12:00", "2026-08-02T13:00"),
            upcoming.map { it.time },
        )
    }

    @Test
    fun recentHoursUpToCurrent_keepsPastIncludingNow() {
        data class Item(
            val time: String,
        )
        val items =
            listOf(
                Item("2026-08-02T10:00"),
                Item("2026-08-02T11:00"),
                Item("2026-08-02T12:00"),
                Item("2026-08-02T13:00"),
            )
        val recent = recentHoursUpToCurrent(items, { it.time }, "2026-08-02T12:00", count = 2)
        assertEquals(
            listOf("2026-08-02T11:00", "2026-08-02T12:00"),
            recent.map { it.time },
        )
    }

    @Test
    fun hoursBetween_nonNegative() {
        assertEquals(3, hoursBetween("2026-01-10T09:00", "2026-01-10T12:00"))
        assertEquals(0, hoursBetween("2026-01-10T12:00", "2026-01-10T12:30"))
    }

    @Test
    fun parseFlexibleDateTime_supportsCommonFormats() {
        assertEquals(14, parseFlexibleDateTime("2026-08-02T14:35:00")?.hour)
        assertEquals(14, parseFlexibleDateTime("2026-08-02T14:35")?.hour)
        assertEquals(14, parseFlexibleDateTime("2026-08-02 14:35")?.hour)
        assertEquals(0, parseFlexibleDateTime("2026-08-02")?.hour)
        assertNull(parseFlexibleDateTime("garbage"))
    }

    @Test
    fun formatHourDetailLabel_todayTomorrowOrDate() {
        val ref = "2026-08-05T07:30"
        val date = { m: Int, d: Int -> "${m}月${d}日" }
        assertEquals(
            "今天 14:00",
            formatHourDetailLabel("2026-08-05T14:00", ref, "今天", "明天", date),
        )
        assertEquals(
            "明天 09:00",
            formatHourDetailLabel("2026-08-06T09:00", ref, "今天", "明天", date),
        )
        assertEquals(
            "8月7日 12:00",
            formatHourDetailLabel("2026-08-07T12:00", ref, "今天", "明天", date),
        )
    }
}

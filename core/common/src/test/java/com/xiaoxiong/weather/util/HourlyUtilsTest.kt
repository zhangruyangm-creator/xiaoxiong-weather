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
    fun isDaytimeHour_boundaries() {
        assertTrue(isDaytimeHour(6))
        assertTrue(isDaytimeHour(17))
        // 18:00 起算夜间（与 SunPath 兜底、MET 口径对齐）
        assertEquals(false, isDaytimeHour(18))
        assertEquals(false, isDaytimeHour(5))
        assertEquals(false, isDaytimeHour(0))
        assertEquals(false, isDaytimeHour(23))
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
    fun parseFlexibleDateTime_boundaries() {
        // 年末分钟边界
        val endOfYear = parseFlexibleDateTime("2026-12-31T23:59")!!
        assertEquals(2026, endOfYear.year)
        assertEquals(23, endOfYear.hour)
        assertEquals(59, endOfYear.minute)
        // 带小数秒的 ISO
        assertEquals(14, parseFlexibleDateTime("2026-08-02T14:35:00.123")?.hour)
        // 带秒 + 时区偏移
        assertEquals(
            java.time.OffsetDateTime
                .parse("2026-08-02T14:35:30+08:00")
                .atZoneSameInstant(java.time.ZoneId.systemDefault())
                .toLocalDateTime(),
            parseFlexibleDateTime("2026-08-02T14:35:30+08:00"),
        )
        // 非法日期（月/日越界）→ null，不得抛出
        assertNull(parseFlexibleDateTime("2026-13-40T25:61"))
    }

    @Test
    fun parseFlexibleDateTime_supportsTimezoneSuffix() {
        val javaTime =
            java.time.OffsetDateTime
                .parse("2026-08-02T14:35+08:00")
                .atZoneSameInstant(java.time.ZoneId.systemDefault())
                .toLocalDateTime()
        assertEquals(javaTime, parseFlexibleDateTime("2026-08-02T14:35+08:00"))
        val utc =
            java.time.OffsetDateTime
                .parse("2026-08-02T14:35Z")
                .atZoneSameInstant(java.time.ZoneId.systemDefault())
                .toLocalDateTime()
        assertEquals(utc, parseFlexibleDateTime("2026-08-02T14:35Z"))
    }

    @Test
    fun formatHourLabel_supportsTAndSpaceSeparators() {
        assertEquals("14:35", formatHourLabel("2026-08-02T14:35"))
        assertEquals("14:35", formatHourLabel("2026-08-02T14:35:00"))
        assertEquals("14:35", formatHourLabel("2026-08-02 14:35"))
    }

    @Test
    fun formatHourLabel_pureDate_returnedAsIs() {
        // 纯日期没有时刻：旧逻辑会截成 "2026-"，现在原样返回
        assertEquals("2026-08-02", formatHourLabel("2026-08-02"))
    }

    @Test
    fun formatHourLabel_boundariesAndShortInputs() {
        assertEquals("00:00", formatHourLabel("2026-08-02T00:00"))
        assertEquals("23:59", formatHourLabel("2026-08-02T23:59"))
        // 带秒/时区偏移：仍取墙钟时刻
        assertEquals("14:35", formatHourLabel("2026-08-02T14:35:30+08:00"))
        // 数据异常（无分钟）时退回截断结果，不抛异常
        assertEquals("14", formatHourLabel("2026-08-02T14"))
    }

    @Test
    fun formatHourLabel_usesUsLocaleForDigits() {
        val original = java.util.Locale.getDefault()
        try {
            // 阿拉伯语区域默认会输出本地数字形；统一 Locale.US 后必须仍是 ASCII 数字
            java.util.Locale.setDefault(java.util.Locale.forLanguageTag("ar-EG"))
            assertEquals("14:35", formatHourLabel("2026-08-02T14:35"))
            assertEquals("今天 09:00", formatHourDetailLabel("2026-08-02T09:00", "2026-08-02T07:30", "今天", "明天") { m, d -> "$m$d" })
        } finally {
            java.util.Locale.setDefault(original)
        }
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

    @Test
    fun formatHourDetailLabel_unparseable_fallsBackToLabelNotBrokenPrefix() {
        assertEquals(
            "garbage",
            formatHourDetailLabel("garbage", "2026-08-05T07:30", "今天", "明天") { m, d -> "$m/$d" },
        )
    }

    @Test
    fun upcomingFromCurrent_acceptsListAndKeepsBehaviorWithManyItems() {
        data class Item(
            val time: String,
        )
        val items = (0..30).map { Item("2026-08-02T%02d:00".format(it % 24)) }
        val upcoming = upcomingFromCurrent(items, { it.time }, "2026-08-02T12:00", count = 3)
        assertEquals(
            listOf("2026-08-02T12:00", "2026-08-02T13:00", "2026-08-02T14:00"),
            upcoming.map { it.time },
        )
    }
}

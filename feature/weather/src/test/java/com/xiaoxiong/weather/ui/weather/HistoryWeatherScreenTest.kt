package com.xiaoxiong.weather.ui.weather

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.util.Locale

class HistoryWeatherScreenTest {
    @Test
    fun monthKey_asciiZeroPadded_regardlessOfDefaultLocale() {
        val original = Locale.getDefault()
        try {
            // 某些地区的数字格式会输出本地化数字/分隔符；固定 Locale.US 后必须稳定
            Locale.setDefault(Locale.forLanguageTag("ar-EG"))
            assertEquals("2026-08", monthKey(2026, 8))
            assertEquals("2026-11", monthKey(2026, 11))
        } finally {
            Locale.setDefault(original)
        }
    }

    @Test
    fun historyDaysByMonth_groupsByIsoPrefix_andKeepsOrder() {
        val days =
            listOf(
                testHistoryWeather(LocalDate.parse("2026-07-31")),
                testHistoryWeather(LocalDate.parse("2026-08-01")),
                testHistoryWeather(LocalDate.parse("2026-08-15")),
            )

        val grouped = historyDaysByMonth(days)

        assertEquals(listOf("2026-07", "2026-08"), grouped.keys.toList())
        assertEquals(
            listOf("2026-08-01", "2026-08-15"),
            grouped.getValue("2026-08").map { it.date },
        )
    }
}

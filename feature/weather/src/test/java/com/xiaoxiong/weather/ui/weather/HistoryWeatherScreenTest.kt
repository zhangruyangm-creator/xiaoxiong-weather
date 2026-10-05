package com.xiaoxiong.weather.ui.weather

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
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

    @Test
    fun restoreReplay_citySwitchThenTabSwitch_doesNotRequery() {
        val replay = HistoryRestoreReplayOnce()

        fun autoQuery(): LocalDate? =
            if (replay.tryConsume()) {
                pendingHistoryDayRestore(
                    pickedDay = "2026-08-11",
                    hasWeather = false,
                    isLoading = false,
                    failed = false,
                    networkFailed = false,
                )
            } else {
                null
            }

        // 进程恢复后首次组合：补发恢复出来的日期
        assertEquals(LocalDate.parse("2026-08-11"), autoQuery())
        // 换城清空数据后切 Tab：闸门已消费，不再静默重发请求
        assertNull(autoQuery())
    }

    @Test
    fun pendingHistoryDayRestore_restoredPickWithoutData_requestsThatDay() {
        assertEquals(
            LocalDate.parse("2026-08-11"),
            pendingHistoryDayRestore(
                pickedDay = "2026-08-11",
                hasWeather = false,
                isLoading = false,
                failed = false,
                networkFailed = false,
            ),
        )
    }

    @Test
    fun pendingHistoryDayRestore_dataOrFailureOrLoading_doesNotRequest() {
        // 未选日期 / 非法日期
        assertNull(pendingHistoryDayRestore(null, false, false, false, false))
        assertNull(pendingHistoryDayRestore("bad-date", false, false, false, false))
        // 已有数据 / loading / 无数据失败 / 网络失败都不自动重查
        assertNull(pendingHistoryDayRestore("2026-08-11", true, false, false, false))
        assertNull(pendingHistoryDayRestore("2026-08-11", false, true, false, false))
        assertNull(pendingHistoryDayRestore("2026-08-11", false, false, true, false))
        assertNull(pendingHistoryDayRestore("2026-08-11", false, false, false, true))
    }

    @Test
    fun pendingHistoryRangeRestore_matchingKeyWithoutData_requestsRange() {
        assertEquals(
            LocalDate.parse("2026-07-01") to LocalDate.parse("2026-07-15"),
            pendingHistoryRangeRestore(
                start = "2026-07-01",
                end = "2026-07-15",
                queriedKey = "2026-07-01|2026-07-15",
                hasSummary = false,
                isLoading = false,
                failed = false,
                networkFailed = false,
            ),
        )
    }

    @Test
    fun pendingHistoryRangeRestore_mismatchedKeyOrState_doesNotRequest() {
        // 日期缺失 / 已查询键与当前所选区间不一致 / 尚未查询过
        assertNull(pendingHistoryRangeRestore(null, "2026-07-15", "2026-07-01|2026-07-15", false, false, false, false))
        assertNull(pendingHistoryRangeRestore("2026-07-02", "2026-07-15", "2026-07-01|2026-07-15", false, false, false, false))
        assertNull(pendingHistoryRangeRestore("2026-07-01", "2026-07-15", null, false, false, false, false))
        // 已有数据 / loading / 无数据失败 / 网络失败都不自动重查
        assertNull(pendingHistoryRangeRestore("2026-07-01", "2026-07-15", "2026-07-01|2026-07-15", true, false, false, false))
        assertNull(pendingHistoryRangeRestore("2026-07-01", "2026-07-15", "2026-07-01|2026-07-15", false, true, false, false))
        assertNull(pendingHistoryRangeRestore("2026-07-01", "2026-07-15", "2026-07-01|2026-07-15", false, false, true, false))
        assertNull(pendingHistoryRangeRestore("2026-07-01", "2026-07-15", "2026-07-01|2026-07-15", false, false, false, true))
    }

    @Test
    fun historyCacheKeys_useUsDecimalRegardlessOfDefaultLocale() {
        val original = Locale.getDefault()
        val city =
            com.xiaoxiong.weather.data.City(
                id = 1L,
                name = "济南",
                latitude = 36.65,
                longitude = 117.12,
            )
        try {
            Locale.setDefault(Locale.GERMANY)
            assertEquals(
                "36.6500,117.1200|2026-08-11",
                historyDayCacheKey(city, LocalDate.parse("2026-08-11")),
            )
            assertEquals(
                "36.6500,117.1200|2026-07-01|2026-07-15",
                historyRangeCacheKey(city, LocalDate.parse("2026-07-01"), LocalDate.parse("2026-07-15")),
            )
        } finally {
            Locale.setDefault(original)
        }
    }
}

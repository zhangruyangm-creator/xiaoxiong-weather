package com.xiaoxiong.weather.data

import com.xiaoxiong.weather.fixtures.WeatherFixtures
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

/**
 * 离线跨午夜读取缓存时的 daily 裁剪：按快照时区丢弃早于「今天」的条目，
 * 避免首页高低温、七日页「今天」、UV/预警整体错位一天。
 */
class WeatherCacheDailyFreshnessTest {
    private val today = LocalDate.of(2026, 9, 13)

    private fun snapshot(
        dates: List<String>,
        timezone: String? = "Asia/Shanghai",
    ): WeatherSnapshot =
        WeatherSnapshot(
            city = WeatherFixtures.city("济南"),
            current = WeatherFixtures.current(),
            daily = dates.map { WeatherFixtures.daily(date = it, tempMaxC = 30.0) },
            timezone = timezone,
        )

    @Test
    fun dropStaleDaily_crossMidnight_dropsYesterdayKeepsTodayAndFuture() {
        val result =
            dropStaleDaily(
                snapshot(listOf("2026-09-12", "2026-09-13", "2026-09-14")),
                today,
            )
        assertEquals(listOf("2026-09-13", "2026-09-14"), result.daily.map { it.date })
    }

    @Test
    fun dropStaleDaily_sameDay_keepsAllEntriesUntouched() {
        val original = snapshot(listOf("2026-09-13", "2026-09-14"))
        val result = dropStaleDaily(original, today)
        assertEquals(listOf("2026-09-13", "2026-09-14"), result.daily.map { it.date })
        assertSame(original, result)
    }

    @Test
    fun dropStaleDaily_allStale_keepsLastEntry() {
        val result =
            dropStaleDaily(
                snapshot(listOf("2026-09-10", "2026-09-11", "2026-09-12")),
                today,
            )
        assertEquals(listOf("2026-09-12"), result.daily.map { it.date })
    }

    @Test
    fun dropStaleDaily_unparseableDate_isKept() {
        val original = snapshot(listOf("not-a-date"))
        val result = dropStaleDaily(original, today)
        assertEquals(listOf("not-a-date"), result.daily.map { it.date })
        assertSame(original, result)
    }

    @Test
    fun dropStaleDaily_crossYear_dropsPreviousYearKeepsNewYear() {
        val result =
            dropStaleDaily(
                snapshot(listOf("2025-12-31", "2026-01-01", "2026-01-02")),
                LocalDate.of(2026, 1, 1),
            )
        assertEquals(listOf("2026-01-01", "2026-01-02"), result.daily.map { it.date })
    }

    @Test
    fun dropStaleDaily_crossYearAllStale_keepsLastEntry() {
        val result =
            dropStaleDaily(
                snapshot(listOf("2025-12-30", "2025-12-31")),
                LocalDate.of(2026, 1, 1),
            )
        assertEquals(listOf("2025-12-31"), result.daily.map { it.date })
    }

    @Test
    fun todayInSnapshotZone_missingTimezone_fallsBackToProvidedZone() {
        val zone = ZoneId.of("Pacific/Kiritimati")
        assertEquals(LocalDate.now(zone), todayInSnapshotZone(null, zone))
    }

    @Test
    fun todayInSnapshotZone_invalidTimezone_fallsBackToProvidedZone() {
        val zone = ZoneId.of("UTC")
        assertEquals(LocalDate.now(zone), todayInSnapshotZone("Not/AZone", zone))
    }

    @Test
    fun todayInSnapshotZone_namedZone_matchesLocalDateNowInThatZone() {
        val zone = ZoneId.of("Pacific/Honolulu")
        assertEquals(LocalDate.now(zone), todayInSnapshotZone("Pacific/Honolulu"))
    }
}

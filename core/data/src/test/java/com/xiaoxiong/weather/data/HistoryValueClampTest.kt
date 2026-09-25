package com.xiaoxiong.weather.data

import com.xiaoxiong.weather.data.remote.ArchiveDailyDto
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 历史（Archive/ERA5）数据的物理量夹紧。
 *
 * 背景：夹紧最初只接在 Open-Meteo 实况路径，历史与备用源都绕过了——离谱值照样
 * 进折线图与极值摘要。这里锁住"历史走同一套区间"，防止缺口再次出现。
 */
class HistoryValueClampTest {
    private val fetcher = HistoryFetcher(OkHttpClient(), Json, "https://archive.test")

    private fun daily(
        tempMax: Double? = 30.0,
        tempMin: Double? = 20.0,
        tempMean: Double? = 25.0,
        precip: Double? = 5.0,
        humidity: Int? = 60,
        windMax: Double? = 20.0,
    ) = ArchiveDailyDto(
        time = listOf("2026-09-01"),
        weatherCode = listOf(0),
        tempMax = listOf(tempMax),
        tempMin = listOf(tempMin),
        temperatureMean = listOf(tempMean),
        precipSum = listOf(precip),
        humidityMean = listOf(humidity),
        windMax = listOf(windMax),
        sunrise = listOf("2026-09-01T06:00"),
        sunset = listOf("2026-09-01T18:00"),
    )

    @Test
    fun `sane archive day passes through unchanged`() {
        val day = fetcher.archiveDailyToHistory(daily(), index = 0, date = "2026-09-01")
        assertEquals(30.0, day.tempMaxC)
        assertEquals(20.0, day.tempMinC)
        assertEquals(25.0, day.tempMeanC)
        assertEquals(5.0, day.precipSumMm)
        assertEquals(60, day.humidityMeanPercent)
        assertEquals(20.0, day.windMaxKmh)
    }

    @Test
    fun `absurd humidity is clamped into percent range`() {
        val day = fetcher.archiveDailyToHistory(daily(humidity = 5000), index = 0, date = "2026-09-01")
        assertEquals(100, day.humidityMeanPercent)
    }

    @Test
    fun `absurd temperature and wind are clamped`() {
        val day =
            fetcher.archiveDailyToHistory(
                daily(tempMax = 1e9, tempMin = -1e9, windMax = 1e308),
                index = 0,
                date = "2026-09-01",
            )
        assertEquals(70.0, day.tempMaxC!!, 0.0)
        assertEquals(-100.0, day.tempMinC!!, 0.0)
        assertEquals(500.0, day.windMaxKmh!!, 0.0)
    }

    @Test
    fun `negative precipitation is clamped to zero`() {
        val day = fetcher.archiveDailyToHistory(daily(precip = -5.0), index = 0, date = "2026-09-01")
        assertEquals(0.0, day.precipSumMm!!, 0.0)
    }

    @Test
    fun `real extreme values are preserved`() {
        // 日降水世界纪录约 1825mm，不能被夹小
        val day =
            fetcher.archiveDailyToHistory(
                daily(precip = 1825.0, tempMax = 56.7),
                index = 0,
                date = "2026-09-01",
            )
        assertEquals(1825.0, day.precipSumMm!!, 0.0)
        assertEquals(56.7, day.tempMaxC!!, 0.0)
    }

    @Test
    fun `missing values stay null rather than becoming zero`() {
        val day =
            fetcher.archiveDailyToHistory(
                daily(tempMax = null, precip = null, humidity = null, windMax = null),
                index = 0,
                date = "2026-09-01",
            )
        assertEquals(null, day.tempMaxC)
        assertEquals(null, day.precipSumMm)
        assertEquals(null, day.humidityMeanPercent)
        assertEquals(null, day.windMaxKmh)
    }
}

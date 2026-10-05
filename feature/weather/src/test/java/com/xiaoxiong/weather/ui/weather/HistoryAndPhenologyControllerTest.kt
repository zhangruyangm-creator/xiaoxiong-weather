package com.xiaoxiong.weather.ui.weather

import com.xiaoxiong.weather.fixtures.WeatherFixtures
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

@OptIn(ExperimentalCoroutinesApi::class)
class HistoryAndPhenologyControllerTest {
    @Test
    fun ensurePhenologyShift_queriesArchiveUsingCityTimezoneToday() =
        runTest {
            val tokyo =
                WeatherFixtures.city("东京", lat = 35.68, lon = 139.76).copy(
                    timezone = "Asia/Tokyo",
                )
            val cityToday = LocalDate.of(2026, 1, 2)
            var requestedToday: LocalDate? = null
            val fetcher =
                RecordingFetcher(
                    phenologyBlock = { _, today ->
                        requestedToday = today
                        -3
                    },
                )
            val controller =
                HistoryAndPhenologyController(
                    scope = this,
                    repository = fetcher,
                    phenologyCache = FakePhenologyCache(),
                    updateState = {},
                    currentCity = { tokyo },
                    todayOf = { timezoneId ->
                        assertEquals("Asia/Tokyo", timezoneId)
                        cityToday
                    },
                )

            controller.ensurePhenologyShift()
            advanceUntilIdle()

            assertEquals(cityToday, requestedToday)
            assertEquals(1, fetcher.phenologyCallCount)
        }
}

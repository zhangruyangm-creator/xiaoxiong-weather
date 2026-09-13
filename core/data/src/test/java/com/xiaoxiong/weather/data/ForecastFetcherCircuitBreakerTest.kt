package com.xiaoxiong.weather.data

import android.content.res.Resources
import com.xiaoxiong.weather.fixtures.WeatherFixtures
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.mockito.Mockito
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/**
 * 主源短期故障标记（熔断）行为：
 * - 主源瞬时故障并降级成功后，短时间内直接跳主源走 MET；
 * - MET 也失败时仍会尝试一次主源，主源成功即清零标记；
 * - 主源正常时不打 MET。
 */
class ForecastFetcherCircuitBreakerTest {
    private lateinit var mainServer: MockWebServer
    private lateinit var metServer: MockWebServer
    private lateinit var fetcher: ForecastFetcher

    private val city = WeatherFixtures.city("北京", lat = 39.9042, lon = 116.4074)
    private val forecastRequests = AtomicInteger()

    @Volatile
    private var forecastResponse: MockResponse = MockResponse()

    private val forecastJson =
        """
        {
          "latitude": 39.9042,
          "longitude": 116.4074,
          "timezone": "Asia/Shanghai",
          "current": { "time": "2026-08-02T12:00", "temperature_2m": 22.0, "weather_code": 0 },
          "hourly": {
            "time": ["2026-08-02T12:00"],
            "temperature_2m": [22.0],
            "weather_code": [0]
          },
          "daily": {
            "time": ["2026-08-02"],
            "weather_code": [0],
            "temperature_2m_max": [30.0],
            "temperature_2m_min": [20.0]
          }
        }
        """.trimIndent()

    @Before
    fun setUp() {
        forecastResponse = MockResponse().setResponseCode(200).setBody(forecastJson)
        mainServer = MockWebServer()
        mainServer.start()
        metServer = MockWebServer()
        metServer.start()
        val client =
            OkHttpClient
                .Builder()
                .connectTimeout(2, TimeUnit.SECONDS)
                .readTimeout(2, TimeUnit.SECONDS)
                .build()
        fetcher =
            ForecastFetcher(
                resources = mockResources(),
                client = client,
                json = Json { ignoreUnknownKeys = true },
                forecastBase = mainServer.url("v1").toString().trimEnd('/'),
                airQualityBase = mainServer.url("v1").toString().trimEnd('/'),
                metNorwayBase = metServer.url("").toString().trimEnd('/'),
            )
        mainServer.dispatcher =
            object : Dispatcher() {
                override fun dispatch(request: RecordedRequest): MockResponse {
                    val path = request.path.orEmpty()
                    return when {
                        path.contains("air-quality") -> MockResponse().setResponseCode(503).setBody("unavailable")
                        path.contains("forecast") -> {
                            forecastRequests.incrementAndGet()
                            forecastResponse
                        }
                        else -> MockResponse().setResponseCode(404)
                    }
                }
            }
    }

    @After
    fun tearDown() {
        mainServer.shutdown()
        metServer.shutdown()
    }

    @Test
    fun transientFailure_opensCircuitAndSkipsPrimaryOnNextFetch() =
        runBlocking {
            forecastResponse = MockResponse().setResponseCode(503).setBody("unavailable")
            metServer.enqueue(metResponse())

            val first = fetcher.fetchWeather(city)
            assertEquals(1, forecastRequests.get())
            assertEquals(1, metServer.requestCount)
            assertEquals(city.latitude, first.city.latitude, 0.0001)

            // 主源已恢复，但熔断期内应直接走 MET，不再尝试主源
            forecastResponse = MockResponse().setResponseCode(200).setBody(forecastJson)
            metServer.enqueue(metResponse())
            fetcher.fetchWeather(city)

            assertEquals(1, forecastRequests.get())
            assertEquals(2, metServer.requestCount)
        }

    @Test
    fun circuitOpenAndMetFails_fallsBackToPrimaryAndClearsCircuit() =
        runBlocking {
            forecastResponse = MockResponse().setResponseCode(503).setBody("unavailable")
            metServer.enqueue(metResponse())
            fetcher.fetchWeather(city)
            assertEquals(1, forecastRequests.get())

            // 熔断期内 MET 也失败 → 尝试一次主源（已恢复）→ 成功并清零标记
            forecastResponse = MockResponse().setResponseCode(200).setBody(forecastJson)
            metServer.enqueue(MockResponse().setResponseCode(500).setBody("boom"))
            val second = fetcher.fetchWeather(city)

            assertEquals(22.0, second.current.temperatureC)
            assertEquals(2, forecastRequests.get())
            assertEquals(2, metServer.requestCount)

            // 标记已清零：下一次直接走主源，不再请求 MET
            val third = fetcher.fetchWeather(city)
            assertEquals(22.0, third.current.temperatureC)
            assertEquals(3, forecastRequests.get())
            assertEquals(2, metServer.requestCount)
        }

    @Test
    fun healthyPrimary_neverHitsMet() =
        runBlocking {
            fetcher.fetchWeather(city)
            fetcher.fetchWeather(city)

            assertEquals(2, forecastRequests.get())
            assertEquals(0, metServer.requestCount)
        }

    private fun metResponse(): MockResponse {
        val base = Instant.now().truncatedTo(ChronoUnit.HOURS).minusSeconds(2 * 3600)
        val entries =
            (0L..5L).joinToString(",") { offset ->
                val time = base.plusSeconds(offset * 3600)
                val temp = 10.0 + offset
                """
                {
                  "time": "$time",
                  "data": {
                    "instant": { "details": {
                      "air_temperature": $temp, "relative_humidity": 55.0,
                      "wind_speed": 3.0, "wind_from_direction": 180.0,
                      "air_pressure_at_sea_level": 1008.0, "cloud_area_fraction": 30.0
                    } },
                    "next_1_hours": {
                      "summary": { "symbol_code": "clearsky_day" },
                      "details": { "precipitation_amount": 0.0, "probability_of_precipitation": 10.0 }
                    }
                  }
                }
                """.trimIndent()
            }
        return MockResponse().setBody("""{"properties":{"timeseries":[$entries]}}""")
    }

    private fun mockResources(): Resources {
        val resources = Mockito.mock(Resources::class.java)
        Mockito.`when`(resources.getString(Mockito.anyInt())).thenReturn("x")
        Mockito.`when`(resources.getString(Mockito.anyInt(), Mockito.any())).thenReturn("x")
        return resources
    }
}

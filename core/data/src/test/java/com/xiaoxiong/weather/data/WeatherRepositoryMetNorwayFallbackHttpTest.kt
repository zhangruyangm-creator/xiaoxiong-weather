package com.xiaoxiong.weather.data

import android.content.res.Resources
import com.xiaoxiong.weather.fixtures.WeatherFixtures
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import okhttp3.mockwebserver.SocketPolicy
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.Mockito
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.concurrent.TimeUnit

class WeatherRepositoryMetNorwayFallbackHttpTest {
    private lateinit var mainServer: MockWebServer
    private lateinit var metServer: MockWebServer
    private lateinit var repository: WeatherRepository

    private val city = WeatherFixtures.city("北京", lat = 39.9042, lon = 116.4074)

    @Before
    fun setUp() {
        mainServer = MockWebServer()
        mainServer.start()
        metServer = MockWebServer()
        metServer.start()
        val base = mainServer.url("v1").toString().trimEnd('/')
        val client =
            OkHttpClient
                .Builder()
                .connectTimeout(2, TimeUnit.SECONDS)
                .readTimeout(2, TimeUnit.SECONDS)
                .build()
        repository =
            WeatherRepository(
                resources = mockResources(),
                client = client,
                searchClient = client,
                geocodingBase = base,
                forecastBase = base,
                airQualityBase = base,
                archiveBase = base,
                metNorwayBase = metServer.url("").toString().trimEnd('/'),
            )
    }

    @After
    fun tearDown() {
        mainServer.shutdown()
        metServer.shutdown()
    }

    @Test
    fun forecast5xx_fallsBackToMetNorway() =
        runBlocking {
            mainServer.dispatcher = mainDispatcher(forecast = MockResponse().setResponseCode(503).setBody("unavailable"))
            metServer.enqueue(MockResponse().setResponseCode(200).setBody(metJson()))

            val snapshot = repository.fetchWeather(city)

            assertEquals("Asia/Shanghai", snapshot.timezone)
            assertNull(snapshot.airQuality)
            assertTrue(snapshot.hourly.isNotEmpty())
            assertTrue(snapshot.daily.isNotEmpty())
            assertTrue(snapshot.current.temperatureC == 12.0 || snapshot.current.temperatureC == 13.0)

            val metRequest = metServer.takeRequest()
            assertTrue(metRequest.path!!.contains("locationforecast/2.0/compact"))
            assertEquals("39.9042", metRequest.requestUrl?.queryParameter("lat"))
            assertEquals("116.4074", metRequest.requestUrl?.queryParameter("lon"))
            assertTrue(metRequest.getHeader("User-Agent")!!.contains("XiaoxiongWeather"))
        }

    @Test
    fun forecastNetworkFailure_fallsBackToMetNorway() =
        runBlocking {
            mainServer.dispatcher =
                mainDispatcher(
                    forecast = MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AT_START),
                )
            metServer.enqueue(MockResponse().setResponseCode(200).setBody(metJson()))

            val snapshot = repository.fetchWeather(city)

            assertEquals("Asia/Shanghai", snapshot.timezone)
            assertEquals(1, metServer.requestCount)
        }

    @Test
    fun forecast429_fallsBackToMetNorway() =
        runBlocking {
            mainServer.dispatcher = mainDispatcher(forecast = MockResponse().setResponseCode(429).setBody("slow down"))
            metServer.enqueue(MockResponse().setResponseCode(200).setBody(metJson()))

            val snapshot = repository.fetchWeather(city)

            assertEquals("Asia/Shanghai", snapshot.timezone)
            assertEquals(1, metServer.requestCount)
        }

    @Test
    fun forecast408_fallsBackToMetNorway() =
        runBlocking {
            mainServer.dispatcher = mainDispatcher(forecast = MockResponse().setResponseCode(408).setBody("timeout"))
            metServer.enqueue(MockResponse().setResponseCode(200).setBody(metJson()))

            repository.fetchWeather(city)

            assertEquals(1, metServer.requestCount)
        }

    @Test
    fun forecast425_fallsBackToMetNorway() =
        runBlocking {
            mainServer.dispatcher = mainDispatcher(forecast = MockResponse().setResponseCode(425).setBody("too early"))
            metServer.enqueue(MockResponse().setResponseCode(200).setBody(metJson()))

            repository.fetchWeather(city)

            assertEquals(1, metServer.requestCount)
        }

    @Test
    fun forecast4xx_doesNotFallBack() =
        runBlocking {
            mainServer.dispatcher = mainDispatcher(forecast = MockResponse().setResponseCode(404).setBody("nope"))

            try {
                repository.fetchWeather(city)
                throw AssertionError("expected AppError.Http")
            } catch (e: AppError.Http) {
                assertEquals(404, e.code)
            }
            assertEquals(0, metServer.requestCount)
        }

    @Test
    fun metNorwayAlsoFails_throwsOriginalError() =
        runBlocking {
            mainServer.dispatcher = mainDispatcher(forecast = MockResponse().setResponseCode(503).setBody("unavailable"))
            metServer.enqueue(MockResponse().setResponseCode(500).setBody("boom"))

            try {
                repository.fetchWeather(city)
                throw AssertionError("expected AppError.Http")
            } catch (e: AppError.Http) {
                // 降级源失败时保留主源错误分类，便于 UI 提示
                assertEquals(503, e.code)
            }
        }

    private fun mainDispatcher(forecast: MockResponse): Dispatcher =
        object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val path = request.path ?: return MockResponse().setResponseCode(404)
                return when {
                    path.contains("air-quality") ->
                        MockResponse().setResponseCode(503).setBody("unavailable")
                    path.contains("forecast") -> forecast
                    else -> MockResponse().setResponseCode(404)
                }
            }
        }

    private fun mockResources(): Resources {
        val resources = Mockito.mock(Resources::class.java)
        Mockito.`when`(resources.getString(Mockito.anyInt())).thenReturn("x")
        Mockito.`when`(resources.getString(Mockito.anyInt(), Mockito.any())).thenReturn("x")
        return resources
    }

    /** 相对当前整点生成 MET 响应：以 now-2h 为起点，第 3 条即当前小时（±1 小时边界）。 */
    private fun metJson(): String {
        val base = Instant.now().truncatedTo(ChronoUnit.HOURS).minusSeconds(2 * 3600)
        val entries =
            (0L..5L).joinToString(",") { offset ->
                val time = base.plusSeconds(offset * 3600)
                val temp = 10.0 + offset
                val symbol = if (offset == 2L) "lightrain" else "clearsky_day"
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
                      "summary": { "symbol_code": "$symbol" },
                      "details": { "precipitation_amount": 0.0, "probability_of_precipitation": 10.0 }
                    }
                  }
                }
                """.trimIndent()
            }
        return """{"properties":{"timeseries":[$entries]}}"""
    }
}

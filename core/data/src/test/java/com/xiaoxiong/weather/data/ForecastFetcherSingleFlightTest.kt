package com.xiaoxiong.weather.data

import android.content.res.Resources
import com.xiaoxiong.weather.fixtures.WeatherFixtures
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Before
import org.junit.Test
import org.mockito.Mockito
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/** 同城并发 fetchWeather 应合并为一次上游请求。 */
class ForecastFetcherSingleFlightTest {
    private lateinit var server: MockWebServer
    private lateinit var fetcher: ForecastFetcher

    private val city = WeatherFixtures.city("北京", lat = 39.9042, lon = 116.4074)
    private val forecastRequests = AtomicInteger()

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
        server = MockWebServer()
        server.start()
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
                forecastBase = server.url("v1").toString().trimEnd('/'),
                airQualityBase = server.url("v1").toString().trimEnd('/'),
                metNorwayBase = null,
            )
        server.dispatcher =
            object : Dispatcher() {
                override fun dispatch(request: RecordedRequest): MockResponse {
                    val path = request.path.orEmpty()
                    return when {
                        path.contains("air-quality") ->
                            MockResponse().setResponseCode(503).setBody("unavailable")
                        path.contains("forecast") -> {
                            // 人为延迟，确保两个并发调用重叠在 in-flight 窗口内
                            Thread.sleep(80)
                            forecastRequests.incrementAndGet()
                            MockResponse().setResponseCode(200).setBody(forecastJson)
                        }
                        else -> MockResponse().setResponseCode(404)
                    }
                }
            }
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun fetchWeather_concurrentSameCity_sharesOneUpstreamCall() =
        runBlocking {
            val first = async { fetcher.fetchWeather(city) }
            val second = async { fetcher.fetchWeather(city.copy(id = city.id + 1)) }
            val a = first.await()
            val b = second.await()
            assertSame(a, b)
            assertEquals(1, forecastRequests.get())
        }

    @Test
    fun fetchWeather_differentCities_runIndependently() =
        runBlocking {
            val other = WeatherFixtures.city("上海", lat = 31.2304, lon = 121.4737)
            val first = async { fetcher.fetchWeather(city) }
            delay(10)
            val second = async { fetcher.fetchWeather(other) }
            first.await()
            second.await()
            assertEquals(2, forecastRequests.get())
        }

    private fun mockResources(): Resources {
        val resources = Mockito.mock(Resources::class.java)
        Mockito.`when`(resources.getString(Mockito.anyInt())).thenReturn("")
        Mockito.`when`(resources.getString(Mockito.anyInt(), Mockito.any())).thenReturn("")
        return resources
    }
}

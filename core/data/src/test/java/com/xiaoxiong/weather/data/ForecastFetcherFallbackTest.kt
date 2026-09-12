package com.xiaoxiong.weather.data

import android.content.res.Resources
import com.xiaoxiong.weather.fixtures.WeatherFixtures
import kotlinx.coroutines.runBlocking
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
import java.util.concurrent.TimeUnit

/**
 * 备用预报入口的 URL 拼接：base 自带路径前缀时必须保留（如自建代理 /proxy）。
 */
class ForecastFetcherFallbackTest {
    private lateinit var primaryServer: MockWebServer
    private lateinit var fallbackServer: MockWebServer
    private lateinit var client: OkHttpClient

    @Before
    fun setUp() {
        primaryServer = MockWebServer()
        primaryServer.start()
        primaryServer.dispatcher =
            object : Dispatcher() {
                override fun dispatch(request: RecordedRequest): MockResponse = MockResponse().setResponseCode(503).setBody("unavailable")
            }
        fallbackServer = MockWebServer()
        fallbackServer.start()
        client =
            OkHttpClient
                .Builder()
                .connectTimeout(2, TimeUnit.SECONDS)
                .readTimeout(2, TimeUnit.SECONDS)
                .build()
    }

    @After
    fun tearDown() {
        primaryServer.shutdown()
        fallbackServer.shutdown()
    }

    @Test
    fun fallbackWithPathPrefix_keepsBasePrefix() =
        runBlocking {
            val repository = repositoryWithFallback(fallbackServer.url("proxy/v1").toString().trimEnd('/'))
            fallbackServer.enqueue(MockResponse().setResponseCode(200).setBody(forecastJson))

            repository.fetchWeather(city)

            val recorded = fallbackServer.takeRequest()
            assertEquals("/proxy/v1/v1/forecast", recorded.requestUrl?.encodedPath)
        }

    @Test
    fun fallbackWithoutPath_appendsRequestPath() =
        runBlocking {
            val repository = repositoryWithFallback(fallbackServer.url("/").toString().trimEnd('/'))
            fallbackServer.enqueue(MockResponse().setResponseCode(200).setBody(forecastJson))

            repository.fetchWeather(city)

            val recorded = fallbackServer.takeRequest()
            assertEquals("/v1/forecast", recorded.requestUrl?.encodedPath)
        }

    private fun repositoryWithFallback(fallbackBase: String): WeatherRepository {
        val primaryBase = primaryServer.url("v1").toString().trimEnd('/')
        return WeatherRepository(
            resources = mockResources(),
            client = client,
            searchClient = client,
            geocodingBase = primaryBase,
            forecastBase = primaryBase,
            airQualityBase = primaryBase,
            archiveBase = primaryBase,
            forecastBaseFallbacks = listOf(fallbackBase),
            // 主链路已由 MockWebServer 覆盖，关闭 MET Norway 降级避免访问外网
            metNorwayBase = null,
        )
    }

    private fun mockResources(): Resources {
        val resources = Mockito.mock(Resources::class.java)
        Mockito.`when`(resources.getString(Mockito.anyInt())).thenReturn("x")
        Mockito.`when`(resources.getString(Mockito.anyInt(), Mockito.any())).thenReturn("x")
        return resources
    }

    private val city = WeatherFixtures.city("济南", lat = 36.65, lon = 117.12)

    private val forecastJson =
        """
        {
          "latitude": 36.65,
          "longitude": 117.12,
          "elevation": 50.0,
          "timezone": "Asia/Shanghai",
          "current": {
            "time": "2026-08-02T12:00",
            "interval": 900,
            "temperature_2m": 22.0
          },
          "hourly": {
            "time": ["2026-08-02T12:00"],
            "temperature_2m": [22.0],
            "relative_humidity_2m": [60],
            "visibility": [10000.0],
            "weather_code": [0],
            "precipitation": [0.0],
            "precipitation_probability": [0],
            "wind_speed_10m": [10.0]
          },
          "daily": {
            "time": ["2026-08-02"],
            "weather_code": [0],
            "temperature_2m_max": [30.0],
            "temperature_2m_min": [20.0],
            "precipitation_sum": [0.0],
            "precipitation_probability_max": [0],
            "sunrise": ["2026-08-02T05:00"],
            "sunset": ["2026-08-02T19:00"],
            "uv_index_max": [5.0],
            "wind_speed_10m_max": [10.0]
          }
        }
        """.trimIndent()
}

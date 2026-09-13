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

/**
 * 取数侧单位口径：ForecastFetcher 注入的温度单位 provider 必须作用于快照里的预警文案，
 * 保证前台下拉/自动刷新后高温、寒潮等文案与「设置 → 温度单位」一致。
 */
class ForecastFetcherAlertUnitTest {
    private lateinit var server: MockWebServer

    private val city = WeatherFixtures.city("北京", lat = 39.9042, lon = 116.4074)

    private val forecastJson =
        """
        {
          "latitude": 39.9042,
          "longitude": 116.4074,
          "timezone": "Asia/Shanghai",
          "current": {
            "time": "2026-08-02T12:00",
            "temperature_2m": 33.0,
            "apparent_temperature": 38.0,
            "weather_code": 0
          },
          "hourly": {
            "time": ["2026-08-02T12:00"],
            "temperature_2m": [33.0],
            "weather_code": [0]
          },
          "daily": {
            "time": ["2026-08-02"],
            "weather_code": [0],
            "temperature_2m_max": [38.0],
            "temperature_2m_min": [27.0]
          }
        }
        """.trimIndent()

    @Before
    fun setUp() {
        server = MockWebServer()
        server.dispatcher =
            object : Dispatcher() {
                override fun dispatch(request: RecordedRequest): MockResponse =
                    if (request.path.orEmpty().contains("air-quality")) {
                        MockResponse().setResponseCode(503).setBody("unavailable")
                    } else {
                        MockResponse().setResponseCode(200).setBody(forecastJson)
                    }
            }
        server.start()
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun fahrenheitProvider_rendersHeatAlertInFahrenheit() =
        runBlocking {
            val heat = fetchHeatAlert { TemperatureUnit.FAHRENHEIT }
            // 38°C → 100°F
            assertEquals("100°F", heat.message)
        }

    @Test
    fun defaultProvider_keepsCelsius() =
        runBlocking {
            val heat = fetchHeatAlert { TemperatureUnit.CELSIUS }
            assertEquals("38°C", heat.message)
        }

    private suspend fun fetchHeatAlert(unit: suspend () -> TemperatureUnit): WeatherAlert {
        val fetcher =
            ForecastFetcher(
                resources = alertArgResources(),
                client = OkHttpClient(),
                json = Json { ignoreUnknownKeys = true },
                forecastBase = server.url("v1").toString().trimEnd('/'),
                airQualityBase = server.url("v1").toString().trimEnd('/'),
                metNorwayBase = null,
                temperatureUnitProvider = unit,
            )
        return fetcher
            .fetchWeather(city)
            .alerts
            .first { it.kind == AlertKind.HEAT }
    }

    /** 带 %s 占位的资源 mock：直接把格式化参数回显为文案，便于断言单位。 */
    private fun alertArgResources(): Resources {
        val resources = Mockito.mock(Resources::class.java)
        Mockito.`when`(resources.getString(Mockito.anyInt())).thenReturn("x")
        Mockito
            .`when`(resources.getString(Mockito.anyInt(), Mockito.any()))
            .thenAnswer { invocation ->
                val arg = invocation.arguments.getOrNull(1)
                if (arg is Array<*>) arg.joinToString(",") { it.toString() } else arg.toString()
            }
        return resources
    }
}

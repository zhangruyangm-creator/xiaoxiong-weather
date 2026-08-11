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
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.Mockito
import java.util.concurrent.TimeUnit

class WeatherRepositoryHttpTest {
    private lateinit var server: MockWebServer
    private lateinit var repository: WeatherRepository

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        val base = server.url("v1").toString().trimEnd('/')
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
            )
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    private fun mockResources(): Resources {
        val resources = Mockito.mock(Resources::class.java)
        Mockito.`when`(resources.getString(Mockito.anyInt())).thenReturn("x")
        Mockito.`when`(resources.getString(Mockito.anyInt(), Mockito.any())).thenReturn("x")
        return resources
    }

    private fun pathDispatcher(
        forecast: MockResponse,
        air: MockResponse,
        archive: MockResponse = MockResponse().setResponseCode(404),
    ): Dispatcher =
        object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val path = request.path ?: return MockResponse().setResponseCode(404)
                return when {
                    path.contains("archive") -> archive
                    path.contains("air-quality") -> air
                    path.contains("forecast") -> forecast
                    else -> MockResponse().setResponseCode(404)
                }
            }
        }

    @Test
    fun searchCities_httpError_throwsAppErrorHttp() =
        runBlocking {
            server.enqueue(MockResponse().setResponseCode(503).setBody("unavailable"))

            try {
                repository.searchCities("Jinan")
                throw AssertionError("expected AppError.Http")
            } catch (e: AppError.Http) {
                assertEquals(503, e.code)
            }
        }

    @Test
    fun searchCities_success_parsesResults() =
        runBlocking {
            server.enqueue(
                MockResponse()
                    .setResponseCode(200)
                    .setBody(
                        """
                        {
                          "results": [
                            {
                              "id": 1,
                              "name": "济南",
                              "latitude": 36.65,
                              "longitude": 117.12,
                              "country": "中国",
                              "admin1": "山东"
                            }
                          ]
                        }
                        """.trimIndent(),
                    ),
            )

            val cities = repository.searchCities("Jinan")
            assertEquals(1, cities.size)
            assertEquals("济南", cities[0].name)
            assertEquals(36.65, cities[0].latitude, 0.001)
            val recorded = server.takeRequest()
            assertTrue(recorded.path!!.contains("search"))
            assertEquals("Jinan", recorded.requestUrl?.queryParameter("name"))
        }

    @Test
    fun fetchWeather_success_mapsSnapshotAndGeneratesAlerts() =
        runBlocking {
            server.dispatcher =
                pathDispatcher(
                    forecast = MockResponse().setResponseCode(200).setBody(FORECAST_JSON),
                    air = MockResponse().setResponseCode(200).setBody(AIR_JSON),
                )

            val snapshot = repository.fetchWeather(WeatherFixtures.city("济南"))

            assertEquals(22.0, snapshot.current.temperatureC)
            assertEquals(95, snapshot.current.weatherCode)
            assertEquals(21.0, snapshot.current.apparentTemperatureC)
            assertEquals(60, snapshot.current.humidityPercent)
            assertTrue(snapshot.current.isDay == true)
            assertEquals(6, snapshot.hourly.size)
            assertEquals(7, snapshot.daily.size)
            assertEquals(50.0, snapshot.elevationMeters)
            assertEquals("Asia/Shanghai", snapshot.timezone)

            assertEquals(75, snapshot.airQuality?.europeanAqi)
            assertEquals(9.0, snapshot.airQuality?.uvIndex)
            assertEquals(35.0, snapshot.airQuality?.pm25)

            assertTrue(snapshot.alerts.any { it.kind == AlertKind.STORM })
            assertTrue(snapshot.alerts.any { it.kind == AlertKind.UV })

            assertNotNull(snapshot.advice)
            assertEquals(8, snapshot.lifeIndices.size)
            // 物候不再随主天气拉取（进入七日页按需拉），故只 2 个请求
            assertEquals(2, server.requestCount)
        }

    @Test
    fun fetchWeather_airQualityFailure_returnsSnapshotWithNullAir() =
        runBlocking {
            server.dispatcher =
                pathDispatcher(
                    forecast = MockResponse().setResponseCode(200).setBody(FORECAST_JSON),
                    air = MockResponse().setResponseCode(500).setBody("boom"),
                )

            val snapshot = repository.fetchWeather(WeatherFixtures.city("济南"))

            assertNull(snapshot.airQuality)
            assertEquals(22.0, snapshot.current.temperatureC)
            assertEquals(95, snapshot.current.weatherCode)
            assertTrue(snapshot.alerts.any { it.kind == AlertKind.STORM })
        }

    @Test
    fun fetchWeather_forecastHttpError_throwsAppErrorHttp() =
        runBlocking {
            server.dispatcher =
                pathDispatcher(
                    forecast = MockResponse().setResponseCode(503).setBody("unavailable"),
                    air = MockResponse().setResponseCode(200).setBody(AIR_JSON),
                )

            try {
                repository.fetchWeather(WeatherFixtures.city("济南"))
                throw AssertionError("expected AppError.Http")
            } catch (e: AppError.Http) {
                assertEquals(503, e.code)
            }
        }

    @Test
    fun fetchWeather_forecastMalformed_throwsAppErrorDataFormat() =
        runBlocking {
            server.dispatcher =
                pathDispatcher(
                    forecast = MockResponse().setResponseCode(200).setBody("not-json"),
                    air = MockResponse().setResponseCode(200).setBody(AIR_JSON),
                )

            try {
                repository.fetchWeather(WeatherFixtures.city("济南"))
                throw AssertionError("expected AppError.DataFormat")
            } catch (e: AppError.DataFormat) {
                // 类型化错误：UI 可据此展示「数据异常」而非技术堆栈
                assertNotNull(e.cause)
            }
        }

    @Test
    fun fetchPhenologyShiftDays_warmerYear_returnsNegativeShift() =
        runBlocking {
            server.enqueue(
                MockResponse()
                    .setResponseCode(200)
                    .setBody(archiveJson(today = "2026-08-10", thisYearOffset = 6.0)),
            )
            val shift =
                repository.fetchPhenologyShiftDays(
                    WeatherFixtures.city("济南"),
                    today = java.time.LocalDate.of(2026, 8, 10),
                )
            assertNotNull(shift)
            assertTrue("expected earlier (negative), got $shift", shift!! < 0)
        }

    @Test
    fun fetchPhenologyShiftDays_colderYear_returnsPositiveShift() =
        runBlocking {
            server.enqueue(
                MockResponse()
                    .setResponseCode(200)
                    .setBody(archiveJson(today = "2026-08-10", thisYearOffset = -6.0)),
            )
            val shift =
                repository.fetchPhenologyShiftDays(
                    WeatherFixtures.city("济南"),
                    today = java.time.LocalDate.of(2026, 8, 10),
                )
            assertNotNull(shift)
            assertTrue("expected later (positive), got $shift", shift!! > 0)
        }

    @Test
    fun fetchPhenologyShiftDays_httpError_returnsNull() =
        runBlocking {
            server.enqueue(MockResponse().setResponseCode(503).setBody("unavailable"))
            val shift =
                repository.fetchPhenologyShiftDays(
                    WeatherFixtures.city("济南"),
                    today = java.time.LocalDate.of(2026, 8, 10),
                )
            assertNull(shift)
        }

    @Test
    fun fetchPhenologyShiftDays_usesYesterdayAsEndDate() =
        runBlocking {
            // ERA5 再分析数据延迟约 1 天：end_date 必须是 today-1，否则 archive 报越界错误
            server.enqueue(
                MockResponse()
                    .setResponseCode(200)
                    .setBody(archiveJson(today = "2026-08-10", thisYearOffset = 6.0)),
            )
            repository.fetchPhenologyShiftDays(
                WeatherFixtures.city("济南"),
                today = java.time.LocalDate.of(2026, 8, 10),
            )
            val recorded = server.takeRequest()
            assertEquals("2026-08-09", recorded.requestUrl?.queryParameter("end_date"))
            assertEquals("2020-01-01", recorded.requestUrl?.queryParameter("start_date"))
        }

    @Test
    fun fetchHistoryDay_success_mapsFields() =
        runBlocking {
            server.enqueue(
                MockResponse()
                    .setResponseCode(200)
                    .setBody(historyJson(date = "2026-08-10")),
            )
            val day =
                repository.fetchHistoryDay(
                    WeatherFixtures.city("济南"),
                    date = java.time.LocalDate.of(2026, 8, 10),
                )
            assertNotNull(day)
            assertEquals("2026-08-10", day!!.date)
            assertEquals(95, day.weatherCode)
            assertEquals(30.0, day.tempMaxC)
            assertEquals(20.0, day.tempMinC)
            assertEquals(25.0, day.tempMeanC)
            assertEquals(5.0, day.precipSumMm)
            assertEquals(60, day.humidityMeanPercent)
            assertEquals(15.0, day.windMaxKmh)
            assertEquals("2026-08-10T05:00", day.sunrise)
            assertEquals("2026-08-10T19:00", day.sunset)

            val recorded = server.takeRequest()
            assertEquals("2026-08-10", recorded.requestUrl?.queryParameter("start_date"))
            assertEquals("2026-08-10", recorded.requestUrl?.queryParameter("end_date"))
        }

    @Test
    fun fetchHistoryDay_httpError_returnsNull() =
        runBlocking {
            server.enqueue(MockResponse().setResponseCode(503).setBody("unavailable"))
            val day =
                repository.fetchHistoryDay(
                    WeatherFixtures.city("济南"),
                    date = java.time.LocalDate.of(2026, 8, 10),
                )
            assertNull(day)
        }

    @Test
    fun fetchHistoryDay_mismatchedDate_returnsNull() =
        runBlocking {
            // 服务端返回的日期与请求日期不一致（异常响应）时按无数据处理
            server.enqueue(
                MockResponse()
                    .setResponseCode(200)
                    .setBody(historyJson(date = "2026-08-09")),
            )
            val day =
                repository.fetchHistoryDay(
                    WeatherFixtures.city("济南"),
                    date = java.time.LocalDate.of(2026, 8, 10),
                )
            assertNull(day)
        }

    @Test
    fun fetchWeather_doesNotFetchArchive() =
        runBlocking {
            server.dispatcher =
                pathDispatcher(
                    forecast = MockResponse().setResponseCode(200).setBody(FORECAST_JSON),
                    air = MockResponse().setResponseCode(200).setBody(AIR_JSON),
                    archive = MockResponse().setResponseCode(500).setBody("boom"),
                )

            val snapshot = repository.fetchWeather(WeatherFixtures.city("济南"))

            // 物候由进入七日页时按需拉取，主天气加载不应命中 archive 接口
            assertEquals(2, server.requestCount)
            val paths = (1..server.requestCount).map { server.takeRequest().path!! }
            assertTrue(paths.none { it.contains("archive") })
            assertEquals(22.0, snapshot.current.temperatureC)
            assertEquals(95, snapshot.current.weatherCode)
        }

    companion object {
        /**
         * 生成多年 archive JSON：往年恒为 base 温度，今年恒为 base + thisYearOffset。
         * 这样多年平均=base，今年温度恒定偏移，GDD 偏移方向确定。
         */
        private fun archiveJson(
            today: String,
            thisYearOffset: Double,
            base: Double = 15.0,
            years: IntRange = 2020..2026,
        ): String {
            val todayDate = java.time.LocalDate.parse(today)
            val times = mutableListOf<String>()
            val temps = mutableListOf<Double>()
            for (year in years) {
                var date = java.time.LocalDate.of(year, 1, 1)
                val end = if (year == todayDate.year) todayDate else java.time.LocalDate.of(year, 12, 31)
                while (!date.isAfter(end)) {
                    times += date.toString()
                    val temp = if (year == todayDate.year) base + thisYearOffset else base
                    temps += temp
                    date = date.plusDays(1)
                }
            }
            val timeJson = times.joinToString(",") { "\"$it\"" }
            val tempJson = temps.joinToString(",")
            return """
                {
                  "latitude": 36.65,
                  "longitude": 117.12,
                  "timezone": "Asia/Shanghai",
                  "daily": {
                    "time": [$timeJson],
                    "temperature_2m_mean": [$tempJson]
                  }
                }
                """.trimIndent()
        }

        private fun historyJson(date: String): String =
            """
            {
              "latitude": 36.65,
              "longitude": 117.12,
              "timezone": "Asia/Shanghai",
              "daily": {
                "time": ["$date"],
                "weather_code": [95],
                "temperature_2m_max": [30.0],
                "temperature_2m_min": [20.0],
                "temperature_2m_mean": [25.0],
                "precipitation_sum": [5.0],
                "relative_humidity_2m_mean": [60],
                "wind_speed_10m_max": [15.0],
                "sunrise": ["${date}T05:00"],
                "sunset": ["${date}T19:00"]
              }
            }
            """.trimIndent()

        private val FORECAST_JSON =
            """
            {
              "latitude": 36.65,
              "longitude": 117.12,
              "elevation": 50.0,
              "timezone": "Asia/Shanghai",
              "current": {
                "time": "2026-08-02T12:00",
                "interval": 900,
                "temperature_2m": 22.0,
                "relative_humidity_2m": 60,
                "apparent_temperature": 21.0,
                "weather_code": 95,
                "wind_speed_10m": 10.0,
                "wind_gusts_10m": 20.0,
                "wind_direction_10m": 180,
                "visibility": 10000,
                "precipitation": 0.0,
                "cloud_cover": 50,
                "pressure_msl": 1013.0,
                "dew_point_2m": 10.0,
                "is_day": 1
              },
              "hourly": {
                "time": ["2026-08-02T10:00","2026-08-02T11:00","2026-08-02T12:00","2026-08-02T13:00","2026-08-02T14:00","2026-08-02T15:00"],
                "temperature_2m": [20.0,21.0,22.0,23.0,24.0,25.0],
                "relative_humidity_2m": [60,60,60,60,60,60],
                "visibility": [10000,10000,10000,10000,10000,10000],
                "weather_code": [0,0,95,95,0,0],
                "precipitation": [0.0,0.0,0.0,0.0,0.0,0.0],
                "precipitation_probability": [0,0,0,0,0,0],
                "wind_speed_10m": [10.0,10.0,10.0,10.0,10.0,10.0]
              },
              "daily": {
                "time": ["2026-08-02","2026-08-03","2026-08-04","2026-08-05","2026-08-06","2026-08-07","2026-08-08"],
                "weather_code": [95,0,0,0,0,0,0],
                "temperature_2m_max": [30.0,31.0,32.0,33.0,34.0,35.0,36.0],
                "temperature_2m_min": [20.0,21.0,22.0,23.0,24.0,25.0,26.0],
                "precipitation_sum": [0.0,0.0,0.0,0.0,0.0,0.0,0.0],
                "precipitation_probability_max": [0,0,0,0,0,0,0],
                "sunrise": ["2026-08-02T05:00","2026-08-03T05:00","2026-08-04T05:00","2026-08-05T05:00","2026-08-06T05:00","2026-08-07T05:00","2026-08-08T05:00"],
                "sunset": ["2026-08-02T19:00","2026-08-03T19:00","2026-08-04T19:00","2026-08-05T19:00","2026-08-06T19:00","2026-08-07T19:00","2026-08-08T19:00"],
                "uv_index_max": [9.0,5.0,5.0,5.0,5.0,5.0,5.0],
                "wind_speed_10m_max": [20.0,15.0,15.0,15.0,15.0,15.0,15.0]
              }
            }
            """.trimIndent()

        private val AIR_JSON =
            """
            {
              "latitude": 36.65,
              "longitude": 117.12,
              "timezone": "Asia/Shanghai",
              "current": {
                "time": "2026-08-02T12:00",
                "european_aqi": 75,
                "us_aqi": 80,
                "pm2_5": 35.0,
                "pm10": 50.0,
                "dust": 10.0,
                "uv_index": 9.0,
                "nitrogen_dioxide": 20.0,
                "ozone": 60.0
              }
            }
            """.trimIndent()
    }
}

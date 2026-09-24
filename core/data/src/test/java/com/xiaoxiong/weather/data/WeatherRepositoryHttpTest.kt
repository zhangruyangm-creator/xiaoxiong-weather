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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.assertThrows
import org.junit.Assert.fail
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
                // 本文件专注 Open-Meteo 主链路错误语义，关闭 MET Norway 降级避免访问外网
                metNorwayBase = null,
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
    fun fetchWeather_requestsSlimAirQualityWithoutHourly() =
        runBlocking {
            server.dispatcher =
                pathDispatcher(
                    forecast = MockResponse().setResponseCode(200).setBody(FORECAST_JSON),
                    air = MockResponse().setResponseCode(200).setBody(AIR_JSON),
                )

            repository.fetchWeather(WeatherFixtures.city("济南"))

            val requests = (1..server.requestCount).map { server.takeRequest() }
            val airRequest = requests.first { it.path.orEmpty().contains("air-quality") }
            assertNull(airRequest.requestUrl?.queryParameter("hourly"))
            assertTrue(
                airRequest.requestUrl
                    ?.queryParameter("current")
                    .orEmpty()
                    .contains("european_aqi"),
            )
        }

    @Test
    fun fetchWeather_forecastCurrentRequestDropsUnusedPrecipitation() =
        runBlocking {
            server.dispatcher =
                pathDispatcher(
                    forecast = MockResponse().setResponseCode(200).setBody(FORECAST_JSON),
                    air = MockResponse().setResponseCode(200).setBody(AIR_JSON),
                )

            val snapshot = repository.fetchWeather(WeatherFixtures.city("济南"))

            val requests = (1..server.requestCount).map { server.takeRequest() }
            val forecastRequest = requests.first { it.path.orEmpty().contains("forecast") }
            val currentFields =
                forecastRequest.requestUrl
                    ?.queryParameter("current")
                    .orEmpty()
                    .split(",")
            assertFalse(currentFields.contains("precipitation"))
            // 响应里仍带 precipitation 时字段解析保持兼容（快照可读到）
            assertEquals(0.0, snapshot.current.precipitationMm)
        }

    @Test
    fun fetchWeather_airQualityCurrentAllNull_returnsNullAirQuality() =
        runBlocking {
            server.dispatcher =
                pathDispatcher(
                    forecast = MockResponse().setResponseCode(200).setBody(FORECAST_JSON),
                    air =
                        MockResponse()
                            .setResponseCode(200)
                            .setBody("""{"latitude":36.65,"longitude":117.12,"timezone":"Asia/Shanghai"}"""),
                )

            val snapshot = repository.fetchWeather(WeatherFixtures.city("济南"))

            assertNull(snapshot.airQuality)
        }

    @Test
    fun fetchWeather_normalizesHailCode96to95() =
        runBlocking {
            // 广州实测 daily 5/7 天返回 96（雷暴伴轻微冰雹），粗网格模式虚报严重；
            // current/hourly/daily 的 96 统一归一为 95（雷暴），只有 99 才保留冰雹
            val body =
                FORECAST_JSON
                    .replace("\"weather_code\": 95,", "\"weather_code\": 96,")
                    .replace(
                        "\"weather_code\": [0,0,95,95,0,0],",
                        "\"weather_code\": [96,0,95,95,0,0],",
                    ).replace(
                        "\"weather_code\": [95,0,0,0,0,0,0],",
                        "\"weather_code\": [96,0,0,0,0,0,0],",
                    )
            server.dispatcher =
                pathDispatcher(
                    forecast = MockResponse().setResponseCode(200).setBody(body),
                    air = MockResponse().setResponseCode(200).setBody(AIR_JSON),
                )

            val snapshot = repository.fetchWeather(WeatherFixtures.city("济南"))

            assertEquals(95, snapshot.current.weatherCode)
            assertEquals(95, snapshot.hourly[0].weatherCode)
            assertEquals(95, snapshot.hourly[2].weatherCode)
            assertEquals(95, snapshot.daily[0].weatherCode)
            // 96 不再触发冰雹预警；雷暴正常触发，且无强降雨证据时是黄色提醒
            assertFalse(snapshot.alerts.any { it.kind == AlertKind.HAIL })
            val storm = snapshot.alerts.first { it.kind == AlertKind.STORM }
            assertEquals(AlertSeverity.WATCH, storm.severity)
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
    fun fetchPhenologyShiftDays_httpError_throwsInsteadOfReturningNull() =
        runBlocking {
            server.enqueue(MockResponse().setResponseCode(503).setBody("unavailable"))
            // 语义约定：null = "确实没有足够数据"（可缓存一天），错误必须抛出。
            // 旧实现对 503 也返回 null，控制器无从区分，一次断网会把"失败"当"无数据"
            // 缓存整天，当天后续重试全被抑制。
            val error =
                assertThrows(AppError.Http::class.java) {
                    runBlocking {
                        repository.fetchPhenologyShiftDays(
                            WeatherFixtures.city("济南"),
                            today = java.time.LocalDate.of(2026, 8, 10),
                        )
                    }
                }
            assertEquals(503, error.code)
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
    fun fetchHistoryDay_httpError_throwsAppError() =
        runBlocking {
            server.enqueue(MockResponse().setResponseCode(503).setBody("unavailable"))
            try {
                repository.fetchHistoryDay(
                    WeatherFixtures.city("济南"),
                    date = java.time.LocalDate.of(2026, 8, 10),
                )
                fail("expected AppError.Http(503)")
            } catch (expected: AppError.Http) {
                assertEquals(503, expected.code)
            }
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
    fun fetchHistoryRange_success_aggregatesExtremes() =
        runBlocking {
            server.enqueue(
                MockResponse()
                    .setResponseCode(200)
                    .setBody(HISTORY_RANGE_JSON),
            )
            val summary =
                repository.fetchHistoryRange(
                    WeatherFixtures.city("济南"),
                    start = java.time.LocalDate.of(2026, 8, 1),
                    end = java.time.LocalDate.of(2026, 8, 3),
                )
            assertNotNull(summary)
            assertEquals(3, summary!!.days.size)
            assertEquals("2026-08-01", summary.days[0].date)
            assertEquals("2026-08-03", summary.days[2].date)
            val hottest = summary.hottest
            assertNotNull(hottest)
            assertEquals(35.0, hottest!!.value, 0.001)
            assertEquals("2026-08-02", hottest.date)
            val coldest = summary.coldest
            assertNotNull(coldest)
            assertEquals(18.0, coldest!!.value, 0.001)
            assertEquals("2026-08-02", coldest.date)
            val wettest = summary.wettest
            assertNotNull(wettest)
            assertEquals(12.0, wettest!!.value, 0.001)
            assertEquals("2026-08-03", wettest.date)

            // 按月聚合：3 天都在 2026-08，最高 35 / 最低 18 / 总降水 17 / 雨日 2
            assertEquals(1, summary.months.size)
            val month = summary.months[0]
            assertEquals(2026, month.year)
            assertEquals(8, month.month)
            assertEquals(35.0, month.highestTempC!!, 0.001)
            assertEquals(18.0, month.lowestTempC!!, 0.001)
            assertEquals(17.0, month.totalPrecipMm!!, 0.001)
            assertEquals(2, month.precipDays)

            val recorded = server.takeRequest()
            assertEquals("2026-08-01", recorded.requestUrl?.queryParameter("start_date"))
            assertEquals("2026-08-03", recorded.requestUrl?.queryParameter("end_date"))
        }

    @Test
    fun fetchHistoryRange_filtersDaysOutsideRequestedRange() =
        runBlocking {
            // 服务端返回了范围外的日期（异常响应），应按请求区间过滤
            server.enqueue(
                MockResponse()
                    .setResponseCode(200)
                    .setBody(
                        """
                        {
                          "latitude": 36.65,
                          "longitude": 117.12,
                          "timezone": "Asia/Shanghai",
                          "daily": {
                            "time": ["2026-07-31","2026-08-01","2026-08-02"],
                            "weather_code": [0,0,0],
                            "temperature_2m_max": [40.0,30.0,35.0],
                            "temperature_2m_min": [10.0,20.0,18.0],
                            "precipitation_sum": [0.0,5.0,0.0],
                            "temperature_2m_mean": [25.0,25.0,25.0],
                            "relative_humidity_2m_mean": [50,60,55],
                            "wind_speed_10m_max": [10.0,15.0,20.0],
                            "sunrise": ["2026-07-31T05:00","2026-08-01T05:00","2026-08-02T05:00"],
                            "sunset": ["2026-07-31T19:00","2026-08-01T19:00","2026-08-02T19:00"]
                          }
                        }
                        """.trimIndent(),
                    ),
            )
            val summary =
                repository.fetchHistoryRange(
                    WeatherFixtures.city("济南"),
                    start = java.time.LocalDate.of(2026, 8, 1),
                    end = java.time.LocalDate.of(2026, 8, 2),
                )
            assertNotNull(summary)
            // 7-31 在区间外，不应计入；8-1 高温 30、8-2 高温 35 → 最高温应落在 8-2
            assertEquals(2, summary!!.days.size)
            assertEquals(listOf("2026-08-01", "2026-08-02"), summary.days.map { it.date })
            val hottest = summary.hottest
            assertNotNull(hottest)
            assertEquals(35.0, hottest!!.value, 0.001)
            assertEquals("2026-08-02", hottest.date)
        }

    @Test
    fun monthlySummaries_aggregatesAcrossMonths() {
        val days =
            listOf(
                HistoryWeather(date = "2026-01-05", tempMaxC = 10.0, tempMinC = 2.0, precipSumMm = 1.0),
                HistoryWeather(date = "2026-01-08", tempMaxC = 12.0, tempMinC = 3.0, precipSumMm = null),
                HistoryWeather(date = "2026-01-20", tempMaxC = 15.0, tempMinC = 5.0, precipSumMm = 0.0),
                HistoryWeather(date = "2026-02-01", tempMaxC = 20.0, tempMinC = 8.0, precipSumMm = 12.0),
            )
        val months = HistoryFetcher.monthlySummaries(days)

        assertEquals(2, months.size)
        val jan = months[0]
        assertEquals(2026, jan.year)
        assertEquals(1, jan.month)
        assertEquals(15.0, jan.highestTempC!!, 0.001)
        assertEquals(2.0, jan.lowestTempC!!, 0.001)
        // null 降水按 0 计：1.0 + 0.0 = 1.0
        assertEquals(1.0, jan.totalPrecipMm!!, 0.001)
        assertEquals(1, jan.precipDays)

        val feb = months[1]
        assertEquals(2, feb.month)
        assertEquals(20.0, feb.highestTempC!!, 0.001)
        assertEquals(8.0, feb.lowestTempC!!, 0.001)
        assertEquals(12.0, feb.totalPrecipMm!!, 0.001)
        assertEquals(1, feb.precipDays)
    }

    @Test
    fun fetchHistoryRange_httpError_throwsAppError() =
        runBlocking {
            server.enqueue(MockResponse().setResponseCode(503).setBody("unavailable"))
            try {
                repository.fetchHistoryRange(
                    WeatherFixtures.city("济南"),
                    start = java.time.LocalDate.of(2026, 8, 1),
                    end = java.time.LocalDate.of(2026, 8, 3),
                )
                fail("expected AppError.Http(503)")
            } catch (expected: AppError.Http) {
                assertEquals(503, expected.code)
            }
        }

    @Test
    fun fetchHistoryRange_endBeforeStart_returnsNullWithoutRequest() =
        runBlocking {
            val summary =
                repository.fetchHistoryRange(
                    WeatherFixtures.city("济南"),
                    start = java.time.LocalDate.of(2026, 8, 3),
                    end = java.time.LocalDate.of(2026, 8, 1),
                )
            assertNull(summary)
            assertEquals(0, server.requestCount)
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

        private val HISTORY_RANGE_JSON =
            """
            {
              "latitude": 36.65,
              "longitude": 117.12,
              "timezone": "Asia/Shanghai",
              "daily": {
                "time": ["2026-08-01","2026-08-02","2026-08-03"],
                "weather_code": [80,0,51],
                "temperature_2m_max": [30.0,35.0,28.0],
                "temperature_2m_min": [20.0,18.0,22.0],
                "temperature_2m_mean": [25.0,26.5,25.0],
                "precipitation_sum": [5.0,0.0,12.0],
                "relative_humidity_2m_mean": [60,55,70],
                "wind_speed_10m_max": [15.0,20.0,25.0],
                "sunrise": ["2026-08-01T05:00","2026-08-02T05:00","2026-08-03T05:00"],
                "sunset": ["2026-08-01T19:00","2026-08-02T19:00","2026-08-03T19:00"]
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

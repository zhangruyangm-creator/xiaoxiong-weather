package com.xiaoxiong.weather.data

import com.xiaoxiong.weather.data.remote.MetNorwayResponse
import com.xiaoxiong.weather.fixtures.WeatherFixtures
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class MetNorwayFetcherTest {
    private val json = Json { ignoreUnknownKeys = true }
    private val fetcher = MetNorwayFetcher(OkHttpClient(), json)

    private val city = WeatherFixtures.city("北京", lat = 39.9042, lon = 116.4074)

    @Test
    fun weatherCodeForSymbol_mapsSymbolFamiliesToWmoCodes() {
        val expected =
            mapOf(
                "clearsky_day" to 0,
                "fair_night" to 1,
                "partlycloudy_day" to 2,
                "cloudy" to 3,
                "fog" to 45,
                "lightrain" to 61,
                "rain" to 63,
                "heavyrain" to 65,
                "lightrainshowers_day" to 80,
                "rainshowers_night" to 81,
                "heavyrainshowers_day" to 82,
                "lightsleet" to 68,
                "sleet" to 68,
                "lightsleetshowers" to 68,
                "sleetshowers" to 68,
                "heavysleet" to 69,
                "heavysleetshowers" to 69,
                "rainandthunder" to 95,
                "heavysnowandthunder" to 95,
                "lightsleetandthunder" to 95,
                "heavysleetandthunder" to 95,
                "lightssleetshowersandthunder" to 95,
                "lightsleetshowersandthunder" to 95,
                "snowshowersandthunder" to 95,
                "heavysnowshowersandthunder" to 95,
                "lightssnowshowersandthunder" to 95,
                "lightsnowshowersandthunder" to 95,
                "lightsnow" to 71,
                "snow" to 73,
                "heavysnow" to 75,
                "snowshowers_day" to 85,
                "heavysnowshowers_night" to 86,
            )
        expected.forEach { (symbol, code) ->
            assertEquals("symbol=$symbol", code, weatherCodeForSymbol(symbol))
        }
        assertNull(weatherCodeForSymbol("unknown_symbol"))
        assertNull(weatherCodeForSymbol(null))
    }

    @Test
    fun approximateZone_usesLongitudeBasedOffset() {
        assertEquals("UTC+08:00", approximateZone(116.4074).id)
        assertEquals("UTC+09:00", approximateZone(139.7).id)
        assertEquals("UTC-05:00", approximateZone(-74.0).id)
        assertEquals("UTC", approximateZone(-0.1).id)
    }

    @Test
    fun mapToSnapshot_picksCurrentHourAndAggregatesDaily() {
        val snapshot =
            fetcher.mapToSnapshot(
                json.decodeFromString(MetNorwayResponse.serializer(), fixtureJson),
                city,
                now = Instant.parse("2026-09-12T07:30:00Z"),
            )

        assertEquals("UTC+08:00", snapshot.timezone)
        assertEquals("2026-09-12T15:00", snapshot.current.time)
        assertEquals(22.0, snapshot.current.temperatureC)
        assertEquals(60, snapshot.current.humidityPercent)
        // m/s → km/h：3.0 × 3.6
        assertEquals(10.8, snapshot.current.windSpeedKmh)
        assertEquals(20, snapshot.current.cloudCoverPercent)
        assertTrue(snapshot.current.isDay == true)
        // 降级源无体感/露点/能见度/空气质量
        assertNull(snapshot.current.apparentTemperatureC)
        assertNull(snapshot.current.dewPointC)
        assertNull(snapshot.airQuality)
        assertTrue(snapshot.alerts.isEmpty())

        // 从当前整点起 28 小时窗口
        assertEquals(4, snapshot.hourly.size)
        assertEquals("2026-09-12T15:00", snapshot.hourly[0].time)
        assertEquals(61, snapshot.hourly[1].weatherCode)
        assertEquals(1.2, snapshot.hourly[1].precipitationMm)
        assertEquals(70, snapshot.hourly[1].precipProbability)
        assertEquals(63, snapshot.hourly[2].weatherCode)
        assertEquals("2026-09-13T03:00", snapshot.hourly[3].time)
        assertEquals(0, snapshot.hourly[3].weatherCode)

        // 每日聚合：极值/累计降水/最高概率/最严重天气码
        assertEquals(2, snapshot.daily.size)
        val today = snapshot.daily[0]
        assertEquals("2026-09-12", today.date)
        assertEquals(22.0, today.tempMaxC)
        assertEquals(19.0, today.tempMinC)
        assertEquals(3.7, today.precipSumMm)
        assertEquals(90, today.precipProbabilityMax)
        assertEquals(63, today.weatherCode)
        assertNull(today.sunrise)
        assertNull(today.uvIndexMax)
        assertEquals("2026-09-13", snapshot.daily[1].date)
        assertEquals(15.0, snapshot.daily[1].tempMaxC)
    }

    @Test
    fun mapToSnapshot_sixHourStep_aggregatesPrecipitationAmountNotRate() {
        val snapshot =
            fetcher.mapToSnapshot(
                json.decodeFromString(MetNorwayResponse.serializer(), sixHourStepJson),
                city,
                now = Instant.parse("2026-09-12T07:30:00Z"),
            )

        // 6 小时步长的原始雨量是「该时段累计」：日聚合必须直接求和（12+6），
        // 不能先用 ÷6 的小时率再求和（否则只有 1/6）
        assertEquals(1, snapshot.daily.size)
        assertEquals(18.0, snapshot.daily[0].precipSumMm)
        // 小时序列与 CurrentWeather 用小时率（6 ÷ 6）
        assertEquals(1.0, snapshot.hourly[0].precipitationMm)
        assertEquals(1.0, snapshot.current.precipitationMm)
        assertEquals(1.0, snapshot.current.precipitationRateMmPerHour)
    }

    @Test
    fun mapToSnapshot_atNightConvertsToLocalDateAndIsDayFalse() {
        val snapshot =
            fetcher.mapToSnapshot(
                json.decodeFromString(MetNorwayResponse.serializer(), fixtureJson),
                city,
                now = Instant.parse("2026-09-12T19:30:00Z"),
            )

        // 19:00Z = 次日 03:00（UTC+8）
        assertEquals("2026-09-13T03:00", snapshot.current.time)
        assertFalse(snapshot.current.isDay == true)
        assertEquals(15.0, snapshot.current.temperatureC)
    }

    @Test
    fun mapToSnapshot_emptyTimeseries_throwsDataFormat() {
        try {
            fetcher.mapToSnapshot(MetNorwayResponse(), city, Instant.now())
            throw AssertionError("expected AppError.DataFormat")
        } catch (e: AppError.DataFormat) {
            assertEquals("data format", e.message)
        }
    }

    private val fixtureJson =
        """
        {
          "properties": {
            "meta": { "updated_at": "2026-09-12T06:33:00Z" },
            "timeseries": [
              {
                "time": "2026-09-12T06:00:00Z",
                "data": {
                  "instant": { "details": {
                    "air_temperature": 20.0, "relative_humidity": 50.0,
                    "wind_speed": 5.0, "wind_from_direction": 180.0,
                    "air_pressure_at_sea_level": 1010.0, "cloud_area_fraction": 10.0
                  } },
                  "next_1_hours": {
                    "summary": { "symbol_code": "partlycloudy_day" },
                    "details": { "precipitation_amount": 0.0, "probability_of_precipitation": 5.0 }
                  }
                }
              },
              {
                "time": "2026-09-12T07:00:00Z",
                "data": {
                  "instant": { "details": {
                    "air_temperature": 22.0, "relative_humidity": 60.0,
                    "wind_speed": 3.0, "wind_from_direction": 200.0,
                    "air_pressure_at_sea_level": 1009.0, "cloud_area_fraction": 20.0
                  } },
                  "next_1_hours": {
                    "summary": { "symbol_code": "clearsky_day" },
                    "details": { "precipitation_amount": 0.0, "probability_of_precipitation": 0.0 }
                  }
                }
              },
              {
                "time": "2026-09-12T08:00:00Z",
                "data": {
                  "instant": { "details": {
                    "air_temperature": 21.0, "relative_humidity": 70.0,
                    "wind_speed": 4.0, "wind_from_direction": 220.0,
                    "air_pressure_at_sea_level": 1008.0, "cloud_area_fraction": 80.0
                  } },
                  "next_1_hours": {
                    "summary": { "symbol_code": "lightrain" },
                    "details": { "precipitation_amount": 1.2, "probability_of_precipitation": 70.0 }
                  }
                }
              },
              {
                "time": "2026-09-12T09:00:00Z",
                "data": {
                  "instant": { "details": {
                    "air_temperature": 19.0, "relative_humidity": 80.0,
                    "wind_speed": 6.0, "wind_from_direction": 240.0,
                    "air_pressure_at_sea_level": 1007.0, "cloud_area_fraction": 95.0
                  } },
                  "next_1_hours": {
                    "summary": { "symbol_code": "rain" },
                    "details": { "precipitation_amount": 2.5, "probability_of_precipitation": 90.0 }
                  }
                }
              },
              {
                "time": "2026-09-12T19:00:00Z",
                "data": {
                  "instant": { "details": {
                    "air_temperature": 15.0, "relative_humidity": 65.0,
                    "wind_speed": 2.0, "wind_from_direction": 90.0,
                    "air_pressure_at_sea_level": 1012.0, "cloud_area_fraction": 5.0
                  } },
                  "next_1_hours": {
                    "summary": { "symbol_code": "clearsky_night" },
                    "details": { "precipitation_amount": 0.0, "probability_of_precipitation": 0.0 }
                  }
                }
              }
            ]
          }
        }
        """.trimIndent()

    private val sixHourStepJson =
        """
        {
          "properties": {
            "timeseries": [
              {
                "time": "2026-09-12T00:00:00Z",
                "data": {
                  "instant": { "details": {
                    "air_temperature": 10.0, "relative_humidity": 55.0,
                    "wind_speed": 3.0, "wind_from_direction": 180.0,
                    "air_pressure_at_sea_level": 1010.0, "cloud_area_fraction": 90.0
                  } },
                  "next_6_hours": {
                    "summary": { "symbol_code": "rain" },
                    "details": { "precipitation_amount": 12.0, "probability_of_precipitation": 80.0 }
                  }
                }
              },
              {
                "time": "2026-09-12T06:00:00Z",
                "data": {
                  "instant": { "details": {
                    "air_temperature": 12.0, "relative_humidity": 60.0,
                    "wind_speed": 4.0, "wind_from_direction": 200.0,
                    "air_pressure_at_sea_level": 1009.0, "cloud_area_fraction": 85.0
                  } },
                  "next_6_hours": {
                    "summary": { "symbol_code": "lightrain" },
                    "details": { "precipitation_amount": 6.0, "probability_of_precipitation": 70.0 }
                  }
                }
              }
            ]
          }
        }
        """.trimIndent()
}

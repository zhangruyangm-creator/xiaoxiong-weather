package com.xiaoxiong.weather.data

import android.content.res.Resources
import com.xiaoxiong.weather.R
import com.xiaoxiong.weather.fixtures.WeatherFixtures
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.Mockito

class DebugWeatherStoreTest {
    @Test
    fun magicQuery_matches() {
        assertTrue(DebugWeatherStore.isMagicQuery("__debug__"))
        assertTrue(DebugWeatherStore.isMagicQuery("  __DEBUG__  "))
        assertTrue(DebugWeatherStore.isMagicQuery("#熊调试"))
        assertFalse(DebugWeatherStore.isMagicQuery("beijing"))
    }

    @Test
    fun apply_overridesTempAndPrecip_andEmitsRainingAlert() {
        val resources = mockResources()
        val city = WeatherFixtures.city("青岛")
        val base =
            WeatherSnapshot(
                city = city,
                current =
                    WeatherFixtures.current(
                        weatherCode = 0,
                        temperatureC = 22.0,
                        precipitationRateMmPerHour = 0.0,
                    ),
                hourly =
                    listOf(
                        WeatherFixtures.hourly("2026-08-02T12:00", precipitationMm = 0.0, weatherCode = 0),
                    ),
                daily = emptyList(),
                alerts =
                    listOf(
                        WeatherAlert("clear", "ok", AlertSeverity.INFO, AlertKind.CLEAR),
                    ),
            )
        val shown =
            DebugWeatherStore.apply(
                base,
                DebugWeatherOverride(
                    temperatureC = 30.0,
                    weatherCode = 63,
                    precipitationRateMmPerHour = 2.2,
                ),
                resources,
            )
        assertEquals(30.0, shown.current.temperatureC)
        assertEquals(63, shown.current.weatherCode)
        assertEquals(2.2, shown.current.precipitationRateMmPerHour)
        assertEquals(2.2, shown.hourly.first().precipitationMm)
        assertTrue(shown.alerts.any { it.kind == AlertKind.RAINING })
        assertFalse(shown.alerts.any { it.kind == AlertKind.CLEAR })
    }

    @Test
    fun apply_onlyTemp_feelsFollowsTemp() {
        val resources = mockResources()
        val base =
            WeatherSnapshot(
                city = WeatherFixtures.city("青岛"),
                current =
                    WeatherFixtures.current(
                        weatherCode = 0,
                        temperatureC = 22.0,
                        apparentTemperatureC = 27.0,
                    ),
                hourly =
                    listOf(
                        WeatherFixtures.hourly("2026-08-02T12:00", precipitationMm = 0.0, weatherCode = 0),
                    ),
                daily = emptyList(),
                alerts = emptyList(),
            )
        val shown =
            DebugWeatherStore.apply(
                base,
                DebugWeatherOverride(temperatureC = -89.2),
                resources,
            )
        assertEquals(-89.2, shown.current.temperatureC)
        // 只改实际温度时体感自动跟随，避免预警按旧体感判断
        assertEquals(-89.2, shown.current.apparentTemperatureC)
    }

    @Test
    fun apply_bothTemps_keepsIndependentFeels() {
        val resources = mockResources()
        val base =
            WeatherSnapshot(
                city = WeatherFixtures.city("青岛"),
                current = WeatherFixtures.current(weatherCode = 0, temperatureC = 22.0),
                hourly = listOf(WeatherFixtures.hourly("2026-08-02T12:00", precipitationMm = 0.0, weatherCode = 0)),
                daily = emptyList(),
                alerts = emptyList(),
            )
        val shown =
            DebugWeatherStore.apply(
                base,
                DebugWeatherOverride(temperatureC = -89.2, apparentTemperatureC = -95.0),
                resources,
            )
        assertEquals(-89.2, shown.current.temperatureC)
        assertEquals(-95.0, shown.current.apparentTemperatureC)
    }

    @Test
    fun apply_dailyTempOverride_emitsHeatWaveAlert() {
        val resources = mockResources()
        val base =
            WeatherSnapshot(
                city = WeatherFixtures.city("青岛"),
                current = WeatherFixtures.current(weatherCode = 0, temperatureC = 30.0),
                hourly =
                    listOf(
                        WeatherFixtures.hourly("2026-08-02T12:00", precipitationMm = 0.0, weatherCode = 0),
                    ),
                daily =
                    (0 until 7).map { i ->
                        WeatherFixtures.daily(
                            date = "2026-08-${(i + 2).toString().padStart(2, '0')}",
                            tempMaxC = 30.0,
                            tempMinC = 24.0,
                        )
                    },
                alerts = emptyList(),
            )
        val shown =
            DebugWeatherStore.apply(
                base,
                DebugWeatherOverride(dailyTempMaxC = listOf(37.0, 38.0, 39.0, 36.0, 35.0)),
                resources,
            )
        assertEquals(37.0, shown.daily[0].tempMaxC)
        assertEquals(38.0, shown.daily[1].tempMaxC)
        assertEquals(39.0, shown.daily[2].tempMaxC)
        // 未覆盖的第 6、7 天保留实况
        assertEquals(30.0, shown.daily[5].tempMaxC)
        assertTrue(shown.alerts.any { it.kind == AlertKind.HEAT_WAVE })
    }

    @Test
    fun apply_heatAlert_usesRequestedTemperatureUnit() {
        val resources = mockResources()
        Mockito
            .`when`(resources.getString(Mockito.eq(R.string.alert_heat_msg), Mockito.any()))
            .thenAnswer { invocation ->
                val arg = invocation.arguments.getOrNull(1)
                val text = if (arg is Array<*>) arg.joinToString(",") { it.toString() } else arg.toString()
                "heat:$text"
            }
        val base =
            WeatherSnapshot(
                city = WeatherFixtures.city("青岛"),
                current = WeatherFixtures.current(weatherCode = 0, temperatureC = 30.0, apparentTemperatureC = 38.0),
                hourly = emptyList(),
                daily = emptyList(),
                alerts = emptyList(),
            )
        val shown =
            DebugWeatherStore.apply(
                base,
                DebugWeatherOverride(temperatureC = 38.0),
                resources,
                TemperatureUnit.FAHRENHEIT,
            )
        val heat = shown.alerts.first { it.kind == AlertKind.HEAT }
        // 38°C → 100°F；单位由调用方传入，不再烤死摄氏
        assertEquals("heat:100°F", heat.message)
    }

    private fun mockResources(): Resources {
        val resources = Mockito.mock(Resources::class.java)
        Mockito.`when`(resources.getString(Mockito.anyInt())).thenReturn("x")
        Mockito.`when`(resources.getString(Mockito.anyInt(), Mockito.any())).thenReturn("x")
        // 连续高温两个占位（%1$d + %2$s）
        Mockito.`when`(resources.getString(Mockito.anyInt(), Mockito.any(), Mockito.any())).thenReturn("x")
        // 避免未 stub 的 format 字符串崩掉
        Mockito.`when`(resources.getString(R.string.alert_raining_title)).thenReturn("rain")
        Mockito.`when`(resources.getString(R.string.alert_raining_msg)).thenReturn("raining")
        return resources
    }
}

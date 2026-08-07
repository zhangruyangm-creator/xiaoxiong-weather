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
        val base = WeatherSnapshot(
            city = city,
            current = WeatherFixtures.current(
                weatherCode = 0,
                temperatureC = 22.0,
                precipitationRateMmPerHour = 0.0
            ),
            hourly = listOf(
                WeatherFixtures.hourly("2026-08-02T12:00", precipitationMm = 0.0, weatherCode = 0)
            ),
            daily = emptyList(),
            alerts = listOf(
                WeatherAlert("clear", "ok", AlertSeverity.INFO, AlertKind.CLEAR)
            )
        )
        val shown = DebugWeatherStore.apply(
            base,
            DebugWeatherOverride(
                temperatureC = 30.0,
                weatherCode = 63,
                precipitationRateMmPerHour = 2.2
            ),
            resources
        )
        assertEquals(30.0, shown.current.temperatureC)
        assertEquals(63, shown.current.weatherCode)
        assertEquals(2.2, shown.current.precipitationRateMmPerHour)
        assertEquals(2.2, shown.hourly.first().precipitationMm)
        assertTrue(shown.alerts.any { it.kind == AlertKind.RAINING })
        assertFalse(shown.alerts.any { it.kind == AlertKind.CLEAR })
    }

    private fun mockResources(): Resources {
        val resources = Mockito.mock(Resources::class.java)
        Mockito.`when`(resources.getString(Mockito.anyInt())).thenReturn("x")
        Mockito.`when`(resources.getString(Mockito.anyInt(), Mockito.any())).thenReturn("x")
        // 避免未 stub 的 format 字符串崩掉
        Mockito.`when`(resources.getString(R.string.alert_raining_title)).thenReturn("rain")
        Mockito.`when`(resources.getString(R.string.alert_raining_msg)).thenReturn("raining")
        return resources
    }
}

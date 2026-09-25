package com.xiaoxiong.weather.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * 天气物理量夹紧。
 *
 * 背景：JSON 层已拒 `NaN`/`Infinity` 字面量，但**有限且荒谬**的值照样进得来
 * （上游字段错位、单位变更、临时故障）。这些值会原样上屏（"湿度 5000%"）。
 */
class WeatherValueClampTest {
    @Test
    fun `percent clamps out-of-range values`() {
        assertEquals(100, WeatherValueClamp.percent(5000))
        assertEquals(0, WeatherValueClamp.percent(-20))
        assertEquals(65, WeatherValueClamp.percent(65))
        assertNull(WeatherValueClamp.percent(null))
    }

    @Test
    fun `temperature clamps to plausible range`() {
        assertEquals(70.0, WeatherValueClamp.temperatureC(1e9)!!, 0.0)
        assertEquals(-100.0, WeatherValueClamp.temperatureC(-1e9)!!, 0.0)
        assertEquals(23.5, WeatherValueClamp.temperatureC(23.5)!!, 0.0)
    }

    @Test
    fun `windSpeed clamps below hurricane scale`() {
        assertEquals(500.0, WeatherValueClamp.windKmh(1e308)!!, 0.0)
        assertEquals(0.0, WeatherValueClamp.windKmh(-5.0)!!, 0.0)
        assertEquals(12.3, WeatherValueClamp.windKmh(12.3)!!, 0.0)
    }

    @Test
    fun `precipitation never goes negative`() {
        assertEquals(0.0, WeatherValueClamp.precipitationMm(-1.0)!!, 0.0)
        assertEquals(1000.0, WeatherValueClamp.precipitationMm(1e9)!!, 0.0)
    }

    @Test
    fun `pressure clamps to sea-level range`() {
        assertEquals(1013.0, WeatherValueClamp.pressureHpa(1013.0)!!, 0.0)
        assertEquals(1100.0, WeatherValueClamp.pressureHpa(9999.0)!!, 0.0)
        assertEquals(800.0, WeatherValueClamp.pressureHpa(0.0)!!, 0.0)
    }

    @Test
    fun `windDirection normalizes out-of-range values`() {
        assertEquals(10, WeatherValueClamp.windDirectionDeg(370))
        assertEquals(350, WeatherValueClamp.windDirectionDeg(-10))
        assertEquals(180, WeatherValueClamp.windDirectionDeg(180))
        assertNull(WeatherValueClamp.windDirectionDeg(null))
    }

    @Test
    fun `finiteOrNull drops non-finite accumulations`() {
        assertNull(WeatherValueClamp.finiteOrNull(Double.POSITIVE_INFINITY))
        assertNull(WeatherValueClamp.finiteOrNull(Double.NaN))
        assertEquals(3.0, WeatherValueClamp.finiteOrNull(3.0)!!, 0.0)
    }
}

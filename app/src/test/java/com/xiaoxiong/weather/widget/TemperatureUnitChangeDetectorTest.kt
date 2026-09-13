package com.xiaoxiong.weather.widget

import com.xiaoxiong.weather.data.TemperatureUnit
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TemperatureUnitChangeDetectorTest {
    @Test
    fun firstEmission_isNotChange() {
        val detector = TemperatureUnitChangeDetector()
        assertFalse(detector.onUnit(TemperatureUnit.CELSIUS))
    }

    @Test
    fun sameUnitRepeated_isNotChange() {
        val detector = TemperatureUnitChangeDetector()
        detector.onUnit(TemperatureUnit.CELSIUS)
        assertFalse(detector.onUnit(TemperatureUnit.CELSIUS))
    }

    @Test
    fun differentUnit_isChange() {
        val detector = TemperatureUnitChangeDetector()
        detector.onUnit(TemperatureUnit.CELSIUS)
        assertTrue(detector.onUnit(TemperatureUnit.FAHRENHEIT))
        assertTrue(detector.onUnit(TemperatureUnit.CELSIUS))
    }
}

package com.xiaoxiong.weather.data

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SelectedCityStoreSelectionTest {
    private fun selection(
        lat: Double,
        lon: Double,
        useLocation: Boolean,
    ) = SelectedCityStore.PersistedSelection(
        city = City(id = 1L, name = "测试市", latitude = lat, longitude = lon),
        useLocation = useLocation,
    )

    @Test
    fun sameSelection_acceptsSameCityWithinThresholdAndSameFlag() {
        val expected = selection(36.65, 117.12, useLocation = true)
        assertTrue(isSameSelection(expected, selection(36.655, 117.124, useLocation = true)))
        assertTrue(isSameSelection(expected, selection(36.641, 117.111, useLocation = true)))
    }

    @Test
    fun sameSelection_rejectsMovedCityOrChangedFlag() {
        val expected = selection(36.65, 117.12, useLocation = true)
        assertFalse(isSameSelection(expected, selection(36.67, 117.12, useLocation = true)))
        assertFalse(isSameSelection(expected, selection(36.65, 117.15, useLocation = true)))
        assertFalse(isSameSelection(expected, selection(36.65, 117.12, useLocation = false)))
    }

    @Test
    fun sameSelection_handlesMissingStates() {
        assertTrue(isSameSelection(null, null))
        assertFalse(isSameSelection(selection(36.65, 117.12, useLocation = true), null))
        assertFalse(isSameSelection(null, selection(36.65, 117.12, useLocation = true)))
    }
}

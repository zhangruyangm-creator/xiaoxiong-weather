package com.xiaoxiong.weather.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HourlyCardLayoutTest {
    @Test
    fun default_allVisibleInCanonicalOrder() {
        val ids = HourlyCardLayout.defaults.map { it.id }
        assertEquals(HourlyCardId.entries, ids)
        assertTrue(HourlyCardLayout.defaults.all { it.visible })
    }

    @Test
    fun merge_forcesChartVisible_andAppendsMissing() {
        val saved =
            listOf(
                HourlyCardPref(HourlyCardId.STRIP, true),
                HourlyCardPref(HourlyCardId.CHART, false),
                HourlyCardPref(HourlyCardId.PRECIP, false),
            )
        val merged = HourlyCardLayout.mergeWithDefaults(saved)
        assertEquals(
            listOf(
                HourlyCardId.STRIP,
                HourlyCardId.CHART,
                HourlyCardId.PRECIP,
            ),
            merged.map { it.id },
        )
        assertTrue(merged.first { it.id == HourlyCardId.CHART }.visible)
        assertFalse(merged.first { it.id == HourlyCardId.PRECIP }.visible)
    }

    @Test
    fun encodeDecode_roundTrip() {
        val prefs =
            HourlyCardLayout.withVisibility(
                HourlyCardLayout.move(HourlyCardLayout.defaults, fromIndex = 0, toIndex = 2),
                HourlyCardId.PRECIP,
                visible = false,
            )
        val again = HourlyCardLayout.decode(HourlyCardLayout.encode(prefs))
        assertEquals(prefs, again)
    }

    @Test
    fun decode_blankUsesDefault() {
        assertEquals(HourlyCardLayout.defaults, HourlyCardLayout.decode(null))
        assertEquals(HourlyCardLayout.defaults, HourlyCardLayout.decode("  "))
    }

    @Test
    fun withVisibility_ignoresHidingChart() {
        val prefs =
            HourlyCardLayout.withVisibility(
                HourlyCardLayout.defaults,
                HourlyCardId.CHART,
                visible = false,
            )
        assertTrue(prefs.first { it.id == HourlyCardId.CHART }.visible)
    }

    @Test
    fun move_reorders() {
        val moved = HourlyCardLayout.move(HourlyCardLayout.defaults, 0, 2)
        assertEquals(HourlyCardId.STRIP, moved[0].id)
        assertEquals(HourlyCardId.CHART, moved[1].id)
        assertEquals(HourlyCardId.PRECIP, moved[2].id)
    }
}

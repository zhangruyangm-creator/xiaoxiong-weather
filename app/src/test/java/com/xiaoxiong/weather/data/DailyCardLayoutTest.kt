package com.xiaoxiong.weather.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DailyCardLayoutTest {
    @Test
    fun default_allVisibleInCanonicalOrder() {
        val ids = DailyCardLayout.defaults.map { it.id }
        assertEquals(DailyCardId.entries, ids)
        assertTrue(DailyCardLayout.defaults.all { it.visible })
    }

    @Test
    fun merge_forcesForecastVisible_andAppendsMissing() {
        val saved =
            listOf(
                DailyCardPref(DailyCardId.SEASON_TIPS, true),
                DailyCardPref(DailyCardId.FORECAST, false),
                DailyCardPref(DailyCardId.HISTORY, false),
            )
        val merged = DailyCardLayout.mergeWithDefaults(saved)
        assertEquals(
            listOf(
                DailyCardId.SEASON_TIPS,
                DailyCardId.FORECAST,
                DailyCardId.HISTORY,
            ),
            merged.map { it.id },
        )
        assertTrue(merged.first { it.id == DailyCardId.FORECAST }.visible)
        assertFalse(merged.first { it.id == DailyCardId.HISTORY }.visible)
    }

    @Test
    fun encodeDecode_roundTrip() {
        val prefs =
            DailyCardLayout.withVisibility(
                DailyCardLayout.move(DailyCardLayout.defaults, fromIndex = 0, toIndex = 2),
                DailyCardId.HISTORY,
                visible = false,
            )
        val again = DailyCardLayout.decode(DailyCardLayout.encode(prefs))
        assertEquals(prefs, again)
    }

    @Test
    fun decode_blankUsesDefault() {
        assertEquals(DailyCardLayout.defaults, DailyCardLayout.decode(null))
        assertEquals(DailyCardLayout.defaults, DailyCardLayout.decode("  "))
    }

    @Test
    fun withVisibility_ignoresHidingForecast() {
        val prefs =
            DailyCardLayout.withVisibility(
                DailyCardLayout.defaults,
                DailyCardId.FORECAST,
                visible = false,
            )
        assertTrue(prefs.first { it.id == DailyCardId.FORECAST }.visible)
    }

    @Test
    fun move_reorders() {
        val moved = DailyCardLayout.move(DailyCardLayout.defaults, 0, 2)
        assertEquals(DailyCardId.SEASON_TIPS, moved[0].id)
        assertEquals(DailyCardId.HISTORY, moved[1].id)
        assertEquals(DailyCardId.FORECAST, moved[2].id)
    }
}

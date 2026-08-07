package com.xiaoxiong.weather.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TodayCardLayoutTest {

    @Test
    fun default_allVisibleInCanonicalOrder() {
        val ids = TodayCardLayout.DEFAULT.map { it.id }
        assertEquals(TodayCardId.entries, ids)
        assertTrue(TodayCardLayout.DEFAULT.all { it.visible })
    }

    @Test
    fun merge_forcesCurrentVisible_andAppendsMissing() {
        val saved = listOf(
            TodayCardPref(TodayCardId.RADAR, true),
            TodayCardPref(TodayCardId.CURRENT, false),
            TodayCardPref(TodayCardId.ALERTS, false)
        )
        val merged = TodayCardLayout.mergeWithDefaults(saved)
        assertEquals(
            listOf(
                TodayCardId.RADAR,
                TodayCardId.CURRENT,
                TodayCardId.ALERTS,
                TodayCardId.LIFE_INDICES,
                TodayCardId.AIR_QUALITY,
                TodayCardId.SUN_EXTRAS
            ),
            merged.map { it.id }
        )
        assertTrue(merged.first { it.id == TodayCardId.CURRENT }.visible)
        assertFalse(merged.first { it.id == TodayCardId.ALERTS }.visible)
        assertTrue(merged.first { it.id == TodayCardId.LIFE_INDICES }.visible)
    }

    @Test
    fun encodeDecode_roundTrip() {
        val prefs = TodayCardLayout.withVisibility(
            TodayCardLayout.move(TodayCardLayout.DEFAULT, fromIndex = 2, toIndex = 0),
            TodayCardId.RADAR,
            visible = false
        )
        val again = TodayCardLayout.decode(TodayCardLayout.encode(prefs))
        assertEquals(prefs, again)
    }

    @Test
    fun decode_blankUsesDefault() {
        assertEquals(TodayCardLayout.DEFAULT, TodayCardLayout.decode(null))
        assertEquals(TodayCardLayout.DEFAULT, TodayCardLayout.decode("  "))
    }

    @Test
    fun withVisibility_ignoresHidingCurrent() {
        val prefs = TodayCardLayout.withVisibility(
            TodayCardLayout.DEFAULT,
            TodayCardId.CURRENT,
            visible = false
        )
        assertTrue(prefs.first { it.id == TodayCardId.CURRENT }.visible)
    }

    @Test
    fun move_reorders() {
        val moved = TodayCardLayout.move(TodayCardLayout.DEFAULT, 0, 2)
        assertEquals(TodayCardId.CURRENT, moved[0].id)
        assertEquals(TodayCardId.LIFE_INDICES, moved[1].id)
        assertEquals(TodayCardId.ALERTS, moved[2].id)
    }
}

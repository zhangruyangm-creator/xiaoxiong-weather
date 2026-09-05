package com.xiaoxiong.weather.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RadarTimelineTest {
    private fun frame(
        time: Long,
        nowcast: Boolean,
    ) = RadarFrame(
        timeEpochSec = time,
        path = "/v2/radar/$time",
        imageUrl = "https://example/$time.png",
        isNowcast = nowcast,
    )

    @Test
    fun merge_appendsNowcastAfterPast() {
        val past = listOf(frame(100, false), frame(200, false))
        val nowcast = listOf(frame(300, true), frame(400, true))
        val merged = mergeRadarTimeline(past, nowcast)
        assertEquals(listOf(100L, 200L, 300L, 400L), merged.map { it.timeEpochSec })
        assertFalse(merged[1].isNowcast)
        assertTrue(merged[2].isNowcast)
    }

    @Test
    fun merge_dropsNowcastThatDuplicatesPastTime() {
        val past = listOf(frame(100, false), frame(200, false))
        val nowcast = listOf(frame(200, true), frame(300, true))
        val merged = mergeRadarTimeline(past, nowcast)
        assertEquals(listOf(100L, 200L, 300L), merged.map { it.timeEpochSec })
        assertFalse(merged[1].isNowcast)
        assertTrue(merged[2].isNowcast)
    }

    @Test
    fun observedNowIndex_pointsAtLastPast() {
        val frames =
            listOf(
                frame(100, false),
                frame(200, false),
                frame(300, true),
                frame(400, true),
            )
        assertEquals(1, radarObservedNowIndex(frames))
    }

    @Test
    fun observedNowIndex_allNowcast_usesLast() {
        val frames = listOf(frame(300, true), frame(400, true))
        assertEquals(1, radarObservedNowIndex(frames))
    }

    @Test
    fun observedNowIndex_empty_isZero() {
        assertEquals(0, radarObservedNowIndex(emptyList()))
    }
}

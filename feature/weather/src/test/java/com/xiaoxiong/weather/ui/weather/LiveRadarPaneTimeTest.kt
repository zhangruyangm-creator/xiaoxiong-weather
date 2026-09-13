package com.xiaoxiong.weather.ui.weather

import com.xiaoxiong.weather.data.RadarFrame
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.ZoneId

class LiveRadarPaneTimeTest {
    @Test
    fun formatRadarTime_usesCityTimezone() {
        // UTC epoch 0 = 1970-01-01T00:00:00Z
        assertEquals("08:00", formatRadarTime(0L, "Asia/Shanghai"))
        assertEquals("19:00", formatRadarTime(0L, "America/New_York"))
    }

    @Test
    fun formatRadarTime_nullTimezoneFallsBackToSystem() {
        val expected =
            Instant
                .ofEpochSecond(0L)
                .atZone(ZoneId.systemDefault())
                .format(RadarTimeFormatter)
        assertEquals(expected, formatRadarTime(0L, null))
    }

    @Test
    fun formatRadarTime_invalidTimezoneFallsBackToSystem() {
        val expected =
            Instant
                .ofEpochSecond(0L)
                .atZone(ZoneId.systemDefault())
                .format(RadarTimeFormatter)
        assertEquals(expected, formatRadarTime(0L, "not/a/zone"))
    }

    @Test
    fun radarTimelineCache_reusesWithinTtl_andExpiresAfter() {
        var now = 1_000L
        val cache = RadarTimelineCache(ttlMs = 600_000L, nowMs = { now })
        val frames = listOf(RadarFrame(timeEpochSec = 1L, path = "/a", imageUrl = "u1"))
        cache.put(39.9042, 116.4074, zoom = 6, frames = frames)

        assertEquals(frames, cache.get(39.9042, 116.4074, 6))
        // TTL 边界内仍复用，重进不重播扫描
        now += 600_000L
        assertEquals(frames, cache.get(39.9042, 116.4074, 6))
        // 超过 TTL 后回源
        now += 1L
        assertNull(cache.get(39.9042, 116.4074, 6))
    }

    @Test
    fun radarLoadPolicy_manualRefresh_isConsumedOnce() {
        val policy = RadarTimelineLoadPolicy()
        policy.onCity("cityA")
        assertFalse(policy.consumeBypassCache())

        policy.requestManualRefresh()
        assertTrue(policy.consumeBypassCache())
        // 一次性：触发过的那次加载之后再进入重新走 TTL 缓存
        assertFalse(policy.consumeBypassCache())
    }

    @Test
    fun radarLoadPolicy_cityChange_dropsPendingManualRefresh() {
        val policy = RadarTimelineLoadPolicy()
        policy.onCity("cityA")
        policy.requestManualRefresh()
        // 手动刷新后立刻换城：未消费的标记作废，新城市重新走 TTL 缓存
        policy.onCity("cityB")
        assertFalse(policy.consumeBypassCache())
    }

    @Test
    fun radarTimelineCache_differentZoomOrCoords_miss() {
        val now = 1_000L
        val cache = RadarTimelineCache(ttlMs = 600_000L, nowMs = { now })
        val frames = listOf(RadarFrame(timeEpochSec = 1L, path = "/a", imageUrl = "u1"))
        cache.put(39.9042, 116.4074, zoom = 6, frames = frames)

        assertNull(cache.get(39.9042, 116.4074, 7))
        assertNull(cache.get(31.2304, 121.4737, 6))
    }
}

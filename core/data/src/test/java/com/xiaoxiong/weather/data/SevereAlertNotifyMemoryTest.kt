package com.xiaoxiong.weather.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SevereAlertNotifyMemoryTest {
    private val now = 1_000_000_000_000L
    private val window = 6 * 60 * 60 * 1000L

    @Test
    fun parse_keepsFreshAndFuture_dropsExpired() {
        val parsed =
            SevereAlertNotifyMemory.parse(
                entries =
                    setOf(
                        "青岛|FOG|1\t${now - 1_000}",
                        // 恰好到静默窗口：视为过期，允许再次推送
                        "青岛|HEAT|2\t${now - window}",
                        // 时钟回拨产生的未来时间戳也要保留，避免重复轰炸
                        "青岛|WIND|3\t${now + 5_000}",
                    ),
                now = now,
            )

        assertEquals(setOf("青岛|FOG|1", "青岛|WIND|3"), parsed.keys)
        assertEquals(now - 1_000, parsed.getValue("青岛|FOG|1"))
        assertEquals(now + 5_000, parsed.getValue("青岛|WIND|3"))
    }

    @Test
    fun parse_skipsMalformedEntries() {
        val parsed =
            SevereAlertNotifyMemory.parse(
                entries = setOf("no-tab", "\t123", "sig\tnot-a-number", ""),
                now = now,
            )
        assertTrue(parsed.isEmpty())
    }

    @Test
    fun parse_nullOrEmpty_returnsEmpty() {
        assertTrue(SevereAlertNotifyMemory.parse(null, now).isEmpty())
        assertTrue(SevereAlertNotifyMemory.parse(emptySet(), now).isEmpty())
    }

    @Test
    fun encode_roundTripsThroughParse() {
        val entries = mapOf("青岛|FOG|1" to now, "北京|UV|9" to now - 10_000)
        val encoded = SevereAlertNotifyMemory.encode(entries)
        assertEquals(2, encoded.size)
        assertTrue(encoded.any { it.startsWith("青岛|FOG|1\t") })
        assertTrue(encoded.any { it.startsWith("北京|UV|9\t") })
        assertEquals(entries, SevereAlertNotifyMemory.parse(encoded, now))
    }
}

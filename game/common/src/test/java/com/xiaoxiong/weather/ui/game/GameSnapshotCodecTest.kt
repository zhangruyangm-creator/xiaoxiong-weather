package com.xiaoxiong.weather.ui.game

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [SnapshotCodec] 分组编解码的 JVM 单测，重点覆盖空列表往返对称性。
 */
class GameSnapshotCodecTest {
    @Test
    fun emptyGroups_roundTripsToEmptyList() {
        val encoded = SnapshotCodec.encodeGroups(emptyList())
        assertEquals("", encoded)
        val decoded = SnapshotCodec.decodeGroups(encoded)
        assertTrue("空字符串应解码为空列表而不是 null", decoded != null)
        assertEquals(0, decoded!!.size)
    }

    @Test
    fun nullText_decodesToNull() {
        assertNull(SnapshotCodec.decodeGroups(null))
    }

    @Test
    fun blankText_decodesToEmptyList() {
        val decoded = SnapshotCodec.decodeGroups("   ")
        assertTrue(decoded != null)
        assertEquals(0, decoded!!.size)
    }

    @Test
    fun groups_roundTripValues() {
        val decoded =
            SnapshotCodec.decodeGroups(
                SnapshotCodec.encodeGroups(listOf(listOf(1, 2.5f), listOf(-3f, 4f))),
            )
        assertTrue(decoded != null)
        assertEquals(2, decoded!!.size)
        assertTrue(decoded[0].contentEquals(floatArrayOf(1f, 2.5f)))
        assertTrue(decoded[1].contentEquals(floatArrayOf(-3f, 4f)))
    }

    @Test
    fun malformedText_decodesToNull() {
        assertNull(SnapshotCodec.decodeGroups("1,2;not-a-number"))
    }
}

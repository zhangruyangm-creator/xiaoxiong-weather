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
    fun malformedGroup_isDroppedAndSiblingsSurvive() {
        val decoded = SnapshotCodec.decodeGroups("1,2;not-a-number;3,4")
        assertTrue("坏组只丢自己，其余照常", decoded != null)
        assertEquals(2, decoded!!.size)
        assertTrue(decoded[0].contentEquals(floatArrayOf(1f, 2f)))
        assertTrue(decoded[1].contentEquals(floatArrayOf(3f, 4f)))
    }

    @Test
    fun nonFiniteGroupValue_isDropped() {
        val decoded = SnapshotCodec.decodeGroups("1,2;NaN,3;Infinity,4;5,6")
        assertEquals("NaN/Infinity 组必须丢弃，不能进物理", 2, decoded!!.size)
        assertTrue(decoded[0].contentEquals(floatArrayOf(1f, 2f)))
        assertTrue(decoded[1].contentEquals(floatArrayOf(5f, 6f)))
    }

    @Test
    fun allGroupsMalformed_yieldsEmptyListNotNull() {
        val decoded = SnapshotCodec.decodeGroups("bad;NaN,1")
        assertTrue("整段坏掉时返回空列表（实体都没了），而不是 null 让上层清档", decoded != null)
        assertEquals(0, decoded!!.size)
    }

    @Test
    fun floatArray_withNaN_isRejected() {
        assertNull("标量数组无法留空洞，含 NaN 只能整份拒绝", SnapshotCodec.decodeFloatArray("1,NaN,3"))
    }

    @Test
    fun floatArray_withMalformedValue_isRejected() {
        assertNull(SnapshotCodec.decodeFloatArray("1,oops,3"))
    }

    @Test
    fun floatArray_roundTripValues() {
        val decoded = SnapshotCodec.decodeFloatArray(SnapshotCodec.encode(1, 2.5f, -3))
        assertTrue(decoded != null)
        assertTrue(decoded!!.contentEquals(floatArrayOf(1f, 2.5f, -3f)))
    }
}

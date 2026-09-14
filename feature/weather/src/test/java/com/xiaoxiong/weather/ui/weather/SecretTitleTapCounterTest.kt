package com.xiaoxiong.weather.ui.weather

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 标题连点彩蛋计数器：
 * - 2s 窗口内连点 4 次触发；3 次不触发；
 * - 超时清零后需重新连点满 4 次；
 * - 触发后计数归零，退出枢纽后再次连点可以重新触发；
 * - 正常单击不受影响。
 */
class SecretTitleTapCounterTest {
    @Test
    fun requiredTaps_isFour() {
        assertEquals("首页入口应为连点 4 下", 4, SecretTitleTapCounter.REQUIRED_TAPS)
        assertEquals(2_000L, SecretTitleTapCounter.WINDOW_MS)
    }

    @Test
    fun fourTapsWithinWindow_triggersOnce() {
        val counter = SecretTitleTapCounter()
        assertFalse(counter.onTap(1_000L))
        assertFalse(counter.onTap(1_400L))
        assertFalse(counter.onTap(1_800L))
        assertTrue("第 4 下应触发", counter.onTap(2_200L))
        // 触发后清零：第 5 下不再触发
        assertFalse(counter.onTap(2_300L))
    }

    @Test
    fun threeTaps_doesNotTrigger() {
        val counter = SecretTitleTapCounter()
        assertFalse(counter.onTap(0L))
        assertFalse(counter.onTap(500L))
        assertFalse(counter.onTap(1_000L))
    }

    @Test
    fun tapOutsideWindow_resetsCount() {
        val counter = SecretTitleTapCounter()
        assertFalse(counter.onTap(0L))
        assertFalse(counter.onTap(500L))
        assertFalse(counter.onTap(1_000L))
        // 超过窗口：前 3 下作废，重新计数
        assertFalse(counter.onTap(1_000L + SecretTitleTapCounter.WINDOW_MS + 1))
        assertFalse(counter.onTap(3_500L))
        assertFalse(counter.onTap(4_000L))
        assertTrue(counter.onTap(4_500L))
    }

    @Test
    fun windowBoundary_isInclusive() {
        val counter = SecretTitleTapCounter()
        assertFalse(counter.onTap(0L))
        assertFalse(counter.onTap(SecretTitleTapCounter.WINDOW_MS))
        assertFalse(counter.onTap(SecretTitleTapCounter.WINDOW_MS * 2))
        assertTrue(counter.onTap(SecretTitleTapCounter.WINDOW_MS * 3))
    }

    @Test
    fun afterTrigger_canTriggerAgain() {
        val counter = SecretTitleTapCounter()
        repeat(3) { assertFalse(counter.onTap(10_000L + it * 100L)) }
        assertTrue(counter.onTap(10_300L))
        // 退出枢纽后再次连点，仍能重新触发
        repeat(3) { assertFalse(counter.onTap(20_000L + it * 100L)) }
        assertTrue(counter.onTap(20_300L))
    }

    @Test
    fun singleTap_neverTriggers() {
        val counter = SecretTitleTapCounter()
        assertFalse(counter.onTap(0L))
        assertFalse(counter.onTap(100_000L))
    }
}

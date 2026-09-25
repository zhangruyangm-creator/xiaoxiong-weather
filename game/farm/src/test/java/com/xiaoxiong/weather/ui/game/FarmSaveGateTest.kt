package com.xiaoxiong.weather.ui.game

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [FarmSaveGate] 的乱序防护测试。
 *
 * 背景：农场有两条写入来源——每次操作/5 秒定时的异步写，以及退出时的同步写。
 * 两者并发时先提交的快照可能后落盘，磁盘上会留下旧进度，比丢一次进度更难排查。
 */
class FarmSaveGateTest {
    @Test
    fun `fresh submissions are accepted`() {
        val gate = FarmSaveGate()
        val first = gate.nextSubmit()
        assertTrue(gate.shouldWrite(first))
        gate.markWritten(first)
        val second = gate.nextSubmit()
        assertTrue("更新的提交应放行", gate.shouldWrite(second))
    }

    @Test
    fun `stale submission arriving after newer write is dropped`() {
        val gate = FarmSaveGate()
        val older = gate.nextSubmit()
        val newer = gate.nextSubmit()
        gate.markWritten(newer)
        assertFalse("旧快照后到必须丢弃，否则磁盘回退", gate.shouldWrite(older))
    }

    @Test
    fun `already written submission is not rewritten`() {
        val gate = FarmSaveGate()
        val seq = gate.nextSubmit()
        gate.markWritten(seq)
        assertFalse(gate.shouldWrite(seq))
    }

    @Test
    fun `submission ids increase monotonically`() {
        val gate = FarmSaveGate()
        val ids = (1..5).map { gate.nextSubmit() }
        assertEquals(listOf(1L, 2L, 3L, 4L, 5L), ids)
    }
}

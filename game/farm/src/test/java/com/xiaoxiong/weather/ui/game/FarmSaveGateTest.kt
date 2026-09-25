package com.xiaoxiong.weather.ui.game

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors

/**
 * [FarmSaveGate] 的乱序与并发防护测试。
 *
 * 背景：农场有两条写入来源——每次操作/5 秒定时的异步写，以及退出时的同步写。
 * 两者并发时先提交的快照可能后落盘，磁盘上会留下旧进度，比丢一次进度更难排查。
 */
class FarmSaveGateTest {
    @Test
    fun `fresh submissions are accepted`() {
        val gate = FarmSaveGate()
        val first = gate.nextSubmit()
        assertTrue(gate.writeIfNewest(first) {})
        val second = gate.nextSubmit()
        assertTrue("更新的提交应放行", gate.writeIfNewest(second) {})
    }

    @Test
    fun `stale submission arriving after newer write is dropped`() {
        val gate = FarmSaveGate()
        val older = gate.nextSubmit()
        val newer = gate.nextSubmit()
        gate.writeIfNewest(newer) {}
        assertFalse("旧快照后到必须丢弃，否则磁盘回退", gate.writeIfNewest(older) {})
    }

    @Test
    fun `already written submission is not rewritten`() {
        val gate = FarmSaveGate()
        val seq = gate.nextSubmit()
        gate.writeIfNewest(seq) {}
        assertFalse(gate.writeIfNewest(seq) {})
    }

    @Test
    fun `stale submission does not execute its write body`() {
        val gate = FarmSaveGate()
        val older = gate.nextSubmit()
        val newer = gate.nextSubmit()
        gate.writeIfNewest(newer) {}
        var wroteStale = false
        gate.writeIfNewest(older) { wroteStale = true }
        assertFalse("被拒绝的提交不得产生任何副作用（不能先删档再自我拒绝）", wroteStale)
    }

    @Test
    fun `submission ids increase monotonically`() {
        val gate = FarmSaveGate()
        val ids = (1..5).map { gate.nextSubmit() }
        assertEquals(listOf(1L, 2L, 3L, 4L, 5L), ids)
    }

    @Test
    fun `concurrent submissions get unique ids and only the newest wins`() {
        val gate = FarmSaveGate()
        val threads = 8
        val perThread = 200
        val pool = Executors.newFixedThreadPool(threads)
        val start = CountDownLatch(1)
        val done = CountDownLatch(threads)
        val seqs = java.util.Collections.synchronizedList(mutableListOf<Long>())
        val written = java.util.Collections.synchronizedList(mutableListOf<Long>())
        repeat(threads) {
            pool.execute {
                start.await()
                repeat(perThread) {
                    val seq = gate.nextSubmit()
                    seqs += seq
                    // 写入体记录实际落盘的序号，用于验证"最终留下的是最大序号"
                    gate.writeIfNewest(seq) { written += seq }
                }
                done.countDown()
            }
        }
        start.countDown()
        done.await()
        pool.shutdown()

        assertEquals("序号不能重复：重复会让其中的写入被静默丢弃", threads * perThread, seqs.toSet().size)
        assertEquals("最终必须停在最大序号", seqs.max(), written.last())
        assertTrue("落盘次数不应超过提交次数", written.size <= seqs.size)
    }
}

package com.xiaoxiong.weather.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class PerformanceMonitorTest {
    @Before
    fun setUp() {
        PerformanceMonitor.enabled = false
        PerformanceMonitor.slowRequestThresholdMs = 1000
        PerformanceMonitor.slowFrameThresholdMs = 16
        PerformanceMonitor.reset()
    }

    // ── enabled 开关 ────────────────────────────────────────────

    @Test
    fun disabled_doesNotRecord() {
        PerformanceMonitor.enabled = false
        PerformanceMonitor.recordStartup(100)
        PerformanceMonitor.recordNetwork("test", 200)
        PerformanceMonitor.recordFrame(16_000_000)
        PerformanceMonitor.recordMemory(Runtime.getRuntime())
        val report = PerformanceMonitor.report()
        assertTrue(report.contains("(无数据)"))
    }

    @Test
    fun enabled_recordsData() {
        PerformanceMonitor.enabled = true
        PerformanceMonitor.recordStartup(500)
        PerformanceMonitor.recordStartup(800)
        val report = PerformanceMonitor.report()
        assertTrue(report.contains("avg=650ms"))
        assertTrue(report.contains("max=800ms"))
        assertTrue(report.contains("n=2"))
    }

    // ── LongBuffer 环形缓冲区 ──────────────────────────────────

    @Test
    fun longBuffer_respectsCapacity() {
        val buf = PerformanceMonitor.LongBuffer(5)
        for (i in 1..10) {
            buf.add(i.toLong())
        }
        assertEquals(5, buf.size)
        val snapshot = buf.snapshot()
        assertEquals(5, snapshot.size)
        // 最近 5 个值: 6, 7, 8, 9, 10
        assertEquals(6L, snapshot[0])
        assertEquals(10L, snapshot[4])
    }

    @Test
    fun longBuffer_snapshotIsSorted() {
        val buf = PerformanceMonitor.LongBuffer(10)
        buf.add(50)
        buf.add(10)
        buf.add(30)
        buf.add(20)
        buf.add(40)
        val snapshot = buf.snapshot()
        assertEquals(10L, snapshot[0])
        assertEquals(20L, snapshot[1])
        assertEquals(30L, snapshot[2])
        assertEquals(40L, snapshot[3])
        assertEquals(50L, snapshot[4])
    }

    @Test
    fun longBuffer_clearResetsState() {
        val buf = PerformanceMonitor.LongBuffer(10)
        buf.add(42)
        assertEquals(1, buf.size)
        buf.clear()
        assertEquals(0, buf.size)
        assertTrue(buf.snapshot().isEmpty())
    }

    // ── 冷启动 ──────────────────────────────────────────────────

    @Test
    fun recordStartup_capturesValues() {
        PerformanceMonitor.enabled = true
        PerformanceMonitor.recordStartup(300)
        PerformanceMonitor.recordStartup(450)
        val report = PerformanceMonitor.report()
        assertTrue(report.contains("avg=375ms"))
        assertTrue(report.contains("max=450ms"))
    }

    // ── 网络请求 ────────────────────────────────────────────────

    @Test
    fun recordNetwork_groupsByPath() {
        PerformanceMonitor.enabled = true
        PerformanceMonitor.recordNetwork("forecast", 200)
        PerformanceMonitor.recordNetwork("forecast", 400)
        PerformanceMonitor.recordNetwork("search", 100)
        val report = PerformanceMonitor.report()
        assertTrue(report.contains("forecast:"))
        assertTrue(report.contains("search:"))
    }

    @Test
    fun recordNetwork_countsSlowRequests() {
        PerformanceMonitor.enabled = true
        PerformanceMonitor.slowRequestThresholdMs = 500
        PerformanceMonitor.recordNetwork("api", 600)
        PerformanceMonitor.recordNetwork("api", 100)
        val report = PerformanceMonitor.report()
        assertTrue(report.contains("慢请求(>500ms): 1"))
    }

    // ── 帧渲染 ──────────────────────────────────────────────────

    @Test
    fun recordFrame_convertsNanosToMillis() {
        PerformanceMonitor.enabled = true
        PerformanceMonitor.slowFrameThresholdMs = 16
        // 8ms = 正常帧
        PerformanceMonitor.recordFrame(8_000_000)
        // 33ms = 慢帧
        PerformanceMonitor.recordFrame(33_000_000)
        val report = PerformanceMonitor.report()
        assertTrue(report.contains("慢帧(>16ms): 1"))
    }

    // ── 内存 ────────────────────────────────────────────────────

    @Test
    fun recordMemory_capturesCurrentUsage() {
        PerformanceMonitor.enabled = true
        PerformanceMonitor.recordMemory()
        val report = PerformanceMonitor.report()
        assertTrue(report.contains("MB"))
        assertTrue(report.contains("n=1"))
    }

    // ── 自定义指标 ──────────────────────────────────────────────

    @Test
    fun recordCustom_createsBufferBy() {
        PerformanceMonitor.enabled = true
        PerformanceMonitor.recordCustom("city_search", 50)
        PerformanceMonitor.recordCustom("city_search", 80)
        val report = PerformanceMonitor.report()
        assertTrue(report.contains("city_search:"))
    }

    // ── reset ───────────────────────────────────────────────────

    @Test
    fun reset_clearsEverything() {
        PerformanceMonitor.enabled = true
        PerformanceMonitor.recordStartup(100)
        PerformanceMonitor.recordNetwork("test", 200)
        PerformanceMonitor.recordMemory()
        PerformanceMonitor.reset()
        val report = PerformanceMonitor.report()
        assertTrue(report.contains("(无数据)"))
    }

    // ── report 格式 ─────────────────────────────────────────────

    @Test
    fun report_containsAllSections() {
        PerformanceMonitor.enabled = true
        PerformanceMonitor.recordStartup(100)
        val report = PerformanceMonitor.report()
        assertTrue(report.contains("冷启动"))
        assertTrue(report.contains("网络请求"))
        assertTrue(report.contains("帧渲染"))
        assertTrue(report.contains("内存使用"))
    }
}

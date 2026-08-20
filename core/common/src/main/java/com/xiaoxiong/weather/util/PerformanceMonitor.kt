package com.xiaoxiong.weather.util

import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

/**
 * 轻量级应用级性能监控。
 *
 * 设计原则：
 * - **零 release 开销**：[enabled] 为 false 时所有 record 方法立即返回（单次 volatile 读）。
 * - **无锁写入**：各指标独立写入固定容量环形缓冲区，不竞争。
 * - **无 GC 压力**：缓冲区为 LongArray，不产生临时对象。
 * - **异步日志**：定时汇总在后台守护线程执行，不阻塞调用方。
 *
 * 用法：
 * ```kotlin
 * // Application.onCreate() 中启用
 * PerformanceMonitor.enabled = BuildConfig.DEBUG
 *
 * // 各处埋点
 * PerformanceMonitor.recordStartup(durationMs)
 * PerformanceMonitor.recordNetwork("forecast", 320)
 * PerformanceMonitor.recordMemory(Runtime.getRuntime())
 * ```
 */
object PerformanceMonitor {
    private const val TAG = "[PerfMonitor] "
    private const val BUFFER_CAPACITY = 64
    private const val LOG_INTERVAL_MS = 60_000L // 1 分钟汇总一次

    /** 是否启用。Release 构建设为 false 即可实现零开销。 */
    @Volatile
    var enabled: Boolean = false

    // ── 指标缓冲区 ──────────────────────────────────────────────

    private val startupBuffer = LongBuffer(BUFFER_CAPACITY)
    private val networkBuffers = ConcurrentHashMap<String, LongBuffer>()
    private val frameBuffer = LongBuffer(BUFFER_CAPACITY)
    private val memoryBuffer = LongBuffer(BUFFER_CAPACITY)

    // ── 慢请求计数器 ────────────────────────────────────────────

    private val slowNetworkCount = AtomicLong(0)
    private val slowFrameCount = AtomicLong(0)

    /** 慢请求阈值（毫秒）。超过此值视为慢请求，计入计数器并输出 warn 日志。 */
    var slowRequestThresholdMs: Long = 1000

    /** 慢帧阈值（毫秒）。超过此值视为丢帧。 */
    var slowFrameThresholdMs: Long = 16

    // ── 定时日志线程 ────────────────────────────────────────────

    // 改为 by lazy：原实现是 object 初始化时立即 start，但 [enabled] 默认 false，
    // 导致 Release 包里线程仍每 60s 被 sleep 唤醒一次（有上下文切换开销），
    // 与"Release 零开销"原则不符。现在仅在首次访问时启动线程——由 WeatherApp 设置
    // [enabled]=true 后调用 [startLoggingIfEnabled] 显式触发，避免任何无意义唤醒
    private val logThread: Thread? by lazy { createLogThread() }

    /**
     * 应用启动时调用：若 [enabled] 为 true 则启动定时日志线程，false 时直接返回。
     * 这样 Release 包 ([enabled]=false) 完全不会有定时线程和睡眠唤醒。
     */
    fun startLoggingIfEnabled() {
        if (!enabled) return
        // 触发 lazy 初始化线程
        logThread
    }

    private fun createLogThread(): Thread =
        Thread({
            while (!Thread.currentThread().isInterrupted) {
                try {
                    Thread.sleep(LOG_INTERVAL_MS)
                    dumpMetrics()
                } catch (_: InterruptedException) {
                    Thread.currentThread().interrupt()
                    break
                }
            }
        }, "PerfMonitor-log").apply {
            isDaemon = true
            start()
        }

    // ── 公开 API ────────────────────────────────────────────────

    /** 记录冷启动耗时（毫秒）。 */
    fun recordStartup(durationMs: Long) {
        if (!enabled) return
        startupBuffer.add(durationMs)
        if (durationMs > 2000) {
            println("$TAG 冷启动耗时 ${durationMs}ms，超过 2s 阈值")
        }
    }

    /**
     * 记录网络请求耗时。
     * @param path 请求路径或标签（如 "forecast"、"search"）
     * @param durationMs 耗时（毫秒）
     * @param success 请求是否成功
     */
    fun recordNetwork(
        path: String,
        durationMs: Long,
        success: Boolean = true,
    ) {
        if (!enabled) return
        getNetworkBuffer(path).add(durationMs)
        if (durationMs > slowRequestThresholdMs) {
            slowNetworkCount.incrementAndGet()
            println("$TAG 慢网络请求: $path ${durationMs}ms success=$success")
        }
    }

    /** 记录单帧渲染耗时（纳秒转毫秒后存入）。 */
    fun recordFrame(frameTimeNs: Long) {
        if (!enabled) return
        val ms = frameTimeNs / 1_000_000
        frameBuffer.add(ms)
        if (ms > slowFrameThresholdMs) {
            slowFrameCount.incrementAndGet()
        }
    }

    /** 记录当前内存使用（MB）。 */
    fun recordMemory(runtime: Runtime = Runtime.getRuntime()) {
        if (!enabled) return
        val usedMb = (runtime.totalMemory() - runtime.freeMemory()) / 1024 / 1024
        memoryBuffer.add(usedMb)
    }

    /**
     * 记录自定义业务指标。
     * @param name 指标名称
     * @param durationMs 耗时（毫秒）
     */
    fun recordCustom(
        name: String,
        durationMs: Long,
    ) {
        if (!enabled) return
        getNetworkBuffer(name).add(durationMs)
    }

    // ── 报告 ────────────────────────────────────────────────────

    /** 生成当前性能报告文本。 */
    fun report(): String {
        val sb = StringBuilder()
        sb.appendLine("═══ 性能监控报告 ═══")

        sb.appendLine("▸ 冷启动")
        dumpStats(sb, startupBuffer, "ms")

        sb.appendLine("▸ 网络请求（总计 ${networkBuffers.size} 个路径）")
        for ((path, buf) in networkBuffers.entries.sortedBy { it.key }) {
            if (buf.size > 0) {
                sb.appendLine("  $path: ${statsString(buf, "ms")}")
            }
        }
        sb.appendLine("  慢请求(>${slowRequestThresholdMs}ms): ${slowNetworkCount.get()}")

        sb.appendLine("▸ 帧渲染")
        dumpStats(sb, frameBuffer, "ms")
        sb.appendLine("  慢帧(>${slowFrameThresholdMs}ms): ${slowFrameCount.get()}")

        sb.appendLine("▸ 内存使用")
        dumpStats(sb, memoryBuffer, "MB")

        return sb.toString()
    }

    /** 清空所有指标数据和计数器。 */
    fun reset() {
        startupBuffer.clear()
        networkBuffers.clear()
        frameBuffer.clear()
        memoryBuffer.clear()
        slowNetworkCount.set(0)
        slowFrameCount.set(0)
    }

    // ── 内部实现 ────────────────────────────────────────────────

    private fun getNetworkBuffer(path: String): LongBuffer = networkBuffers.getOrPut(path) { LongBuffer(BUFFER_CAPACITY) }

    private fun dumpMetrics() {
        if (!enabled) return
        if (startupBuffer.size == 0 &&
            networkBuffers.values.none { it.size > 0 } &&
            frameBuffer.size == 0
        ) {
            return // 无数据则跳过
        }
        println("$TAG\n${report()}")
        // 汇总后重置计数器（保留缓冲区最近数据供 report() 查询）
        slowNetworkCount.set(0)
        slowFrameCount.set(0)
    }

    private fun dumpStats(
        sb: StringBuilder,
        buf: LongBuffer,
        unit: String,
    ) {
        if (buf.size == 0) {
            sb.appendLine("  (无数据)")
            return
        }
        sb.appendLine("  ${statsString(buf, unit)}")
    }

    private fun statsString(
        buf: LongBuffer,
        unit: String,
    ): String {
        val snapshot = buf.snapshot()
        val avg = snapshot.average().toLong()
        val p50 = percentile(snapshot, 0.50)
        val p95 = percentile(snapshot, 0.95)
        val max = snapshot.max()
        return "avg=${avg}$unit  p50=${p50}$unit  p95=${p95}$unit  max=${max}$unit  n=${snapshot.size}"
    }

    private fun percentile(
        sorted: LongArray,
        p: Double,
    ): Long {
        if (sorted.isEmpty()) return 0
        val idx = (p * (sorted.size - 1)).toInt().coerceIn(0, sorted.size - 1)
        return sorted[idx]
    }

    /**
     * 固定容量环形缓冲区，无锁写入（单写者前提），读取返回快照。
     */
    internal class LongBuffer(
        private val capacity: Int,
    ) {
        private val buf = LongArray(capacity)
        private var head = 0
        private var count = 0

        val size: Int get() = count

        fun add(value: Long) {
            buf[head] = value
            head = (head + 1) % capacity
            if (count < capacity) count++
        }

        fun clear() {
            head = 0
            count = 0
        }

        /** 返回当前数据的有序快照。 */
        fun snapshot(): LongArray =
            if (count == 0) {
                LongArray(0)
            } else {
                buf.copyOf(count).also { it.sort() }
            }
    }
}

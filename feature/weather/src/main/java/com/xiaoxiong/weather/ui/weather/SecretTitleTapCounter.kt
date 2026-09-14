package com.xiaoxiong.weather.ui.weather

/**
 * 标题连点彩蛋计数器：窗口内累计点击次数，达到阈值返回 true 并清零。
 *
 * 时钟由调用方以单调时基（`SystemClock.elapsedRealtime()`）注入，
 * 不使用 `uptimeMillis()`，避免设备深睡后计时窗口被拉长；纯逻辑可在 JVM 单测。
 */
class SecretTitleTapCounter(
    private val requiredTaps: Int = REQUIRED_TAPS,
    private val windowMs: Long = WINDOW_MS,
) {
    private var count = 0
    private var lastTapAtMs = 0L

    /** @return true 表示本次点击达到阈值（应触发彩蛋），计数随即清零。 */
    fun onTap(nowMs: Long): Boolean {
        // 窗口外再点 = 重新开一轮；首次点击 count 为 0，无需依赖 lastTapAtMs 初值
        if (count > 0 && nowMs - lastTapAtMs > windowMs) count = 0
        lastTapAtMs = nowMs
        count++
        if (count >= requiredTaps) {
            count = 0
            return true
        }
        return false
    }

    companion object {
        /** 连点标题打开游戏枢纽所需的连续点击次数。 */
        const val REQUIRED_TAPS = 4

        /** 连点标题的计时窗口（毫秒）：窗口内点击计数，超时清零。 */
        const val WINDOW_MS = 2_000L
    }
}

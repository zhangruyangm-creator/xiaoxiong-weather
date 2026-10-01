package com.xiaoxiong.weather.ui.game

/**
 * 接住音效软门控：同帧多件 / 高频连击时避免 CATCH 糊成一片。
 *
 * 纯函数 [catchCueAllowed] 便于 JVM 单测；[CatchCueGate] 只是有状态外壳。
 * CATCH cue 最小间隔（毫秒）：软节流，保留「叮」感又不刷屏。
 */
internal const val CATCH_CUE_MIN_INTERVAL_MS = 55L

/** `nowMs - lastPlayedMs >= minIntervalMs` 时允许播放；[lastPlayedMs] 用 [Long.MIN_VALUE] 表示从未播放。 */
internal fun catchCueAllowed(
    nowMs: Long,
    lastPlayedMs: Long,
    minIntervalMs: Long = CATCH_CUE_MIN_INTERVAL_MS,
): Boolean {
    if (minIntervalMs <= 0L) return true
    if (lastPlayedMs == Long.MIN_VALUE) return true
    return nowMs - lastPlayedMs >= minIntervalMs
}

/** 接住 cue 按墙钟软节流。 */
internal class CatchCueGate {
    private var lastCatchMs = Long.MIN_VALUE

    /** 本帧 CATCH 是否允许播放。 */
    fun allowCatch(nowMs: Long): Boolean {
        if (!catchCueAllowed(nowMs, lastCatchMs)) return false
        lastCatchMs = nowMs
        return true
    }

    /** 开新局复位，避免上一局末声吞掉新局首声。 */
    fun reset() {
        lastCatchMs = Long.MIN_VALUE
    }
}

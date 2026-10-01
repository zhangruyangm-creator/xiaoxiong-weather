package com.xiaoxiong.weather.ui.game

/*
 * 小熊战机音效节流。
 *
 * 射击 cue 按「逻辑开火轮次」放行：高分屏 / 补步下相邻 shot 事件的墙钟间隔可能小于
 * 一发的逻辑间隔（L4 激光 7 tick），墙钟节流会吞掉音效；按轮次则每发必响，
 * 普通武器轮次间隔更长（9~11 tick），自然不会变噪。
 *
 * 爆炸 cue 仍按墙钟节流，避免成簇击毁时声音糊在一起；道具 / 受击 / 炸弹 / 预警 /
 * 胜负由 [PlaneGame.consumeEvents] 的布尔去重保证单次触发，无需再限流。
 *
 * 纯函数 [planeCueAllowed] / [planeShotCueAllowed] 便于 JVM 单测；[PlaneCueGate] 只是有状态外壳。
 */

/** 爆炸类 cue 最小间隔（毫秒）：成簇击毁时避免声音糊在一起。 */
internal const val PLANE_EXPLOSION_CUE_MIN_INTERVAL_MS = 80L

/** 擦弹 cue 最小间隔（毫秒）：擦弹高频，短门控保留「叮」感又不刷屏。 */
internal const val PLANE_GRAZE_CUE_MIN_INTERVAL_MS = 55L

/** `nowMs - lastPlayedMs >= minIntervalMs` 时允许播放；[lastPlayedMs] 用 [Long.MIN_VALUE] 表示「从未播放」。 */
internal fun planeCueAllowed(
    nowMs: Long,
    lastPlayedMs: Long,
    minIntervalMs: Long,
): Boolean {
    if (minIntervalMs <= 0L) return true
    if (lastPlayedMs == Long.MIN_VALUE) return true
    return nowMs - lastPlayedMs >= minIntervalMs
}

/**
 * 射击 cue：与上次已播放的开火轮次不同即放行（同一轮不重复）。
 * 不读墙钟，因此 L4 激光在 120Hz / 补步下每发都出声。
 */
internal fun planeShotCueAllowed(
    shotCount: Long,
    lastPlayedShotCount: Long,
): Boolean = shotCount != lastPlayedShotCount

/** 射击 cue 按逻辑开火轮次节流；爆炸 cue 按墙钟节流，两者互不影响。 */
internal class PlaneCueGate {
    private var lastShotCount = Long.MIN_VALUE
    private var lastExplosionMs = Long.MIN_VALUE
    private var lastGrazeMs = Long.MIN_VALUE

    /** 本帧 shot 事件是否允许播放；[shotCount] 取 [PlaneGame.shotCount]（累计开火轮次）。 */
    fun allowShot(shotCount: Long): Boolean {
        if (!planeShotCueAllowed(shotCount, lastShotCount)) return false
        lastShotCount = shotCount
        return true
    }

    /** 本帧 explosion 事件是否允许播放。 */
    fun allowExplosion(nowMs: Long): Boolean {
        if (!planeCueAllowed(nowMs, lastExplosionMs, PLANE_EXPLOSION_CUE_MIN_INTERVAL_MS)) return false
        lastExplosionMs = nowMs
        return true
    }

    /** 本帧 graze 事件是否允许播放。 */
    fun allowGraze(nowMs: Long): Boolean {
        if (!planeCueAllowed(nowMs, lastGrazeMs, PLANE_GRAZE_CUE_MIN_INTERVAL_MS)) return false
        lastGrazeMs = nowMs
        return true
    }

    /** 开新局可复位计时，避免上一局的最后一声把新局首声吞掉。 */
    fun reset() {
        lastShotCount = Long.MIN_VALUE
        lastExplosionMs = Long.MIN_VALUE
        lastGrazeMs = Long.MIN_VALUE
    }
}

/** 武器等级 ≥4（单管 / 双管穿透激光）使用独立音色。 */
internal fun planeShotCue(weaponLevel: Int): GameCue = if (weaponLevel >= 4) GameCue.LASER else GameCue.SHOT

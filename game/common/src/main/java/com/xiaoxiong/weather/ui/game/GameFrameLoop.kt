package com.xiaoxiong.weather.ui.game

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.withFrameNanos
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlin.math.min

/** 帧循环节流策略：是否每帧都推进，还是按 5 帧节流（约 12fps）。 */
enum class FrameThrottle {
    /** 每帧都回调（60fps），用于对局/粒子等需要满速的场景。 */
    FULL,

    /** 每 5 帧回调一次（约 12fps），用于标题 / 结算等非对局界面，省电并减少重组。 */
    THROTTLED,
}

/**
 * 由相邻帧时间戳计算 delta（秒），带首帧兜底与掉帧钳制：
 *  - 首帧（[lastNanos] == [LONG_MISSING]）返回 1/60s，避免瞬移；
 *  - 掉帧时钳制到 [maxDtSeconds]，世界走慢动作而非穿模。
 */
fun computeFrameDt(
    lastNanos: Long,
    nowNanos: Long,
    maxDtSeconds: Float = 0.033f,
): Float =
    if (lastNanos == Long.MIN_VALUE) {
        1f / 60f
    } else {
        min((nowNanos - lastNanos) / 1e9f, maxDtSeconds)
    }

/**
 * 生命周期感知的游戏帧循环。
 *
 * 各游戏（跳跳 / 摘星 / 迷宫 / 接雨雪 / 农场）在 [withFrameNanos] 里重复写着
 * 「切后台不推进 → dt 钳制 → 掉帧慢动作」三段逻辑。这里统一收口：
 *
 *  - 后台（低于 [Lifecycle.State.STARTED]）不推进、重置上一帧时间并降频轮询；
 *  - dt 钳制到 [maxDtSeconds]，掉帧时世界走慢动作而非瞬移（见 [computeFrameDt]）；
 *  - [throttle] 为 [FrameThrottle.FULL] 时每帧回调，否则每 5 帧回调一次（约 12fps）。
 *
 * 循环在 [LaunchedEffect] 内运行，随组合取消而自动停止。
 * [key] 变化时循环重启（重置 dt 与节流计数），如天气变化重新选派场景。
 *
 * @param onTick 每帧（或节流后的每帧）回调。入参为钳制后的 dt（秒）与帧时间戳（ns）。
 */
@Composable
fun GameFrameLoop(
    key: Any? = Unit,
    throttle: FrameThrottle = FrameThrottle.FULL,
    maxDtSeconds: Float = 0.033f,
    paused: () -> Boolean = { false },
    onTick: (dt: Float, nowNanos: Long) -> Unit,
) {
    val lifecycleOwner = LocalLifecycleOwner.current
    // 用 rememberUpdatedState 捕获最新参数/回调，避免循环启动后重组换新值/新 lambda 时读到过期闭包
    val currentPaused = rememberUpdatedState(paused)
    val currentOnTick = rememberUpdatedState(onTick)
    val currentThrottle = rememberUpdatedState(throttle)
    val currentMaxDt = rememberUpdatedState(maxDtSeconds)

    LaunchedEffect(key) {
        // repeatOnLifecycle：低于 STARTED 时**挂起**而不是每 300ms 轮询一次。
        // 轮询虽不推进物理，却仍持续唤醒 CPU 耗电；锁屏/切后台时这正是主要来源。
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            var lastNanos = Long.MIN_VALUE
            var slowTick = 0
            while (isActive) {
                // 弹窗/选关等暂停态：不注册 vsync，低频轮询等待恢复，避免空转唤醒主线程
                if (currentPaused.value()) {
                    lastNanos = Long.MIN_VALUE
                    slowTick = 0
                    delay(PAUSED_POLL_MS)
                    continue
                }
                withFrameNanos { now ->
                    val dt = computeFrameDt(lastNanos, now, currentMaxDt.value)
                    lastNanos = now
                    if (currentPaused.value()) return@withFrameNanos
                    if (currentThrottle.value == FrameThrottle.FULL || slowTick % 5 == 0) {
                        currentOnTick.value(dt, now)
                    }
                    slowTick++
                }
            }
        }
    }
}

/** 暂停态轮询间隔：弹窗/选关停留期间避免每 vsync 唤醒。 */
private const val PAUSED_POLL_MS = 100L

/**
 * 独立于帧循环的定时协程（如农场自动存档）。
 *
 * 农场需要「渲染帧循环」与「自动存档」解耦：存档按固定间隔、不依赖帧率，
 * 避免低帧率时存档滞后、高帧率时频繁写盘。定时器独立运行，不阻塞渲染。
 * 回调内部已做异常兜底，定时器不会因回调抛异常而中断。
 *
 *  - [intervalMs]/[onInterval] 用 rememberUpdatedState 捕获最新值，重组换新参数/回调不会读过期闭包；
 *  - 低于 [Lifecycle.State.STARTED]（切后台）不再注册定时、只低频轮询，避免后台仍定时写盘；
 *  - 后台恢复后立即补一次回调（幂等调用方安全），不用等满一个间隔才有下一次。
 */
@Composable
fun GameRepeatingInterval(
    intervalMs: Long,
    onInterval: () -> Unit,
) {
    val lifecycleOwner = LocalLifecycleOwner.current
    val currentIntervalMs = rememberUpdatedState(intervalMs)
    val currentOnInterval = rememberUpdatedState(onInterval)

    LaunchedEffect(lifecycleOwner) {
        // 同 GameFrameLoop：低于 STARTED 时挂起而非轮询。挂起还会自动取消内部的
        // delay，因此「切后台时正好在等下一轮」也不会留下残余唤醒。
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            // 进入 STARTED（含后台恢复）：立即补一次，农场 save 幂等，省去等满一个间隔
            runCatching { currentOnInterval.value() }
            while (isActive) {
                delay(intervalDelayMs(currentIntervalMs.value))
                runCatching { currentOnInterval.value() }
            }
        }
    }
}

/** 定时器间隔下限：非正数会让 `delay(0)` 忙循环。 */
internal fun intervalDelayMs(intervalMs: Long): Long = intervalMs.coerceAtLeast(1L)

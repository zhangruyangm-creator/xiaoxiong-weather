package com.xiaoxiong.weather.ui.game

import android.content.SharedPreferences
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner

/**
 * 统一的对局快照会话：把各游戏「保存 / 恢复 / 清档」三个动作收口成同一接口，
 * 供 [GameSnapshotLifecycle] 驱动。
 *
 * 各游戏底层方法命名与职责可能不同（如跑酷/摘星/接雨雪/冰球/台球/战机为
 * `saveSnapshot`/`restoreSnapshot`/`clearSnapshot`，迷宫为 `saveSession`/
 * `restoreSession`/`abandonSession`），用 [gameSnapshotSession] 适配即可：
 *
 * ```kotlin
 * val session =
 *     remember(game, prefs) {
 *         gameSnapshotSession(
 *             save = game::saveSnapshot,
 *             restore = game::restoreSnapshot,
 *             clear = game::clearSnapshot,
 *         )
 *     }
 * ```
 *
 * 约定：
 *  - [saveSnapshot] 只应在「可续玩」的对局态真正写入，READY / 结算 / 放弃时不应复活陈旧档；
 *  - [restoreSnapshot] 无档 / 坏档返回 false，且不得抛异常；
 *  - [clearSnapshot] 幂等，重复调用安全。
 */
interface GameSnapshotSession {
    fun saveSnapshot(prefs: SharedPreferences)

    fun restoreSnapshot(prefs: SharedPreferences): Boolean

    fun clearSnapshot(prefs: SharedPreferences)
}

/** 用三个 lambda 把任意命名的方法适配成 [GameSnapshotSession]（方法签名不同时的包装入口）。 */
fun gameSnapshotSession(
    save: (SharedPreferences) -> Unit,
    restore: (SharedPreferences) -> Boolean,
    clear: (SharedPreferences) -> Unit,
): GameSnapshotSession =
    object : GameSnapshotSession {
        override fun saveSnapshot(prefs: SharedPreferences) = save(prefs)

        override fun restoreSnapshot(prefs: SharedPreferences): Boolean = restore(prefs)

        override fun clearSnapshot(prefs: SharedPreferences) = clear(prefs)
    }

/**
 * 退出抑制守卫：各游戏历史上分别用 `snapshotDiscarded` / `exiting` / `snapshotSuppressed`
 * 表达「主动退出后 onDispose / ON_STOP 的兜底落盘不得把已清理的对局写回复活」，这里统一。
 *
 * 时序约定：主动退出时先 [suppress]（或 [exit]）再导航离开；[suppressed] 置位后
 * [GameSnapshotLifecycle] 的 ON_STOP / onDispose 落盘一律跳过。
 * 重开新局如需恢复落盘，调用 [reset]（现有游戏在 `start()`/`restoreSnapshot` 时会复位各自的标记）。
 */
class ExitGuard {
    /** 是否已抑制兜底落盘。 */
    var suppressed: Boolean = false
        private set

    /** 抑制后续兜底落盘（幂等）。 */
    fun suppress() {
        suppressed = true
    }

    /** 解除抑制（重开新局等场景）。 */
    fun reset() {
        suppressed = false
    }

    /** 主动退出：先清档再抑制兜底落盘，避免清掉的档被 ON_STOP / onDispose 写回复活。 */
    fun exit(
        session: GameSnapshotSession,
        prefs: SharedPreferences,
    ) {
        suppress()
        session.clearSnapshot(prefs)
    }
}

/** 落盘时机判定结果（纯逻辑，便于 JVM 单测）。 */
internal enum class SnapshotPersistAction {
    SAVE,
    CLEAR,
    SKIP,
}

/**
 * 决定 ON_STOP / onDispose 时应执行的动作：
 *  - 已退出抑制 → [SnapshotPersistAction.SKIP]；
 *  - 当前阶段不可续玩（[shouldClear] 为 true，如结算 / 放弃）→ [SnapshotPersistAction.CLEAR]；
 *  - 其余 → [SnapshotPersistAction.SAVE]。
 */
internal fun <P> snapshotPersistAction(
    suppressed: Boolean,
    phase: P,
    shouldClear: (P) -> Boolean,
): SnapshotPersistAction =
    when {
        suppressed -> SnapshotPersistAction.SKIP
        shouldClear(phase) -> SnapshotPersistAction.CLEAR
        else -> SnapshotPersistAction.SAVE
    }

/**
 * [GameSnapshotLifecycle] 的纯逻辑内核：不依赖 Compose / Lifecycle，可直接 JVM 单测。
 *
 * 时序：进入恢复（[restoreOnEnter]）→ 阶段变化清档（[onPhaseChanged]）→
 * ON_STOP 落盘（[persist]）→ onDispose 兜底（[persist]）→ 主动退出（[exit]）。
 */
internal class GameSnapshotController(
    private val session: GameSnapshotSession,
    private val prefs: SharedPreferences,
) {
    val exitGuard = ExitGuard()

    /** 进入对局时恢复一次；仅在恢复成功时回调 [onRestored]。 */
    fun restoreOnEnter(onRestored: () -> Unit = {}): Boolean {
        val restored = session.restoreSnapshot(prefs)
        if (restored) onRestored()
        return restored
    }

    /** 阶段进入不可续玩的终态时立即清档，避免停留在结算界面被系统回收后续上已结束的对局。 */
    fun <P> onPhaseChanged(
        phase: P,
        shouldClear: (P) -> Boolean,
    ) {
        if (shouldClear(phase)) session.clearSnapshot(prefs)
    }

    /** ON_STOP / onDispose 的落盘决策入口（先判退出抑制，再判是否该清档）。 */
    fun <P> persist(
        phase: P,
        shouldClear: (P) -> Boolean,
    ) {
        when (snapshotPersistAction(exitGuard.suppressed, phase, shouldClear)) {
            SnapshotPersistAction.SAVE -> session.saveSnapshot(prefs)
            SnapshotPersistAction.CLEAR -> session.clearSnapshot(prefs)
            SnapshotPersistAction.SKIP -> Unit
        }
    }

    /** 主动退出：清档 + 抑制兜底落盘（等价于各游戏 `exitGame`）。 */
    fun exit() {
        exitGuard.exit(session, prefs)
    }
}

/**
 * 各游戏统一的快照生命周期组件，按固定时序驱动 [session]：
 *
 *  1. **进入恢复**：[LaunchedEffect] 内调用 `restoreSnapshot`（[restoreOnEnter] 为 true 时），
 *     成功才回调 [onRestored]（用于恢复后的 UI 对齐，如战机拉起暂停菜单、台球同步规则暂存）；
 *  2. **阶段清档**：[phase] 变化时若 [shouldClear] 返回 true（如 `OVER` / `MATCH_OVER`），
 *     立即 `clearSnapshot`，进程被杀也不会续上已结束的对局；
 *  3. **ON_STOP 落盘**：[LifecycleEventObserver] 监听 `ON_STOP`（按 Home 等切后台）落盘，
 *     避免进程被系统回收丢档；
 *  4. **onDispose 兜底**：组合销毁（切屏 / 旋转重建）时再落盘一次；
 *  5. **退出抑制**：返回 [ExitGuard]，主动退出时 `guard.exit(session, prefs)` 先清档再抑制，
 *     之后的 ON_STOP / onDispose 兜底一律跳过（等价 `snapshotDiscarded`/`exiting`/`snapshotSuppressed`）。
 *
 * 落盘时的动作由 [snapshotPersistAction] 决定：抑制 → 跳过；[shouldClear] → 清档；否则保存。
 *
 * 注意：本轮仅提供 API，不改动各游戏现有行为；迁移时用 [gameSnapshotSession] 适配后替换
 * 各 Screen 里手写的 `DisposableEffect(LocalLifecycleOwner)` 块。
 */
@Composable
fun <P> GameSnapshotLifecycle(
    session: GameSnapshotSession,
    prefs: SharedPreferences,
    phase: P,
    shouldClear: (P) -> Boolean,
    restoreOnEnter: Boolean = true,
    onRestored: () -> Unit = {},
): ExitGuard {
    val controller = remember(session, prefs) { GameSnapshotController(session, prefs) }
    val currentOnRestored = rememberUpdatedState(onRestored)
    val currentShouldClear = rememberUpdatedState(shouldClear)
    val currentPhase = rememberUpdatedState(phase)

    // 1) 进入恢复：只跑一次；恢复失败保持初始界面（对齐各游戏 LaunchedEffect(Unit) 的写法）
    LaunchedEffect(controller, restoreOnEnter) {
        if (restoreOnEnter) {
            controller.restoreOnEnter { currentOnRestored.value() }
        }
    }
    // 2) 阶段变化：进入不可续玩终态时立即清档
    LaunchedEffect(controller, phase) {
        controller.onPhaseChanged(phase, currentShouldClear.value)
    }
    // 3) ON_STOP 落盘 + 4) onDispose 兜底；闭包经 rememberUpdatedState 读取最新 phase/shouldClear
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, controller) {
        val observer =
            LifecycleEventObserver { _, event ->
                if (event == Lifecycle.Event.ON_STOP) {
                    controller.persist(currentPhase.value, currentShouldClear.value)
                }
            }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            controller.persist(currentPhase.value, currentShouldClear.value)
        }
    }
    return controller.exitGuard
}

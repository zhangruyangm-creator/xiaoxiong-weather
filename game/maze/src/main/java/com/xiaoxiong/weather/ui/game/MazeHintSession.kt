package com.xiaoxiong.weather.ui.game

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * 提示求解会话状态机：只依赖 Compose 状态承载 [busy]，玩法逻辑与组合无关，
 * 可在 JVM 单测中直接驱动。
 *
 *  - [begin]：开启一次后台 BFS，占用 [busy] 并返回代次 token；
 *  - [complete] / [finish]：仅当 token 仍等于当前代次时生效，
 *    切关/重开后的旧结果会因代次不符被丢弃；
 *  - [cancel]：让在途任务过期并立即复位 [busy] 与缓存，
 *    用于切换关卡/重开，避免按钮一直禁用到旧 BFS 结束；
 *  - [cachedFor]：同一局面（棋盘/位置/钥匙一致）复用上次结果，
 *    与 UI「同一局面不重复跑 BFS、不重复计数」的语义一致。
 *
 * 线程约束：所有方法都在主线程调用；BFS 只在后台线程做纯计算。
 * [busy] 用 Compose 状态承载，HUD 按钮据此实时禁用/恢复。
 */
internal class MazeHintSession {
    /** 是否有在途的提示求解。 */
    var busy by mutableStateOf(false)
        private set

    private var generation = 0
    private var cachedRequest: MazeHintRequest? = null
    private var cachedHint: MazeHint? = null

    /** 同一局面已有结果时直接返回缓存，不发起新的求解。 */
    fun cachedFor(request: MazeHintRequest): MazeHint? = if (cachedRequest == request) cachedHint else null

    /** 开始一次求解：进入 [busy] 并返回本次代次 token。 */
    fun begin(request: MazeHintRequest): Int {
        generation++
        cachedRequest = request
        cachedHint = null
        busy = true
        return generation
    }

    /** 回写后台求解结果：仅当前代次采纳，返回是否可用于展示。 */
    fun complete(
        token: Int,
        hint: MazeHint,
    ): Boolean {
        if (token != generation) return false
        cachedHint = hint
        busy = false
        return true
    }

    /** 任务结束但结果不可用（过期/异常）：仅当前代次复位 [busy]。 */
    fun finish(token: Int) {
        if (token == generation) busy = false
    }

    /** 切关/重开/退出：让在途任务过期，并立即复位 [busy] 与缓存。 */
    fun cancel() {
        generation++
        cachedRequest = null
        cachedHint = null
        busy = false
    }
}

package com.xiaoxiong.weather.ui.weather

import com.xiaoxiong.weather.data.WeatherRefreshPolicy
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * 前台整点对齐静默刷新；退后台停止，避免与 WorkManager 叠跑。
 */
class ForegroundAutoRefresh(
    private val scope: CoroutineScope,
    private val hasCity: () -> Boolean,
    private val onQuietRefresh: () -> Unit
) {
    private var job: Job? = null
    var isInForeground: Boolean = false
        private set

    /** @return true 表示本次进入前台（刚从后台切回或首次） */
    fun enterForeground(): Boolean {
        val became = !isInForeground
        isInForeground = true
        if (became) startLoop()
        return became
    }

    fun leaveForeground() {
        isInForeground = false
        job?.cancel()
        job = null
    }

    private fun startLoop() {
        job?.cancel()
        if (!isInForeground) return
        job = scope.launch {
            while (isActive && isInForeground) {
                delay(WeatherRefreshPolicy.millisUntilNextQuarterHour())
                if (!isInForeground) break
                if (hasCity()) onQuietRefresh()
            }
        }
    }
}

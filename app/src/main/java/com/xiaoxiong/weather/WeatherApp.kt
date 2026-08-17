package com.xiaoxiong.weather

import android.app.Application
import android.os.SystemClock
import com.xiaoxiong.weather.data.ChinaCityCatalog
import com.xiaoxiong.weather.di.AppContainer
import com.xiaoxiong.weather.util.CrashReporting
import com.xiaoxiong.weather.util.PerformanceMonitor
import com.xiaoxiong.weather.worker.WeatherRefreshWorker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean

class WeatherApp : Application() {
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val postConsentInitialized = AtomicBoolean(false)

    /** 进程启动时刻（elapsedRealtime），用于计算冷启动耗时 */
    private val processStartMs = SystemClock.elapsedRealtime()

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()

        // 性能监控：Debug 构建启用，Release 包零开销
        PerformanceMonitor.enabled = BuildConfig.DEBUG
        startMemoryTracking()

        container = AppContainer(this)

        // 记录冷启动耗时（Application.onCreate 完成时）
        val duration = SystemClock.elapsedRealtime() - processStartMs
        PerformanceMonitor.recordStartup(duration)

        // 隐私合规：同意前只做无副作用的准备，崩溃上报/后台任务等同意后再初始化
        appScope.launch {
            if (container.settingsStore.getOnce().privacyAccepted) {
                initializeAfterConsent()
            }
        }
    }

    /** 每 30 秒采样一次内存使用，仅 Debug 构建生效。 */
    private fun startMemoryTracking() {
        if (!PerformanceMonitor.enabled) return
        Thread({
            while (!Thread.currentThread().isInterrupted) {
                try {
                    Thread.sleep(30_000)
                    PerformanceMonitor.recordMemory()
                } catch (_: InterruptedException) {
                    Thread.currentThread().interrupt()
                    break
                }
            }
        }, "PerfMonitor-memory").apply {
            isDaemon = true
            start()
        }
    }

    /** 隐私同意后触发（老用户冷启动走 onCreate 分支）；进程内幂等。 */
    fun initializeAfterConsent() {
        if (!postConsentInitialized.compareAndSet(false, true)) return
        CrashReporting.init(this)
        appScope.launch {
            ChinaCityCatalog.init(this@WeatherApp)
            val hours =
                container.settingsStore
                    .getOnce()
                    .backgroundRefreshInterval.hours
            WeatherRefreshWorker.enqueue(this@WeatherApp, intervalHours = hours)
        }
    }
}

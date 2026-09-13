package com.xiaoxiong.weather

import android.app.Application
import android.os.SystemClock
import com.xiaoxiong.weather.data.ChinaCityCatalog
import com.xiaoxiong.weather.data.WeatherRefreshPolicy
import com.xiaoxiong.weather.di.AppContainer
import com.xiaoxiong.weather.util.CrashReporting
import com.xiaoxiong.weather.util.PerformanceMonitor
import com.xiaoxiong.weather.util.WeatherLauncherIcon
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
        PerformanceMonitor.startLoggingIfEnabled()
        startMemoryTracking()

        container = AppContainer(this)

        // 启动后台准备：PackageManager 对账 + ServiceLoader 扫描都不该占用首帧主线程
        appScope.launch {
            runCatching { WeatherLauncherIcon.reconcile(this@WeatherApp) }
            runCatching { container.preloadGameEntries() }
        }

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

    /**
     * Activity 退后台时调用：排队中的桌面图标切换放到 appScope 执行。
     * `flushPending` 内含 PackageManager IPC 与 prefs 写盘，不在主线程做。
     */
    fun flushLauncherIconPending() {
        appScope.launch {
            runCatching { WeatherLauncherIcon.flushPending(this@WeatherApp) }
        }
    }

    /** 隐私同意后触发（老用户冷启动走 onCreate 分支）；进程内幂等。 */
    fun initializeAfterConsent() {
        if (!postConsentInitialized.compareAndSet(false, true)) return
        CrashReporting.init(this)
        appScope.launch {
            // 各步骤独立容错：目录/设置初始化失败不能拖累周期任务入队
            runCatching { ChinaCityCatalog.init(this@WeatherApp) }
            val settings = runCatching { container.settingsStore.getOnce() }.getOrNull()
            val hours =
                settings?.backgroundRefreshInterval?.hours
                    ?: WeatherRefreshPolicy.BACKGROUND_HOURS
            runCatching { WeatherRefreshWorker.enqueue(this@WeatherApp, intervalHours = hours) }
        }
    }
}

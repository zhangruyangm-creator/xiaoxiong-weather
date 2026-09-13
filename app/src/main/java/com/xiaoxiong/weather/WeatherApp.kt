package com.xiaoxiong.weather

import android.app.Application
import android.os.SystemClock
import com.xiaoxiong.weather.data.ChinaCityCatalog
import com.xiaoxiong.weather.data.WeatherRefreshPolicy
import com.xiaoxiong.weather.di.AppContainer
import com.xiaoxiong.weather.util.CrashReporting
import com.xiaoxiong.weather.util.PerformanceMonitor
import com.xiaoxiong.weather.util.WeatherLauncherIcon
import com.xiaoxiong.weather.util.runCatchingCancellable
import com.xiaoxiong.weather.widget.TemperatureUnitChangeDetector
import com.xiaoxiong.weather.widget.WeatherWidgetUpdater
import com.xiaoxiong.weather.worker.WeatherRefreshWorker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean

class WeatherApp : Application() {
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val postConsentInitialized = AtomicBoolean(false)

    /** 温度单位变化触发的桌面小组件合并刷新任务。 */
    private var widgetUnitRefreshJob: Job? = null

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

        // 桌面图标：前台只短锁排队 pending，绝不做 PackageManager 切换
        // （前台切 alias 会在部分机型杀进程）；退后台由 MainActivity.onStop 立即应用，
        // 后台由 Worker 应用，冷启动由 reconcile 对账兜底。

        // 启动后台准备：游戏入口预载与 PackageManager 对账并行，互不等待
        // （游戏入口优先可读，冷启动早期输口令/连点标题不会因对账耗时无响应）
        appScope.launch {
            runCatching { container.preloadGameEntries() }
                .onFailure { CrashReporting.record(it) }
        }
        appScope.launch {
            runCatching { WeatherLauncherIcon.reconcile(this@WeatherApp) }
                .onFailure { CrashReporting.record(it) }
        }

        // 温度单位切换后：重绘桌面小组件，并触发一次静默天气刷新
        // （预警文案等派生字符串按旧单位生成，需重新拉取；静默保留界面数据，
        // 刷新节流由 WeatherViewModel.refresh(quiet=true) 自身的去重窗口兜底）。
        // 设置流首次发射是初值，跳过；短时间连续切换由 scheduleWidgetRefreshForUnitChange 合并
        appScope.launch {
            val unitChanges = TemperatureUnitChangeDetector()
            container.settingsStore.settings.collect { settings ->
                if (unitChanges.onUnit(settings.temperatureUnit)) {
                    scheduleWidgetRefreshForUnitChange()
                    container.requestQuietWeatherRefresh()
                }
            }
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
     * 温度单位变化触发的合并刷新：短时间内的连续切换取消上一个待执行任务，
     * 只对最终单位刷新一次桌面小组件。
     */
    private fun scheduleWidgetRefreshForUnitChange() {
        widgetUnitRefreshJob?.cancel()
        widgetUnitRefreshJob =
            appScope.launch {
                delay(WIDGET_UNIT_REFRESH_DEBOUNCE_MS)
                runCatchingCancellable { WeatherWidgetUpdater.updateAll(this@WeatherApp) }
                    .onFailure { CrashReporting.record(it) }
            }
    }

    /**
     * Activity 退后台时调用：立即执行排队中的桌面图标切换。
     * `flushPending` 内含 PackageManager IPC 与 prefs 写盘，不在主线程做。
     */
    fun flushLauncherIconPending() {
        appScope.launch {
            runCatching { WeatherLauncherIcon.flushPending(this@WeatherApp) }
                .onFailure { CrashReporting.record(it) }
        }
    }

    /** 隐私同意后触发（老用户冷启动走 onCreate 分支）；进程内幂等。 */
    fun initializeAfterConsent() {
        if (!postConsentInitialized.compareAndSet(false, true)) return
        appScope.launch {
            // 各步骤独立容错：崩溃上报/目录初始化失败不能拖累周期任务入队
            runCatching { CrashReporting.init(this@WeatherApp) }
            runCatching { ChinaCityCatalog.init(this@WeatherApp) }
                .onFailure { CrashReporting.record(it) }
            val settings =
                runCatching { container.settingsStore.getOnce() }
                    .onFailure { CrashReporting.record(it) }
                    .getOrNull()
            val hours =
                settings?.backgroundRefreshInterval?.hours
                    ?: WeatherRefreshPolicy.BACKGROUND_HOURS
            runCatching { WeatherRefreshWorker.enqueue(this@WeatherApp, intervalHours = hours) }
                .onFailure { CrashReporting.record(it) }
        }
    }

    private companion object {
        /** 温度单位切换的合并窗口：连续切换只刷新一次小组件。 */
        const val WIDGET_UNIT_REFRESH_DEBOUNCE_MS = 600L
    }
}

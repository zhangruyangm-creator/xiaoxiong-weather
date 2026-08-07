package com.xiaoxiong.weather

import android.app.Application
import com.xiaoxiong.weather.data.ChinaCityCatalog
import com.xiaoxiong.weather.di.AppContainer
import com.xiaoxiong.weather.util.CrashReporting
import com.xiaoxiong.weather.worker.WeatherRefreshWorker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean

class WeatherApp : Application() {
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val postConsentInitialized = AtomicBoolean(false)

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        // 隐私合规：同意前只做无副作用的准备，崩溃上报/后台任务等同意后再初始化
        appScope.launch {
            if (container.settingsStore.getOnce().privacyAccepted) {
                initializeAfterConsent()
            }
        }
    }

    /** 隐私同意后触发（老用户冷启动走 onCreate 分支）；进程内幂等。 */
    fun initializeAfterConsent() {
        if (!postConsentInitialized.compareAndSet(false, true)) return
        CrashReporting.init(this)
        appScope.launch {
            ChinaCityCatalog.init(this@WeatherApp)
            val hours = container.settingsStore.getOnce().backgroundRefreshInterval.hours
            WeatherRefreshWorker.enqueue(this@WeatherApp, intervalHours = hours)
        }
    }
}

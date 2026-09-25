package com.xiaoxiong.weather.worker

import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.xiaoxiong.weather.MainActivity
import com.xiaoxiong.weather.R
import com.xiaoxiong.weather.data.AppError
import com.xiaoxiong.weather.data.SelectedCityStore
import com.xiaoxiong.weather.data.WeatherAlertGenerator
import com.xiaoxiong.weather.data.WeatherRefreshPolicy
import com.xiaoxiong.weather.data.WeatherSnapshot
import com.xiaoxiong.weather.data.isSameSelection
import com.xiaoxiong.weather.di.AppContainer
import com.xiaoxiong.weather.di.appContainer
import com.xiaoxiong.weather.util.WeatherCodes
import com.xiaoxiong.weather.util.WeatherLauncherIcon
import com.xiaoxiong.weather.util.formatTemperature
import com.xiaoxiong.weather.util.runCatchingCancellable
import com.xiaoxiong.weather.widget.WeatherWidgetUpdater
import java.util.concurrent.TimeUnit

class WeatherRefreshWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result {
        return try {
            val container = applicationContext.appContainer()
            val store = container.selectedCityStore
            val selection = store.getOnce() ?: return Result.success()
            val locationHelper = container.locationHelper
            val cacheStore = container.weatherCacheStore

            val city =
                if (selection.useLocation && locationHelper.hasLocationPermission()) {
                    runCatchingCancellable { locationHelper.resolveCurrentCityReuseLast() }.getOrDefault(selection.city)
                } else {
                    selection.city
                }

            val cached = cacheStore.get(city, container.presentationStamp())
            val now = System.currentTimeMillis()
            if (cached != null &&
                now - cached.savedAtEpochMs < WeatherRefreshPolicy.BACKGROUND_FRESH_MS
            ) {
                // 前台刚刷过：只跳过联网/写缓存，仍刷新小组件、桌面图标并评估推送预警
                WeatherWidgetUpdater.updateAll(applicationContext)
                val current = cached.snapshot.current
                WeatherLauncherIcon.applyForWeatherCode(
                    applicationContext,
                    current.weatherCode,
                    WeatherCodes.intensityPrecipMmPerHour(current),
                    current.isDay,
                )
                notifyForSnapshot(container, selection, cached.snapshot)
                return Result.success()
            }

            // 取数发起时刻：与前台/小组件的并发刷新比较新旧，避免旧数据覆盖新数据
            val fetchStartedAt = System.currentTimeMillis()
            val snapshot = container.weatherRepository.fetchWeather(city)
            // 后台刷新结果写入缓存，前台打开时可直接用到最新数据；
            // 落盘失败不能丢掉已到手的数据，后续小组件/图标/通知继续执行。
            // runCatchingCancellable：系统取消 Worker 时不能让取消被吞成普通失败
            runCatchingCancellable {
                cacheStore.save(snapshot, container.presentationStamp(), fetchStartedAt)
            }
            if (selection.useLocation) {
                runCatchingCancellable { store.saveIfUnchanged(selection, city, useLocation = true) }
            }

            val current = snapshot.current
            val precipRate = WeatherCodes.intensityPrecipMmPerHour(current)
            // 缓存已更新：小组件始终跟着刷新（不再要求气温变化 ≥0.5°C）
            WeatherWidgetUpdater.updateAll(applicationContext)
            WeatherLauncherIcon.applyForWeatherCode(
                applicationContext,
                current.weatherCode,
                precipRate,
                current.isDay,
            )

            notifyForSnapshot(container, selection, snapshot)
            Result.success()
        } catch (e: kotlinx.coroutines.CancellationException) {
            // 系统取消时必须透传，让 Worker 正常结束而不是被当作"网络失败"重试
            throw e
        } catch (e: AppError) {
            // 4xx 属永久性错误（城市/参数不变时重试无意义）：放弃本轮，等下一个周期；
            // 408/425/429 是瞬态码，仍需退避重试；其余（网络不可用、格式错）同样交给 BackoffPolicy
            if (e is AppError.Http && e.code in 400..499 && e.code !in TRANSIENT_HTTP_CODES) {
                Result.success()
            } else {
                Result.retry()
            }
        } catch (_: Exception) {
            Result.retry()
        }
    }

    /**
     * fresh 命中与联网刷新共用的预警评估 + 推送：
     * 系统拦截通知时只做判定、不写去重记忆，保证渠道恢复后仍能补报。
     */
    private suspend fun notifyForSnapshot(
        container: AppContainer,
        fetchedSelection: SelectedCityStore.PersistedSelection,
        snapshot: WeatherSnapshot,
    ) {
        val settings = container.settingsStore.getOnce()
        if (!settings.notificationsEnabled) return
        val channelImportance =
            applicationContext
                .getSystemService(NotificationManager::class.java)
                .getNotificationChannel(CHANNEL_ID)
                ?.importance
        val systemEnabled = NotificationManagerCompat.from(applicationContext).areNotificationsEnabled()
        if (!canDisplaySystemNotifications(systemEnabled, channelImportance)) return
        // 抓取/评估期间用户可能已切城：当前选择与抓取到的城市不一致就不推
        // （定位模式会把选中城市更新为新坐标，因此以快照城市对比，避免把定位更新误判成切城）
        val fetchedCitySelection =
            SelectedCityStore.PersistedSelection(snapshot.city, fetchedSelection.useLocation)
        if (!isSameSelection(fetchedCitySelection, container.selectedCityStore.getOnce())) return

        val dedup = container.notifyDedupStore
        val city = snapshot.city
        val current = snapshot.current
        // 候选=所有 WARNING + 关键 kind 的 WATCH + 临近雨雪（沿用旧行为）；
        // 高温/寒潮/雾/沙尘/大风/UV/积水等严重预警不再被整类漏掉。
        // 通知文案按当前设置单位重算（快照里的 message 可能是旧单位）：与前台快照解析同一套规格
        val severeSpecs =
            selectSevereNotificationSpecs(
                WeatherAlertGenerator.specs(
                    snapshot.current,
                    snapshot.hourly,
                    snapshot.daily,
                    snapshot.airQuality,
                    settings.temperatureUnit,
                ),
            )
        // 判定 → 展示 → 写记忆在 store 内串行：并发 Worker 不会重复推送；
        // 全部展示失败时不写任何记忆，继续走下面的通用天气变化通知兜底
        val postedSignatures =
            dedup.notifyAlertsIfDue(
                cityName = city.name,
                weatherCode = current.weatherCode,
                temperatureC = current.temperatureC,
                signatures = severeSpecs.map { severeNotificationSignature(city.name, it) },
            ) { pendingSignatures ->
                val specBySignature = severeSpecs.associateBy { severeNotificationSignature(city.name, it) }
                pendingSignatures.filter { signature ->
                    val spec = specBySignature[signature] ?: return@filter false
                    showNotification(
                        title = "${city.name} · ${applicationContext.getString(spec.titleRes)}",
                        text = alertMessage(spec),
                        notifyId = SEVERE_NOTIFICATION_ID_BASE + spec.kind.ordinal,
                    )
                }
            }
        // 严重预警已真实展示：通用分支不再立刻重复推一条
        if (postedSignatures.isNotEmpty()) return

        dedup.notifyWeatherChangeIfDue(city.name, current.weatherCode, current.temperatureC) {
            val precipRate = WeatherCodes.intensityPrecipMmPerHour(current)
            val displayCode = WeatherCodes.displayCode(current.weatherCode, precipRate)
            val title = "${city.name} ${formatTemperature(current.temperatureC, settings.temperatureUnit)}"
            val text =
                applicationContext.getString(
                    R.string.notify_body,
                    WeatherCodes.emoji(displayCode, current.isDay != false),
                    WeatherCodes.description(
                        applicationContext,
                        displayCode,
                        precipRate,
                    ),
                    current.humidityPercent?.toString() ?: "--",
                    com.xiaoxiong.weather.util
                        .formatVisibility(current.visibilityMeters),
                )
            showNotification(title, text)
        }
    }

    private fun alertMessage(spec: WeatherAlertGenerator.Spec): String =
        when {
            spec.messageArg == null -> applicationContext.getString(spec.messageRes)
            spec.messageArg2 != null ->
                applicationContext.getString(spec.messageRes, spec.messageArg, spec.messageArg2)
            else -> applicationContext.getString(spec.messageRes, spec.messageArg)
        }

    @SuppressLint("MissingPermission")
    private fun showNotification(
        title: String,
        text: String,
        notifyId: Int = NOTIFICATION_ID,
    ): Boolean {
        ensureChannel()
        // 显式指向 MainActivity：动态图标会禁用当前 activity-alias，解析 alias 的 launch intent 会失效
        val openApp =
            Intent(applicationContext, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_CLEAR_TOP or
                    Intent.FLAG_ACTIVITY_SINGLE_TOP
            }
        val contentPending =
            PendingIntent.getActivity(
                applicationContext,
                0,
                openApp,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
        val notification =
            NotificationCompat
                .Builder(applicationContext, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_stat_weather)
                .setContentTitle(title)
                .setContentText(text)
                .setStyle(NotificationCompat.BigTextStyle().bigText(text))
                .setContentIntent(contentPending)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setSilent(true)
                .setAutoCancel(true)
                .build()

        // Android 13+ 未授权通知时静默失败/抛异常都不影响后续预警，返回是否真正提交
        return runCatching {
            NotificationManagerCompat.from(applicationContext).notify(notifyId, notification)
        }.onFailure {
            Log.w(TAG, "failed to post weather notification", it)
        }.isSuccess
    }

    private fun ensureChannel() {
        val manager = applicationContext.getSystemService(NotificationManager::class.java)
        val channel =
            NotificationChannel(
                CHANNEL_ID,
                applicationContext.getString(R.string.notify_channel_name),
                NotificationManager.IMPORTANCE_LOW,
            ).apply {
                description = applicationContext.getString(R.string.notify_channel_desc)
                setShowBadge(false)
            }
        manager.createNotificationChannel(channel)
    }

    companion object {
        private const val TAG = "WeatherRefreshWorker"

        const val UNIQUE_WORK = "weather_periodic_refresh_v3"

        /** 4xx 中属瞬态的码：408 超时、425 过早、429 限流 */
        private val TRANSIENT_HTTP_CODES = setOf(408, 425, 429)

        // v2 无网络约束，升级后 KEEP 不会追加约束，必须换名迁移
        private const val LEGACY_UNIQUE_WORK_V2 = "weather_periodic_refresh_v2"
        private const val LEGACY_UNIQUE_WORK = "weather_periodic_refresh"
        private const val CHANNEL_ID = "weather_auto_refresh"
        private const val NOTIFICATION_ID = 1001

        /** 每条严重预警一个通知槽位（kind.ordinal 偏移），避免同类多预警互相覆盖 */
        private const val SEVERE_NOTIFICATION_ID_BASE = 1100

        fun enqueue(
            context: Context,
            intervalHours: Int = WeatherRefreshPolicy.BACKGROUND_HOURS,
            policy: ExistingPeriodicWorkPolicy = ExistingPeriodicWorkPolicy.KEEP,
        ) {
            val wm = WorkManager.getInstance(context.applicationContext)
            // 停掉旧的周期任务，避免重复刷通知
            wm.cancelUniqueWork(LEGACY_UNIQUE_WORK)
            wm.cancelUniqueWork(LEGACY_UNIQUE_WORK_V2)
            val hours = intervalHours.coerceIn(1, 24).toLong()
            val constraints =
                Constraints
                    .Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .setRequiresBatteryNotLow(true)
                    .build()
            val request =
                PeriodicWorkRequestBuilder<WeatherRefreshWorker>(hours, TimeUnit.HOURS)
                    .setConstraints(constraints)
                    // 周期任务失败后线性退避 30 分钟，避免短时反复重试造成网络空转
                    .setBackoffCriteria(BackoffPolicy.LINEAR, 30, TimeUnit.MINUTES)
                    .build()
            wm.enqueueUniquePeriodicWork(UNIQUE_WORK, policy, request)
        }
    }
}

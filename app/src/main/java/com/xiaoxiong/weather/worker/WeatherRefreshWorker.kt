package com.xiaoxiong.weather.worker

import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
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
import com.xiaoxiong.weather.data.AlertKind
import com.xiaoxiong.weather.data.AppError
import com.xiaoxiong.weather.data.WeatherAlertGenerator
import com.xiaoxiong.weather.data.WeatherRefreshPolicy
import com.xiaoxiong.weather.di.appContainer
import com.xiaoxiong.weather.util.WeatherCodes
import com.xiaoxiong.weather.util.WeatherLauncherIcon
import com.xiaoxiong.weather.util.formatTemperature
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
            val dedup = container.notifyDedupStore

            val city =
                if (selection.useLocation && locationHelper.hasLocationPermission()) {
                    runCatching { locationHelper.resolveCurrentCityReuseLast() }.getOrDefault(selection.city)
                } else {
                    selection.city
                }

            val cached = cacheStore.get(city)
            val now = System.currentTimeMillis()
            if (cached != null &&
                now - cached.savedAtEpochMs < WeatherRefreshPolicy.BACKGROUND_FRESH_MS
            ) {
                // 前台刚刷过：跳过联网，仍刷新小组件与桌面图标（flush 排队中的 alias）
                WeatherWidgetUpdater.updateAll(applicationContext)
                val current = cached.snapshot.current
                WeatherLauncherIcon.applyForWeatherCode(
                    applicationContext,
                    current.weatherCode,
                    WeatherCodes.intensityPrecipMmPerHour(current),
                    current.isDay,
                )
                return Result.success()
            }

            val snapshot = container.weatherRepository.fetchWeather(city)
            // 后台刷新结果写入缓存，前台打开时可直接用到最新数据；
            // 落盘失败不能丢掉已到手的数据，后续小组件/图标/通知继续执行
            runCatching { cacheStore.save(snapshot) }
            if (selection.useLocation) {
                runCatching { store.saveIfUnchanged(selection, city, useLocation = true) }
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

            val settings = container.settingsStore.getOnce()
            if (settings.notificationsEnabled) {
                val soonSpec =
                    WeatherAlertGenerator
                        .specs(snapshot.current, snapshot.hourly, snapshot.daily, snapshot.airQuality)
                        .firstOrNull { it.kind == AlertKind.RAIN_SOON || it.kind == AlertKind.SNOW_SOON }
                when {
                    soonSpec != null &&
                        dedup.shouldNotifySoonAndRemember(
                            city.name,
                            soonSignature(city.name, soonSpec),
                        ) -> {
                        val alertTitle = applicationContext.getString(soonSpec.titleRes)
                        val alertText =
                            if (soonSpec.messageArg != null) {
                                applicationContext.getString(soonSpec.messageRes, soonSpec.messageArg)
                            } else {
                                applicationContext.getString(soonSpec.messageRes)
                            }
                        showNotification("${city.name} · $alertTitle", alertText)
                        // 同步通用去重基线：否则下一轮 soon 被去重后会因 lastCity 为空误判 firstEver 再推一条
                        dedup.shouldNotifyAndRemember(
                            city.name,
                            current.weatherCode,
                            current.temperatureC,
                        )
                    }
                    dedup.shouldNotifyAndRemember(
                        city.name,
                        current.weatherCode,
                        current.temperatureC,
                    ) -> {
                        val displayCode = WeatherCodes.displayCode(current.weatherCode, precipRate)
                        val title = "${city.name} ${formatTemperature(current.temperatureC, settings.temperatureUnit)}"
                        val text =
                            applicationContext.getString(
                                R.string.notify_body,
                                WeatherCodes.emoji(displayCode),
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
            }
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

    private fun soonSignature(
        cityName: String,
        spec: WeatherAlertGenerator.Spec,
    ): String = "$cityName|${spec.kind}|${spec.titleRes}"

    @SuppressLint("MissingPermission")
    private fun showNotification(
        title: String,
        text: String,
    ) {
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

        // Android 13+ 未授权通知时静默失败即可
        runCatching {
            NotificationManagerCompat.from(applicationContext).notify(NOTIFICATION_ID, notification)
        }
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
        const val UNIQUE_WORK = "weather_periodic_refresh_v3"

        /** 4xx 中属瞬态的码：408 超时、425 过早、429 限流 */
        private val TRANSIENT_HTTP_CODES = setOf(408, 425, 429)

        // v2 无网络约束，升级后 KEEP 不会追加约束，必须换名迁移
        private const val LEGACY_UNIQUE_WORK_V2 = "weather_periodic_refresh_v2"
        private const val LEGACY_UNIQUE_WORK = "weather_periodic_refresh"
        private const val CHANNEL_ID = "weather_auto_refresh"
        private const val NOTIFICATION_ID = 1001

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

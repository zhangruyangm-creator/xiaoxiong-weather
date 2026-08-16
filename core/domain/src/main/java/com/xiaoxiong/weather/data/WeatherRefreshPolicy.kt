package com.xiaoxiong.weather.data

import androidx.annotation.StringRes
import com.xiaoxiong.weather.R
import java.time.ZonedDateTime
import java.time.temporal.ChronoUnit

/**
 * 前台按整点四分位（:00 / :15 / :30 / :45）对齐刷新；
 * 后台 WorkManager 周期单独配置，避免 UI 文案与实际行为不一致。
 */
object WeatherRefreshPolicy {
    /** 前台对齐刷新的分钟步长（0/15/30/45） */
    const val FOREGROUND_MINUTES = 15

    /** 默认后台周期（与 [BackgroundRefreshInterval.HOURS_3] 一致） */
    const val BACKGROUND_HOURS = 3

    /** 小组件缓存新鲜度，与前台刷新步长对齐 */
    const val WIDGET_STALE_MS = FOREGROUND_MINUTES * 60_000L

    /** Worker 在此窗口内命中缓存则跳过联网（前台刚刷过时省电） */
    const val BACKGROUND_FRESH_MS = FOREGROUND_MINUTES * 60_000L

    /**
     * 距离下一个 :00 / :15 / :30 / :45 的毫秒数。
     * 若刚好落在整分位上，则等到下一档（避免刚触发后又立刻再刷）。
     */
    fun millisUntilNextQuarterHour(now: ZonedDateTime = ZonedDateTime.now()): Long {
        val truncated = now.truncatedTo(ChronoUnit.MINUTES)
        val minute = truncated.minute
        val nextSlotMinute = ((minute / FOREGROUND_MINUTES) + 1) * FOREGROUND_MINUTES
        val next =
            if (nextSlotMinute >= 60) {
                truncated.truncatedTo(ChronoUnit.HOURS).plusHours(1)
            } else {
                truncated.withMinute(nextSlotMinute).withSecond(0).withNano(0)
            }
        // 加上当前这一分钟内已过的秒/纳秒，得到真实等待时间
        val delay = ChronoUnit.MILLIS.between(now, next)
        return delay.coerceAtLeast(1_000L)
    }
}

/** 用户可选的后台刷新档位（WorkManager 仍可能被系统延迟） */
enum class BackgroundRefreshInterval(
    val hours: Int,
    @param:StringRes val labelRes: Int,
) {
    HOURS_1(1, R.string.settings_refresh_1h),
    HOURS_3(3, R.string.settings_refresh_3h),
    HOURS_6(6, R.string.settings_refresh_6h),
    ;

    companion object {
        fun fromHours(hours: Int): BackgroundRefreshInterval = entries.firstOrNull { it.hours == hours } ?: HOURS_3
    }
}

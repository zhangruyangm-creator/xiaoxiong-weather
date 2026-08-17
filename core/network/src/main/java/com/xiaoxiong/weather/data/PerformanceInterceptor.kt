package com.xiaoxiong.weather.data

import android.os.SystemClock
import com.xiaoxiong.weather.util.PerformanceMonitor
import okhttp3.Interceptor
import okhttp3.Response

/**
 * OkHttp 拦截器：自动记录每个请求的耗时到 [PerformanceMonitor]。
 *
 * 仅在 [PerformanceMonitor.enabled] 为 true 时采集，release 包零开销。
 *
 * 从请求 URL 中提取第一个路径段作为指标标签（如 "v1/forecast" → "forecast"）。
 */
class PerformanceInterceptor : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val startMs = SystemClock.elapsedRealtime()
        val request = chain.request()
        val pathLabel = extractPathLabel(request.url.encodedPath)

        val response =
            try {
                chain.proceed(request)
            } catch (e: Exception) {
                val duration = SystemClock.elapsedRealtime() - startMs
                PerformanceMonitor.recordNetwork(pathLabel, duration, success = false)
                throw e
            }

        val duration = SystemClock.elapsedRealtime() - startMs
        PerformanceMonitor.recordNetwork(pathLabel, duration, success = response.isSuccessful)
        return response
    }

    /**
     * 从路径中提取标签，例如：
     * - "/v1/forecast?..." → "forecast"
     * - "/v1/air-quality?..." → "air-quality"
     * - "/v1/search?..." → "search"
     */
    private fun extractPathLabel(encodedPath: String): String {
        val segments = encodedPath.split('/').filter { it.isNotEmpty() }
        // 取最后一个有意义的路径段（跳过 "v1" 版本号）
        return segments.lastOrNull { !it.startsWith('v') && it != "api" }
            ?: segments.lastOrNull()
            ?: "unknown"
    }
}

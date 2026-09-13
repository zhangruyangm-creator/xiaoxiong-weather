package com.xiaoxiong.weather.data

import okhttp3.Call
import okhttp3.Interceptor
import okhttp3.Response
import java.io.IOException
import kotlin.random.Random

/**
 * 对幂等 GET 做有限次重试：网络异常或 5xx / 429 / 408 / 425。
 */
class RetryInterceptor(
    private val maxRetries: Int = 2,
    private val baseDelayMs: Long = 400L,
) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val idempotent = request.method.equals("GET", ignoreCase = true)
        var lastError: IOException? = null
        var attempt = 0
        while (true) {
            try {
                val response = chain.proceed(request)
                if (!idempotent || attempt >= maxRetries || !shouldRetry(response.code)) {
                    return response
                }
                response.close()
            } catch (e: IOException) {
                lastError = e
                if (!idempotent || attempt >= maxRetries) throw e
            }
            attempt++
            try {
                // 指数退避(400ms→800ms→…)叠加 0~50% 随机抖动，避免多端同时重试形成惊群
                val delay =
                    baseDelayMs * (1L shl (attempt - 1)) +
                        Random.nextLong(0, baseDelayMs * (1L shl (attempt - 1)) / 2 + 1)
                sleepCheckingCancellation(delay, chain.call())
            } catch (_: InterruptedException) {
                Thread.currentThread().interrupt()
                throw lastError ?: IOException("retry interrupted")
            }
        }
    }

    /**
     * 分段 sleep 并检查取消：一次性 sleep 期间 [Call.cancel] 无法唤醒，
     * 最多要等完整个退避窗口（约 1.8s）；分段后取消可在 100ms 内生效。
     */
    private fun sleepCheckingCancellation(
        delayMs: Long,
        call: Call,
    ) {
        var remaining = delayMs
        while (remaining > 0) {
            if (call.isCanceled()) throw IOException("retry canceled")
            val slice = remaining.coerceAtMost(SLEEP_SLICE_MS)
            Thread.sleep(slice)
            remaining -= slice
        }
    }

    private fun shouldRetry(code: Int): Boolean = code == 429 || code == 408 || code == 425 || code in 500..599

    companion object {
        private const val SLEEP_SLICE_MS = 100L
    }
}

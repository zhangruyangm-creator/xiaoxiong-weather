package com.xiaoxiong.weather.data

import okhttp3.Interceptor
import okhttp3.Response
import java.io.IOException

/**
 * 对幂等 GET 做有限次重试：网络异常或 5xx / 429。
 */
class RetryInterceptor(
    private val maxRetries: Int = 2,
    private val baseDelayMs: Long = 400L
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
                Thread.sleep(baseDelayMs * (1L shl (attempt - 1)))
            } catch (_: InterruptedException) {
                Thread.currentThread().interrupt()
                throw lastError ?: IOException("retry interrupted")
            }
        }
    }

    private fun shouldRetry(code: Int): Boolean =
        code == 429 || code in 500..599
}

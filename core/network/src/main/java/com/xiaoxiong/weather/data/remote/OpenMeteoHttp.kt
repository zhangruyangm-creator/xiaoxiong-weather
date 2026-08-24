package com.xiaoxiong.weather.data.remote

import com.xiaoxiong.weather.data.AppError
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException

/**
 * Open-Meteo 通用 GET：统一错误映射（HTTP → AppError.Http，IO → NetworkUnavailable）。
 *
 * [executeWithFallback] 支持同一请求在多 base URL 间顺序故障转移：
 * 主 URL 因网络/超时/DNS 失败时，自动尝试后续候选 URL（用于预报主站的备用入口）。
 */
object OpenMeteoHttp {
    /** App 启动时由 [com.xiaoxiong.weather.di.AppContainer] 注入，用于 User-Agent。 */
    var versionName: String? = null

    fun execute(
        url: String,
        client: OkHttpClient,
    ): String = executeWithFallback(listOf(url), client)

    /**
     * 主 URL 请求失败时按候选顺序重试。仅对 IOException（含超时/断连）转移，
     * [AppError.Http] 等业务错误直接上抛不转移。
     */
    fun executeWithFallback(
        baseUrls: List<String>,
        client: OkHttpClient,
    ): String {
        require(baseUrls.isNotEmpty()) { "baseUrls must not be empty" }
        var lastError: IOException? = null
        for (url in baseUrls) {
            try {
                return executeOnce(url, client)
            } catch (e: AppError) {
                // 业务错误（4xx/5xx）：不在候选间转移，直接上抛
                throw e
            } catch (e: IOException) {
                lastError = e
            }
        }
        throw AppError.NetworkUnavailable(lastError)
    }

    private fun executeOnce(
        url: String,
        client: OkHttpClient,
    ): String {
        val request =
            Request
                .Builder()
                .url(url)
                .header("Accept", "application/json")
                .header("User-Agent", userAgent())
                .get()
                .build()
        return client.newCall(request).execute().use { response ->
            val body = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                throw AppError.Http(response.code)
            }
            body
        }
    }

    private fun userAgent(): String {
        val ver = versionName
        return if (ver != null) "XiaoxiongWeather/$ver (Android)" else "XiaoxiongWeather (Android)"
    }
}

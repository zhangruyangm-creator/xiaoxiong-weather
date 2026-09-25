package com.xiaoxiong.weather.data.remote

import com.xiaoxiong.weather.data.AppError
import com.xiaoxiong.weather.data.MAX_WEATHER_JSON_BYTES
import com.xiaoxiong.weather.data.readBodyLimited
import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.Call
import okhttp3.Callback
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.IOException
import kotlin.coroutines.resumeWithException

/**
 * Open-Meteo 通用 GET：统一错误映射（HTTP → AppError.Http，IO → NetworkUnavailable）。
 *
 * [executeWithFallback] 支持同一请求在多 base URL 间顺序故障转移：
 * 主 URL 因网络/超时/DNS 失败或 5xx 服务端故障时，自动尝试后续候选 URL。
 */
object OpenMeteoHttp {
    /** App 启动时由 [com.xiaoxiong.weather.di.AppContainer] 注入，用于 User-Agent。 */
    @Volatile
    var versionName: String? = null

    suspend fun execute(
        url: String,
        client: OkHttpClient,
        userAgent: String? = null,
    ): String = executeWithFallback(listOf(url), client, userAgent)

    /**
     * 主 URL 请求失败时按候选顺序重试。网络异常（IOException）与 5xx（服务端故障）、
     * 429（限流）、408（请求超时）、425（过早）都会转移到候选入口；其余 4xx 是请求本身问题，
     * 直接上抛不转移。取消信号（CancellationException）始终透传，不参与故障转移。
     *
     * [userAgent] 非空时覆盖默认 User-Agent（部分数据源要求带联系方式的标识，如 MET Norway）。
     */
    suspend fun executeWithFallback(
        baseUrls: List<String>,
        client: OkHttpClient,
        userAgent: String? = null,
    ): String {
        require(baseUrls.isNotEmpty()) { "baseUrls must not be empty" }
        var lastError: Exception? = null
        var httpError: AppError.Http? = null
        for (url in baseUrls) {
            try {
                return executeOnce(url, client, userAgent)
            } catch (e: AppError.Http) {
                // 5xx / 429 / 408 / 425 可转移；其余 4xx 是请求本身问题，不在候选间转移
                if (!isRetryableHttpCode(e.code)) throw e
                lastError = e
                httpError = e
            } catch (e: IOException) {
                lastError = e
            }
        }
        // 后续候选只断连时仍保留服务端故障分类：Http(5xx/429/408/425) 比 IOException 更具体
        httpError?.let { throw it }
        throw AppError.NetworkUnavailable(lastError as? IOException)
    }

    internal fun isRetryableHttpCode(code: Int): Boolean = code in 500..599 || code == 429 || code == 408 || code == 425

    private suspend fun executeOnce(
        url: String,
        client: OkHttpClient,
        userAgent: String? = null,
    ): String {
        val request =
            Request
                .Builder()
                .url(url)
                .header("Accept", "application/json")
                .header("User-Agent", userAgent ?: userAgent())
                .get()
                .build()
        return client.newCall(request).awaitBody()
    }

    /**
     * 用 enqueue（异步回调）包装 OkHttp 调用，替代同步 execute()：
     * 同步 execute 阻塞在协程的 IO 线程上且无法响应协程取消——快速切城/退后台时
     * 旧请求会变成"僵尸"，继续占用 IO 线程与连接池直到超时。
     * 这里在取消时 cancel 底层 Call，让请求尽快释放。
     */
    private suspend fun Call.awaitBody(): String =
        suspendCancellableCoroutine { cont ->
            enqueue(
                object : Callback {
                    override fun onFailure(
                        call: Call,
                        e: IOException,
                    ) {
                        if (cont.isActive) cont.resumeWithException(e)
                    }

                    override fun onResponse(
                        call: Call,
                        response: Response,
                    ) {
                        // 取消回调先于 resume 执行，避免 isActive 判断与取消之间的竞态泄漏响应
                        cont.resume(response) { response.close() }
                    }
                },
            )
            cont.invokeOnCancellation { cancel() }
        }.use { response ->
            // 协程已取消时继续执行的取消信号会从这里抛给调用方，use 负责关闭 response
            if (!response.isSuccessful) {
                // 先判状态码再读体：错误页同样可能有超大 body
                throw AppError.Http(response.code)
            }
            response.readBodyLimited(MAX_WEATHER_JSON_BYTES, "天气")
        }

    private fun userAgent(): String {
        val ver = versionName
        return if (ver != null) "XiaoxiongWeather/$ver (Android)" else "XiaoxiongWeather (Android)"
    }
}

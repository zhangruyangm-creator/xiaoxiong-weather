package com.xiaoxiong.weather.data.remote

import com.xiaoxiong.weather.data.AppError
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException

/** Open-Meteo 通用 GET：统一错误映射（HTTP → AppError.Http，IO → NetworkUnavailable）。 */
object OpenMeteoHttp {
    /** App 启动时由 [com.xiaoxiong.weather.di.AppContainer] 注入，用于 User-Agent。 */
    var versionName: String? = null

    fun execute(
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
        try {
            client.newCall(request).execute().use { response ->
                val body = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    throw AppError.Http(response.code)
                }
                return body
            }
        } catch (e: AppError) {
            throw e
        } catch (e: IOException) {
            throw AppError.NetworkUnavailable(e)
        }
    }

    private fun userAgent(): String {
        val ver = versionName
        return if (ver != null) "XiaoxiongWeather/$ver (Android)" else "XiaoxiongWeather (Android)"
    }
}

package com.xiaoxiong.weather.data

import android.content.res.Resources
import androidx.annotation.StringRes
import com.xiaoxiong.weather.R
import kotlinx.serialization.SerializationException
import java.io.IOException

/**
 * 类型化应用错误，避免 UI 解析 `"HTTP 503"` 这类技术字符串。
 */
sealed class AppError(
    message: String? = null,
    cause: Throwable? = null,
) : Exception(message, cause) {
    class Http(
        val code: Int,
    ) : AppError("HTTP $code")

    class NetworkUnavailable(
        cause: Throwable? = null,
    ) : AppError(
            message = cause?.message ?: "network unavailable",
            cause = cause,
        )

    /** 响应结构不符合预期（接口变更/坏包），不给用户展示解析细节 */
    class DataFormat(
        cause: Throwable? = null,
    ) : AppError("data format", cause)

    class Message(
        detail: String,
        cause: Throwable? = null,
    ) : AppError(detail, cause)
}

fun Throwable.toAppError(): AppError =
    when (this) {
        is AppError -> this
        is IOException -> AppError.NetworkUnavailable(this)
        is SerializationException -> AppError.DataFormat(this)
        else -> {
            val httpCode =
                Regex("""^HTTP\s+(\d+)$""")
                    .find(message.orEmpty())
                    ?.groupValues
                    ?.getOrNull(1)
                    ?.toIntOrNull()
            when {
                httpCode != null -> AppError.Http(httpCode)
                cause is IOException -> AppError.NetworkUnavailable(this)
                message.isNullOrBlank() -> AppError.Message("unknown", this)
                else -> AppError.Message(message.orEmpty(), this)
            }
        }
    }

fun AppError.toUserMessage(
    resources: Resources,
    @StringRes fallback: Int,
): String =
    when (this) {
        is AppError.Http -> resources.getString(R.string.error_network_request, code)
        is AppError.NetworkUnavailable -> resources.getString(R.string.error_network_unavailable)
        is AppError.DataFormat -> resources.getString(R.string.error_data_format)
        is AppError.Message ->
            message?.takeIf { it.isNotBlank() }
                ?: resources.getString(fallback)
    }

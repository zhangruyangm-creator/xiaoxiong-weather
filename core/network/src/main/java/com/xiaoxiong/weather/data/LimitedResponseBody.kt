package com.xiaoxiong.weather.data

import okhttp3.Response
import okio.Buffer
import okio.ForwardingSource
import okio.IOException
import okio.Source
import okio.buffer
import okio.source

/**
 * 带字节上限的响应体读取。
 *
 * `ResponseBody.string()` / `.bytes()` 会把整个响应读进内存：上游异常（代理返回
 * HTML 错误页、截断重试、镜像站返回超大 JSON/图片）时，一次请求就能把进程推到
 * OOM。OkHttp 只在 `Content-Length` 存在时才做提示性校验，所以流式限额是唯一可靠防线。
 */
internal fun Response.readBodyLimited(
    maxBytes: Long,
    what: String,
): String = readBytesLimited(maxBytes, what).toString(Charsets.UTF_8)

/** 二进制响应（雷达瓦片/卫星图）的字节读取，带上限。 */
internal fun Response.readBytesLimited(
    maxBytes: Long,
    what: String,
): ByteArray = limitedSource(maxBytes, what).use { source -> source.readByteArray() }

/** 把响应体包成边读边计数的 Source：超过 [maxBytes] 立刻中断，不会先读完再判断。 */
private fun Response.limitedSource(
    maxBytes: Long,
    what: String,
): Buffer {
    val body = body ?: return Buffer()
    val out = Buffer()
    body.byteStream().source().countingLimit(maxBytes, what).buffer().use { source ->
        while (true) {
            if (source.read(out, SEGMENT_BYTES) == -1L) break
        }
    }
    return out
}

/** 计数转发源：超过 [maxBytes] 抛 [IOException]，由调用方的统一错误处理降级。 */
private fun Source.countingLimit(
    maxBytes: Long,
    what: String,
): Source =
    object : ForwardingSource(this) {
        private var count = 0L

        override fun read(
            sink: Buffer,
            byteCount: Long,
        ): Long {
            val read = super.read(sink, byteCount)
            if (read == -1L) return read
            count += read
            if (count > maxBytes) throw IOException("$what 响应超过 $maxBytes 字节上限")
            return read
        }
    }

private const val SEGMENT_BYTES = 8L * 1024

/** 天气类 JSON 响应上限：正常响应在几十 KB，1 MiB 已有极大余量。 */
internal const val MAX_WEATHER_JSON_BYTES = 1L * 1024 * 1024

/** 雷达帧索引 JSON 上限：正常含数十帧，256 KiB 足够。 */
internal const val MAX_RADAR_JSON_BYTES = 256L * 1024

/** 雷达瓦片/卫星图片上限：单张通常几十到几百 KB，4 MiB 足够。 */
internal const val MAX_RADAR_IMAGE_BYTES = 4L * 1024 * 1024

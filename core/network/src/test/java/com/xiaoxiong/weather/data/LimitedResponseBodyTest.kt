package com.xiaoxiong.weather.data

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 响应体字节上限。
 *
 * 背景：`body.string()` / `.bytes()` 会把整个响应读进内存。上游异常（代理返回 HTML
 * 错误页、镜像站返回超大文件）时一次请求就能 OOM；OkHttp 只在 Content-Length 存在时
 * 做提示性校验，所以必须流式限额。
 */
class LimitedResponseBodyTest {
    private fun responseOf(text: String): Response =
        Response
            .Builder()
            .request(Request.Builder().url("https://example.test/").build())
            .protocol(Protocol.HTTP_1_1)
            .code(200)
            .message("OK")
            .body(text.toByteArray().toResponseBody("text/plain".toMediaType()))
            .build()

    private fun responseOfBytes(size: Int): Response =
        Response
            .Builder()
            .request(Request.Builder().url("https://example.test/").build())
            .protocol(Protocol.HTTP_1_1)
            .code(200)
            .message("OK")
            .body(ByteArray(size).toResponseBody("application/octet-stream".toMediaType()))
            .build()

    @Test
    fun `body within limit is read intact`() {
        val text = "x".repeat(1_000)
        val body = responseOf(text).readBodyLimited(10_000, "测试")
        assertEquals(text, body)
    }

    @Test
    fun `body exceeding limit is rejected`() {
        val text = "x".repeat(5_000)
        val failed =
            runCatching { responseOf(text).readBodyLimited(1_000, "测试") }.exceptionOrNull()
        assertTrue("超限必须抛错而不是读进内存", failed != null)
    }

    @Test
    fun `oversized binary payload is rejected`() {
        val failed =
            runCatching { responseOfBytes(50_000).readBytesLimited(1_024, "图片") }.exceptionOrNull()
        assertTrue(failed != null)
    }

    @Test
    fun `binary within limit is read intact`() {
        val bytes = responseOfBytes(2_048).readBytesLimited(10_000, "图片")
        assertEquals(2_048, bytes.size)
    }

    @Test
    fun `empty body yields empty string`() {
        val empty =
            Response
                .Builder()
                .request(Request.Builder().url("https://example.test/").build())
                .protocol(Protocol.HTTP_1_1)
                .code(204)
                .message("No Content")
                .build()
        assertEquals("", empty.readBodyLimited(1_000, "测试"))
    }
}

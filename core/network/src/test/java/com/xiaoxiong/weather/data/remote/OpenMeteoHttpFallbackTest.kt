package com.xiaoxiong.weather.data.remote

import com.xiaoxiong.weather.data.AppError
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * OpenMeteoHttp 故障转移：主 URL 网络失败或 5xx 时按序重试候选；
 * 4xx（请求本身问题）不转移，直接上抛。
 */
class OpenMeteoHttpFallbackTest {
    private lateinit var serverA: MockWebServer
    private lateinit var serverB: MockWebServer
    private lateinit var client: OkHttpClient

    @Before
    fun setUp() {
        serverA = MockWebServer()
        serverB = MockWebServer()
        client =
            OkHttpClient
                .Builder()
                .connectTimeout(3, TimeUnit.SECONDS)
                .readTimeout(3, TimeUnit.SECONDS)
                .build()
        serverA.start()
        serverB.start()
    }

    @After
    fun tearDown() {
        serverA.shutdown()
        serverB.shutdown()
    }

    @Test
    fun primarySucceeds_returnsPrimaryBodyWithoutHittingBackup() =
        runBlocking {
            serverA.enqueue(MockResponse().setResponseCode(200).setBody("""{"ok":true}"""))
            val urlA = serverA.url("/v1/forecast").toString()
            val urlB = serverB.url("/v1/forecast").toString()

            val body = OpenMeteoHttp.executeWithFallback(listOf(urlA, urlB), client)

            assertEquals("""{"ok":true}""", body)
            assertEquals(0, serverB.requestCount)
        }

    @Test
    fun primaryNetworkError_fallsBackToSecondary() =
        runBlocking {
            // serverA 不 enqueue：请求会挂起直到超时，模拟主站不可达
            // 直接关掉 serverA 的 socket 更接近真实断连：enqueue 一个异常响应不行，
            // 用 MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AT_START) 模拟连接被拒
            serverA.enqueue(
                MockResponse()
                    .setSocketPolicy(okhttp3.mockwebserver.SocketPolicy.DISCONNECT_AT_START),
            )
            serverB.enqueue(MockResponse().setResponseCode(200).setBody("""{"ok":true,"from":"b"}"""))
            val urlA = serverA.url("/v1/forecast").toString()
            val urlB = serverB.url("/v1/forecast").toString()

            val body = OpenMeteoHttp.executeWithFallback(listOf(urlA, urlB), client)

            assertEquals("""{"ok":true,"from":"b"}""", body)
            assertEquals(1, serverB.requestCount)
        }

    @Test
    fun allCandidatesFail_throwsNetworkUnavailable() =
        runBlocking {
            serverA.enqueue(
                MockResponse()
                    .setSocketPolicy(okhttp3.mockwebserver.SocketPolicy.DISCONNECT_AT_START),
            )
            serverB.enqueue(
                MockResponse()
                    .setSocketPolicy(okhttp3.mockwebserver.SocketPolicy.DISCONNECT_AT_START),
            )
            val urlA = serverA.url("/v1/forecast").toString()
            val urlB = serverB.url("/v1/forecast").toString()

            val error =
                assertThrows(AppError.NetworkUnavailable::class.java) {
                    runBlocking {
                        OpenMeteoHttp.executeWithFallback(listOf(urlA, urlB), client)
                    }
                }

            assertTrue(error.cause is IOException || error.cause == null)
        }

    @Test
    fun httpError5xx_fallsBackToSecondary() =
        runBlocking {
            // 5xx 是服务端故障：换 host 有意义，应转移到候选入口
            serverA.enqueue(MockResponse().setResponseCode(500).setBody("oops"))
            serverB.enqueue(MockResponse().setResponseCode(200).setBody("""{"ok":true,"from":"b"}"""))
            val urlA = serverA.url("/v1/forecast").toString()
            val urlB = serverB.url("/v1/forecast").toString()

            val body = OpenMeteoHttp.executeWithFallback(listOf(urlA, urlB), client)

            assertEquals("""{"ok":true,"from":"b"}""", body)
            assertEquals(1, serverB.requestCount)
        }

    @Test
    fun httpError4xx_doesNotFallBack() =
        runBlocking {
            // 4xx 是请求本身问题：换 host 没意义，不应命中备用站
            serverA.enqueue(MockResponse().setResponseCode(404).setBody("not found"))
            serverB.enqueue(MockResponse().setResponseCode(200).setBody("""{"ok":true}"""))
            val urlA = serverA.url("/v1/forecast").toString()
            val urlB = serverB.url("/v1/forecast").toString()

            val error =
                assertThrows(AppError.Http::class.java) {
                    runBlocking {
                        OpenMeteoHttp.executeWithFallback(listOf(urlA, urlB), client)
                    }
                }

            assertEquals(404, error.code)
            assertEquals(0, serverB.requestCount)
        }

    @Test
    fun singleUrl_executeMatchesFallback() =
        runBlocking {
            serverA.enqueue(MockResponse().setResponseCode(200).setBody("""{"ok":true}"""))
            val urlA = serverA.url("/v1/forecast").toString()

            val body = OpenMeteoHttp.execute(urlA, client)

            assertEquals("""{"ok":true}""", body)
        }
}
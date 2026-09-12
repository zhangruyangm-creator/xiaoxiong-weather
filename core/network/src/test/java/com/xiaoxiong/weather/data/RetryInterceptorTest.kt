package com.xiaoxiong.weather.data

import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.After
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.IOException
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class RetryInterceptorTest {
    private lateinit var server: MockWebServer
    private lateinit var client: OkHttpClient

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        server.dispatcher =
            object : Dispatcher() {
                override fun dispatch(request: RecordedRequest): MockResponse = MockResponse().setResponseCode(500).setBody("oops")
            }
        client =
            OkHttpClient
                .Builder()
                .addInterceptor(RetryInterceptor(maxRetries = 3, baseDelayMs = 2_000))
                .build()
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun cancelDuringBackoff_stopsRetryingQuickly() {
        val executor = Executors.newSingleThreadExecutor()
        try {
            val call = client.newCall(Request.Builder().url(server.url("/retry")).build())
            val future =
                executor.submit<IOException?> {
                    try {
                        call.execute().close()
                        null
                    } catch (e: IOException) {
                        e
                    }
                }

            // 首个请求已拿到 500 并进入退避窗口
            assertNotNull(server.takeRequest(2, TimeUnit.SECONDS))
            Thread.sleep(200)
            val start = System.nanoTime()
            call.cancel()
            val error = future.get(1, TimeUnit.SECONDS)
            val elapsedMs = (System.nanoTime() - start) / 1_000_000

            assertTrue("expected IOException, got $error", error is IOException)
            assertTrue("cancel should interrupt backoff, took ${elapsedMs}ms", elapsedMs < 500)
        } finally {
            executor.shutdownNow()
        }
    }
}

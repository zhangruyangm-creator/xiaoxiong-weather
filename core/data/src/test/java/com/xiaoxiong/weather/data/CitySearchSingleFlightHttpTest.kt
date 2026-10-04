package com.xiaoxiong.weather.data

import android.content.res.Resources
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.mockito.Mockito
import java.util.concurrent.TimeUnit

/**
 * 城市搜索的上游去重：并发相同 query 只打一次上游；TTL 内重复搜索命中缓存；
 * 「济南市」归一化后与「济南」共用同一缓存项。
 */
class CitySearchSingleFlightHttpTest {
    private lateinit var server: MockWebServer
    private lateinit var repository: WeatherRepository

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        server.dispatcher =
            object : Dispatcher() {
                override fun dispatch(request: RecordedRequest): MockResponse {
                    val path = request.path.orEmpty()
                    return if (path.contains("search")) {
                        MockResponse().setBody(searchJson).setBodyDelay(300, TimeUnit.MILLISECONDS)
                    } else {
                        MockResponse().setResponseCode(404)
                    }
                }
            }
        val base = server.url("v1").toString().trimEnd('/')
        val client =
            OkHttpClient
                .Builder()
                .connectTimeout(2, TimeUnit.SECONDS)
                .readTimeout(5, TimeUnit.SECONDS)
                .build()
        repository =
            WeatherRepository(
                resources = mockResources(),
                client = client,
                searchClient = client,
                geocodingBase = base,
                forecastBase = base,
                airQualityBase = base,
                archiveBase = base,
                metNorwayBase = null,
            )
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun concurrentSameQuery_sharesSingleUpstreamRequest() =
        runBlocking {
            val results =
                coroutineScope {
                    val first = async { repository.searchCities("Jinan") }
                    delay(50)
                    val second = async { repository.searchCities("Jinan") }
                    first.await() to second.await()
                }

            assertEquals(1, server.requestCount)
            assertEquals(results.first.map { it.name }, results.second.map { it.name })
            assertEquals("济南", results.first.firstOrNull()?.name)
        }

    @Test
    fun cancelledOwner_doesNotCancelConcurrentSameQuery() =
        runBlocking {
            val owner = launch { repository.searchCities("Jinan") }
            delay(50)
            val waiter = async { repository.searchCities("Jinan") }
            delay(10)
            owner.cancel()
            // 等待者应仍拿到合并结果，而不是随被取消的 owner 一起抛出取消
            val result = waiter.await()
            assertEquals("济南", result.firstOrNull()?.name)
        }

    @Test
    fun repeatedQueryWithinTtl_hitsCacheWithoutUpstream() =
        runBlocking {
            repository.searchCities("Jinan")
            repository.searchCities("Jinan")
            repository.searchCities("Jinan")

            assertEquals(1, server.requestCount)
        }

    @Test
    fun normalizedQuerySharesCacheEntry() =
        runBlocking {
            val first = repository.searchCities("济南")
            val second = repository.searchCities("济南市")

            // 首查：主查询 + 拼音补查，共 2 次；“济南市”归一化后命中缓存不再请求
            assertEquals(2, server.requestCount)
            assertEquals(first.map { it.name }, second.map { it.name })
        }

    private fun mockResources(): Resources {
        val resources = Mockito.mock(Resources::class.java)
        Mockito.`when`(resources.getString(Mockito.anyInt())).thenReturn("x")
        Mockito.`when`(resources.getString(Mockito.anyInt(), Mockito.any())).thenReturn("x")
        return resources
    }

    private val searchJson =
        """
        {
          "results": [
            {
              "id": 1,
              "name": "济南",
              "latitude": 36.65,
              "longitude": 117.12,
              "country": "中国",
              "admin1": "山东",
              "timezone": "Asia/Shanghai"
            }
          ]
        }
        """.trimIndent()
}

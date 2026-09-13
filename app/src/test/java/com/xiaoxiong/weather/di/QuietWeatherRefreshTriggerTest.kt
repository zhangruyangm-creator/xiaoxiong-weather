package com.xiaoxiong.weather.di

import android.content.Context
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import org.mockito.Mockito

/**
 * 温度单位切换后的应用内静默刷新信号：AppContainer 持有 SharedFlow，
 * WeatherApp 检测到单位真实变化后触发，WeatherViewModel 订阅消费。
 */
@OptIn(ExperimentalCoroutinesApi::class)
class QuietWeatherRefreshTriggerTest {
    private fun container(): AppContainer {
        val context = Mockito.mock(Context::class.java)
        Mockito.`when`(context.applicationContext).thenReturn(context)
        return AppContainer(context)
    }

    @Test
    fun requestQuietWeatherRefresh_emitsSignalForEachChange() =
        runTest {
            val container = container()
            val received = mutableListOf<Unit>()
            val job =
                launch(UnconfinedTestDispatcher(testScheduler)) {
                    container.quietWeatherRefreshRequests.collect { received += it }
                }

            container.requestQuietWeatherRefresh()
            runCurrent()
            assertEquals(1, received.size)

            container.requestQuietWeatherRefresh()
            runCurrent()
            assertEquals(2, received.size)
            job.cancel()
        }
}

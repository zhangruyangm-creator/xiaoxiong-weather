package com.xiaoxiong.weather.data

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 进程级温度单位 provider 安装语义：Worker/小组件等后台拉取路径不经过 DebugWeatherStore，
 * 由装配层（AppContainer）提前安装；安装幂等，provider 每次调用都读当前设置单位。
 */
class TemperatureUnitProviderInstallTest {
    @After
    fun tearDown() {
        ForecastFetcher.clearInstalledTemperatureUnitProvider()
    }

    @Test
    fun resolve_withoutInstall_fallsBackToCelsius() =
        runBlocking {
            ForecastFetcher.clearInstalledTemperatureUnitProvider()
            assertEquals(TemperatureUnit.CELSIUS, ForecastFetcher.resolveTemperatureUnitProvider().invoke())
        }

    @Test
    fun install_isIdempotentAndLastOneWins() =
        runBlocking {
            ForecastFetcher.clearInstalledTemperatureUnitProvider()
            ForecastFetcher.installTemperatureUnitProvider { TemperatureUnit.FAHRENHEIT }
            assertEquals(TemperatureUnit.FAHRENHEIT, ForecastFetcher.resolveTemperatureUnitProvider().invoke())

            // 重复安装（AppContainer 之后 DebugWeatherStore 再装一次）不抛错，最后一次生效
            ForecastFetcher.installTemperatureUnitProvider { TemperatureUnit.CELSIUS }
            assertEquals(TemperatureUnit.CELSIUS, ForecastFetcher.resolveTemperatureUnitProvider().invoke())
        }

    @Test
    fun installedProvider_readsSettingsOnEachInvocation() =
        runBlocking {
            val settings =
                object : AppSettingsSource {
                    var unit = TemperatureUnit.CELSIUS
                    override val settings: Flow<AppSettings> =
                        MutableStateFlow(AppSettings(temperatureUnit = unit))

                    override suspend fun getOnce(): AppSettings = AppSettings(temperatureUnit = unit)
                }
            // AppContainer 使用的装配入口：无需 DebugWeatherStore 也能拿到设置单位
            ForecastFetcher.installTemperatureUnitProvider(settings)
            val provider = ForecastFetcher.installedTemperatureUnitProviderOrNull()!!

            assertEquals(TemperatureUnit.CELSIUS, provider.invoke())
            settings.unit = TemperatureUnit.FAHRENHEIT
            assertEquals(TemperatureUnit.FAHRENHEIT, provider.invoke())
        }
}

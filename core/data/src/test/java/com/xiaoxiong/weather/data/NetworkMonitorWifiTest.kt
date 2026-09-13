package com.xiaoxiong.weather.data

import android.net.NetworkCapabilities
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.Mockito

/**
 * [isWifiTransportCapability] 的判定口径：具备 INTERNET 的生效网络且带 WIFI transport。
 * VPN 场景下默认网络是 VPN，但底层 Wi-Fi 网络的 capabilities 仍会被此判定命中。
 */
class NetworkMonitorWifiTest {
    private fun capabilities(
        internet: Boolean,
        wifi: Boolean,
    ): NetworkCapabilities {
        val caps = Mockito.mock(NetworkCapabilities::class.java)
        Mockito.`when`(caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)).thenReturn(internet)
        Mockito.`when`(caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)).thenReturn(wifi)
        return caps
    }

    @Test
    fun wifiWithInternet_isWifi() {
        assertTrue(isWifiTransportCapability(capabilities(internet = true, wifi = true)))
    }

    @Test
    fun internetWithoutWifi_isNotWifi() {
        assertFalse(isWifiTransportCapability(capabilities(internet = true, wifi = false)))
    }

    @Test
    fun wifiWithoutInternet_isNotWifi() {
        // 仅本地/未生效网络不算，避免误走雷达帧懒加载
        assertFalse(isWifiTransportCapability(capabilities(internet = false, wifi = true)))
    }

    @Test
    fun nullCapabilities_isNotWifi() {
        assertFalse(isWifiTransportCapability(null))
    }
}

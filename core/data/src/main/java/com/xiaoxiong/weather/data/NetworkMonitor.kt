package com.xiaoxiong.weather.data

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import okhttp3.ConnectionPool

/** 网络在线状态来源；生产由 [NetworkMonitor] 实现，测试可注入假实现。 */
interface NetworkStateSource {
    val isOnline: StateFlow<Boolean>
}

/**
 * 监听默认网络变化，解决切换网络 / 连 VPN 后 OkHttp 连接池复用旧网络死连接、
 * 导致「怎么刷新都不行」的问题：
 * - 默认网络丢失、恢复或能力变化时清空连接池（evictAll），下次请求强制新建连接；
 * - 暴露在线状态（需系统 VALIDATED），供 ViewModel 在网络恢复时自动静默刷新。
 */
class NetworkMonitor(
    context: Context,
    private val connectionPool: ConnectionPool,
) : NetworkStateSource {
    private val connectivityManager =
        context.applicationContext
            .getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

    private val _isOnline = MutableStateFlow(queryOnline())
    override val isOnline: StateFlow<Boolean> = _isOnline.asStateFlow()

    private val callback =
        object : ConnectivityManager.NetworkCallback() {
            override fun onLost(network: Network) {
                // 默认网络丢失：池内连接全部对应旧网络，清池强制下次重建；
                // 切换网络时旧网 onLost 可能晚于新网 onAvailable，不能无条件置离线，按当前活动网络重新判定
                connectionPool.evictAll()
                _isOnline.value = queryOnline()
            }

            override fun onAvailable(network: Network) {
                // 出现可用网络：先清池，避免复用刚随路由变化失效的半死连接
                connectionPool.evictAll()
                updateOnline(connectivityManager.getNetworkCapabilities(network))
            }

            override fun onCapabilitiesChanged(
                network: Network,
                capabilities: NetworkCapabilities,
            ) {
                // VALIDATED 就绪后网络才算真正可用，此时自动刷新才可靠
                updateOnline(capabilities)
            }
        }

    private fun updateOnline(capabilities: NetworkCapabilities?) {
        val online =
            capabilities?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true &&
                capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
        if (online != _isOnline.value) {
            if (online) connectionPool.evictAll()
            _isOnline.value = online
        }
    }

    private fun queryOnline(): Boolean {
        val network = connectivityManager.activeNetwork ?: return false
        val capabilities =
            connectivityManager.getNetworkCapabilities(network) ?: return false
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
            capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
    }

    fun start() {
        connectivityManager.registerDefaultNetworkCallback(callback)
    }
}

/**
 * 当前设备是否具备可用的 Wi-Fi 传输。
 *
 * 不能只看默认网络：VPN 生效时默认网络是 VPN（只有 TRANSPORT_VPN），
 * 底层 Wi-Fi 仍在 [ConnectivityManager.allNetworks] 里。因此遍历所有具备
 * 上网能力（INTERNET）的生效网络，任一携带 WIFI transport 即视为 Wi-Fi。
 * 无活动网络、无权限或系统异常时返回 false（保守回退蜂窝语义，走雷达帧懒加载）。
 */
fun Context.isWifiConnected(): Boolean {
    val cm =
        applicationContext
            .getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            ?: return false
    val networks =
        try {
            cm.allNetworks
        } catch (e: Exception) {
            // 缺少 ACCESS_NETWORK_STATE 等系统限制：按蜂窝处理
            return false
        }
    return networks.any { network ->
        val capabilities =
            try {
                cm.getNetworkCapabilities(network)
            } catch (e: Exception) {
                null
            }
        isWifiTransportCapability(capabilities)
    }
}

/**
 * 单张网络能力是否是「生效的 Wi-Fi」：需具备 INTERNET（排除仅本地/未生效网络），
 * 且携带 WIFI transport（VPN 的底层 Wi-Fi 网络同样满足）。
 */
internal fun isWifiTransportCapability(capabilities: NetworkCapabilities?): Boolean =
    capabilities?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true &&
        capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)

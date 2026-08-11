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
                // 默认网络丢失：池内连接全部对应旧网络，清池强制下次重建
                connectionPool.evictAll()
                _isOnline.value = false
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
 * 当前默认网络是否走 Wi-Fi（VPN 网络按底层传输判断）。
 * 无活动网络或未知时返回 false（视为蜂窝，走雷达帧懒加载）。
 */
fun Context.isWifiConnected(): Boolean {
    val cm =
        applicationContext
            .getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            ?: return false
    val network = cm.activeNetwork ?: return false
    val capabilities = cm.getNetworkCapabilities(network) ?: return false
    return capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
}

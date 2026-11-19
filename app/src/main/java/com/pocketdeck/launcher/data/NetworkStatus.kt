package com.pocketdeck.launcher.data

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 全局网络连通性感知（支持离线无感降级与联网自动恢复）。
 */
object NetworkStatus {
    private val _online = MutableStateFlow(false)
    val online: StateFlow<Boolean> = _online.asStateFlow()

    @Volatile
    private var initialized = false

    fun init(context: Context) {
        val appCtx = context.applicationContext
        _online.value = queryOnline(appCtx)
        if (initialized) return
        synchronized(this) {
            if (initialized) return
            initialized = true
            val cm = appCtx.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
                ?: return
            runCatching {
                cm.registerDefaultNetworkCallback(object : ConnectivityManager.NetworkCallback() {
                    override fun onAvailable(network: Network) {
                        _online.value = queryOnline(appCtx)
                    }

                    override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) {
                        _online.value = hasUsableInternet(caps)
                    }

                    override fun onLost(network: Network) {
                        _online.value = queryOnline(appCtx, excluding = network)
                    }

                    override fun onUnavailable() {
                        _online.value = false
                    }
                })
            }
        }
    }

    fun isOnline(context: Context): Boolean {
        if (initialized) return _online.value
        val result = queryOnline(context.applicationContext)
        _online.value = result
        return result
    }

    private fun queryOnline(context: Context, excluding: Network? = null): Boolean = runCatching {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            ?: return false
        val active = cm.activeNetwork
        if (active != null && active != excluding) {
            val caps = cm.getNetworkCapabilities(active)
            if (caps != null && hasUsableInternet(caps)) return true
        }
        @Suppress("DEPRECATION")
        cm.allNetworks.any { net ->
            net != excluding && cm.getNetworkCapabilities(net)?.let(::hasUsableInternet) == true
        }
    }.getOrDefault(false)

    private fun hasUsableInternet(caps: NetworkCapabilities): Boolean {
        if (!caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)) return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED) ||
            caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
            caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) ||
            caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) ||
            caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN)
    }
}

package com.screendrop.network

import android.content.Context
import android.net.ConnectivityManager
import android.net.LinkProperties
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import com.screendrop.model.NetworkStatus
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.net.Inet4Address

/**
 * Network Manager: tracks Wi-Fi/Ethernet connectivity, resolves the local
 * IPv4 used in the QR URL, and reacts to network changes.
 *
 * Notes:
 * - Hotspot networks often have no validated INTERNET capability; we must
 *   not require it — local-only networks are the core scenario.
 * - VPN transports are skipped so a tunnel address never lands in the QR.
 * - Wi-Fi is preferred over Ethernet, Ethernet over anything else.
 */
class NetworkMonitor(context: Context) {

    private val cm = context.getSystemService(ConnectivityManager::class.java)

    private val _status = MutableStateFlow(NetworkStatus(connected = false, ipv4 = null))
    val status: StateFlow<NetworkStatus> = _status.asStateFlow()

    private var registered = false

    private val callback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) = refresh()
        override fun onLost(network: Network) = refresh()
        override fun onLinkPropertiesChanged(network: Network, lp: LinkProperties) = refresh()
        override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) = refresh()
    }

    @Synchronized
    fun start() {
        if (registered) return
        val request = NetworkRequest.Builder()
            .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
            .addTransportType(NetworkCapabilities.TRANSPORT_ETHERNET)
            .build()
        runCatching { cm.registerNetworkCallback(request, callback) }
        registered = true
        refresh()
    }

    @Synchronized
    fun stop() {
        if (!registered) return
        runCatching { cm.unregisterNetworkCallback(callback) }
        registered = false
    }

    /** Recomputes the status from all current networks (thread-safe). */
    fun refresh() {
        val result = computeStatus()
        _status.value = result
    }

    private fun computeStatus(): NetworkStatus {
        val networks = cm.allNetworks ?: return NetworkStatus(connected = false, ipv4 = null)
        var best: Network? = null
        var bestScore = -1
        for (network in networks) {
            val caps = cm.getNetworkCapabilities(network) ?: continue
            if (caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN)) continue
            val score = when {
                caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> 2
                caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> 1
                else -> 0
            }
            if (score > bestScore) {
                bestScore = score
                best = network
            }
        }
        if (best == null) return NetworkStatus(connected = false, ipv4 = null)
        val lp = cm.getLinkProperties(best) ?: return NetworkStatus(connected = false, ipv4 = null)
        val ipv4 = lp.linkAddresses
            .mapNotNull { it.address as? Inet4Address }
            .firstOrNull { !it.isLoopbackAddress }
            ?.hostAddress
        return NetworkStatus(connected = ipv4 != null, ipv4 = ipv4)
    }
}

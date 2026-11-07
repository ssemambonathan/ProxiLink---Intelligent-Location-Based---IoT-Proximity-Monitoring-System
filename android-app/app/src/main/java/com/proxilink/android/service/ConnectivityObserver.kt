package com.proxilink.android.service

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

enum class NetworkStatus(val label: String) {
    ONLINE("Online"),
    OFFLINE("Offline"),
}

data class ConnectivityState(
    val status: NetworkStatus = NetworkStatus.OFFLINE,
    val transport: String = "Unknown",
    val metered: Boolean = false,
) {
    val isOnline: Boolean get() = status == NetworkStatus.ONLINE
}

@Singleton
class ConnectivityObserver @Inject constructor(
    @ApplicationContext context: Context,
) {
    private val manager = context.getSystemService(ConnectivityManager::class.java)
    private val _state = MutableStateFlow(ConnectivityState())
    val state: StateFlow<ConnectivityState> = _state.asStateFlow()

    init {
        refresh()
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) = refresh()

            override fun onLost(network: Network) = refresh()

            override fun onCapabilitiesChanged(network: Network, capabilities: NetworkCapabilities) = refresh()
        }
        runCatching {
            manager?.registerNetworkCallback(
                NetworkRequest.Builder()
                    .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                    .build(),
                callback,
            )
        }
    }

    fun refresh() {
        val active = manager?.activeNetwork
        val capabilities = active?.let { manager.getNetworkCapabilities(it) }
        val hasInternet = capabilities?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true
        val transport = when {
            capabilities == null -> "Unknown"
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> "Wi-Fi"
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> "Cellular"
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> "Ethernet"
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_VPN) -> "VPN"
            else -> "Other"
        }
        _state.value = ConnectivityState(
            status = if (hasInternet) NetworkStatus.ONLINE else NetworkStatus.OFFLINE,
            transport = transport,
            metered = capabilities?.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED) == false,
        )
    }
}

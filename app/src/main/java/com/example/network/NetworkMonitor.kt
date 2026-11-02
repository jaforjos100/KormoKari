package com.example.network

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged

enum class ConnectionType {
    WIFI,
    CELLULAR,
    ETHERNET,
    VPN,
    OTHER
}

sealed interface NetworkState {
    object Checking : NetworkState
    data class Connected(
        val hasInternet: Boolean,
        val isMetered: Boolean,
        val connectionType: ConnectionType = ConnectionType.OTHER
    ) : NetworkState
    data class Disconnected(val reason: String) : NetworkState
}

class NetworkMonitor(private val context: Context) {

    private val connectivityManager =
        context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager

    /**
     * Synchronous boolean check whether the device has validated internet access.
     */
    val isOnline: Boolean
        get() = when (val state = checkCurrentNetwork()) {
            is NetworkState.Connected -> state.hasInternet
            else -> false
        }

    /**
     * Checks current instantaneous network connectivity state using ConnectivityManager.
     */
    fun checkCurrentNetwork(): NetworkState {
        val cm = connectivityManager ?: return NetworkState.Disconnected("Connectivity service unavailable")
        val activeNetwork = cm.activeNetwork ?: return NetworkState.Disconnected("No active network connection")
        val capabilities = cm.getNetworkCapabilities(activeNetwork)
            ?: return NetworkState.Disconnected("Network interface detected but has no capabilities")

        val hasInternet = capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
        val hasValidated = capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
        val isMetered = !capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED)

        val connectionType = when {
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> ConnectionType.WIFI
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> ConnectionType.CELLULAR
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> ConnectionType.ETHERNET
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_VPN) -> ConnectionType.VPN
            else -> ConnectionType.OTHER
        }

        return if (hasInternet) {
            NetworkState.Connected(
                hasInternet = hasValidated || hasInternet,
                isMetered = isMetered,
                connectionType = connectionType
            )
        } else {
            NetworkState.Disconnected("Connected to local network but internet access is not available")
        }
    }

    /**
     * Continuous flow of network updates.
     */
    val networkState: Flow<NetworkState> = callbackFlow {
        val cm = connectivityManager
        if (cm == null) {
            trySend(NetworkState.Disconnected("Connectivity service unavailable"))
            close()
            return@callbackFlow
        }

        // Send initial state
        trySend(checkCurrentNetwork())

        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                trySend(checkCurrentNetwork())
            }

            override fun onLost(network: Network) {
                trySend(NetworkState.Disconnected("Network connection lost"))
            }

            override fun onCapabilitiesChanged(network: Network, capabilities: NetworkCapabilities) {
                trySend(checkCurrentNetwork())
            }
        }

        val request = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()

        cm.registerNetworkCallback(request, callback)

        awaitClose {
            cm.unregisterNetworkCallback(callback)
        }
    }.distinctUntilChanged()
}

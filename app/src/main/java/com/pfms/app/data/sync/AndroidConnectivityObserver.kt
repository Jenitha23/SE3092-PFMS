package com.pfms.app.data.sync

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import com.pfms.app.domain.repository.ConnectivityObserver
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.*
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AndroidConnectivityObserver @Inject constructor(@ApplicationContext context: Context) : ConnectivityObserver {
    private val manager = context.getSystemService(ConnectivityManager::class.java)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private fun validated(capabilities: NetworkCapabilities?): Boolean = capabilities?.let {
        it.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
            it.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
    } ?: false
    private fun online() = validated(manager.getNetworkCapabilities(manager.activeNetwork))

    override val isOnline: StateFlow<Boolean> = callbackFlow {
        val lock = Any()
        var currentNetwork: Network? = null
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) { synchronized(lock) {
                currentNetwork = network
                trySend(validated(manager.getNetworkCapabilities(network)))
            } }
            override fun onLost(network: Network) { synchronized(lock) {
                if (currentNetwork == network) {
                    currentNetwork = null
                    // activeNetwork can still reference the lost, validated network in this
                    // callback. Its stale snapshot must not keep the offline indicator online.
                    trySend(false)
                }
            } }
            override fun onCapabilitiesChanged(network: Network, capabilities: NetworkCapabilities) { synchronized(lock) {
                if (currentNetwork == network) trySend(validated(capabilities))
            } }
            override fun onUnavailable() { synchronized(lock) {
                currentNetwork = null
                trySend(false)
            } }
        }
        synchronized(lock) {
            manager.registerDefaultNetworkCallback(callback)
            currentNetwork = manager.activeNetwork
            trySend(online())
        }
        awaitClose { manager.unregisterNetworkCallback(callback) }
    }.distinctUntilChanged().stateIn(scope, SharingStarted.Eagerly, online())
}

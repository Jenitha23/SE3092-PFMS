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
    private fun online(): Boolean = manager.getNetworkCapabilities(manager.activeNetwork)?.let {
        it.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
            it.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
    } ?: false

    override val isOnline: StateFlow<Boolean> = callbackFlow {
        fun report() { trySend(online()) }
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) = report()
            override fun onLost(network: Network) = report()
            override fun onCapabilitiesChanged(network: Network, capabilities: NetworkCapabilities) = report()
            override fun onUnavailable() = report()
        }
        manager.registerDefaultNetworkCallback(callback)
        report()
        awaitClose { manager.unregisterNetworkCallback(callback) }
    }.distinctUntilChanged().stateIn(scope, SharingStarted.Eagerly, online())
}

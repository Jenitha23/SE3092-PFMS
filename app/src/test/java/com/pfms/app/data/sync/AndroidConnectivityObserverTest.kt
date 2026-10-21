package com.pfms.app.data.sync

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.kotlin.*

class AndroidConnectivityObserverTest {
    private val context = mock<Context>()
    private val manager = mock<ConnectivityManager>()
    private val old = mock<Network>()
    private val valid = mock<NetworkCapabilities> {
        on { hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) } doReturn true
        on { hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED) } doReturn true
    }
    private fun fixture(): Pair<AndroidConnectivityObserver, ConnectivityManager.NetworkCallback> {
        whenever(context.getSystemService(ConnectivityManager::class.java)).thenReturn(manager)
        whenever(manager.activeNetwork).thenReturn(old)
        whenever(manager.getNetworkCapabilities(old)).thenReturn(valid)
        val observer = AndroidConnectivityObserver(context)
        val callbacks = argumentCaptor<ConnectivityManager.NetworkCallback>()
        verify(manager, org.mockito.Mockito.timeout(2000)).registerDefaultNetworkCallback(callbacks.capture())
        return observer to callbacks.firstValue
    }

    @Test fun staleManagerSnapshotDoesNotKeepTheIndicatorOnlineAfterNetworkLoss() = runBlocking {
        val (observer, callback) = fixture()
        assertTrue(withTimeout(2000) { observer.isOnline.first { it } })
        // Simulate Android dispatching onLost before activeNetwork/capabilities reflect the loss.
        callback.onLost(old)
        assertFalse(withTimeout(2000) { observer.isOnline.first { !it } })
    }

    @Test fun capabilityCallbacksAreAuthoritativeEvenWhenTheManagerSnapshotIsStale() = runBlocking {
        val (observer, callback) = fixture()
        val unvalidated = mock<NetworkCapabilities> {
            on { hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) } doReturn true
        }
        callback.onCapabilitiesChanged(old, unvalidated)
        assertFalse(withTimeout(2000) { observer.isOnline.first { !it } })
        callback.onCapabilitiesChanged(old, valid)
        assertTrue(withTimeout(2000) { observer.isOnline.first { it } })
    }

    @Test fun lateLossOfAnOldNetworkCannotOverrideAValidatedReplacement() = runBlocking {
        val (observer, callback) = fixture()
        val replacement = mock<Network>()
        whenever(manager.getNetworkCapabilities(replacement)).thenReturn(valid)
        callback.onAvailable(replacement)
        callback.onCapabilitiesChanged(replacement, valid)
        callback.onLost(old)
        assertTrue(withTimeout(2000) { observer.isOnline.first { it } })
        callback.onLost(replacement)
        assertFalse(withTimeout(2000) { observer.isOnline.first { !it } })
    }
}

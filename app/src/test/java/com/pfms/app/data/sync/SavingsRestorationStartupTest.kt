package com.pfms.app.data.sync

import android.util.Log
import com.pfms.app.data.FakeAuthRepository
import com.pfms.app.data.FakeSavingsGoalRepository
import com.pfms.app.domain.model.AuthUser
import com.pfms.app.domain.repository.ConnectivityObserver
import com.pfms.app.domain.repository.SavingsGoalRepository
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test
import org.mockito.Mockito.mockStatic
import org.mockito.kotlin.mock
import java.io.IOException

@OptIn(ExperimentalCoroutinesApi::class)
class SavingsRestorationStartupTest {
    private val user = AuthUser("account-a", "Test", "test@example.com", true)
    private val auth = FakeAuthRepository()
    private val online = MutableStateFlow(false)
    private val connectivity = object : ConnectivityObserver { override val isOnline = online }
    private var calls = 0
    private var fail = false
    private var cancelled = 0
    private var gate: CompletableDeferred<Unit>? = null
    private val repository = object : SavingsGoalRepository by FakeSavingsGoalRepository() {
        override suspend fun refreshRemoteSavingsData(): Result<Unit> {
            calls++
            try { gate?.await() } catch (error: CancellationException) { cancelled++; throw error }
            return if (fail) Result.failure(IOException("Transient connection failure")) else Result.success(Unit)
        }
    }
    private val startup = SyncStartup(mock(), auth, connectivity, repository)

    @Test fun loginReconnectAndAccountSwitchRestoreOncePerSessionTransition() = runTest {
        val observer = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { startup.restoreForSessions {} }
        runCurrent(); assertEquals(0, calls)
        online.value = true; runCurrent(); assertEquals(0, calls)
        auth.userFlow.value = user; runCurrent(); assertEquals(1, calls)
        auth.userFlow.value = user.copy(displayName = "Renamed"); runCurrent(); assertEquals(1, calls)
        online.value = false; runCurrent()
        online.value = true; runCurrent(); assertEquals(2, calls)
        auth.userFlow.value = null; runCurrent()
        auth.userFlow.value = user; runCurrent(); assertEquals(3, calls)
        auth.userFlow.value = user.copy(uid = "account-b"); runCurrent(); assertEquals(4, calls)
        observer.cancel()
    }

    @Test fun transientFailureRetriesWithoutAnotherConnectivityEventAndStopsAfterSuccess() = runTest {
        mockStatic(Log::class.java).use {
            auth.userFlow.value = user; online.value = true; fail = true
            val observer = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { startup.restoreForSessions {} }
            runCurrent(); assertEquals(1, calls)
            advanceTimeBy(1000); runCurrent(); assertEquals(2, calls)
            fail = false
            advanceTimeBy(2000); runCurrent(); assertEquals(3, calls)
            advanceTimeBy(60000); runCurrent(); assertEquals(3, calls)
            observer.cancel()
        }
    }

    @Test fun signOutAndGoingOfflineCancelAnInFlightRestoration() = runTest {
        auth.userFlow.value = user; online.value = true; gate = CompletableDeferred()
        val observer = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { startup.restoreForSessions {} }
        runCurrent(); assertEquals(1, calls)
        auth.userFlow.value = null; runCurrent(); assertEquals(1, cancelled)
        auth.userFlow.value = user; runCurrent(); assertEquals(2, calls)
        online.value = false; runCurrent(); assertEquals(2, cancelled)
        advanceTimeBy(60000); runCurrent(); assertEquals(2, calls)
        observer.cancel()
    }
}

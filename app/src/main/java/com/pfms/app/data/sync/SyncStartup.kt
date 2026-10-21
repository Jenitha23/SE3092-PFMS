package com.pfms.app.data.sync

import android.content.Context
import androidx.work.ExistingWorkPolicy
import com.pfms.app.domain.repository.AuthRepository
import com.pfms.app.domain.repository.ConnectivityObserver
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import javax.inject.Inject
import javax.inject.Singleton

/** Reschedules the existing workers on sign-in/reconnection; WorkManager survives process death. */
@Singleton
class SyncStartup @Inject constructor(
    @ApplicationContext private val context: Context,
    private val auth: AuthRepository,
    private val connectivity: ConnectivityObserver
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var job: Job? = null
    fun start() {
        if (job != null) return
        IncomeSyncWorker.enqueuePeriodic(context)
        ExpenseSyncWorker.enqueuePeriodic(context)
        job = combine(auth.currentUser.map { it?.uid }, connectivity.isOnline) { uid, online -> uid to online }
            .distinctUntilChanged().onEach { (uid, _) ->
                if (!uid.isNullOrBlank()) {
                    IncomeSyncWorker.enqueue(context, ExistingWorkPolicy.APPEND_OR_REPLACE)
                    ExpenseSyncWorker.enqueue(context)
                }
            }.launchIn(scope)
    }
}

package com.pfms.app.data.sync

import android.content.Context
import android.util.Log
import androidx.work.ExistingWorkPolicy
import com.pfms.app.domain.repository.AuthRepository
import com.pfms.app.domain.repository.ConnectivityObserver
import com.pfms.app.domain.repository.SavingsGoalRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/** Restores savings data after sign-in/reconnection and schedules pending-write sync. */
@Singleton
class SyncStartup @Inject constructor(
    @ApplicationContext private val context: Context,
    private val auth: AuthRepository,
    private val connectivity: ConnectivityObserver,
    private val savingsGoalRepository: SavingsGoalRepository
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var job: Job? = null

    fun start() {
        if (job != null) return

        IncomeSyncWorker.enqueuePeriodic(context)
        ExpenseSyncWorker.enqueuePeriodic(context)

        job = scope.launch {
            combine(auth.currentUser.map { it?.uid }, connectivity.isOnline) { uid, online ->
                uid to online
            }
                .distinctUntilChanged()
                .collectLatest { (uid, online) ->
                    if (uid.isNullOrBlank() || !online) return@collectLatest

                    // Keep existing pending-write scheduling behaviour.
                    IncomeSyncWorker.enqueue(context, ExistingWorkPolicy.APPEND_OR_REPLACE)
                    ExpenseSyncWorker.enqueue(context)

                    // Firestore -> Room restoration. The repository skips pending local edits.
                    try {
                        savingsGoalRepository.refreshRemoteSavingsData()
                            .onFailure { error ->
                                Log.w(TAG, "Could not restore savings data; will retry on next online event", error)
                            }
                    } catch (error: CancellationException) {
                        throw error
                    } catch (error: Exception) {
                        Log.w(TAG, "Unexpected savings restoration failure", error)
                    }
                }
        }
    }

    companion object {
        private const val TAG = "PfmsSavingsRestore"
    }
}

package com.pfms.app.data.sync

import android.content.Context
import android.util.Log
import androidx.work.ExistingWorkPolicy
import com.pfms.app.domain.repository.AuthRepository
import com.pfms.app.domain.repository.ConnectivityObserver
import com.pfms.app.domain.repository.SavingsGoalRepository
import com.pfms.app.data.error.OperationErrors
import com.pfms.app.data.error.ErrorAction
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
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.TimeoutCancellationException
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
            restoreForSessions {
                IncomeSyncWorker.enqueue(context, ExistingWorkPolicy.APPEND_OR_REPLACE)
                ExpenseSyncWorker.enqueue(context)
            }
        }
    }

    /** collectLatest cancels retries and in-flight restoration on sign-out/account switch/offline. */
    internal suspend fun restoreForSessions(schedulePendingWrites: () -> Unit) {
        combine(auth.currentUser.map { it?.uid }, connectivity.isOnline) { uid, online ->
            uid to online
        }
            .distinctUntilChanged()
            .collectLatest { (uid, online) ->
                if (uid.isNullOrBlank() || !online) return@collectLatest

                // Keep existing pending-write scheduling behaviour.
                schedulePendingWrites()

                // Firestore -> Room restoration. The repository skips pending local edits.
                var retryDelay = 1_000L
                while (true) {
                    val result = try {
                        withTimeout(25_000L) { savingsGoalRepository.refreshRemoteSavingsData() }
                    } catch (error: TimeoutCancellationException) {
                        Result.failure(error)
                    } catch (error: CancellationException) {
                        throw error
                    } catch (error: Exception) {
                        Result.failure(error)
                    }
                    if (result.isSuccess) break
                    if (!OperationErrors.present(result.exceptionOrNull(), ErrorAction.LOAD).canRetry) {
                        Log.w(TAG, "Savings restoration requires user attention", result.exceptionOrNull())
                        break
                    }
                    Log.w(TAG, "Could not restore savings data; retrying while online", result.exceptionOrNull())
                    delay(retryDelay)
                    retryDelay = (retryDelay * 2).coerceAtMost(30_000L)
                }
            }
    }

    companion object {
        private const val TAG = "PfmsSavingsRestore"
    }
}

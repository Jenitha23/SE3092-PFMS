package com.pfms.app.data.sync

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import androidx.work.BackoffPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.ExistingPeriodicWorkPolicy
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout

/**
 * AndroidX WorkManager [CoroutineWorker] that executes pending local sync for income records
 * and recurring salary templates when network connectivity becomes available.
 */
@HiltWorker
class IncomeSyncWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val syncCoordinator: PendingSyncCoordinator
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        return try {
            val result = withTimeout(60_000) { syncCoordinator.syncAllPending() }
            if (result.isSuccess) {
                Result.success()
            } else {
                Result.retry()
            }
        } catch (e: Exception) {
            if (e is CancellationException && e !is TimeoutCancellationException) throw e
            Result.retry()
        }
    }

    companion object {
        const val UNIQUE_WORK_NAME = "pfms_pending_income_sync"
        fun enqueuePeriodic(context: Context) {
            val work = PeriodicWorkRequestBuilder<IncomeSyncWorker>(15, TimeUnit.MINUTES)
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS).build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork("pfms_income_sync_recovery", ExistingPeriodicWorkPolicy.KEEP, work)
        }

        /**
         * Enqueues a one-time sync worker constrained by network connectivity.
         * Default policy is [ExistingWorkPolicy.KEEP] to prevent duplicate concurrent executions.
         */
        fun enqueue(
            context: Context,
            policy: ExistingWorkPolicy = ExistingWorkPolicy.KEEP
        ) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            val syncWorkRequest = OneTimeWorkRequestBuilder<IncomeSyncWorker>()
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
                .setConstraints(constraints)
                .build()

            WorkManager.getInstance(context).enqueueUniqueWork(
                UNIQUE_WORK_NAME,
                policy,
                syncWorkRequest
            )
        }
    }
}

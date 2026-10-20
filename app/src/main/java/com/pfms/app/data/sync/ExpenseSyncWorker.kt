package com.pfms.app.data.sync

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.*
import com.pfms.app.data.repository.OfflineFirstExpenseRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.util.concurrent.TimeUnit

@HiltWorker
class ExpenseSyncWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val repository: OfflineFirstExpenseRepository
) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result = try {
        if (repository.syncPending()) Result.success() else Result.retry()
    } catch (e: kotlinx.coroutines.CancellationException) { throw e }
    catch (e: Exception) { Result.retry() }

    companion object {
        fun enqueuePeriodic(context: Context) {
            val work = PeriodicWorkRequestBuilder<ExpenseSyncWorker>(15, TimeUnit.MINUTES)
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS).build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork("pfms_expense_sync_recovery", ExistingPeriodicWorkPolicy.KEEP, work)
        }
        fun enqueue(context: Context) {
            val work = OneTimeWorkRequestBuilder<ExpenseSyncWorker>()
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()).build()
            WorkManager.getInstance(context).enqueueUniqueWork("pfms_expense_sync", ExistingWorkPolicy.APPEND_OR_REPLACE, work)
        }
    }
}

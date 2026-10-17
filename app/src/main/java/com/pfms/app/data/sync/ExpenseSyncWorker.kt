package com.pfms.app.data.sync

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.*
import com.pfms.app.data.repository.OfflineFirstExpenseRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

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
        fun enqueue(context: Context) {
            val work = OneTimeWorkRequestBuilder<ExpenseSyncWorker>()
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()).build()
            WorkManager.getInstance(context).enqueueUniqueWork("pfms_expense_sync", ExistingWorkPolicy.APPEND_OR_REPLACE, work)
        }
    }
}

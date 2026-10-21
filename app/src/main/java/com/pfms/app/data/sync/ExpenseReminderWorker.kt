package com.pfms.app.data.sync

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.pfms.app.data.reminder.*
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

/** Reuses the existing class so persisted pre-upgrade work can finish safely. */
@HiltWorker
class ExpenseReminderWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val delivery: ReminderDelivery
) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result = when (delivery.run(
        inputData.getString(ReminderWorkScheduler.USER), inputData.getString(ReminderWorkScheduler.TYPE),
        inputData.getString(ReminderWorkScheduler.REVISION), inputData.getLong(ReminderWorkScheduler.NOT_BEFORE, 0))) {
        ReminderRunResult.COMPLETE -> Result.success()
        ReminderRunResult.RETRY -> Result.retry()
    }
    companion object {
        const val CHANNEL_ID = AndroidReminderNotifier.LEGACY_CHANNEL
        const val EXTRA_DESTINATION_ROUTE = AndroidReminderNotifier.EXTRA_DESTINATION_ROUTE
    }
}

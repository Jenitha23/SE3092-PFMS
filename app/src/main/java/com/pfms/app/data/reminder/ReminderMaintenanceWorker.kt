package com.pfms.app.data.reminder

import android.content.*
import androidx.hilt.work.HiltWorker
import androidx.work.*
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.CancellationException
import java.util.concurrent.TimeUnit

@HiltWorker
class ReminderMaintenanceWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val coordinator: ReminderCoordinator
) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result = try {
        coordinator.reconcileCurrent(inputData.getBoolean(FORCE, false)); Result.success()
    } catch (error: CancellationException) { throw error }
    catch (_: Exception) { Result.retry() }

    companion object {
        private const val FORCE = "reset_reminder_times"
        fun enqueueRecovery(context: Context) {
            WorkManager.getInstance(context).enqueueUniquePeriodicWork("pfms_reminder_recovery", ExistingPeriodicWorkPolicy.KEEP,
                PeriodicWorkRequestBuilder<ReminderMaintenanceWorker>(6, TimeUnit.HOURS).build())
        }
        fun clockChanged(context: Context) {
            WorkManager.getInstance(context).enqueueUniqueWork("pfms_reminder_clock_change", ExistingWorkPolicy.REPLACE,
                OneTimeWorkRequestBuilder<ReminderMaintenanceWorker>().setInputData(workDataOf(FORCE to true)).build())
        }
    }
}

/** WorkManager restores its own jobs on boot; also repair wall-clock/settings changes. */
class ReminderScheduleReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action in setOf(Intent.ACTION_BOOT_COMPLETED, Intent.ACTION_TIME_CHANGED,
                Intent.ACTION_TIMEZONE_CHANGED, Intent.ACTION_MY_PACKAGE_REPLACED)) {
            ReminderMaintenanceWorker.clockChanged(context)
        }
    }
}

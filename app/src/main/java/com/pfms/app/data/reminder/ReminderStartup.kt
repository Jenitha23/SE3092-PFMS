package com.pfms.app.data.reminder

import android.content.Context
import android.util.Log
import com.pfms.app.domain.repository.ReminderSettingsRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ReminderStartup @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val repository: ReminderSettingsRepository,
    private val coordinator: ReminderCoordinator,
    private val notifier: ReminderNotifier
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var job: Job? = null
    fun start() {
        if (job != null) return
        notifier.createChannels()
        ReminderMaintenanceWorker.enqueueRecovery(context)
        job = repository.observeSettings().retryWhen { error, _ ->
            if (error is CancellationException) throw error
            delay(30_000); true
        }.onEach {
            try { coordinator.reconcileCurrent() }
            catch (error: CancellationException) { throw error }
            catch (error: Exception) { Log.w("FinancialReminders", "Schedule recovery will retry", error) }
        }.launchIn(scope)
    }
}

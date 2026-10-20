package com.pfms.app.data.reminder

import com.pfms.app.data.error.repositoryResult
import com.pfms.app.domain.model.*
import com.pfms.app.domain.repository.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
@OptIn(ExperimentalCoroutinesApi::class)
class LocalReminderSettingsRepository @Inject constructor(
    private val auth: AuthRepository,
    private val store: ReminderPreferencesStore,
    private val coordinator: ReminderCoordinator,
    private val notifier: ReminderNotifier
) : ReminderSettingsRepository {
    override fun observeSettings(): Flow<UserReminderSettings?> = auth.currentUser.map { it?.uid?.takeIf(String::isNotBlank) }
        .distinctUntilChanged().flatMapLatest { uid ->
            if (uid == null) flowOf(null) else store.observe(uid).map<UserReminderSettings, UserReminderSettings?> { it }
                .onStart { emit(null) }
        }.flowOn(Dispatchers.IO)
    override fun notificationAccess() = notifier.access()
    override suspend fun update(userId: String, type: ReminderType, enabled: Boolean?, minuteOfDay: Int?, dayOfWeek: Int?) =
        withContext(Dispatchers.IO) { repositoryResult {
            store.locked {
                require(userId.isNotBlank() && auth.currentUser.first()?.uid == userId) { "Account changed" }
                store.update(userId, type, enabled, minuteOfDay, dayOfWeek)
            }
            // Preferences are durable first. Recovery reconciles a scheduler interruption.
            try { coordinator.reconcileCurrent() }
            catch (error: kotlinx.coroutines.CancellationException) { throw error }
            catch (error: Exception) { throw ReminderSchedulingException(error) }
        } }
    override suspend fun reconcile() = withContext(Dispatchers.IO) { repositoryResult { coordinator.reconcileCurrent() } }
}

class ReminderSchedulingException(cause: Throwable) : Exception("Reminder preferences saved; scheduling pending", cause)

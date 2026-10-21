package com.pfms.app.data.reminder

import com.pfms.app.domain.repository.AuthRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ReminderCoordinator @Inject constructor(
    private val auth: AuthRepository,
    private val store: ReminderPreferencesStore,
    private val scheduler: ReminderScheduler
) {
    private val mutex = Mutex()
    suspend fun reconcileCurrent(force: Boolean = false) = mutex.withLock {
        val uid = auth.currentUser.first()?.uid?.takeIf(String::isNotBlank)
        scheduler.reconcile(uid?.let { store.read(it) }, force)
    }
}

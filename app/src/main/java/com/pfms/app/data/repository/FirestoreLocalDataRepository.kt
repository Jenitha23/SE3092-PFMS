package com.pfms.app.data.repository

import android.content.Context
import androidx.work.WorkManager
import com.google.firebase.firestore.FirebaseFirestore
import com.pfms.app.data.auth.BiometricPreferences
import com.pfms.app.data.local.CacheClearFlag
import com.pfms.app.data.local.PfmsDatabase
import com.pfms.app.domain.repository.LocalDataRepository
import com.pfms.app.domain.repository.AuthRepository
import kotlinx.coroutines.flow.first
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject

/** Checks both Room's queued writes and Firestore's pending writes before logout. */
class FirestoreLocalDataRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val firestore: FirebaseFirestore,
    private val biometricPreferences: BiometricPreferences,
    private val database: PfmsDatabase,
    private val authRepository: AuthRepository
) : LocalDataRepository {

    override suspend fun hasUnsynchronizedData(): Boolean {
        val uid = authRepository.currentUser.first()?.uid
        // Keep the shared queue count and main's warning for expenses awaiting confirmation.
        if (uid != null && (database.pendingChangesDao().pendingCount(uid) > 0 ||
                    database.expenseDao().hasPendingOrConflicting(uid))) return true

        // Firestore can have additional pending changes made outside Room.
        val allSynced = withTimeoutOrNull(1_000L) {
            firestore.waitForPendingWrites().await()
            true
        }
        return allSynced != true
    }

    override suspend fun clearLocalData() {
        // This method is called after successful Firebase sign-out by LogoutUseCase.
        // Stop known financial synchronization jobs before clearing the local database.
        val workManager = WorkManager.getInstance(context)
        workManager.cancelUniqueWork("pfms_pending_income_sync")
        workManager.cancelUniqueWork("pfms_expense_sync")

        withContext(Dispatchers.IO) {
            // Includes all seven tables: income, expenses, salary, savings goals,
            // contributions, categories and recurring templates.
            database.clearAllTables()
        }
        biometricPreferences.clearAll()
        CacheClearFlag.request(context)
    }
}

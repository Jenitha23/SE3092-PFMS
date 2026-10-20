package com.pfms.app.data.repository

import android.content.Context
import androidx.work.WorkManager
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.pfms.app.data.auth.BiometricPreferences
import com.pfms.app.data.local.CacheClearFlag
import com.pfms.app.data.local.PfmsDatabase
import com.pfms.app.domain.repository.LocalDataRepository
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
    private val firebaseAuth: FirebaseAuth
) : LocalDataRepository {

    override suspend fun hasUnsynchronizedData(): Boolean {
        val uid = firebaseAuth.currentUser?.uid ?: return false

        // Room is the source of truth for queued local financial mutations.
        val roomPending = database.incomeDao().getPendingSync(uid).isNotEmpty() ||
                database.expenseDao().hasPendingOrConflicting(uid) ||
                database.salaryTemplateDao().getPendingSync(uid).isNotEmpty() ||
                database.savingsGoalDao().getPendingGoals(uid).isNotEmpty() ||
                database.goalTransactionDao().getPendingTransactions(uid).isNotEmpty()
        if (roomPending) return true

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

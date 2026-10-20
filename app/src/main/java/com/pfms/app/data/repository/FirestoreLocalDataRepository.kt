package com.pfms.app.data.repository

import android.content.Context
import com.google.firebase.firestore.FirebaseFirestore
import com.pfms.app.data.auth.BiometricPreferences
import com.pfms.app.data.local.CacheClearFlag
import com.pfms.app.domain.repository.LocalDataRepository
import com.pfms.app.domain.repository.AuthRepository
import com.pfms.app.data.local.PfmsDatabase
import kotlinx.coroutines.flow.first
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject

/**
 * FR-05 without Room: Firestore's own offline persistence is the local store, so
 * "unsynchronised" means "writes still waiting for server acknowledgement".
 */
class FirestoreLocalDataRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val firestore: FirebaseFirestore,
    private val biometricPreferences: BiometricPreferences,
    private val database: PfmsDatabase,
    private val authRepository: AuthRepository
) : LocalDataRepository {

    /**
     * waitForPendingWrites() completes immediately when nothing is queued, and only after the
     * server acknowledges when something is. If it has not completed within the timeout we
     * treat the data as unsynchronised (a slow connection errs on the safe side: one extra warning).
     */
    override suspend fun hasUnsynchronizedData(): Boolean {
        val uid = authRepository.currentUser.first()?.uid
        if (uid != null && database.pendingChangesDao().pendingCount(uid) > 0) return true
        val allSynced = withTimeoutOrNull(PENDING_WRITES_TIMEOUT_MS) {
            firestore.waitForPendingWrites().await()
            true
        }
        return allSynced == null
    }

    override suspend fun clearLocalData() {
        biometricPreferences.clearAll()
        CacheClearFlag.request(context) // cache is wiped at next cold start, see CacheClearFlag
    }

    private companion object {
        const val PENDING_WRITES_TIMEOUT_MS = 1_000L
    }
}

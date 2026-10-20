package com.pfms.app.data.repository

import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.pfms.app.data.firebase.FirestoreCollections
import com.pfms.app.domain.model.UserProfile
import com.pfms.app.domain.repository.AuthRepository
import com.pfms.app.domain.repository.UserRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import javax.inject.Inject

class FirestoreUserRepository @Inject constructor(
    private val firestore: FirebaseFirestore,
    private val authRepository: AuthRepository
) : UserRepository {

    @Inject lateinit var issues: com.pfms.app.data.sync.SyncIssueStore

    /** Real-time listener; switches automatically when the signed-in user changes or signs out. */
    @OptIn(ExperimentalCoroutinesApi::class)
    override fun observeProfile(): Flow<UserProfile?> =
        authRepository.currentUser.flatMapLatest { user ->
            if (user == null) flowOf(null) else profileFlow(user.uid)
        }

    private fun profileFlow(uid: String): Flow<UserProfile?> = callbackFlow {
        val registration = firestore.collection(FirestoreCollections.USERS)
            .document(uid)
            .addSnapshotListener { snapshot, error ->
                // A failed read is not an empty profile; callers render a safe error and Retry.
                if (error != null) {
                    if (::issues.isInitialized) issues.failed(uid, "profile-load", error)
                    close(error)
                } else {
                    if (::issues.isInitialized && snapshot?.metadata?.isFromCache == false) issues.recovered(uid, "profile-load")
                    trySend(snapshot?.toUserProfile())
                }
            }
        awaitClose { registration.remove() }
    }

    override suspend fun updateEstimatedMonthlyIncome(amountMinor: Long?): Result<Unit> = withContext(Dispatchers.IO) {
        val user = authRepository.currentUser.first()
            ?: return@withContext Result.failure(IllegalStateException("No authenticated user session"))

        try {
            require(amountMinor == null || amountMinor >= 0L) { "Estimated income cannot be negative" }
            val data = mapOf("estimatedMonthlyIncomeMinor" to amountMinor, "incomeOnboardingComplete" to true)
            firestore.collection(FirestoreCollections.USERS)
                .document(user.uid)
                .set(data, SetOptions.merge())
                .await()
            Result.success(Unit)
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            Result.failure(e)
        }
    }

    private fun DocumentSnapshot.toUserProfile(): UserProfile? {
        if (!exists()) return null
        return UserProfile(
            uid = id,
            displayName = getString("displayName").orEmpty(),
            email = getString("email").orEmpty(),
            baseCurrency = getString("baseCurrency") ?: "LKR",
            defaultPaymentMethod = getString("defaultPaymentMethod"),
            estimatedMonthlyIncomeMinor = getLong("estimatedMonthlyIncomeMinor"),
            incomeOnboardingComplete = getBoolean("incomeOnboardingComplete") ?: false
        )
    }
}

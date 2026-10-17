package com.pfms.app.data.remote.firestore

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.pfms.app.data.firebase.FirestoreCollections
import com.pfms.app.data.remote.firestore.mapper.toFirestoreMap
import com.pfms.app.data.remote.firestore.mapper.toIncome
import com.pfms.app.domain.model.Income
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FirestoreIncomeRemoteDataSource @Inject constructor(
    private val firestore: FirebaseFirestore,
    private val auth: FirebaseAuth
) : IncomeRemoteDataSource {

    private fun validateAuthenticatedAccess(userId: String) {
        require(userId.isNotBlank()) { "User ID must not be blank" }
        val currentUid = auth.currentUser?.uid
        require(!currentUid.isNullOrBlank()) { "Authentication required: no active user session" }
        require(currentUid == userId) {
            "Access denied: authenticated user '$currentUid' cannot access resources for user '$userId'"
        }
    }

    private fun userIncomeCollection(userId: String) =
        firestore.collection(FirestoreCollections.USERS)
            .document(userId)
            .collection(FirestoreCollections.INCOME)

    override suspend fun getAllIncome(userId: String): List<Income> {
        validateAuthenticatedAccess(userId)

        val snapshot = userIncomeCollection(userId).get().await()
        return snapshot.documents.mapNotNull { it.toIncome() }
    }

    override suspend fun getIncomeById(userId: String, incomeId: String): Income? {
        validateAuthenticatedAccess(userId)
        require(incomeId.isNotBlank()) { "Income ID must not be blank" }

        val snapshot = userIncomeCollection(userId).document(incomeId).get().await()
        return snapshot.toIncome()
    }

    override suspend fun upsertIncome(userId: String, income: Income) {
        validateAuthenticatedAccess(userId)
        require(income.id.isNotBlank()) { "Income ID must not be blank" }

        require(income.userId == userId) {
            "Cross-user write prevented: path userId '$userId' does not match income.userId '${income.userId}'"
        }

        userIncomeCollection(userId)
            .document(income.id)
            .set(income.toFirestoreMap(), SetOptions.merge())
            .await()
    }

    override suspend fun deleteIncome(userId: String, incomeId: String) {
        validateAuthenticatedAccess(userId)
        require(incomeId.isNotBlank()) { "Income ID must not be blank" }

        userIncomeCollection(userId)
            .document(incomeId)
            .delete()
            .await()
    }

    override fun observeIncome(userId: String): Flow<List<Income>> = observeSnapshots(userId).map { it.rows }

    override fun observeSnapshots(userId: String): Flow<RemoteRecords<Income>> = callbackFlow {
        validateAuthenticatedAccess(userId)

        val registration = userIncomeCollection(userId)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    close(error)
                    return@addSnapshotListener
                }
                val list = snapshot?.documents?.mapNotNull { it.toIncome() }.orEmpty()
                trySend(RemoteRecords(list, snapshot != null && !snapshot.metadata.isFromCache && !snapshot.metadata.hasPendingWrites()))
            }

        awaitClose { registration.remove() }
    }
}

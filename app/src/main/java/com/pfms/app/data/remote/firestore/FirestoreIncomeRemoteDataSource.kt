package com.pfms.app.data.remote.firestore

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.google.firebase.firestore.MetadataChanges
import com.pfms.app.data.sync.*
import java.util.UUID
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
        return snapshot.documents.filter { it.getBoolean("isDeleted") != true }.mapNotNull { it.toIncome() }
    }

    override suspend fun getIncomeById(userId: String, incomeId: String): Income? {
        validateAuthenticatedAccess(userId)
        require(incomeId.isNotBlank()) { "Income ID must not be blank" }

        val snapshot = userIncomeCollection(userId).document(incomeId).get().await()
        return snapshot.takeUnless { it.getBoolean("isDeleted") == true }?.toIncome()
    }

    override suspend fun upsertIncome(userId: String, income: Income) {
        validateAuthenticatedAccess(userId)
        require(income.id.isNotBlank()) { "Income ID must not be blank" }

        require(income.userId == userId) {
            "Cross-user write prevented: path userId '$userId' does not match income.userId '${income.userId}'"
        }

        synchronize(userId, income, SyncVersion(income.updatedAt))
    }

    override suspend fun deleteIncome(userId: String, incomeId: String) {
        validateAuthenticatedAccess(userId)
        require(incomeId.isNotBlank()) { "Income ID must not be blank" }

        synchronize(userId, Income(id = incomeId, userId = userId),
            SyncVersion(System.currentTimeMillis(), UUID.randomUUID().toString(), deleted = true))
    }

    override suspend fun synchronize(userId: String, income: Income, version: SyncVersion): FinancialSyncResult<Income> {
        validateAuthenticatedAccess(userId)
        require(income.id.isNotBlank() && income.userId == userId) { "Cross-user write prevented" }
        val ref = userIncomeCollection(userId).document(income.id)
        return firestore.runTransaction { transaction ->
            validateAuthenticatedAccess(userId)
            val existing = transaction.get(ref)
            val previous = existing.syncVersion()
            if (existing.exists() && previous >= version) {
                FinancialSyncResult(if (previous.deleted) null else checkNotNull(existing.toIncome()), previous)
            } else {
                val fields = if (version.deleted) mapOf("id" to income.id, "userId" to userId)
                    else income.toFirestoreMap()
                transaction.set(ref, fields + mapOf("updatedAt" to version.updatedAt,
                    "syncRevision" to version.revision, "isDeleted" to version.deleted))
                FinancialSyncResult(income.takeUnless { version.deleted }, version)
            }
        }.await()
    }

    override fun observeIncome(userId: String): Flow<List<Income>> = observeSnapshots(userId).map { it.rows }

    override fun observeSnapshots(userId: String): Flow<RemoteRecords<Income>> = callbackFlow {
        validateAuthenticatedAccess(userId)

        val registration = userIncomeCollection(userId)
            .addSnapshotListener(MetadataChanges.INCLUDE) { snapshot, error ->
                if (error != null) {
                    close(error)
                    return@addSnapshotListener
                }
                val documents = snapshot?.documents.orEmpty()
                val list = documents.filter { it.getBoolean("isDeleted") != true }.mapNotNull { it.toIncome() }
                trySend(RemoteRecords(list, snapshot != null && !snapshot.metadata.isFromCache && !snapshot.metadata.hasPendingWrites(),
                    versions = documents.associate { it.id to it.syncVersion() },
                    deletions = documents.filter { it.getBoolean("isDeleted") == true }.map { RemoteDeletion(it.id, it.syncVersion()) }))
            }

        awaitClose { registration.remove() }
    }
}

package com.pfms.app.data.repository

import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.pfms.app.data.firebase.FirestoreCollections
import com.pfms.app.domain.model.Income
import com.pfms.app.domain.repository.AuthRepository
import com.pfms.app.domain.repository.IncomeRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.tasks.await
import javax.inject.Inject

/** User-scoped Firestore income storage. Firestore offline caching is enabled by default. */
class FirestoreIncomeRepository @Inject constructor(
    private val firestore: FirebaseFirestore,
    private val authRepository: AuthRepository
) : IncomeRepository {
    private fun collection(uid: String) = firestore
        .collection(FirestoreCollections.USERS)
        .document(uid)
        .collection(FirestoreCollections.INCOME)

    private suspend fun uid(): String =
        authRepository.currentUser.first()?.uid ?: error("Sign in to manage income")

    @OptIn(ExperimentalCoroutinesApi::class)
    override fun observeIncome(): Flow<List<Income>> = authRepository.currentUser
        .flatMapLatest { user ->
            if (user == null) flowOf(emptyList())
            else callbackFlow {
                val listener = collection(user.uid).addSnapshotListener { snapshot, error ->
                    if (error != null) {
                        close(error)
                    } else {
                        trySend(snapshot?.documents.orEmpty().map { it.asIncome(user.uid) })
                    }
                }
                awaitClose { listener.remove() }
            }
        }

    override suspend fun addIncome(income: Income) {
        val uid = uid()
        require(income.userId == uid) { "Income belongs to another user" }
        require(income.id.isNotBlank()) { "Income ID is required" }
        collection(uid).document(income.id).set(income.toFirestoreMap()).await()
    }

    override suspend fun updateIncome(income: Income) {
        val uid = uid()
        require(income.userId == uid) { "Income belongs to another user" }
        require(income.id.isNotBlank()) { "Income ID is required" }
        // Update only an existing document; do not re-create a concurrently deleted record.
        val doc = collection(uid).document(income.id)
        firestore.runTransaction { transaction ->
            check(transaction.get(doc).exists()) { "Income not found" }
            transaction.update(doc, income.toFirestoreMap())
        }.await()
    }

    override suspend fun deleteIncome(incomeId: String) {
        require(incomeId.isNotBlank())
        collection(uid()).document(incomeId).delete().await()
    }

    override suspend fun getIncomeById(incomeId: String): Income? {
        val uid = uid()
        return collection(uid).document(incomeId).get().await()
            .takeIf { it.exists() }?.asIncome(uid)
    }

    private fun Income.toFirestoreMap(): Map<String, Any?> = mapOf(
        "userId" to userId, "amountMinor" to amountMinor, "currency" to currency,
        "source" to source, "receivedDate" to receivedDate, "note" to note,
        "amountLkrMinor" to amountLkrMinor, "exchangeRate" to exchangeRate,
        "exchangeRateDate" to exchangeRateDate, "paymentStatus" to paymentStatus,
        "projectName" to projectName, "cryptoType" to cryptoType,
        "cryptoAsset" to cryptoAsset, "createdAt" to createdAt, "updatedAt" to updatedAt
    )

    private fun DocumentSnapshot.asIncome(uid: String) = Income(
        id = id,
        userId = uid, // Never trust an embedded userId from document data.
        amountMinor = getLong("amountMinor") ?: 0L,
        currency = getString("currency") ?: "LKR",
        source = getString("source") ?: "Other",
        receivedDate = getString("receivedDate").orEmpty(),
        note = getString("note").orEmpty(),
        amountLkrMinor = getLong("amountLkrMinor"),
        exchangeRate = getString("exchangeRate"),
        exchangeRateDate = getString("exchangeRateDate"),
        paymentStatus = getString("paymentStatus") ?: "Received",
        projectName = getString("projectName"),
        cryptoType = getString("cryptoType"),
        cryptoAsset = getString("cryptoAsset"),
        createdAt = getLong("createdAt") ?: 0L,
        updatedAt = getLong("updatedAt") ?: 0L
    )
}

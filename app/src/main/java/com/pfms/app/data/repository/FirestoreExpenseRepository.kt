package com.pfms.app.data.repository

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.pfms.app.data.firebase.FirestoreCollections
import com.pfms.app.data.firebase.userIds
import com.pfms.app.domain.model.Expense
import com.pfms.app.domain.model.ExpenseClassification
import com.pfms.app.domain.model.ExpensePaymentMethod
import com.pfms.app.domain.repository.ExpenseRepository
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Legacy direct Firestore repository.
 *
 * NOTE: Production binds [OfflineFirstExpenseRepository] in [com.pfms.app.di.RepositoryModule],
 * which uses the shared Room [com.pfms.app.data.local.PfmsDatabase], [com.pfms.app.data.local.dao.ExpenseDao],
 * [com.pfms.app.data.local.entity.ExpenseEntity], and [com.pfms.app.data.sync.ExpenseSyncWorker]
 * for full offline-first caching, local SQLite transactions, and WorkManager background sync.
 */
@Deprecated("Use OfflineFirstExpenseRepository for offline-first Room persistence and sync")
@Singleton
@OptIn(ExperimentalCoroutinesApi::class)
class FirestoreExpenseRepository @Inject constructor(
    private val auth: FirebaseAuth,
    private val firestore: FirebaseFirestore
) : ExpenseRepository {

    private fun uid(): String = auth.currentUser?.uid ?: error("User must be authenticated")

    private fun expensesRef(userId: String = uid()) = firestore
        .collection(FirestoreCollections.USERS)
        .document(userId)
        .collection(FirestoreCollections.EXPENSES)

    private fun DocumentSnapshot.asExpense(currentUid: String): Expense = Expense(
        id = id,
        userId = currentUid,
        amountMinor = getLong("amountMinor") ?: 0L,
        currency = getString("currency") ?: "LKR",
        categoryId = getString("categoryId").orEmpty(),
        categoryName = getString("categoryName").orEmpty(),
        classification = enumOr(getString("classification"), ExpenseClassification.DISCRETIONARY),
        paymentMethod = enumOr(getString("paymentMethod"), ExpensePaymentMethod.CASH),
        date = getString("date").orEmpty(),
        merchant = getString("merchant").orEmpty(),
        note = getString("note").orEmpty(),
        createdAt = getLong("createdAt") ?: 0L,
        updatedAt = getLong("updatedAt") ?: 0L
    )

    private inline fun <reified T : Enum<T>> enumOr(value: String?, fallback: T): T =
        enumValues<T>().firstOrNull { it.name.equals(value, ignoreCase = true) } ?: fallback

    override fun observeExpenses(): Flow<List<Expense>> = auth.userIds().flatMapLatest { currentUid ->
        if (currentUid == null) flowOf(emptyList()) else observeUserExpenses(currentUid)
            .onStart { emit(emptyList()) }
    }

    private fun observeUserExpenses(currentUid: String): Flow<List<Expense>> = callbackFlow {
        val listener = expensesRef(currentUid)
            .orderBy("date", Query.Direction.DESCENDING)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    close(error)
                    return@addSnapshotListener
                }
                try {
                    val list = snapshot?.documents?.map { it.asExpense(currentUid) }.orEmpty()
                    trySend(list)
                } catch (e: Exception) {
                    // Firebase invokes listeners outside the collecting coroutine.
                    // Route malformed-data errors through the UI's retry/error path.
                    close(e)
                }
            }

        awaitClose { listener.remove() }
    }

    override suspend fun getExpenseById(expenseId: String): Expense? {
        val currentUid = uid()
        val doc = expensesRef().document(expenseId).get().await()
        return if (doc.exists()) doc.asExpense(currentUid) else null
    }

    override suspend fun addExpense(expense: Expense) {
        val currentUid = uid()
        val expenseId = expense.id.ifBlank { java.util.UUID.randomUUID().toString() }
        val now = if (expense.createdAt > 0L) expense.createdAt else System.currentTimeMillis()

        val data = mapOf(
            "id" to expenseId,
            "userId" to currentUid,
            "amountMinor" to expense.amountMinor,
            "currency" to expense.currency,
            "categoryId" to expense.categoryId,
            "categoryName" to expense.categoryName,
            "classification" to expense.classification.name,
            "paymentMethod" to expense.paymentMethod.name,
            "date" to expense.date,
            "merchant" to expense.merchant,
            "note" to expense.note,
            "createdAt" to now,
            "updatedAt" to (if (expense.updatedAt > 0L) expense.updatedAt else now)
        )

        expensesRef().document(expenseId).set(data).await()
    }

    override suspend fun updateExpense(expense: Expense) {
        val currentUid = uid()
        require(expense.id.isNotBlank()) { "Expense ID cannot be blank" }
        val now = System.currentTimeMillis()

        val data = mapOf(
            "id" to expense.id,
            "userId" to currentUid,
            "amountMinor" to expense.amountMinor,
            "currency" to expense.currency,
            "categoryId" to expense.categoryId,
            "categoryName" to expense.categoryName,
            "classification" to expense.classification.name,
            "paymentMethod" to expense.paymentMethod.name,
            "date" to expense.date,
            "merchant" to expense.merchant,
            "note" to expense.note,
            "updatedAt" to now
        )

        expensesRef().document(expense.id).update(data).await()
    }

    override suspend fun deleteExpense(expenseId: String) {
        require(expenseId.isNotBlank()) { "Expense ID cannot be blank" }
        expensesRef().document(expenseId).delete().await()
    }

    override suspend fun getExpensesCreatedSince(timestampMillis: Long): List<Expense> {
        val currentUid = uid()
        val snapshot = expensesRef()
            .whereGreaterThanOrEqualTo("createdAt", timestampMillis)
            .get()
            .await()
        return snapshot.documents.map { it.asExpense(currentUid) }
    }
}

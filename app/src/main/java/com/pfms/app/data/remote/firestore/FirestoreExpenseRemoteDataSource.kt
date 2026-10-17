package com.pfms.app.data.remote.firestore

import com.google.firebase.firestore.FirebaseFirestore
import com.pfms.app.domain.model.*
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import java.security.MessageDigest
import javax.inject.Inject

class FirestoreExpenseRemoteDataSource @Inject constructor(private val firestore: FirebaseFirestore) : ExpenseRemoteDataSource {
    private fun collection(uid: String) = firestore.collection("users").document(uid).collection("expenses")

    override fun observe(userId: String) = callbackFlow {
        val listener = collection(userId).addSnapshotListener { snapshot, error ->
            if (error != null) close(error)
            else if (snapshot != null) try {
                val rows = snapshot.documents.filter { it.getBoolean("internalDuplicateGuard") != true }.map { doc ->
                    fun classification() = ExpenseClassification.entries.firstOrNull { it.name.equals(doc.getString("classification"), true) } ?: ExpenseClassification.DISCRETIONARY
                    fun payment() = ExpensePaymentMethod.entries.firstOrNull { it.name.equals(doc.getString("paymentMethod"), true) } ?: ExpensePaymentMethod.CASH
                    Expense(doc.id, userId, doc.getLong("amountMinor") ?: 0L, doc.getString("currency") ?: "LKR",
                        doc.getString("categoryId").orEmpty(), doc.getString("categoryName").orEmpty(), classification(), payment(),
                        doc.getString("date").orEmpty(), doc.getString("merchant").orEmpty(), doc.getString("note").orEmpty(),
                        doc.getLong("createdAt") ?: 0L, doc.getLong("updatedAt") ?: 0L)
                }
                trySend(ExpenseRemoteSnapshot(rows, !snapshot.metadata.isFromCache && !snapshot.metadata.hasPendingWrites()))
            } catch (e: Exception) { close(e) }
        }
        awaitClose { listener.remove() }
    }

    override suspend fun insert(userId: String, expense: Expense, allowDuplicate: Boolean) {
        val ref = collection(userId).document(expense.id)
        val key = fingerprint(expense)
        val guard = firestore.collection("users").document(userId).collection("expenseDuplicateGuards").document(key)
        firestore.runTransaction { tx ->
            val existing = tx.get(ref)
            val previous = tx.get(guard)
            if (!existing.exists()) {
                val previousTime = previous.getLong("createdAt")
                val age = previousTime?.let { kotlin.math.abs(expense.createdAt - it) }
                if (!allowDuplicate && previous.exists() && age != null && age <= 60_000L) throw DuplicateExpenseException()
                tx.set(ref, data(expense))
                tx.set(guard, mapOf("internalDuplicateGuard" to true, "createdAt" to expense.createdAt,
                    "expenseId" to expense.id, "userId" to userId))
            } else if ((existing.getLong("updatedAt") ?: 0L) <= expense.updatedAt) {
                // A local edit can follow an insert before the insert was acknowledged.
                tx.set(ref, data(expense))
            }
        }.await()
    }

    override suspend fun update(userId: String, expense: Expense) { collection(userId).document(expense.id).set(data(expense)).await() }
    override suspend fun delete(userId: String, id: String) { collection(userId).document(id).delete().await() }

    private fun data(e: Expense): Map<String, Any> = mapOf("id" to e.id, "userId" to e.userId,
        "amountMinor" to e.amountMinor, "currency" to e.currency, "categoryId" to e.categoryId,
        "categoryName" to e.categoryName, "classification" to e.classification.name,
        "paymentMethod" to e.paymentMethod.name, "date" to e.date, "merchant" to e.merchant,
        "note" to e.note, "createdAt" to e.createdAt, "updatedAt" to e.updatedAt)

    companion object {
        fun fingerprint(e: Expense): String {
            val fields = listOf(e.amountMinor.toString(), e.currency, e.categoryId, e.classification.name,
                e.paymentMethod.name, e.date, e.merchant.trim(), e.note.trim())
            val text = fields.joinToString("") { "${it.length}:$it" }
            return MessageDigest.getInstance("SHA-256").digest(text.toByteArray(Charsets.UTF_8))
                .joinToString("") { "%02x".format(it) }
        }
    }
}

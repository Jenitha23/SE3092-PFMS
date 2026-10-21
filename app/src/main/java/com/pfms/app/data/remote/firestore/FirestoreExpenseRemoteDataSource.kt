package com.pfms.app.data.remote.firestore

import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.MetadataChanges
import com.google.firebase.auth.FirebaseAuth
import com.pfms.app.data.sync.*
import java.util.UUID
import com.pfms.app.domain.model.*
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import java.security.MessageDigest
import javax.inject.Inject

class FirestoreExpenseRemoteDataSource @Inject constructor(private val firestore: FirebaseFirestore, private val auth: FirebaseAuth) : ExpenseRemoteDataSource {
    private fun validate(uid: String) {
        require(uid.isNotBlank() && auth.currentUser?.uid == uid) { "Authentication required: cross-user access prevented" }
    }
    private fun collection(uid: String) = firestore.collection("users").document(uid).collection("expenses")

    override fun observe(userId: String) = callbackFlow {
        validate(userId)
        val listener = collection(userId).addSnapshotListener(MetadataChanges.INCLUDE) { snapshot, error ->
            if (error != null) close(error)
            else if (snapshot != null) try {
                val documents = snapshot.documents.filter { it.getBoolean("internalDuplicateGuard") != true }
                val rows = documents.filter { it.getBoolean("isDeleted") != true }.map { decode(it, userId) }
                trySend(ExpenseRemoteSnapshot(rows, !snapshot.metadata.isFromCache && !snapshot.metadata.hasPendingWrites(),
                    versions = documents.associate { it.id to it.syncVersion() },
                    deletions = documents.filter { it.getBoolean("isDeleted") == true }.map { RemoteDeletion(it.id, it.syncVersion()) }))
            } catch (e: Exception) { close(e) }
        }
        awaitClose { listener.remove() }
    }

    override suspend fun insert(userId: String, expense: Expense, allowDuplicate: Boolean) {
        synchronize(userId, expense, SyncVersion(expense.updatedAt), insert = true, allowDuplicate = allowDuplicate)
    }

    override suspend fun synchronize(userId: String, expense: Expense, version: SyncVersion, insert: Boolean, allowDuplicate: Boolean): FinancialSyncResult<Expense> {
        validate(userId)
        require(expense.userId == userId && expense.id.isNotBlank()) { "Cross-user write prevented" }
        val ref = collection(userId).document(expense.id)
        val key = fingerprint(expense)
        val guard = firestore.collection("users").document(userId).collection("expenseDuplicateGuards").document(key)
        return firestore.runTransaction { tx ->
            validate(userId)
            val existing = tx.get(ref)
            val previousVersion = existing.syncVersion()
            val previous = if (insert && !version.deleted && !existing.exists()) tx.get(guard) else null
            if (existing.exists() && previousVersion >= version) {
                FinancialSyncResult(if (previousVersion.deleted) null else decode(existing, userId), previousVersion)
            } else {
                if (previous != null) {
                    val previousTime = previous.getLong("createdAt")
                    val age = previousTime?.let { kotlin.math.abs(expense.createdAt - it) }
                    if (!allowDuplicate && previous.exists() && age != null && age <= 60_000L) throw DuplicateExpenseException()
                    tx.set(guard, mapOf("internalDuplicateGuard" to true, "createdAt" to expense.createdAt,
                        "expenseId" to expense.id, "userId" to userId))
                }
                val fields = if (version.deleted) mapOf("id" to expense.id, "userId" to userId) else data(expense)
                tx.set(ref, fields + mapOf("updatedAt" to version.updatedAt, "syncRevision" to version.revision, "isDeleted" to version.deleted))
                FinancialSyncResult(expense.takeUnless { version.deleted }, version)
            }
        }.await()
    }

    override suspend fun update(userId: String, expense: Expense) {
        synchronize(userId, expense, SyncVersion(expense.updatedAt), insert = false, allowDuplicate = true)
    }
    override suspend fun delete(userId: String, id: String) {
        synchronize(userId, Expense(id = id, userId = userId),
            SyncVersion(System.currentTimeMillis(), UUID.randomUUID().toString(), deleted = true), insert = false, allowDuplicate = true)
    }

    private fun decode(doc: DocumentSnapshot, userId: String) = Expense(
        id = doc.id, userId = userId, amountMinor = doc.getLong("amountMinor") ?: 0L,
        currency = doc.getString("currency") ?: "LKR", categoryId = doc.getString("categoryId").orEmpty(),
        categoryName = doc.getString("categoryName").orEmpty(),
        classification = ExpenseClassification.entries.firstOrNull { it.name.equals(doc.getString("classification"), true) } ?: ExpenseClassification.DISCRETIONARY,
        paymentMethod = ExpensePaymentMethod.entries.firstOrNull { it.name.equals(doc.getString("paymentMethod"), true) } ?: ExpensePaymentMethod.CASH,
        date = doc.getString("date").orEmpty(), merchant = doc.getString("merchant").orEmpty(), note = doc.getString("note").orEmpty(),
        createdAt = doc.getLong("createdAt") ?: 0L, updatedAt = doc.syncVersion().updatedAt)

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

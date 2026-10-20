package com.pfms.app.data.remote.firestore

import com.pfms.app.domain.model.Expense
import com.pfms.app.data.sync.SyncVersion
import com.pfms.app.data.sync.RemoteDeletion
import com.pfms.app.data.sync.FinancialSyncResult
import kotlinx.coroutines.flow.Flow

data class ExpenseRemoteSnapshot(val rows: List<Expense>, val authoritative: Boolean,
    val versions: Map<String, SyncVersion> = emptyMap(), val deletions: List<RemoteDeletion> = emptyList())

interface ExpenseRemoteDataSource {
    suspend fun synchronize(userId: String, expense: Expense, version: SyncVersion, insert: Boolean, allowDuplicate: Boolean): FinancialSyncResult<Expense> {
        when {
            version.deleted -> delete(userId, expense.id)
            insert -> insert(userId, expense, allowDuplicate)
            else -> update(userId, expense)
        }
        return FinancialSyncResult(expense.takeUnless { version.deleted }, version)
    }

    fun observe(userId: String): Flow<ExpenseRemoteSnapshot>
    suspend fun insert(userId: String, expense: Expense, allowDuplicate: Boolean)
    suspend fun update(userId: String, expense: Expense)
    suspend fun delete(userId: String, id: String)
}

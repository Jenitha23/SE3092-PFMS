package com.pfms.app.data.remote.firestore

import com.pfms.app.domain.model.Expense
import kotlinx.coroutines.flow.Flow

data class ExpenseRemoteSnapshot(val rows: List<Expense>, val authoritative: Boolean)

interface ExpenseRemoteDataSource {
    fun observe(userId: String): Flow<ExpenseRemoteSnapshot>
    suspend fun insert(userId: String, expense: Expense, allowDuplicate: Boolean)
    suspend fun update(userId: String, expense: Expense)
    suspend fun delete(userId: String, id: String)
}

package com.pfms.app.data.remote.firestore

import com.pfms.app.domain.model.Income
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

interface IncomeRemoteDataSource {
    fun observeSnapshots(userId: String): Flow<RemoteRecords<Income>> = observeIncome(userId).map { RemoteRecords(it, false) }
    suspend fun getAllIncome(userId: String): List<Income>
    suspend fun getIncomeById(userId: String, incomeId: String): Income?
    suspend fun upsertIncome(userId: String, income: Income)
    suspend fun deleteIncome(userId: String, incomeId: String)
    fun observeIncome(userId: String): Flow<List<Income>>
}


package com.pfms.app.domain.repository

import com.pfms.app.domain.model.Income
import kotlinx.coroutines.flow.Flow

interface IncomeRepository {

    // Get signed-in user's income records.
    fun observeIncome(): Flow<List<Income>>

    // Add a new income record.
    suspend fun addIncome(income: Income)

    // Update an existing income record.
    suspend fun updateIncome(income: Income)

    // Delete an income record by ID.
    suspend fun deleteIncome(incomeId: String)

    // Retrieve one income record for editing.
    suspend fun getIncomeById(incomeId: String): Income?
}


package com.pfms.app.domain.repository

import com.pfms.app.domain.model.Expense
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import com.pfms.app.domain.model.ExpensePaymentMethod

interface ExpenseRepository {
    fun observeDuplicateConflict(): Flow<Expense?> = flowOf(null)
    fun observeLastPaymentMethod(): Flow<ExpensePaymentMethod> = flowOf(ExpensePaymentMethod.CASH)
    suspend fun addExpenseChecked(expense: Expense, allowDuplicate: Boolean = false) = addExpense(expense)
    suspend fun dismissDuplicate(expenseId: String) = Unit

    // Observe the signed-in user's expenses.
    fun observeExpenses(): Flow<List<Expense>>

    // Retrieve an expense for editing.
    suspend fun getExpenseById(
        expenseId: String
    ): Expense?

    // Create a new expense record.
    suspend fun addExpense(
        expense: Expense
    )

    // Update an existing expense.
    suspend fun updateExpense(
        expense: Expense
    )

    // Delete an expense by ID.
    suspend fun deleteExpense(
        expenseId: String
    )

    // Find recently created expenses to support
    // FR-22 duplicate detection.
    suspend fun getExpensesCreatedSince(
        timestampMillis: Long
    ): List<Expense>
}

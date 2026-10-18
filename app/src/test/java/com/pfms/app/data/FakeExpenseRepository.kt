package com.pfms.app.data

import com.pfms.app.domain.model.Expense
import com.pfms.app.domain.repository.ExpenseRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

import com.pfms.app.domain.model.ExpensePaymentMethod

class FakeExpenseRepository : ExpenseRepository {

    private val expenses =
        MutableStateFlow<List<Expense>>(emptyList())

    private val lastPaymentMethodFlow =
        MutableStateFlow(ExpensePaymentMethod.CASH)

    var addCallCount = 0
        private set

    fun setLastPaymentMethod(method: ExpensePaymentMethod) {
        lastPaymentMethodFlow.value = method
    }

    override fun observeLastPaymentMethod(): Flow<ExpensePaymentMethod> =
        lastPaymentMethodFlow.asStateFlow()

    override fun observeExpenses(): Flow<List<Expense>> =
        expenses.asStateFlow()

    override suspend fun getExpenseById(
        expenseId: String
    ): Expense? =
        expenses.value.firstOrNull { it.id == expenseId }

    override suspend fun addExpense(expense: Expense) {
        addCallCount++
        lastPaymentMethodFlow.value = expense.paymentMethod

        expenses.value =
            expenses.value + expense
    }

    override suspend fun addExpenseChecked(expense: Expense, allowDuplicate: Boolean) {
        addExpense(expense)
    }

    override suspend fun updateExpense(expense: Expense) {
        expenses.value = expenses.value.map {
            if (it.id == expense.id) expense else it
        }
    }

    override suspend fun deleteExpense(expenseId: String) {
        expenses.value =
            expenses.value.filterNot {
                it.id == expenseId
            }
    }

    override suspend fun getExpensesCreatedSince(
        timestampMillis: Long
    ): List<Expense> =
        expenses.value.filter {
            it.createdAt >= timestampMillis
        }
}
package com.pfms.app.viewmodel

import com.pfms.app.data.*
import com.pfms.app.domain.model.*
import com.pfms.app.domain.repository.*
import com.pfms.app.utils.MainDispatcherRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.mockito.kotlin.mock
import java.io.IOException

@OptIn(ExperimentalCoroutinesApi::class)
class BackdatedEntryViewModelTest {
    @get:Rule val main = MainDispatcherRule()

    @Test fun previousMonthForeignIncomeUsesSelectedDateAndManualRateThroughRetry() = runTest {
        val attempts = mutableListOf<Income>()
        val repository = object : IncomeRepository by FakeIncomeRepository() {
            override suspend fun addIncome(income: Income) {
                attempts += income
                if (attempts.size == 1) throw IOException("Offline")
            }
        }
        val vm = main.track(IncomeViewModel(repository,
            FakeAuthRepository(AuthUser("a", "A", "a@example.test", true)), mock()))
        runCurrent()
        assertTrue(vm.setManualExchangeRate("300.25", "2026-09-15"))
        vm.addIncome("100.00", "USD", "Salary", "2026-09-15", "Historical income")
        runCurrent(); assertTrue(vm.uiState.value.canRetry)
        vm.retry(); runCurrent()
        assertEquals(2, attempts.size)
        val row = attempts.last()
        assertEquals(attempts.first().id, row.id)
        assertEquals("2026-09-15", row.receivedDate)
        assertEquals("2026-09-15", row.exchangeRateDate)
        assertEquals("300.25", row.exchangeRate)
        assertEquals(3002500L, row.amountLkrMinor)
        assertTrue(vm.uiState.value.saveSuccessful)
    }

    @Test fun previousYearExpenseKeepsSelectedDateAndStableIdAfterFailedSave() = runTest {
        val attempts = mutableListOf<Expense>()
        val repository = object : ExpenseRepository by FakeExpenseRepository() {
            override suspend fun addExpenseChecked(expense: Expense, allowDuplicate: Boolean) {
                attempts += expense
                if (attempts.size == 1) throw IOException("Offline")
            }
        }
        val vm = main.track(ExpenseViewModel(repository))
        runCurrent()
        vm.addExpense(ExpenseFormData("250.00", "food", "Food", ExpenseClassification.DISCRETIONARY,
            null, ExpensePaymentMethod.CASH, "2025-09-20", "Cafe", "Historical expense"))
        runCurrent(); assertTrue(vm.uiState.value.canRetry)
        vm.retry(); runCurrent()
        assertEquals(2, attempts.size)
        assertEquals(attempts.first().id, attempts.last().id)
        assertEquals("2025-09-20", attempts.last().date)
        assertEquals(25000L, attempts.last().amountMinor)
        assertTrue(vm.uiState.value.operationSuccessful)
    }
}

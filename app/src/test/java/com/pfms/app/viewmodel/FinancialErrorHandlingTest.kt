package com.pfms.app.viewmodel

import com.pfms.app.data.*
import com.pfms.app.data.exchange.ExchangeRateService
import com.pfms.app.domain.model.*
import com.pfms.app.domain.repository.*
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.test.*
import org.junit.*
import org.junit.Assert.*
import org.mockito.kotlin.*
import java.io.IOException

@OptIn(ExperimentalCoroutinesApi::class)
class FinancialErrorHandlingTest {
    private val dispatcher = StandardTestDispatcher()
    @Before fun setup() = Dispatchers.setMain(dispatcher)
    @After fun teardown() = Dispatchers.resetMain()
    private val auth = FakeAuthRepository(AuthUser("a", "A", "a@example.test", true))

    @Test fun incomeRetryKeepsAllFieldsRateAndIdAfterLostAcknowledgement() = runTest(dispatcher) {
        val saved = linkedMapOf<String, Income>()
        val attempts = mutableListOf<Income>()
        val repository = object : IncomeRepository by FakeIncomeRepository() {
            override suspend fun addIncome(income: Income) {
                attempts += income
                saved[income.id] = income
                if (attempts.size == 1) throw IOException("Firebase internal endpoint stack")
            }
        }
        val vm = IncomeViewModel(repository, auth, mock<ExchangeRateService>())
        runCurrent()
        assertTrue(vm.setManualExchangeRate("300.25", "2026-10-01"))
        vm.addIncome("123.45", "USD", "Freelance", "2026-10-01", "Invoice note", "Client A", "Expected")
        vm.addIncome("123.45", "USD", "Freelance", "2026-10-01", "Invoice note", "Client A", "Expected")
        assertTrue(vm.uiState.value.isSaving)
        runCurrent()
        assertEquals(1, attempts.size)
        assertTrue(vm.uiState.value.canRetry)
        assertFalse(vm.uiState.value.saveSuccessful)
        assertEquals("300.25", vm.uiState.value.exchangeRate?.toPlainString())
        assertFalse(vm.uiState.value.errorMessage.orEmpty().contains("Firebase"))
        vm.retry(); vm.retry()
        runCurrent()
        assertEquals(2, attempts.size)
        assertEquals(attempts[0].id, attempts[1].id)
        assertEquals(1, saved.size)
        val row = saved.values.single()
        assertEquals(12345L, row.amountMinor)
        assertEquals("USD", row.currency)
        assertEquals("2026-10-01", row.receivedDate)
        assertEquals("Client A", row.projectName)
        assertEquals("Invoice note", row.note)
        assertEquals("Expected", row.paymentStatus)
        assertEquals("300.25", row.exchangeRate)
        assertTrue(vm.uiState.value.saveSuccessful)
    }

    @Test fun incomeRetryViaSaveAlsoUsesSameId() = runTest(dispatcher) {
        val ids = mutableListOf<String>()
        val repository = object : IncomeRepository by FakeIncomeRepository() {
            override suspend fun addIncome(income: Income) {
                ids += income.id
                if (ids.size == 1) error("SQL details should not be displayed")
            }
        }
        val vm = IncomeViewModel(repository, auth, mock())
        runCurrent()
        vm.addIncome("12", "LKR", "Crypto", "2026-10-01", "note", cryptoType = "Loss", cryptoAsset = "BTC")
        runCurrent()
        vm.addIncome("12", "LKR", "Crypto", "2026-10-01", "note", cryptoType = "Loss", cryptoAsset = "BTC")
        runCurrent()
        assertEquals(2, ids.size)
        assertEquals(ids[0], ids[1])
    }

    @Test fun expenseRetryRetainsCategoryDatePaymentClassificationAndStableId() = runTest(dispatcher) {
        val saved = linkedMapOf<String, Expense>()
        val attempts = mutableListOf<Expense>()
        val repository = object : ExpenseRepository by FakeExpenseRepository() {
            override suspend fun addExpenseChecked(expense: Expense, allowDuplicate: Boolean) {
                attempts += expense
                saved[expense.id] = expense
                if (attempts.size == 1) throw IOException("private Firestore path")
            }
        }
        val vm = ExpenseViewModel(repository)
        runCurrent()
        val form = ExpenseFormData("234.56", "food", "Food", ExpenseClassification.DISCRETIONARY,
            ExpenseClassification.COMMITTED, ExpensePaymentMethod.DIGITAL_WALLET_PLATFORM, "2026-10-01", "Cafe", "Receipt note")
        vm.addExpense(form); vm.addExpense(form)
        runCurrent()
        assertEquals(1, attempts.size)
        assertTrue(vm.uiState.value.canRetry)
        assertFalse(vm.uiState.value.operationSuccessful)
        vm.retry(); vm.retry()
        runCurrent()
        assertEquals(1, saved.size)
        assertEquals(2, attempts.size)
        assertEquals(attempts[0].id, attempts[1].id)
        val row = saved.values.single()
        assertEquals(23456L, row.amountMinor)
        assertEquals("food", row.categoryId)
        assertEquals("Food", row.categoryName)
        assertEquals("2026-10-01", row.date)
        assertEquals("Cafe", row.merchant)
        assertEquals("Receipt note", row.note)
        assertEquals(ExpensePaymentMethod.DIGITAL_WALLET_PLATFORM, row.paymentMethod)
        assertEquals(ExpenseClassification.COMMITTED, row.classification)
        assertTrue(vm.uiState.value.operationSuccessful)
    }

    @Test fun incomePermissionFailureOffersExplanationWithoutRetry() = runTest(dispatcher) {
        val repository = object : IncomeRepository by FakeIncomeRepository() {
            override suspend fun addIncome(income: Income) { throw SecurityException("permission denied users/private") }
        }
        val vm = IncomeViewModel(repository, auth, mock())
        runCurrent()
        vm.addIncome("10", "LKR", "Other", "2026-10-01", "draft")
        runCurrent()
        assertFalse(vm.uiState.value.canRetry)
        assertTrue(vm.uiState.value.errorMessage.orEmpty().contains("right account"))
        assertFalse(vm.uiState.value.errorMessage.orEmpty().contains("users/private"))
    }

    @Test fun incomeLoadCanBeRetriedAfterStreamFails() = runTest(dispatcher) {
        var attempts = 0
        val repository = object : IncomeRepository by FakeIncomeRepository() {
            override fun observeIncome() = flow<List<Income>> {
                if (attempts++ == 0) throw IOException("private URL")
                emit(listOf(Income(id = "recovered")))
            }
        }
        val vm = IncomeViewModel(repository, auth, mock())
        runCurrent()
        assertFalse(vm.uiState.value.isLoading)
        assertTrue(vm.uiState.value.canRetry)
        vm.retry()
        runCurrent()
        assertEquals("recovered", vm.uiState.value.incomes.single().id)
        assertNull(vm.uiState.value.errorMessage)
    }

    @Test fun roomEmissionDoesNotEraseAnUnresolvedSaveError() = runTest(dispatcher) {
        val cache = FakeIncomeRepository()
        val repository = object : IncomeRepository by cache {
            override suspend fun addIncome(income: Income) { throw IOException("offline") }
        }
        val vm = IncomeViewModel(repository, auth, mock())
        runCurrent()
        vm.addIncome("12", "LKR", "Other", "2026-10-01", "draft")
        runCurrent()
        val error = vm.uiState.value.errorMessage
        cache.incomes.value = listOf(Income(id = "background-sync"))
        runCurrent()
        assertEquals(error, vm.uiState.value.errorMessage)
        assertTrue(vm.uiState.value.canRetry)
    }

    @Test fun recurringSaveRetryKeepsTemplateAndCallsSuccessOnce() = runTest(dispatcher) {
        val attempts = mutableListOf<RecurringExpense>()
        val repository = object : AdvancedExpenseRepository by FakeAdvancedExpenseRepository() {
            override suspend fun saveTemplate(template: RecurringExpense) {
                attempts += template
                if (attempts.size == 1) throw IOException("internal")
            }
        }
        val vm = AdvancedExpenseViewModel(repository)
        var success = 0
        val template = RecurringExpense(id = "stable", title = "Rent", amountMinor = 10000, nextDueDate = "2026-10-01")
        vm.saveTemplate(template) { success++ }
        runCurrent()
        assertEquals(0, success)
        assertTrue(vm.canRetry.value)
        vm.retry(); vm.retry()
        runCurrent()
        assertEquals(listOf(template, template), attempts)
        assertEquals(1, success)
    }

    @Test fun historyRetryReconnectsAfterLoadFailure() = runTest(dispatcher) {
        var attempts = 0
        val incomes = object : IncomeRepository by FakeIncomeRepository() {
            override fun observeIncome() = flow<List<Income>> {
                if (attempts++ == 0) throw IOException("Firestore private")
                emit(listOf(Income(id = "restored", userId = "a", amountMinor = 100, receivedDate = "2026-10-01")))
            }
        }
        val dashboard = object : DashboardRepository {
            override fun observeExpenses() = flowOf(emptyList<Expense>())
            override fun observeExpensesBetween(start: java.time.LocalDate, end: java.time.LocalDate) = observeExpenses()
        }
        val vm = TransactionHistoryViewModel(incomes, dashboard)
        try {
            val first = vm.uiState.first { !it.isLoading }
            assertNotNull(first.errorMessage)
            vm.retry()
            val recovered = vm.uiState.first { !it.isLoading && it.errorMessage == null }
            assertEquals("restored", recovered.visible.single().id)
        } finally {
            // Wait for flowOn's background producers before resetting the test Main dispatcher.
            vm.viewModelScope.coroutineContext[Job]?.cancelAndJoin()
        }
    }
}

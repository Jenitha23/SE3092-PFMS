package com.pfms.app.viewmodel

import com.pfms.app.data.FakeAdvancedExpenseRepository
import com.pfms.app.data.FakeExpenseRepository
import com.pfms.app.domain.model.*
import com.pfms.app.domain.repository.AdvancedExpenseRepository
import com.pfms.app.domain.repository.ExpenseRepository
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.Assert.*

@OptIn(ExperimentalCoroutinesApi::class)
class ExpenseRecoveryTest {
    private val dispatcher = StandardTestDispatcher()
    @Before fun setup() = Dispatchers.setMain(dispatcher)
    @After fun tearDown() = Dispatchers.resetMain()

    @Test fun expenseListenerRecoversAfterFailure() = runTest(dispatcher) {
        var attempts = 0
        val repository = object : ExpenseRepository by FakeExpenseRepository() {
            override fun observeExpenses() = flow {
                if (attempts++ == 0) error("Temporary listener failure")
                emit(listOf(Expense(id = "recovered")))
            }
        }
        val vm = ExpenseViewModel(repository)
        runCurrent()
        assertNotNull(vm.uiState.value.errorMessage)
        advanceTimeBy(2_001)
        runCurrent()
        assertEquals("recovered", vm.uiState.value.expenses.single().id)
        assertNull(vm.uiState.value.errorMessage)
    }

    @Test fun categoryListenerFailureDoesNotCrashAndRetries() = runTest(dispatcher) {
        var attempts = 0
        val repository = object : AdvancedExpenseRepository by FakeAdvancedExpenseRepository() {
            override fun categories() = flow {
                if (attempts++ == 0) error("Permission temporarily unavailable")
                emit(listOf(ExpenseCategory(id = "rent")))
            }
        }
        val vm = AdvancedExpenseViewModel(repository)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.categories.collect {} }
        runCurrent()
        assertNotNull(vm.message.value)
        advanceTimeBy(2_001)
        runCurrent()
        assertEquals("rent", vm.categories.value.single().id)
    }

    @Test fun hangingSaveTimesOutAndKeepsFormOpen() = runTest(dispatcher) {
        val repository = object : AdvancedExpenseRepository by FakeAdvancedExpenseRepository() {
            override suspend fun saveTemplate(template: RecurringExpense): Unit = awaitCancellation()
        }
        val vm = AdvancedExpenseViewModel(repository)
        var closed = false
        vm.saveTemplate(RecurringExpense()) { closed = true }
        assertTrue(vm.busy.value)
        runCurrent()
        advanceTimeBy(20_001)
        runCurrent()
        assertFalse(vm.busy.value)
        assertFalse(closed)
        assertTrue(vm.message.value.orEmpty().contains("timed out"))
    }

    @Test fun hangingDuplicateCheckReleasesExpenseSaveButton() = runTest(dispatcher) {
        val repository = object : ExpenseRepository by FakeExpenseRepository() {
            override suspend fun getExpensesCreatedSince(timestampMillis: Long): List<Expense> = awaitCancellation()
        }
        val vm = ExpenseViewModel(repository)
        vm.addExpense(ExpenseFormData("100.00", "rent", "Rent", ExpenseClassification.COMMITTED))
        runCurrent()
        assertTrue(vm.uiState.value.isSaving)
        advanceTimeBy(20_001)
        runCurrent()
        assertFalse(vm.uiState.value.isSaving)
        assertTrue(vm.uiState.value.errorMessage.orEmpty().contains("timed out"))
    }

    @Test fun repeatedTapsStartOnlyOneSave() = runTest(dispatcher) {
        var saves = 0
        val repository = object : AdvancedExpenseRepository by FakeAdvancedExpenseRepository() {
            override suspend fun saveTemplate(template: RecurringExpense) { saves++ }
        }
        val vm = AdvancedExpenseViewModel(repository)
        vm.saveTemplate(RecurringExpense())
        vm.saveTemplate(RecurringExpense())
        runCurrent()
        assertEquals(1, saves)
    }

    @Test fun resumingCancelledSubscriptionRestoresMonthlyTotalAndDuePrompt() = runTest(dispatcher) {
        val repository = FakeAdvancedExpenseRepository()
        val vm = AdvancedExpenseViewModel(repository)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.templates.collect {} }
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.monthlySubscriptionMinor.collect {} }
        val cancelled = RecurringExpense(id = "sub", amountMinor = 250000, subscription = true,
            active = false, flag = SubscriptionFlag.CANCEL, nextDueDate = "2020-01-01")
        repository.saveTemplate(cancelled)
        runCurrent()
        vm.toggleActive(cancelled)
        runCurrent()
        val resumed = vm.templates.value.single()
        assertEquals(SubscriptionFlag.NONE, resumed.flag)
        assertTrue(vm.isDue(resumed))
        assertEquals(250000L, vm.monthlySubscriptionMinor.value)
    }

    @Test fun overflowingTotalDoesNotCrashAndRecoversAfterEditingAmounts() = runTest(dispatcher) {
        val repository = FakeAdvancedExpenseRepository()
        val vm = AdvancedExpenseViewModel(repository)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.monthlySubscriptionMinor.collect {} }
        repository.saveTemplate(RecurringExpense(id = "large", subscription = true,
            recurrence = Recurrence.DAILY, amountMinor = Long.MAX_VALUE))
        runCurrent()
        assertNull(vm.monthlySubscriptionMinor.value)
        repository.saveTemplate(RecurringExpense(id = "large", subscription = true, amountMinor = 10000))
        runCurrent()
        assertEquals(10000L, vm.monthlySubscriptionMinor.value)
    }
}

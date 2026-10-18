
package com.pfms.app.viewmodel

import com.pfms.app.data.FakeExpenseRepository
import com.pfms.app.domain.model.Expense
import com.pfms.app.domain.model.ExpenseClassification
import com.pfms.app.domain.model.ExpensePaymentMethod

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain

import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

import java.time.LocalDate

@OptIn(ExperimentalCoroutinesApi::class)
class ExpenseViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    private lateinit var repository: FakeExpenseRepository
    private lateinit var viewModel: ExpenseViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)

        repository = FakeExpenseRepository()
        viewModel = ExpenseViewModel(repository)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    // -----------------------------------------
    // TEST HELPERS
    // -----------------------------------------

    private fun expenseInput(
        amount: String = "1500.00",
        override: ExpenseClassification? = null
    ): ExpenseFormData {

        return ExpenseFormData(
            amount = amount,
            categoryId = "groceries",
            categoryName = "Groceries",
            categoryDefault =
                ExpenseClassification.COMMITTED,
            classificationOverride = override,
            paymentMethod = ExpensePaymentMethod.CARD,
            date = LocalDate.now().toString()
        )
    }

    /**
     * Retrieves the current list from the
     * fake repository's StateFlow.
     */
    private suspend fun getExpenses(): List<Expense> {
        return repository.observeExpenses().first()
    }

    /**
     * Retrieves the first stored expense.
     */
    private suspend fun getFirstExpense(): Expense {
        return getExpenses().first()
    }

    // -----------------------------------------
    // TEST 01 - ADD EXPENSE
    // -----------------------------------------

    @Test
    fun addExpenseStoresCorrectAmount() = runTest(dispatcher) {

        viewModel.addExpense(expenseInput())

        advanceUntilIdle()

        val records = getExpenses()

        assertEquals(1, records.size)

        assertEquals(
            150000L,
            records.first().amountMinor
        )

        assertTrue(
            viewModel.uiState.value.operationSuccessful
        )
    }

    // -----------------------------------------
    // TEST 02 - INVALID AMOUNT
    // -----------------------------------------

    @Test
    fun invalidAmountIsRejected() = runTest(dispatcher) {

        viewModel.addExpense(
            expenseInput(amount = "-50")
        )

        advanceUntilIdle()

        assertEquals(
            0,
            repository.addCallCount
        )

        assertNotNull(
            viewModel.uiState.value.errorMessage
        )
    }

    // -----------------------------------------
    // TEST 03 - DEFAULT CLASSIFICATION
    // -----------------------------------------

    @Test
    fun defaultClassificationIsApplied() = runTest(dispatcher) {

        viewModel.addExpense(expenseInput())

        advanceUntilIdle()

        val expense = getFirstExpense()

        assertEquals(
            ExpenseClassification.COMMITTED,
            expense.classification
        )
    }

    // -----------------------------------------
    // TEST 04 - MANUAL CLASSIFICATION
    // -----------------------------------------

    @Test
    fun classificationCanBeOverridden() = runTest(dispatcher) {

        viewModel.addExpense(
            expenseInput(
                override =
                    ExpenseClassification.DISCRETIONARY
            )
        )

        advanceUntilIdle()

        val expense = getFirstExpense()

        assertEquals(
            ExpenseClassification.DISCRETIONARY,
            expense.classification
        )
    }

    // -----------------------------------------
    // TEST 05 - DUPLICATE DETECTION
    // -----------------------------------------

    @Test
    fun duplicateRequiresConfirmation() = runTest(dispatcher) {

        viewModel.addExpense(expenseInput())

        advanceUntilIdle()

        viewModel.addExpense(expenseInput())

        advanceUntilIdle()

        assertNotNull(
            viewModel.uiState.value.pendingDuplicate
        )

        assertEquals(
            1,
            repository.addCallCount
        )
    }

    // -----------------------------------------
    // TEST 06 - CONFIRM DUPLICATE
    // -----------------------------------------

    @Test
    fun confirmingDuplicateSavesSecondRecord() = runTest(dispatcher) {

        viewModel.addExpense(expenseInput())

        advanceUntilIdle()

        viewModel.addExpense(expenseInput())

        advanceUntilIdle()

        viewModel.confirmDuplicateExpense()

        advanceUntilIdle()

        assertEquals(
            2,
            repository.addCallCount
        )

        assertNull(
            viewModel.uiState.value.pendingDuplicate
        )
    }

    // -----------------------------------------
    // TEST 07 - CANCEL DUPLICATE
    // -----------------------------------------

    @Test
    fun cancelledDuplicateIsNotSaved() = runTest(dispatcher) {

        viewModel.addExpense(expenseInput())

        advanceUntilIdle()

        viewModel.addExpense(expenseInput())

        advanceUntilIdle()

        viewModel.cancelDuplicateExpense()

        advanceUntilIdle()

        assertEquals(
            1,
            repository.addCallCount
        )

        assertNull(
            viewModel.uiState.value.pendingDuplicate
        )
    }

    // -----------------------------------------
    // TEST 08 - EDIT EXPENSE
    // -----------------------------------------

    @Test
    fun editExpenseUpdatesRecord() = runTest(dispatcher) {

        viewModel.addExpense(expenseInput())

        advanceUntilIdle()

        val original = getFirstExpense()

        viewModel.updateExpense(
            expenseId = original.id,
            input = expenseInput(amount = "2500.00")
        )

        advanceUntilIdle()

        val updated =
            repository.getExpenseById(original.id)

        assertNotNull(updated)

        assertEquals(
            250000L,
            updated!!.amountMinor
        )
    }

    // -----------------------------------------
    // TEST 09 - DELETE EXPENSE
    // -----------------------------------------

    @Test
    fun deleteExpenseRemovesRecord() = runTest(dispatcher) {

        viewModel.addExpense(expenseInput())

        advanceUntilIdle()

        val original = getFirstExpense()

        viewModel.deleteExpense(original.id)

        advanceUntilIdle()

        val deleted =
            repository.getExpenseById(original.id)

        assertNull(deleted)
    }

    // -----------------------------------------
    // TEST 10 - FR-14 QUICK ADD DEFAULTS
    // -----------------------------------------

    @Test
    fun quickAddExpense_appliesTodayDate_categoryDefaults_andPaymentMethod() = runTest(dispatcher) {
        advanceUntilIdle()

        viewModel.quickAddExpense(
            amount = "1250.50",
            categoryId = "groceries",
            categoryName = "Groceries",
            categoryDefault = ExpenseClassification.COMMITTED
        )

        advanceUntilIdle()

        val saved = getFirstExpense()
        assertEquals(125050L, saved.amountMinor)
        assertEquals("groceries", saved.categoryId)
        assertEquals("Groceries", saved.categoryName)
        assertEquals(ExpenseClassification.COMMITTED, saved.classification)
        assertEquals(LocalDate.now(java.time.ZoneId.of("Asia/Colombo")).toString(), saved.date)
        assertEquals(ExpensePaymentMethod.CASH, saved.paymentMethod)
        assertTrue(viewModel.uiState.value.operationSuccessful)
        assertEquals(ExpenseOperation.ADD, viewModel.uiState.value.successfulOperation)
    }

    // -----------------------------------------
    // TEST 11 - FR-14 PRESERVES PAYMENT METHOD ACROSS RESTARTS
    // -----------------------------------------

    @Test
    fun quickAddExpense_preservesLastUsedPaymentMethodAcrossRestarts() = runTest(dispatcher) {
        advanceUntilIdle()

        // 1. Add an expense using CARD payment method
        viewModel.addExpense(
            expenseInput().copy(paymentMethod = ExpensePaymentMethod.CARD)
        )
        advanceUntilIdle()

        assertEquals(ExpensePaymentMethod.CARD, viewModel.uiState.value.lastUsedPaymentMethod)

        // 2. Simulate app restart by instantiating a fresh ViewModel using the same repository
        val restartedViewModel = ExpenseViewModel(repository)
        advanceUntilIdle()

        // Verify the restored state has CARD as the last used payment method
        assertEquals(ExpensePaymentMethod.CARD, restartedViewModel.uiState.value.lastUsedPaymentMethod)

        // 3. Perform Quick Add on the restarted ViewModel
        restartedViewModel.quickAddExpense(
            amount = "450.00",
            categoryId = "transport",
            categoryName = "Transport",
            categoryDefault = ExpenseClassification.COMMITTED
        )
        advanceUntilIdle()

        // Verify the new expense used CARD payment method preserved from before the restart
        val allExpenses = getExpenses()
        val quickAdded = allExpenses.find { it.amountMinor == 45000L }
        assertNotNull(quickAdded)
        assertEquals(ExpensePaymentMethod.CARD, quickAdded!!.paymentMethod)
        assertEquals(ExpensePaymentMethod.CARD, restartedViewModel.uiState.value.lastUsedPaymentMethod)
    }

    // -----------------------------------------
    // TEST 12 - FR-14 QUICK ADD WITH REAL CATEGORIES
    // -----------------------------------------

    @Test
    fun quickAddExpense_withRealCategories_savesAccurately() = runTest(dispatcher) {
        advanceUntilIdle()

        // Real categories typically available in PFMS:
        val realCategories = listOf(
            Triple("rent", "Rent", ExpenseClassification.COMMITTED),
            Triple("food_delivery", "Food Delivery", ExpenseClassification.DISCRETIONARY),
            Triple("utilities", "Electricity / Water", ExpenseClassification.COMMITTED)
        )

        for ((id, name, classification) in realCategories) {
            viewModel.quickAddExpense(
                amount = "2500.00",
                categoryId = id,
                categoryName = name,
                categoryDefault = classification
            )
            advanceUntilIdle()
        }

        val allExpenses = getExpenses()
        assertEquals(3, allExpenses.size)

        val rent = allExpenses.find { it.categoryId == "rent" }
        assertNotNull(rent)
        assertEquals(ExpenseClassification.COMMITTED, rent!!.classification)

        val delivery = allExpenses.find { it.categoryId == "food_delivery" }
        assertNotNull(delivery)
        assertEquals(ExpenseClassification.DISCRETIONARY, delivery!!.classification)
    }
}

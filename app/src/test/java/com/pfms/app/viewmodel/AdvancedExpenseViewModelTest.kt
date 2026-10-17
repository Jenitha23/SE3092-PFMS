package com.pfms.app.viewmodel

import com.pfms.app.data.FakeAdvancedExpenseRepository
import com.pfms.app.domain.model.ExpenseCategory
import com.pfms.app.domain.model.ExpenseClassification
import com.pfms.app.domain.model.Recurrence
import com.pfms.app.domain.model.RecurringExpense
import com.pfms.app.domain.model.SubscriptionFlag
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AdvancedExpenseViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private lateinit var repository: FakeAdvancedExpenseRepository
    private lateinit var viewModel: AdvancedExpenseViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        repository = FakeAdvancedExpenseRepository()
        viewModel = AdvancedExpenseViewModel(repository)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun ensureDefaults_populatesTwelveDefaultCategories() = runTest(dispatcher) {
        backgroundScope.launch { viewModel.categories.collect {} }

        viewModel.ensureDefaults()
        advanceUntilIdle()

        val categories = viewModel.categories.value
        assertEquals(12, categories.size)
        assertTrue(categories.any { it.name == "Rent" && it.classification == ExpenseClassification.COMMITTED })
        assertTrue(categories.any { it.name == "Food Delivery" && it.classification == ExpenseClassification.DISCRETIONARY })
    }

    @Test
    fun createCategory_addsCustomCategory() = runTest(dispatcher) {
        backgroundScope.launch { viewModel.categories.collect {} }

        viewModel.createCategory("Fitness Membership", ExpenseClassification.COMMITTED)
        advanceUntilIdle()

        val categories = viewModel.categories.value
        assertTrue(categories.any { it.name == "Fitness Membership" && it.custom })
    }

    @Test
    fun updateCategory_archivesAndRestores() = runTest(dispatcher) {
        backgroundScope.launch { viewModel.categories.collect {} }

        viewModel.createCategory("Coffee Beans", ExpenseClassification.DISCRETIONARY)
        advanceUntilIdle()

        val created = viewModel.categories.value.first { it.name == "Coffee Beans" }
        assertFalse(created.archived)

        // Archive
        viewModel.updateCategory(created.copy(archived = true))
        advanceUntilIdle()
        val archived = viewModel.categories.value.first { it.id == created.id }
        assertTrue(archived.archived)

        // Restore
        viewModel.updateCategory(archived.copy(archived = false))
        advanceUntilIdle()
        val restored = viewModel.categories.value.first { it.id == created.id }
        assertFalse(restored.archived)
    }

    @Test
    fun cancelSubscription_stopsRecurrenceAndExcludesFromTotal() = runTest(dispatcher) {
        backgroundScope.launch { viewModel.templates.collect {} }
        backgroundScope.launch { viewModel.monthlySubscriptionMinor.collect {} }

        val subscription = RecurringExpense(
            id = "sub_netflix",
            title = "Netflix Premium",
            amountMinor = 250000L,
            recurrence = Recurrence.MONTHLY,
            active = true,
            subscription = true,
            flag = SubscriptionFlag.NONE,
            nextDueDate = "2025-07-01",
            reminderDate = "2025-06-25"
        )
        viewModel.saveTemplate(subscription)
        advanceUntilIdle()

        assertEquals(250000L, viewModel.monthlySubscriptionMinor.value)

        // Cancel
        viewModel.cancelSubscription(subscription)
        advanceUntilIdle()

        val updated = viewModel.templates.value.first { it.id == "sub_netflix" }
        assertFalse("Must be inactive", updated.active)
        assertEquals(SubscriptionFlag.CANCEL, updated.flag)
        assertEquals("", updated.reminderDate)
        assertEquals(0L, viewModel.monthlySubscriptionMinor.value)
    }

    @Test
    fun confirmDue_triggersConfirmation() = runTest(dispatcher) {
        val template = RecurringExpense(
            id = "t_wifi",
            title = "Home Wifi",
            amountMinor = 180000L,
            nextDueDate = "2025-05-01",
            active = true
        )
        viewModel.confirm(template)
        advanceUntilIdle()

        assertEquals(1, repository.confirmedCount)
    }
}

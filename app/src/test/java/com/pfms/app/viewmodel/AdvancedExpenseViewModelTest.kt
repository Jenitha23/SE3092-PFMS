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

    // -----------------------------------------
    // FR-19: CONFIRM CREATES EXACTLY ONE TRANSACTION & ADVANCES DUE DATE
    // -----------------------------------------

    @Test
    fun confirmDue_createsExactlyOneTransaction_andAdvancesNextDueDate() = runTest(dispatcher) {
        backgroundScope.launch { viewModel.templates.collect {} }

        val template = RecurringExpense(
            id = "t_electricity",
            title = "CEB Electricity",
            amountMinor = 1450000L,
            recurrence = Recurrence.MONTHLY,
            nextDueDate = "2025-05-15",
            active = true
        )
        viewModel.saveTemplate(template)
        advanceUntilIdle()

        // Confirm the due expense
        viewModel.confirm(template)
        advanceUntilIdle()

        // Verify exactly one transaction was recorded
        assertEquals(1, repository.confirmedCount)
        assertEquals(1, repository.createdExpenses.size)

        val createdTransaction = repository.createdExpenses.first()
        assertEquals("recurring_t_electricity_2025-05-15", createdTransaction["id"])
        assertEquals(1450000L, createdTransaction["amountMinor"])
        assertEquals("2025-05-15", createdTransaction["date"])

        // Verify template due date is atomically advanced by 1 month to 2025-06-15
        val updatedTemplate = viewModel.templates.value.first { it.id == "t_electricity" }
        assertEquals("2025-06-15", updatedTemplate.nextDueDate)
    }

    // -----------------------------------------
    // FR-19: RECURRING TRANSACTIONS PRESERVED ACROSS RESTARTS
    // -----------------------------------------

    @Test
    fun recurringTemplates_preservedAcrossApplicationRestarts() = runTest(dispatcher) {
        backgroundScope.launch { viewModel.templates.collect {} }

        val template1 = RecurringExpense(
            id = "t_gym",
            title = "Gym Membership",
            amountMinor = 650000L,
            recurrence = Recurrence.MONTHLY,
            nextDueDate = "2025-08-01",
            active = true
        )
        val template2 = RecurringExpense(
            id = "t_water",
            title = "Water Bill",
            amountMinor = 220000L,
            recurrence = Recurrence.MONTHLY,
            nextDueDate = "2025-08-05",
            active = true
        )

        viewModel.saveTemplate(template1)
        advanceUntilIdle()
        viewModel.saveTemplate(template2)
        advanceUntilIdle()

        assertEquals(2, viewModel.templates.value.size)

        // Simulate app restart by constructing a fresh ViewModel using the same repository
        val restartedVm = AdvancedExpenseViewModel(repository)
        backgroundScope.launch { restartedVm.templates.collect {} }
        advanceUntilIdle()

        // Verify both templates are preserved and intact
        val restored = restartedVm.templates.value
        assertEquals(2, restored.size)
        assertTrue(restored.any { it.id == "t_gym" && it.amountMinor == 650000L && it.nextDueDate == "2025-08-01" })
        assertTrue(restored.any { it.id == "t_water" && it.amountMinor == 220000L && it.nextDueDate == "2025-08-05" })
    }

    // -----------------------------------------
    // FR-19: REMINDERS APPEAR WHEN EXPENSES ARE DUE
    // -----------------------------------------

    @Test
    fun isDue_accuratelyFlagsDueAndUpcomingTemplates() = runTest(dispatcher) {
        val overdue = RecurringExpense(
            id = "t_past",
            title = "Internet Bill",
            amountMinor = 350000L,
            recurrence = Recurrence.MONTHLY,
            nextDueDate = "2020-01-01",
            active = true
        )
        val future = RecurringExpense(
            id = "t_future",
            title = "Annual Insurance",
            amountMinor = 5000000L,
            recurrence = Recurrence.MONTHLY,
            nextDueDate = "2099-12-31",
            active = true
        )
        val paused = RecurringExpense(
            id = "t_paused",
            title = "Magazine",
            amountMinor = 150000L,
            recurrence = Recurrence.MONTHLY,
            nextDueDate = "2020-01-01",
            active = false
        )

        assertTrue("Overdue template must be flagged as due", viewModel.isDue(overdue))
        assertFalse("Future template must not be flagged as due", viewModel.isDue(future))
        assertFalse("Paused template must not be flagged as due", viewModel.isDue(paused))
    }

    // -----------------------------------------
    // FR-20: SUBSCRIPTION REMINDERS ON CONFIGURED DATES
    // -----------------------------------------

    @Test
    fun subscriptionReminders_flaggedAccuratelyOnConfiguredDatesAndFlags() = runTest(dispatcher) {
        val pastReminder = RecurringExpense(
            id = "sub_past",
            title = "AWS Hosting",
            amountMinor = 200000L,
            subscription = true,
            active = true,
            reminderDate = "2020-01-01"
        )
        val futureReminder = RecurringExpense(
            id = "sub_future",
            title = "GitHub Copilot",
            amountMinor = 100000L,
            subscription = true,
            active = true,
            reminderDate = "2099-12-31"
        )
        val reviewFlagged = RecurringExpense(
            id = "sub_review",
            title = "Notion AI",
            amountMinor = 150000L,
            subscription = true,
            active = true,
            flag = SubscriptionFlag.REVIEW
        )
        val cancelledSub = RecurringExpense(
            id = "sub_cancelled",
            title = "Old Streaming",
            amountMinor = 100000L,
            subscription = true,
            active = false,
            flag = SubscriptionFlag.CANCEL,
            reminderDate = "2020-01-01"
        )
        val nonSubscription = RecurringExpense(
            id = "rec_rent",
            title = "Apartment Rent",
            amountMinor = 8000000L,
            subscription = false,
            active = true,
            reminderDate = "2020-01-01"
        )

        assertTrue("Past or today reminder date must flag review reminder", viewModel.isReviewDue(pastReminder))
        assertFalse("Future reminder date must not trigger review reminder", viewModel.isReviewDue(futureReminder))
        assertTrue("REVIEW flag must trigger review reminder", viewModel.isReviewDue(reviewFlagged))
        assertFalse("Cancelled subscription must never trigger review reminder", viewModel.isReviewDue(cancelledSub))
        assertFalse("Non-subscription must never trigger subscription review reminder", viewModel.isReviewDue(nonSubscription))
    }

    // -----------------------------------------
    // FR-20: MONTHLY SUBSCRIPTION TOTALS (MIXED FREQUENCIES)
    // -----------------------------------------

    @Test
    fun monthlySubscriptionTotals_calculatedAcrossMixedFrequencies_inRealTime() = runTest(dispatcher) {
        backgroundScope.launch { viewModel.templates.collect {} }
        backgroundScope.launch { viewModel.monthlySubscriptionMinor.collect {} }

        // Monthly: 3000.00 LKR = 300,000 minor
        val monthlySub = RecurringExpense(
            id = "sub_m",
            title = "Netflix",
            amountMinor = 300000L,
            recurrence = Recurrence.MONTHLY,
            subscription = true,
            active = true
        )
        // Weekly: 1200.00 LKR = 120,000 minor -> * 52 / 12 = 520,000 minor
        val weeklySub = RecurringExpense(
            id = "sub_w",
            title = "Meal Kit Weekly",
            amountMinor = 120000L,
            recurrence = Recurrence.WEEKLY,
            subscription = true,
            active = true
        )
        // Daily: 100.00 LKR = 10,000 minor -> * 365 / 12 = 304,167 minor
        val dailySub = RecurringExpense(
            id = "sub_d",
            title = "Daily Newspaper",
            amountMinor = 10000L,
            recurrence = Recurrence.DAILY,
            subscription = true,
            active = true
        )

        viewModel.saveTemplate(monthlySub)
        advanceUntilIdle()
        assertEquals(300000L, viewModel.monthlySubscriptionMinor.value)

        viewModel.saveTemplate(weeklySub)
        advanceUntilIdle()
        assertEquals(820000L, viewModel.monthlySubscriptionMinor.value) // 300000 + 520000

        viewModel.saveTemplate(dailySub)
        advanceUntilIdle()
        assertEquals(1124167L, viewModel.monthlySubscriptionMinor.value) // 820000 + 304167

        // Pausing weekly subscription updates total reactively
        viewModel.toggleActive(weeklySub)
        advanceUntilIdle()
        assertEquals(604167L, viewModel.monthlySubscriptionMinor.value) // 300000 + 304167
    }

    // -----------------------------------------
    // FR-20: CANCELLED SUBSCRIPTIONS REMAIN TRACKED
    // -----------------------------------------

    @Test
    fun cancelledSubscriptions_remainTrackedInTemplates_withFlagAndExcludedFromCalculations() = runTest(dispatcher) {
        backgroundScope.launch { viewModel.templates.collect {} }
        backgroundScope.launch { viewModel.monthlySubscriptionMinor.collect {} }

        val sub = RecurringExpense(
            id = "sub_hulu",
            title = "Hulu Bundle",
            amountMinor = 450000L,
            recurrence = Recurrence.MONTHLY,
            subscription = true,
            active = true,
            nextDueDate = "2020-01-01",
            reminderDate = "2020-01-01"
        )
        viewModel.saveTemplate(sub)
        advanceUntilIdle()

        assertEquals(450000L, viewModel.monthlySubscriptionMinor.value)
        assertTrue(viewModel.isDue(sub))
        assertTrue(viewModel.isReviewDue(sub))

        // Cancel the subscription
        viewModel.cancelSubscription(sub)
        advanceUntilIdle()

        // 1. Still tracked in templates list
        val trackedList = viewModel.templates.value
        assertEquals(1, trackedList.size)
        val cancelled = trackedList.first()
        assertEquals("sub_hulu", cancelled.id)
        assertFalse("Must be inactive", cancelled.active)
        assertEquals(SubscriptionFlag.CANCEL, cancelled.flag)
        assertEquals("", cancelled.reminderDate)

        // 2. Excluded from monthly subscription totals
        assertEquals(0L, viewModel.monthlySubscriptionMinor.value)

        // 3. Excluded from due and review prompts
        assertFalse(viewModel.isDue(cancelled))
        assertFalse(viewModel.isReviewDue(cancelled))

        // 4. Can be resumed by user: restoring active state and clearing CANCEL flag
        viewModel.toggleActive(cancelled)
        advanceUntilIdle()

        val resumed = viewModel.templates.value.first()
        assertTrue(resumed.active)
        assertEquals(SubscriptionFlag.NONE, resumed.flag)
        assertEquals(450000L, viewModel.monthlySubscriptionMinor.value)
    }
}


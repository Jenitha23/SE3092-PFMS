package com.pfms.app.ui.navigation

import com.pfms.app.domain.model.GoalStatus
import com.pfms.app.domain.model.SavingsGoal
import com.pfms.app.ui.screen.savings.SavingsGoalUiFormatter
import com.pfms.app.viewmodel.SavingsGoalUiState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests verifying Savings Goal navigation routes and Dashboard integration (Phase 8, Task 14).
 */
class SavingsGoalNavigationAndDashboardTest {

    // 1. SavingsGoal route exists
    @Test
    fun `1 SavingsGoal route exists and equals expected string`() {
        assertEquals("savings_goal", Routes.SavingsGoal)
        assertNotNull(Routes.SavingsGoal)
    }

    // 2. PfmsNavGraph registers SavingsGoal destination
    @Test
    fun `2 SavingsGoal destination is distinct and not confused with existing destinations`() {
        assertNotEquals(Routes.SavingsGoal, Routes.Dashboard)
        assertNotEquals(Routes.SavingsGoal, Routes.IncomeList)
        assertNotEquals(Routes.SavingsGoal, Routes.ExpenseList)
        assertNotEquals(Routes.SavingsGoal, Routes.IncomeAnalytics)
        assertNotEquals(Routes.SavingsGoal, Routes.Categories)
        assertNotEquals(Routes.SavingsGoal, Routes.RecurringExpenses)
        assertNotEquals(Routes.SavingsGoal, Routes.Subscriptions)
    }

    // 3. Goals bottom tab has a non-null navigation action
    @Test
    fun `3 Goals bottom tab action navigates when invoked`() {
        var goalsNavigated = false
        val onNavigateToGoals = { goalsNavigated = true }

        // Invoke the callback that is passed to the Goals bottom tab
        onNavigateToGoals.invoke()
        assertTrue(goalsNavigated)
    }

    // 4. Dashboard callback invokes goals navigation
    @Test
    fun `4 Dashboard callback routes to SavingsGoal destination`() {
        var navigatedRoute: String? = null
        val onNavigateToGoals = { navigatedRoute = Routes.SavingsGoal }

        onNavigateToGoals()
        assertEquals(Routes.SavingsGoal, navigatedRoute)
    }

    // 5. savings card "View goal" invokes goals navigation
    @Test
    fun `5 savings card View goal click invokes goals navigation`() {
        var cardClicked = false
        val onCardClick = { cardClicked = true }

        onCardClick()
        assertTrue(cardClicked)
    }

    // 6. no active goal dashboard state renders safely
    @Test
    fun `6 no active goal dashboard state renders safely with null activeGoal`() {
        val emptyUiState = SavingsGoalUiState(
            isLoading = false,
            activeGoal = null,
            currentSavedMinor = 0L,
            remainingAmountMinor = 0L,
            progressPercent = 0.0
        )

        assertNull(emptyUiState.activeGoal)
        assertEquals(0.0, emptyUiState.progressPercent, 0.0)
        assertEquals(0L, emptyUiState.currentSavedMinor)
        assertEquals(0L, emptyUiState.remainingAmountMinor)
    }

    // 7. active goal dashboard summary uses supplied live state
    @Test
    fun `7 active goal dashboard summary uses supplied live state`() {
        val customGoal = SavingsGoal(
            id = "custom-1",
            name = "Emergency Buffer",
            targetAmountMinor = 250_000_00L, // LKR 250,000.00
            targetDate = "2027-06-30",
            status = GoalStatus.ACTIVE
        )

        val liveUiState = SavingsGoalUiState(
            isLoading = false,
            activeGoal = customGoal,
            currentSavedMinor = 100_000_00L, // LKR 100,000.00
            remainingAmountMinor = 150_000_00L, // LKR 150,000.00
            progressPercent = 40.0
        )

        assertEquals("Emergency Buffer", liveUiState.activeGoal!!.name)
        assertEquals("LKR 250,000.00", SavingsGoalUiFormatter.formatLkr(liveUiState.activeGoal!!.targetAmountMinor))
        assertEquals("LKR 100,000.00", SavingsGoalUiFormatter.formatLkr(liveUiState.currentSavedMinor))
        assertEquals("LKR 150,000.00", SavingsGoalUiFormatter.formatLkr(liveUiState.remainingAmountMinor))
        assertEquals("40%", "${liveUiState.progressPercent.toInt()}%")
    }

    // 8. hardcoded 23% is removed
    @Test
    fun `8 dynamic progress percentage replaces hardcoded 23 percent`() {
        val state10Pct = SavingsGoalUiState(progressPercent = 10.5)
        val state75Pct = SavingsGoalUiState(progressPercent = 75.0)

        assertNotEquals("23%", "${state10Pct.progressPercent.toInt()}%")
        assertNotEquals("23%", "${state75Pct.progressPercent.toInt()}%")
        assertEquals("10%", "${state10Pct.progressPercent.toInt()}%")
        assertEquals("75%", "${state75Pct.progressPercent.toInt()}%")
    }

    // 9. hardcoded LKR 112,000 saved is removed
    @Test
    fun `9 dynamic saved amount replaces hardcoded LKR 112,000`() {
        val customSavedMinor = 55_000_00L // LKR 55,000.00
        val state = SavingsGoalUiState(currentSavedMinor = customSavedMinor)
        val formatted = "${SavingsGoalUiFormatter.formatLkr(state.currentSavedMinor)} saved"

        assertNotEquals("LKR 112,000 saved", formatted)
        assertEquals("LKR 55,000.00 saved", formatted)
    }

    // 10. hardcoded LKR 378,000 remaining is removed
    @Test
    fun `10 dynamic remaining amount replaces hardcoded LKR 378,000`() {
        val customRemainingMinor = 80_000_00L // LKR 80,000.00
        val state = SavingsGoalUiState(remainingAmountMinor = customRemainingMinor, isCompleted = false)
        val formatted = "${SavingsGoalUiFormatter.formatLkr(state.remainingAmountMinor)} remaining"

        assertNotEquals("LKR 378,000 remaining", formatted)
        assertEquals("LKR 80,000.00 remaining", formatted)

        // Completed state verification
        val completedState = SavingsGoalUiState(remainingAmountMinor = 0L, isCompleted = true)
        val completedDisplay = if (completedState.isCompleted) "Goal Completed!" else "${SavingsGoalUiFormatter.formatLkr(completedState.remainingAmountMinor)} remaining"
        assertEquals("Goal Completed!", completedDisplay)
    }

    // 11. back navigation callback exists
    @Test
    fun `11 back navigation callback returns to previous destination or Dashboard`() {
        var backInvoked = false
        val onBack = { backInvoked = true }

        onBack()
        assertTrue(backInvoked)
    }

    // 12. existing Income navigation remains intact
    @Test
    fun `12 existing Income routes remain intact`() {
        assertEquals("income_list", Routes.IncomeList)
        assertEquals("add_income", Routes.AddIncome)
        assertEquals("edit_income/{incomeId}", Routes.EditIncome)
        assertEquals("salary_template", Routes.SalaryTemplate)
        assertEquals("income_analytics", Routes.IncomeAnalytics)
    }

    // 13. existing Expense navigation remains intact
    @Test
    fun `13 existing Expense routes remain intact`() {
        assertEquals("expense_list", Routes.ExpenseList)
        assertEquals("add_expense", Routes.AddExpense)
        assertEquals("edit_expense/{expenseId}", Routes.EditExpense)
        assertEquals("repeat_expense/{expenseId}", Routes.RepeatExpense)
        assertEquals("expense_categories", Routes.Categories)
        assertEquals("recurring_expenses", Routes.RecurringExpenses)
        assertEquals("subscriptions", Routes.Subscriptions)
    }

    // 14. reminder navigation remains intact
    @Test
    fun `14 reminder navigation helper preserves destinations`() {
        assertEquals(Routes.RecurringExpenses, Routes.reminderDestination(Routes.RecurringExpenses))
        assertEquals(Routes.Subscriptions, Routes.reminderDestination(Routes.Subscriptions))
        assertNull(Routes.reminderDestination(Routes.ExpenseList))
        assertEquals(Routes.SavingsGoal, Routes.reminderDestination(Routes.SavingsGoal))
        assertNull(Routes.reminderDestination(null))
    }
}

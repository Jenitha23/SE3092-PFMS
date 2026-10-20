package com.pfms.app.viewmodel

import com.pfms.app.data.*
import com.pfms.app.data.repository.OfflineFirstDashboardRepository
import com.pfms.app.domain.model.*
import com.pfms.app.domain.usecase.analytics.GetMonthlyIncomeSummaryUseCase
import com.pfms.app.domain.usecase.dashboard.AppClock
import com.pfms.app.domain.usecase.savings.*
import com.pfms.app.utils.MainDispatcherRule
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class EmptyUserViewModelTest {
    @get:Rule val dispatcherRule = MainDispatcherRule()
    private val incomes = FakeIncomeRepository()
    private val expenses = FakeExpenseRepository()
    private val dashboard = OfflineFirstDashboardRepository(expenses)

    @Test fun newUserDashboardAnalyticsAndHistoryExposeLoadedZeroStates() = runTest {
        val home = dispatcherRule.track(DashboardViewModel(incomes, dashboard)).uiState.first { !it.isLoading }
        assertEquals(0L, home.summary!!.totalIncomeMinor)
        assertEquals(0L, home.summary.totalExpenseMinor)
        assertTrue(home.latestTransactions.isEmpty())
        val analytics = dispatcherRule.track(SpendingAnalyticsViewModel(incomes, dashboard)).uiState.first { !it.isLoading }
        assertNull(analytics.errorMessage)
        assertEquals(0L, analytics.summary!!.totalExpenseMinor)
        assertTrue(analytics.insights!!.largestExpenses.isEmpty())
        assertEquals(6, analytics.trend.size)
        val history = dispatcherRule.track(TransactionHistoryViewModel(incomes, dashboard)).uiState.first { !it.isLoading }
        assertFalse(history.hasAnyTransactions)
        assertEquals(0L, history.summary.netMinor)
        assertFalse(history.canLoadMore)
    }

    @Test fun monthWithoutIncomeStillShowsExpensesAndNegativeNetSavings() = runTest {
        expenses.addExpense(Expense(id = "expense", amountMinor = 100, date = AppClock.currentMonth().atDay(1).toString()))
        val state = dispatcherRule.track(DashboardViewModel(incomes, dashboard)).uiState.first { !it.isLoading }
        assertEquals(0L, state.summary!!.totalIncomeMinor)
        assertEquals(-100L, state.summary.netSavingsMinor)
        assertTrue(state.summary.hasData)
    }

    @Test fun emptySavingsRepositoryExposesNoGoalAndZeroProgress() = runTest {
        val saved = CalculateSavedAmountUseCase()
        val progress = CalculateGoalProgressUseCase(saved)
        val contribution = CalculateCurrentMonthGoalContributionUseCase()
        val net = CalculateMonthlyNetSavingsUseCase(GetMonthlyIncomeSummaryUseCase())
        val trailing = CalculateTrailingNetSavingsAverageUseCase(net)
        val vm = dispatcherRule.track(SavingsGoalViewModel(
            FakeSavingsGoalRepository(), expenses, incomes,
            FakeAuthRepository(AuthUser("new-user", "New user", "new@example.com", true)),
            saved, progress, contribution, net, trailing,
            CalculateGoalPaceUseCase(progress, contribution, net, trailing), CalculateDiscretionaryImpactUseCase()
        ))
        val state = vm.uiState.first { !it.isLoading }
        assertNull(state.activeGoal)
        assertEquals(0L, state.currentSavedMinor)
        assertEquals(0.0, state.progressPercent, 0.0)
        assertTrue(state.transactions.isEmpty())
        assertNull(state.errorMessage)
    }
}

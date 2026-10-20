package com.pfms.app.viewmodel

import com.pfms.app.domain.model.Expense
import com.pfms.app.domain.model.Income
import com.pfms.app.domain.repository.DashboardRepository
import com.pfms.app.domain.repository.IncomeRepository
import com.pfms.app.domain.usecase.dashboard.AppClock
import com.pfms.app.domain.usecase.dashboard.expense
import com.pfms.app.domain.usecase.dashboard.income
import com.pfms.app.utils.MainDispatcherRule
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.time.LocalDate

class DashboardViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private class FakeIncomeRepository(initial: List<Income> = emptyList()) : IncomeRepository {
        val flow = MutableStateFlow(initial)
        override fun observeIncome(): Flow<List<Income>> = flow
        override suspend fun addIncome(income: Income) { flow.value = flow.value + income }
        override suspend fun updateIncome(income: Income) = Unit
        override suspend fun deleteIncome(incomeId: String) { flow.value = flow.value.filterNot { it.id == incomeId } }
        override suspend fun getIncomeById(incomeId: String): Income? = flow.value.firstOrNull { it.id == incomeId }
    }

    private class FakeDashboardRepository(
        initial: List<Expense> = emptyList()
    ) : DashboardRepository {
        val expenses = MutableStateFlow(initial)
        override fun observeExpenses(): Flow<List<Expense>> = expenses
        override fun observeExpensesBetween(start: LocalDate, end: LocalDate): Flow<List<Expense>> =
            expenses.map { list -> list.filter { it.date >= start.toString() && it.date <= end.toString() } }
    }

    private val thisMonth = AppClock.currentMonth()
    private fun day(n: Int) = thisMonth.atDay(n).toString()

    @Test
    fun emitsTotalsForTheCurrentMonth() = runTest {
        val vm = DashboardViewModel(
            FakeIncomeRepository(listOf(income("i1", day(1), 10_000_000L))),
            FakeDashboardRepository(listOf(expense("e1", day(1), 2_500_000L)))
        ).also { mainDispatcherRule.track(it) }
        val state = vm.uiState.first { !it.isLoading }
        assertEquals(thisMonth, state.selectedMonth)
        assertEquals(10_000_000L, state.summary?.totalIncomeMinor)
        assertEquals(2_500_000L, state.summary?.totalExpenseMinor)
        assertEquals(7_500_000L, state.summary?.netSavingsMinor)
        assertEquals(2, state.latestTransactions.size)
    }

    @Test
    fun emptyDataGivesZeroedSummaryNotAnError() = runTest {
        val vm = mainDispatcherRule.track(DashboardViewModel(FakeIncomeRepository(), FakeDashboardRepository()))
        val state = vm.uiState.first { !it.isLoading }
        assertNull(state.errorMessage)
        assertFalse(state.summary!!.hasData)
        assertTrue(state.latestTransactions.isEmpty())
    }

    @Test
    fun latestTransactionsAreCappedAtTen() = runTest {
        val many = (1..15).map { expense("e$it", day(1), 1_000L * it) }
        val vm = mainDispatcherRule.track(DashboardViewModel(FakeIncomeRepository(), FakeDashboardRepository(many)))
        val state = vm.uiState.first { !it.isLoading }
        assertEquals(10, state.latestTransactions.size)
        assertEquals(15, state.summary?.expenseCount)
    }

    @Test
    fun newExpenseUpdatesTheDashboardInRealTime() = runTest {
        val repo = FakeDashboardRepository()
        val vm = mainDispatcherRule.track(DashboardViewModel(FakeIncomeRepository(), repo))
        vm.uiState.first { !it.isLoading }
        repo.expenses.value = listOf(expense("e1", day(1), 500_000L))
        val updated = vm.uiState.first { it.summary?.totalExpenseMinor == 500_000L }
        assertNotNull(updated.summary)
    }

    @Test
    fun monthNavigationStopsAtTheCurrentMonth() = runTest {
        val vm = mainDispatcherRule.track(DashboardViewModel(FakeIncomeRepository(), FakeDashboardRepository()))
        vm.uiState.first { !it.isLoading }

        vm.previousMonth()
        val previous = vm.uiState.first { it.selectedMonth == thisMonth.minusMonths(1) && !it.isLoading }
        assertFalse(previous.isCurrentMonth)

        vm.nextMonth()
        vm.uiState.first { it.selectedMonth == thisMonth && !it.isLoading }

        vm.nextMonth() // already current: must not move into the future
        assertEquals(thisMonth, vm.uiState.value.selectedMonth)
    }
}

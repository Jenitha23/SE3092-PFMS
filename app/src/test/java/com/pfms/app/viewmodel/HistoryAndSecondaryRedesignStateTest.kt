package com.pfms.app.viewmodel

import com.pfms.app.data.FakeIncomeRepository
import com.pfms.app.data.FakeExpenseRepository
import com.pfms.app.data.repository.OfflineFirstDashboardRepository
import com.pfms.app.domain.model.*
import com.pfms.app.domain.model.dashboard.*
import com.pfms.app.domain.usecase.dashboard.*
import com.pfms.app.utils.MainDispatcherRule
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.time.YearMonth

class HistoryAndSecondaryRedesignStateTest {
    @get:Rule val main = MainDispatcherRule()
    private val month = YearMonth.of(2026, 10)
    private val expenses = (1..8).map { Expense(id = "expense-$it", amountMinor = it * 10000L, date = "2026-10-${it.toString().padStart(2, '0')}", categoryName = "Groceries") }

    @Test fun dashboardPreviewShowsFourNewestWhileHistoryRetainsEveryRecord() {
        val all = TransactionHistoryCalculator.merge(emptyList(), expenses)
        val overview = DashboardOverviewCalculator.calculate(emptyList(), expenses, month, month.atDay(25))
        val display = DashboardPresentationMapper.content(overview, all)
        assertEquals(all.take(4).map { it.id }, display.transactions.map { it.transaction.id })
        assertEquals("8", display.ledgerCount)
        assertEquals("Groceries", display.transactions.first().title)
        val history = HistoryScreenViewModel()
        history.sync(TransactionHistoryUiState(isLoading = false, visible = all, summary = TransactionHistoryCalculator.summarize(all)))
        assertEquals(8, history.state.value.display!!.rows.size)
        assertEquals("8 transactions", history.state.value.display!!.count)
        assertEquals("−LKR 3,600.00", history.state.value.display!!.net)
    }
    @Test fun expenseMerchantAndRecurringNoteStayVisibleWithoutCallingCashTheMerchant() {
        val row = expenses.first().copy(merchant = "Keells", note = "Weekly pantry · Recurring expense").toTransaction()
        val display = FinanceTransactionPresentation.create(row)
        assertEquals("Keells", display.title)
        assertTrue(display.detail.contains("Groceries")); assertTrue(display.detail.contains("Recurring expense"))
        assertEquals("Groceries", FinanceTransactionPresentation.create(expenses.first().toTransaction()).title)
    }
    @Test fun crossTypeHistoryFiltersClearIncompatibleFields() = runTest {
        val vm = main.track(TransactionHistoryViewModel(FakeIncomeRepository(), OfflineFirstDashboardRepository(FakeExpenseRepository())))
        vm.uiState.first { !it.isLoading }
        vm.setExpenseCategory("Rent"); vm.setPaymentMethod(ExpensePaymentMethod.CASH)
        vm.setType(TransactionTypeFilter.ALL); vm.setIncomeSource("Salary")
        val income = vm.uiState.first { it.filter.incomeSource == "Salary" }.filter
        assertNull(income.expenseCategory); assertNull(income.paymentMethod); assertEquals(TransactionTypeFilter.INCOME, income.type)
        vm.setType(TransactionTypeFilter.ALL); vm.setExpenseCategory("Groceries")
        val expense = vm.uiState.first { it.filter.expenseCategory == "Groceries" }.filter
        assertNull(expense.incomeSource); assertEquals(TransactionTypeFilter.EXPENSE, expense.type)
    }
    @Test fun blankSimulationInputResetsToZeroAndInvalidCentsNeverReachTheCalculation() {
        val vm = SavingsSimulatorScreenViewModel()
        var last = -1L
        vm.edit("1200.50", true) { last = it }; assertEquals(120050L, last)
        vm.edit("", true) { last = it }; assertEquals(0L, last)
        vm.edit("12.001", true) { last = it }; assertEquals(0L, last)
        assertNotNull(vm.state.value.reductionError)
        vm.sync(SavingsSimulatorUiState(isLoading = false))
        assertEquals("12.001", vm.state.value.reduction)
    }
    @Test fun salaryConfirmationPreservesDraftAndUsesExactPositiveMoneyValidation() {
        val vm = SalaryConfirmationViewModel()
        vm.sync("salary", 25000000)
        vm.edit("150000.001")
        var saved: String? = null
        vm.confirm { saved = it }; assertNull(saved); assertNotNull(vm.state.value.error)
        vm.edit("150000.50"); vm.sync("salary", 25000000)
        vm.confirm { saved = it }; assertEquals("150000.50", saved)
    }
    @Test fun spendingPresentationUsesActualTotalsAndPreparedChartCoordinates() {
        val vm = SpendingScreenViewModel()
        val summary = MonthlyFinanceCalculator.summarize(emptyList(), expenses, month)
        vm.sync(SpendingAnalyticsUiState(isLoading = false, selectedMonth = month, summary = summary,
            insights = SpendingInsightsCalculator.build(expenses), trend = MonthlyFinanceCalculator.trend(emptyList(), expenses, month, 6)))
        val display = vm.display.value!!
        assertEquals("LKR 3,600.00", display.total)
        assertEquals("−LKR 3,600.00", display.net)
        assertEquals(6, display.trend.size)
        assertTrue(display.trend.all { it.expenseFraction in 0f..1f && it.incomeFraction.isFinite() })
        assertEquals(5, display.topExpenses.size)
    }
}

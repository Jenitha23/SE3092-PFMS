package com.pfms.app.viewmodel

import com.pfms.app.data.FakeExpenseRepository
import com.pfms.app.domain.model.*
import com.pfms.app.domain.repository.ExpenseRepository
import com.pfms.app.domain.usecase.DetectSpendingPatternsUseCase
import com.pfms.app.domain.usecase.ObserveSpendingPatternsUseCase
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.test.*
import org.junit.*
import org.junit.Assert.*
import java.io.IOException
import java.time.LocalDate
import java.time.YearMonth

@OptIn(ExperimentalCoroutinesApi::class)
class SpendingPatternsViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val storage = FakeExpenseRepository()
    private var failure: Exception? = null
    private var gate: CompletableDeferred<Unit>? = null
    private var observations = 0
    private val expenses = object : ExpenseRepository by storage {
        override fun observeExpenses(): Flow<List<Expense>> = flow {
            observations++
            failure?.let { throw it }
            gate?.await()
            emitAll(storage.observeExpenses())
        }
    }
    private lateinit var vm: SpendingPatternsViewModel
    private val date = LocalDate.of(2026, 10, 15)
    private val august = YearMonth.of(2026, 8)
    private val september = YearMonth.of(2026, 9)
    @Before fun setup() {
        Dispatchers.setMain(dispatcher)
        vm = SpendingPatternsViewModel(ObserveSpendingPatternsUseCase(expenses, DetectSpendingPatternsUseCase()))
        vm.dateProvider = { date }; vm.refreshDate()
    }
    @After fun cleanup() { Dispatchers.resetMain() }
    private fun TestScope.observe() = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.uiState.collect {} }
    private fun confirm() { vm.setMonthConfirmed(august, true); vm.setMonthConfirmed(september, true) }
    private fun expense(id: String, date: String, amount: Long, category: String = "food") =
        Expense(id = id, date = date, amountMinor = amount, categoryId = category, categoryName = category)
    private suspend fun history(category: String = "food", amount: Long = 1000000) {
        storage.addExpense(expense("${category}-aug", "2026-08-10", amount, category))
        storage.addExpense(expense("${category}-sep", "2026-09-10", amount, category))
    }

    @Test fun multipleCategoriesUseTheDomainAmountsPercentagesAndSeverities() = runTest(dispatcher) {
        history(); history("transport")
        storage.addExpense(expense("food-now", "2026-10-10", 1400000))
        storage.addExpense(expense("transport-now", "2026-10-10", 2000000, "transport"))
        confirm(); observe(); runCurrent()
        val result = vm.uiState.value.result!!
        assertEquals(SpendingPatternStatus.READY, result.status)
        assertEquals(listOf("transport", "food"), result.insights.map { it.categoryId })
        assertEquals(listOf(SpendingIncreaseLevel.HIGH, SpendingIncreaseLevel.NOTICEABLE), result.insights.map { it.level })
        assertEquals(1400000L, result.insights.last().currentAmountMinor)
        assertEquals(1000000L, result.insights.last().baselineAmountMinor)
        assertEquals(40, result.insights.last().increasePercent)
    }

    @Test fun addingEditingAndDeletingExpensesRecalculatesWithoutRetryOrReopening() = runTest(dispatcher) {
        history(); confirm(); observe(); runCurrent()
        assertTrue(vm.uiState.value.result!!.insights.isEmpty())
        val current = expense("current", "2026-10-10", 1500000)
        storage.addExpense(current); runCurrent()
        assertEquals(50, vm.uiState.value.result!!.insights.single().increasePercent)
        storage.updateExpense(current.copy(amountMinor = 1100000)); runCurrent()
        assertTrue(vm.uiState.value.result!!.insights.isEmpty())
        storage.updateExpense(current.copy(amountMinor = 1300000)); runCurrent()
        assertEquals(30, vm.uiState.value.result!!.insights.single().increasePercent)
        storage.deleteExpense(current.id); runCurrent()
        assertTrue(vm.uiState.value.result!!.insights.isEmpty())
        assertEquals(1, observations)
    }

    @Test fun emptyOrUnconfirmedHistoryDoesNotInventAComparison() = runTest(dispatcher) {
        observe(); runCurrent()
        assertEquals(SpendingPatternStatus.INSUFFICIENT_HISTORY, vm.uiState.value.result!!.status)
        assertEquals(0, vm.uiState.value.result!!.eligibleHistoryMonths)
        vm.setMonthConfirmed(august, true); runCurrent()
        assertEquals(1, vm.uiState.value.result!!.eligibleHistoryMonths)
        vm.setMonthConfirmed(september, true); runCurrent()
        assertEquals(SpendingPatternStatus.READY, vm.uiState.value.result!!.status)
        assertTrue(vm.uiState.value.result!!.insights.isEmpty())
    }

    @Test fun zeroBaselineCategoriesAreNotAssignedAnIncreaseOrSeverity() = runTest(dispatcher) {
        storage.addExpense(expense("new-category", "2026-10-10", 5000000))
        confirm(); observe(); runCurrent()
        assertTrue(vm.uiState.value.result!!.insights.isEmpty())
    }

    @Test fun partialMonthExcludesLaterHistoricalDaysAndFutureCurrentExpenses() = runTest(dispatcher) {
        history(amount = 1000000)
        storage.addExpense(expense("aug-late", "2026-08-25", 5000000))
        storage.addExpense(expense("sep-late", "2026-09-25", 5000000))
        storage.addExpense(expense("current", "2026-10-10", 1500000))
        storage.addExpense(expense("future", "2026-10-25", 5000000))
        confirm(); observe(); runCurrent()
        val insight = vm.uiState.value.result!!.insights.single()
        assertEquals(1000000L, insight.baselineAmountMinor)
        assertEquals(1500000L, insight.currentAmountMinor)
        assertTrue(insight.isMonthToDateComparison)
    }

    @Test fun streamErrorsHaveSafeMessagesAndRetryResubscribesToCorrectedData() = runTest(dispatcher) {
        failure = IOException("private Firebase account details")
        confirm(); observe(); runCurrent()
        assertNull(vm.uiState.value.result)
        assertFalse(vm.uiState.value.isLoading)
        assertEquals("Please check your internet connection and try again.", vm.uiState.value.error)
        assertTrue(vm.uiState.value.canRetry)
        failure = null; history()
        storage.addExpense(expense("current", "2026-10-10", 1500000))
        vm.retry(); runCurrent()
        assertNull(vm.uiState.value.error)
        assertEquals(50, vm.uiState.value.result!!.insights.single().increasePercent)
        storage.deleteExpense("current"); runCurrent()
        assertTrue(vm.uiState.value.result!!.insights.isEmpty())
        assertEquals(2, observations)
    }

    @Test fun changingComparisonMonthsClearsStaleResultsWhileLoading() = runTest(dispatcher) {
        history(); storage.addExpense(expense("current", "2026-10-10", 1500000))
        confirm(); observe(); runCurrent()
        assertNotNull(vm.uiState.value.result)
        gate = CompletableDeferred()
        vm.setMonthConfirmed(august, false); runCurrent()
        assertTrue(vm.uiState.value.isLoading)
        assertNull(vm.uiState.value.result)
        gate!!.complete(Unit); runCurrent()
        assertFalse(vm.uiState.value.isLoading)
        assertEquals(SpendingPatternStatus.INSUFFICIENT_HISTORY, vm.uiState.value.result!!.status)
    }

    @Test fun resumingAfterCalendarChangePrunesUnavailableMonths() = runTest(dispatcher) {
        confirm(); observe(); runCurrent()
        vm.setMonthConfirmed(YearMonth.of(2026, 6), true)
        vm.setMonthConfirmed(YearMonth.of(2026, 10), true)
        assertEquals(setOf(august, september), vm.confirmedMonths.value)
        vm.dateProvider = { LocalDate.of(2026, 12, 1) }; vm.retry(); runCurrent()
        assertEquals(setOf(september), vm.confirmedMonths.value)
        assertEquals(LocalDate.of(2026, 12, 1), vm.uiState.value.date)
    }

    @Test fun presentationUsesReturnedSeverityRatherThanInferringOneFromPercent() {
        val insight = SpendingPatternInsight("food", "Food", YearMonth.of(2026, 10), 99999999999,
            1000000, 99998999999, 99, SpendingIncreaseLevel.NOTICEABLE, 2, true)
        val display = SecondaryFinancePresentation.pattern(insight)
        assertEquals("Noticeable increase", display.severity)
        assertEquals("99% above baseline", display.increase)
        assertEquals("LKR 999,999,999.99", display.current)
        assertEquals(SpendingIncreaseLevel.NOTICEABLE, display.level)
    }
}

package com.pfms.app.viewmodel

import com.pfms.app.data.FakeSavingsGoalRepository
import com.pfms.app.domain.model.*
import com.pfms.app.domain.repository.SavingsGoalRepository
import com.pfms.app.domain.usecase.CalculateSavingsSimulationUseCase
import com.pfms.app.domain.usecase.savings.CalculateSavedAmountUseCase
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.test.*
import org.junit.*
import org.junit.Assert.*
import java.io.IOException
import java.time.LocalDate

@OptIn(ExperimentalCoroutinesApi::class)
class SavingsSimulatorViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val storage = FakeSavingsGoalRepository()
    private var failure: Exception? = null
    private val repository = object : SavingsGoalRepository by storage {
        override fun observeActiveGoal(): Flow<SavingsGoal?> = flow {
            failure?.let { throw it }
            emitAll(storage.observeActiveGoal())
        }
    }
    private val today = LocalDate.of(2026, 10, 25)
    private val goal = SavingsGoal("goal", "user", "Laptop", 10000000, "2027-10-25", 1000000)
    private val deposit = GoalTransaction("deposit", goal.id, goal.userId, 1500000, GoalTransactionType.CONTRIBUTION, "2026-10-01")
    private val withdrawal = GoalTransaction("withdrawal", goal.id, goal.userId, 500000, GoalTransactionType.WITHDRAWAL, "2026-10-02")
    private val calculation = CalculateSavingsSimulationUseCase(CalculateSavedAmountUseCase())
    private lateinit var vm: SavingsSimulatorViewModel
    @Before fun setup() {
        Dispatchers.setMain(dispatcher)
        storage.goals.value = listOf(goal)
        storage.transactions.value = listOf(deposit, withdrawal)
        vm = SavingsSimulatorViewModel(repository, calculation)
        vm.dateProvider = { today }
    }
    @After fun cleanup() { Dispatchers.resetMain() }
    private fun TestScope.observe() = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.uiState.collect {} }
    private fun source(reduction: Long = 0, planned: Long = 0, selected: SavingsGoal = goal) = SavingsSimulatorUiState(
        isLoading = false, goal = selected, monthlyReductionMinor = reduction, plannedContributionMinor = planned,
        result = calculation.simulate(selected, listOf(deposit, withdrawal), reduction, planned, today))

    @Test fun validScenarioDisplaysTheDomainProjectionWithoutChangingGoalOrLedger() = runTest(dispatcher) {
        observe(); runCurrent()
        vm.setMonthlyReductionMinor(100000); vm.setPlannedContributionMinor(50000); runCurrent()
        val result = vm.uiState.value.result!!
        assertEquals(calculation.simulate(goal, listOf(deposit, withdrawal), 100000, 50000, today), result)
        assertEquals(1200000L, result.potentialExtraByDeadlineMinor)
        assertEquals(3800000L, result.projectedBalanceAtDeadlineMinor)
        assertEquals(38, result.projectedProgressPercent)
        assertNotNull(result.estimatedCompletionDate)
        assertEquals(listOf(goal), storage.goals.value)
        assertEquals(listOf(deposit, withdrawal), storage.transactions.value)
    }

    @Test fun zeroReductionAddsNothingAndHasNoInventedCompletionDate() = runTest(dispatcher) {
        observe(); runCurrent()
        val result = vm.uiState.value.result!!
        assertEquals(0L, result.potentialExtraByDeadlineMinor)
        assertEquals(2000000L, result.projectedBalanceAtDeadlineMinor)
        assertNull(result.estimatedCompletionDate)
        val screen = SavingsSimulatorScreenViewModel(); screen.sync(vm.uiState.value)
        assertTrue(screen.state.value.canShowResults)
        assertFalse(screen.state.value.metrics.any { it.first == "Estimated completion" })
    }

    @Test fun repeatedInputAndLedgerChangesAlwaysRecalculateFromTheObservedActualBalance() = runTest(dispatcher) {
        observe(); runCurrent()
        for (amount in listOf(100000L, 250075L, 0L, 12345L)) {
            vm.setMonthlyReductionMinor(amount); runCurrent()
            assertEquals(calculation.simulate(goal, listOf(deposit, withdrawal), amount, 0, today), vm.uiState.value.result)
        }
        storage.transactions.value = listOf(deposit)
        runCurrent()
        assertEquals(2500000L, vm.uiState.value.result!!.currentSavedMinor)
        assertEquals(listOf(deposit), storage.transactions.value)
    }

    @Test fun noActiveGoalClearsTheOldForecastAndRestoredGoalUpdatesAutomatically() = runTest(dispatcher) {
        observe(); runCurrent(); assertNotNull(vm.uiState.value.result)
        storage.goals.value = emptyList(); runCurrent()
        assertNull(vm.uiState.value.goal); assertNull(vm.uiState.value.result); assertNull(vm.uiState.value.error)
        storage.goals.value = listOf(goal); runCurrent()
        assertEquals(goal, vm.uiState.value.goal); assertNotNull(vm.uiState.value.result)
    }

    @Test fun completedGoalHasNoAdditionalSavingsOrMisleadingCompletionEstimate() = runTest(dispatcher) {
        val completed = goal.copy(openingSavedAmountMinor = 10000000)
        storage.goals.value = listOf(completed)
        vm.setMonthlyReductionMinor(100000); observe(); runCurrent()
        val result = vm.uiState.value.result!!
        assertTrue(result.isGoalAlreadyComplete)
        assertEquals(0L, result.potentialExtraByDeadlineMinor)
        assertEquals(result.currentSavedMinor, result.projectedBalanceAtDeadlineMinor)
        assertEquals(100, result.projectedProgressPercent)
        val screen = SavingsSimulatorScreenViewModel(); screen.sync(vm.uiState.value)
        assertFalse(screen.state.value.metrics.any { it.first == "Estimated completion" })
    }

    @Test fun overdueGoalUsesTheDomainDeadlineProjectionAndOptionalPaceEstimate() = runTest(dispatcher) {
        val overdue = goal.copy(targetDate = "2026-10-01")
        storage.goals.value = listOf(overdue)
        vm.setMonthlyReductionMinor(100000); observe(); runCurrent()
        val result = vm.uiState.value.result!!
        assertTrue(result.isGoalOverdue)
        assertEquals(0L, result.monthsUntilDeadline)
        assertEquals(0L, result.potentialExtraByDeadlineMinor)
        assertEquals(result.currentSavedMinor, result.projectedBalanceAtDeadlineMinor)
        assertTrue(result.estimatedCompletionDate!!.isAfter(today))
    }

    @Test fun errorsAreSeparateFromEmptyStateAndRetryRestartsObservation() = runTest(dispatcher) {
        failure = IOException("private remote details")
        observe(); runCurrent()
        assertNull(vm.uiState.value.result)
        assertEquals("Please check your internet connection and try again.", vm.uiState.value.error)
        assertTrue(vm.uiState.value.canRetry)
        failure = null; vm.retry(); runCurrent()
        assertNull(vm.uiState.value.error); assertNotNull(vm.uiState.value.result)
    }

    @Test fun overflowAndMalformedGoalReturnSafeErrorsAndCorrectionCanRecalculate() = runTest(dispatcher) {
        vm.setMonthlyReductionMinor(Long.MAX_VALUE)
        observe(); runCurrent()
        assertNull(vm.uiState.value.result)
        assertEquals("This scenario couldn't be calculated. Try smaller amounts or check your goal's target and date.", vm.uiState.value.error)
        vm.setMonthlyReductionMinor(0); runCurrent()
        assertNotNull(vm.uiState.value.result)
        storage.goals.value = listOf(goal.copy(targetDate = "private-invalid-date")); runCurrent()
        assertFalse(vm.uiState.value.error.orEmpty().contains("private-invalid-date"))
        assertNull(vm.uiState.value.result)
        storage.goals.value = listOf(goal); runCurrent(); assertNotNull(vm.uiState.value.result)
    }

    @Test fun negativeDirectInputsDoNotReplaceValidScenarioValues() = runTest(dispatcher) {
        vm.setMonthlyReductionMinor(100000); vm.setPlannedContributionMinor(50000)
        observe(); runCurrent()
        val before = vm.uiState.value
        vm.setMonthlyReductionMinor(-1); vm.setPlannedContributionMinor(-1); runCurrent()
        assertEquals(before, vm.uiState.value)
    }

    @Test fun restorationLoadingIsNotPresentedAsAnEmptyGoal() = runTest(dispatcher) {
        storage.goals.value = emptyList()
        storage.restoration.value = SavingsRestorationState(isRefreshing = true)
        observe(); runCurrent(); assertTrue(vm.uiState.value.isLoading)
        storage.restoration.value = SavingsRestorationState(error = IOException("Offline"))
        runCurrent(); assertFalse(vm.uiState.value.isLoading); assertNotNull(vm.uiState.value.error)
        storage.restoration.value = SavingsRestorationState()
        storage.goals.value = listOf(goal); runCurrent(); assertNotNull(vm.uiState.value.result)
    }

    @Test fun invalidInputKeepsTheDraftAndCannotShowAnOldForecast() {
        val screen = SavingsSimulatorScreenViewModel(); screen.sync(source())
        assertTrue(screen.state.value.canShowResults)
        var calls = 0
        for (input in listOf("-1", "abc", "12.001", "999999999999999999999")) {
            screen.edit(input, true) { calls++ }
            assertNotNull(screen.state.value.reductionError)
            assertEquals(input, screen.state.value.reduction)
            assertFalse(screen.state.value.canShowResults)
            assertTrue(screen.state.value.metrics.isEmpty())
        }
        assertEquals(0, calls)
        screen.edit("", true) { assertEquals(0L, it); calls++ }
        assertNull(screen.state.value.reductionError)
        assertTrue(screen.state.value.canShowResults)
        assertEquals(1, calls)
    }

    @Test fun validInputWaitsForAMatchingCalculationAndUntouchedFieldsKeepSyncing() {
        val screen = SavingsSimulatorScreenViewModel(); screen.sync(source(100000, 50000))
        screen.edit("2500.75", true) { assertEquals(250075L, it) }
        assertFalse(screen.state.value.canShowResults)
        screen.sync(source(100000, 70000))
        assertFalse(screen.state.value.canShowResults)
        assertEquals("700.00", screen.state.value.planned)
        screen.sync(source(250075, 70000))
        assertTrue(screen.state.value.canShowResults)
        assertEquals("2500.75", screen.state.value.reduction)
        assertEquals("700.00", screen.state.value.planned)
    }

    @Test fun unknownOrExcessivelyDistantCompletionIsNotDisplayedAsADate() {
        val screen = SavingsSimulatorScreenViewModel(); screen.sync(source(1))
        assertTrue(screen.state.value.canShowResults)
        assertNull(screen.state.value.source.result!!.estimatedCompletionDate)
        assertFalse(screen.state.value.metrics.any { it.first == "Estimated completion" })
    }
}

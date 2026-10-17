package com.pfms.app.viewmodel

import com.pfms.app.data.FakeAuthRepository
import com.pfms.app.data.FakeExpenseRepository
import com.pfms.app.data.FakeIncomeRepository
import com.pfms.app.data.FakeSavingsGoalRepository
import com.pfms.app.domain.model.AuthUser
import com.pfms.app.domain.model.Expense
import com.pfms.app.domain.model.ExpenseClassification
import com.pfms.app.domain.model.GoalStatus
import com.pfms.app.domain.model.GoalTransaction
import com.pfms.app.domain.model.GoalTransactionType
import com.pfms.app.domain.model.Income
import com.pfms.app.domain.model.SavingsGoal
import com.pfms.app.domain.model.savings.GoalPaceStatus
import com.pfms.app.domain.usecase.analytics.GetMonthlyIncomeSummaryUseCase
import com.pfms.app.domain.usecase.savings.CalculateCurrentMonthGoalContributionUseCase
import com.pfms.app.domain.usecase.savings.CalculateDiscretionaryImpactUseCase
import com.pfms.app.domain.usecase.savings.CalculateGoalPaceUseCase
import com.pfms.app.domain.usecase.savings.CalculateGoalProgressUseCase
import com.pfms.app.domain.usecase.savings.CalculateMonthlyNetSavingsUseCase
import com.pfms.app.domain.usecase.savings.CalculateSavedAmountUseCase
import com.pfms.app.domain.usecase.savings.CalculateTrailingNetSavingsAverageUseCase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.LocalDate

@OptIn(ExperimentalCoroutinesApi::class)
class SavingsGoalViewModelTest {

    private val testDispatcher = StandardTestDispatcher()

    private lateinit var savingsGoalRepository: FakeSavingsGoalRepository
    private lateinit var expenseRepository: FakeExpenseRepository
    private lateinit var incomeRepository: FakeIncomeRepository
    private lateinit var authRepository: FakeAuthRepository
    private lateinit var viewModel: SavingsGoalViewModel

    private val fixedDate = LocalDate.of(2026, 10, 17)
    private val testUser = AuthUser(uid = "user-123", displayName = "Test User", email = "test@example.com", isEmailVerified = true)

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)

        savingsGoalRepository = FakeSavingsGoalRepository()
        expenseRepository = FakeExpenseRepository()
        incomeRepository = FakeIncomeRepository()
        authRepository = FakeAuthRepository(initialUser = testUser)

        val calculateSavedAmountUseCase = CalculateSavedAmountUseCase()
        val calculateGoalProgressUseCase = CalculateGoalProgressUseCase(calculateSavedAmountUseCase)
        val calculateCurrentMonthGoalContributionUseCase = CalculateCurrentMonthGoalContributionUseCase()
        val getMonthlyIncomeSummaryUseCase = GetMonthlyIncomeSummaryUseCase()
        val calculateMonthlyNetSavingsUseCase = CalculateMonthlyNetSavingsUseCase(getMonthlyIncomeSummaryUseCase)
        val calculateTrailingNetSavingsAverageUseCase = CalculateTrailingNetSavingsAverageUseCase(calculateMonthlyNetSavingsUseCase)
        val calculateGoalPaceUseCase = CalculateGoalPaceUseCase(
            calculateGoalProgressUseCase = calculateGoalProgressUseCase,
            calculateCurrentMonthGoalContributionUseCase = calculateCurrentMonthGoalContributionUseCase,
            calculateMonthlyNetSavingsUseCase = calculateMonthlyNetSavingsUseCase,
            calculateTrailingNetSavingsAverageUseCase = calculateTrailingNetSavingsAverageUseCase
        )
        val calculateDiscretionaryImpactUseCase = CalculateDiscretionaryImpactUseCase()

        viewModel = SavingsGoalViewModel(
            savingsGoalRepository = savingsGoalRepository,
            expenseRepository = expenseRepository,
            incomeRepository = incomeRepository,
            authRepository = authRepository,
            calculateSavedAmountUseCase = calculateSavedAmountUseCase,
            calculateGoalProgressUseCase = calculateGoalProgressUseCase,
            calculateCurrentMonthGoalContributionUseCase = calculateCurrentMonthGoalContributionUseCase,
            calculateMonthlyNetSavingsUseCase = calculateMonthlyNetSavingsUseCase,
            calculateTrailingNetSavingsAverageUseCase = calculateTrailingNetSavingsAverageUseCase,
            calculateGoalPaceUseCase = calculateGoalPaceUseCase,
            calculateDiscretionaryImpactUseCase = calculateDiscretionaryImpactUseCase
        ).apply {
            dateProvider = { fixedDate }
        }
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    // 1. no active goal produces empty safe state
    @Test
    fun `no active goal produces empty safe state`() = runTest {
        advanceUntilIdle()
        val state = viewModel.uiState.value

        assertNull(state.activeGoal)
        assertTrue(state.transactions.isEmpty())
        assertEquals(0L, state.currentSavedMinor)
        assertEquals(0L, state.remainingAmountMinor)
        assertEquals(0.0, state.progressPercent, 0.001)
        assertEquals(0L, state.remainingMonths)
        assertEquals(0L, state.requiredMonthlySavingMinor)
        assertFalse(state.isCompleted)
        assertFalse(state.isOverdue)
        assertNull(state.pace)
        assertNull(state.discretionaryImpact)
        assertFalse(state.isLoading)
    }

    // 2. active goal appears reactively
    @Test
    fun `active goal appears reactively`() = runTest {
        val goal = SavingsGoal(
            id = "g-1",
            userId = "user-123",
            name = "MacBook Pro",
            targetAmountMinor = 40_000_000L,
            targetDate = "2027-10-17",
            openingSavedAmountMinor = 10_000_000L,
            status = GoalStatus.ACTIVE
        )
        savingsGoalRepository.goals.value = listOf(goal)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals("g-1", state.activeGoal?.id)
        assertEquals("MacBook Pro", state.activeGoal?.name)
        assertEquals(10_000_000L, state.currentSavedMinor)
    }

    // 3. transactions follow active goal
    @Test
    fun `transactions follow active goal`() = runTest {
        val goal = SavingsGoal(id = "g-1", userId = "user-123", name = "MacBook Pro", status = GoalStatus.ACTIVE)
        val tx1 = GoalTransaction(id = "tx-1", goalId = "g-1", userId = "user-123", amountMinor = 50_000L, date = "2026-10-17")
        val txOther = GoalTransaction(id = "tx-2", goalId = "g-other", userId = "user-123", amountMinor = 99_000L, date = "2026-10-17")

        savingsGoalRepository.goals.value = listOf(goal)
        savingsGoalRepository.transactions.value = listOf(tx1, txOther)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(1, state.transactions.size)
        assertEquals("tx-1", state.transactions.first().id)
    }

    // 4. switching goal cancels old transaction state
    @Test
    fun `switching goal cancels old transaction state`() = runTest {
        val goal1 = SavingsGoal(id = "g-1", userId = "user-123", name = "Goal 1", status = GoalStatus.ACTIVE)
        val goal2 = SavingsGoal(id = "g-2", userId = "user-123", name = "Goal 2", status = GoalStatus.ARCHIVED)

        val tx1 = GoalTransaction(id = "tx-1", goalId = "g-1", userId = "user-123", amountMinor = 50_000L)
        val tx2 = GoalTransaction(id = "tx-2", goalId = "g-2", userId = "user-123", amountMinor = 80_000L)

        savingsGoalRepository.goals.value = listOf(goal1, goal2)
        savingsGoalRepository.transactions.value = listOf(tx1, tx2)
        advanceUntilIdle()

        assertEquals("g-1", viewModel.uiState.value.activeGoal?.id)
        assertEquals("tx-1", viewModel.uiState.value.transactions.first().id)

        // Switch active goal to g-2
        savingsGoalRepository.goals.value = listOf(
            goal1.copy(status = GoalStatus.ARCHIVED),
            goal2.copy(status = GoalStatus.ACTIVE)
        )
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals("g-2", state.activeGoal?.id)
        assertEquals(1, state.transactions.size)
        assertEquals("tx-2", state.transactions.first().id)
    }

    // 5. current saved = opening + contributions - withdrawals
    @Test
    fun `current saved equals opening plus contributions minus withdrawals`() = runTest {
        val goal = SavingsGoal(
            id = "g-1",
            userId = "user-123",
            name = "Goal",
            targetAmountMinor = 1_000_000L,
            openingSavedAmountMinor = 500_000L,
            status = GoalStatus.ACTIVE
        )
        val txDeposit = GoalTransaction(id = "tx-1", goalId = "g-1", amountMinor = 200_000L, transactionType = GoalTransactionType.CONTRIBUTION, date = "2026-10-10")
        val txWithdraw = GoalTransaction(id = "tx-2", goalId = "g-1", amountMinor = 50_000L, transactionType = GoalTransactionType.WITHDRAWAL, date = "2026-10-12")

        savingsGoalRepository.goals.value = listOf(goal)
        savingsGoalRepository.transactions.value = listOf(txDeposit, txWithdraw)
        advanceUntilIdle()

        // 500_000 + 200_000 - 50_000 = 650_000
        assertEquals(650_000L, viewModel.uiState.value.currentSavedMinor)
    }

    // 6. progress percentage correct
    // 7. remaining amount correct
    // 8. remaining months correct
    // 9. required monthly saving correct
    @Test
    fun `progress metrics calculated correctly`() = runTest {
        // Goal: 1,000,000 target, opening 200,000, target date 10 months ahead (2027-08-17)
        val goal = SavingsGoal(
            id = "g-1",
            userId = "user-123",
            name = "Trip",
            targetAmountMinor = 1_000_000L,
            openingSavedAmountMinor = 200_000L,
            targetDate = "2027-08-17",
            status = GoalStatus.ACTIVE
        )
        savingsGoalRepository.goals.value = listOf(goal)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(20.0, state.progressPercent, 0.001) // 200,000 / 1,000,000 = 20%
        assertEquals(800_000L, state.remainingAmountMinor) // 1,000,000 - 200,000 = 800,000
        assertEquals(10L, state.remainingMonths) // Oct 2026 to Aug 2027 = 10 months
        assertEquals(80_000L, state.requiredMonthlySavingMinor) // 800,000 / 10 = 80,000
    }

    // 10. current-month contribution calculation correct
    // 11. withdrawals excluded from contribution total
    @Test
    fun `current-month goal contribution correctly accounts for deposits and ignores other months`() = runTest {
        val goal = SavingsGoal(id = "g-1", userId = "user-123", name = "Goal", status = GoalStatus.ACTIVE)
        val currentMonthContrib = GoalTransaction(id = "tx-1", goalId = "g-1", amountMinor = 50_000L, transactionType = GoalTransactionType.CONTRIBUTION, date = "2026-10-05")
        val currentMonthWithdrawal = GoalTransaction(id = "tx-2", goalId = "g-1", amountMinor = 10_000L, transactionType = GoalTransactionType.WITHDRAWAL, date = "2026-10-08")
        val previousMonthContrib = GoalTransaction(id = "tx-3", goalId = "g-1", amountMinor = 40_000L, transactionType = GoalTransactionType.CONTRIBUTION, date = "2026-09-20")

        savingsGoalRepository.goals.value = listOf(goal)
        savingsGoalRepository.transactions.value = listOf(currentMonthContrib, currentMonthWithdrawal, previousMonthContrib)
        advanceUntilIdle()

        // Oct net contribution: 50_000 - 10_000 = 40_000
        assertEquals(40_000L, viewModel.uiState.value.currentMonthContributionMinor)
    }

    // 12. current-month net savings correct
    @Test
    fun `current-month net savings equals realized income minus total expenses`() = runTest {
        val goal = SavingsGoal(id = "g-1", userId = "user-123", name = "Goal", status = GoalStatus.ACTIVE)
        savingsGoalRepository.goals.value = listOf(goal)

        val income = Income(id = "inc-1", userId = "user-123", amountMinor = 200_000L, amountLkrMinor = 200_000L, receivedDate = "2026-10-01", paymentStatus = "Received")
        val expense = Expense(id = "exp-1", userId = "user-123", amountMinor = 80_000L, date = "2026-10-05")

        incomeRepository.incomes.value = listOf(income)
        expenseRepository.addExpense(expense)
        advanceUntilIdle()

        // 200,000 - 80,000 = 120,000
        assertEquals(120_000L, viewModel.uiState.value.currentMonthNetSavingsMinor)
    }

    // 13. trailing 3-month average correct
    @Test
    fun `trailing 3-month net savings average divides M-3 to M-1 by three`() = runTest {
        val goal = SavingsGoal(id = "g-1", userId = "user-123", name = "Goal", status = GoalStatus.ACTIVE)
        savingsGoalRepository.goals.value = listOf(goal)

        // M-1 (Sep 2026): net = 90_000
        val incSep = Income(id = "inc-sep", userId = "user-123", amountMinor = 150_000L, amountLkrMinor = 150_000L, receivedDate = "2026-09-01", paymentStatus = "Received")
        val expSep = Expense(id = "exp-sep", userId = "user-123", amountMinor = 60_000L, date = "2026-09-10")

        // M-2 (Aug 2026): net = 60_000
        val incAug = Income(id = "inc-aug", userId = "user-123", amountMinor = 120_000L, amountLkrMinor = 120_000L, receivedDate = "2026-08-01", paymentStatus = "Received")
        val expAug = Expense(id = "exp-aug", userId = "user-123", amountMinor = 60_000L, date = "2026-08-10")

        // M-3 (Jul 2026): net = 30_000
        val incJul = Income(id = "inc-jul", userId = "user-123", amountMinor = 100_000L, amountLkrMinor = 100_000L, receivedDate = "2026-07-01", paymentStatus = "Received")
        val expJul = Expense(id = "exp-jul", userId = "user-123", amountMinor = 70_000L, date = "2026-07-10")

        incomeRepository.incomes.value = listOf(incSep, incAug, incJul)
        expenseRepository.addExpense(expSep)
        expenseRepository.addExpense(expAug)
        expenseRepository.addExpense(expJul)
        advanceUntilIdle()

        // Total = 90_000 + 60_000 + 30_000 = 180_000; Avg = 180_000 / 3 = 60_000
        assertEquals(60_000L, viewModel.uiState.value.trailingThreeMonthNetSavingsMinor)
    }

    // 14. pace ON_TRACK correct
    // 15. pace BEHIND correct
    // 16. shortfall correct
    @Test
    fun `pace evaluates ON_TRACK and BEHIND against trailing average benchmark`() = runTest {
        // Required saving = 50_000/mo
        val goal = SavingsGoal(
            id = "g-1",
            userId = "user-123",
            name = "Goal",
            targetAmountMinor = 500_000L,
            openingSavedAmountMinor = 0L,
            targetDate = "2027-08-17", // 10 months -> 50_000/mo
            status = GoalStatus.ACTIVE
        )
        savingsGoalRepository.goals.value = listOf(goal)

        // Trailing avg = 60_000 -> >= 50_000 -> ON_TRACK
        val inc = Income(id = "inc-sep", userId = "user-123", amountMinor = 180_000L, amountLkrMinor = 180_000L, receivedDate = "2026-09-01", paymentStatus = "Received")
        incomeRepository.incomes.value = listOf(inc)
        advanceUntilIdle()

        val stateOnTrack = viewModel.uiState.value
        assertEquals(GoalPaceStatus.ON_TRACK, stateOnTrack.pace?.paceStatus)
        assertEquals(0L, stateOnTrack.pace?.shortfallMinor)

        // Now lower income so trailing avg = 20_000 (< 50_000) -> BEHIND, shortfall = 30_000
        val incLower = Income(id = "inc-sep", userId = "user-123", amountMinor = 60_000L, amountLkrMinor = 60_000L, receivedDate = "2026-09-01", paymentStatus = "Received")
        incomeRepository.incomes.value = listOf(incLower)
        advanceUntilIdle()

        val stateBehind = viewModel.uiState.value
        assertEquals(GoalPaceStatus.BEHIND, stateBehind.pace?.paceStatus)
        assertEquals(30_000L, stateBehind.pace?.shortfallMinor) // 50_000 - 20_000 = 30_000
    }

    // 17. projected completion populated when pace > 0
    // 18. projected completion safe when pace <= 0
    @Test
    fun `projected completion date handles positive and non-positive pace safely`() = runTest {
        val goal = SavingsGoal(
            id = "g-1",
            userId = "user-123",
            name = "Goal",
            targetAmountMinor = 100_000L,
            openingSavedAmountMinor = 0L,
            targetDate = "2027-10-17",
            status = GoalStatus.ACTIVE
        )
        savingsGoalRepository.goals.value = listOf(goal)

        // Pace = 50_000/mo -> 100_000 / 50_000 = 2 months from fixedDate (2026-10-17) -> 2026-12-17
        val inc = Income(id = "inc-1", userId = "user-123", amountMinor = 150_000L, amountLkrMinor = 150_000L, receivedDate = "2026-09-01", paymentStatus = "Received")
        incomeRepository.incomes.value = listOf(inc)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertNotNull(state.pace?.projectedCompletionDate)
        assertEquals(LocalDate.of(2026, 12, 17), state.pace?.projectedCompletionDate)

        // Zero/negative pace: no income, high expense
        val exp = Expense(id = "exp-1", userId = "user-123", amountMinor = 500_000L, date = "2026-09-01")
        expenseRepository.addExpense(exp)
        advanceUntilIdle()

        assertNull(viewModel.uiState.value.pace?.projectedCompletionDate)
    }

    // 19. only discretionary expenses counted for FR-27
    // 20. committed expenses excluded
    @Test
    fun `discretionary impact only counts DISCRETIONARY classification`() = runTest {
        // Goal requires 50_000/mo
        val goal = SavingsGoal(
            id = "g-1",
            userId = "user-123",
            name = "Goal",
            targetAmountMinor = 500_000L,
            targetDate = "2027-08-17", // 10 months -> 50_000/mo
            status = GoalStatus.ACTIVE
        )
        savingsGoalRepository.goals.value = listOf(goal)

        val discExp = Expense(
            id = "exp-d",
            userId = "user-123",
            amountMinor = 25_000L,
            classification = ExpenseClassification.DISCRETIONARY,
            date = "2026-10-05"
        )
        val committedExp = Expense(
            id = "exp-c",
            userId = "user-123",
            amountMinor = 100_000L,
            classification = ExpenseClassification.COMMITTED,
            date = "2026-10-06"
        )
        expenseRepository.addExpense(discExp)
        expenseRepository.addExpense(committedExp)
        advanceUntilIdle()

        val impact = viewModel.uiState.value.discretionaryImpact
        assertNotNull(impact)
        assertEquals(25_000L, impact?.discretionaryExpensesMinor)
        assertEquals(100_000L, impact?.committedExpensesMinor)
        // 25_000 / 50_000 = 50.0%
        assertEquals(50.0, impact?.percentageOfRequiredSaving ?: 0.0, 0.001)
    }

    // 21. overdue unfinished goal sets isOverdue=true
    @Test
    fun `overdue unfinished goal exposes isOverdue true`() = runTest {
        val overdueGoal = SavingsGoal(
            id = "g-overdue",
            userId = "user-123",
            name = "Expired Goal",
            targetAmountMinor = 500_000L,
            openingSavedAmountMinor = 100_000L,
            targetDate = "2026-09-01", // Before fixedDate (2026-10-17)
            status = GoalStatus.ACTIVE
        )
        savingsGoalRepository.goals.value = listOf(overdueGoal)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertTrue(state.isOverdue)
        assertFalse(state.isCompleted)
    }

    // 22. completed goal does not show overdue
    // 23. completed goal state works at >=100%
    @Test
    fun `completed goal exposes isCompleted true and isOverdue false even if targetDate is past`() = runTest {
        val completedGoal = SavingsGoal(
            id = "g-done",
            userId = "user-123",
            name = "Done Goal",
            targetAmountMinor = 500_000L,
            openingSavedAmountMinor = 600_000L, // >= 100%
            targetDate = "2026-09-01", // In past
            status = GoalStatus.ACTIVE
        )
        savingsGoalRepository.goals.value = listOf(completedGoal)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertTrue(state.isCompleted)
        assertFalse(state.isOverdue)
        assertEquals(100.0, state.progressPercent, 0.001)
        assertEquals(0L, state.remainingAmountMinor)
    }

    // 24. create goal success
    @Test
    fun `create goal success adds goal to repository`() = runTest {
        val newGoal = SavingsGoal(
            id = "g-new",
            name = "Trip to Tokyo",
            targetAmountMinor = 800_000L,
            status = GoalStatus.ACTIVE
        )
        viewModel.createGoal(newGoal)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals("g-new", state.activeGoal?.id)
        assertEquals("user-123", state.activeGoal?.userId) // User ID auto-assigned from AuthRepository
        assertNull(state.errorMessage)
        assertFalse(state.operationInProgress)
    }

    // 25. second active goal failure is surfaced
    @Test
    fun `second active goal creation surfaces error and preserves existing active goal`() = runTest {
        val existingActive = SavingsGoal(id = "g-1", userId = "user-123", name = "Goal 1", status = GoalStatus.ACTIVE)
        savingsGoalRepository.goals.value = listOf(existingActive)
        advanceUntilIdle()

        val secondActive = SavingsGoal(id = "g-2", userId = "user-123", name = "Goal 2", status = GoalStatus.ACTIVE)
        viewModel.createGoal(secondActive)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals("An active savings goal already exists", state.errorMessage)
        assertEquals("g-1", state.activeGoal?.id)
    }

    // 26. update goal success
    @Test
    fun `update goal success modifies repository state`() = runTest {
        val goal = SavingsGoal(id = "g-1", userId = "user-123", name = "Old Name", status = GoalStatus.ACTIVE)
        savingsGoalRepository.goals.value = listOf(goal)
        advanceUntilIdle()

        viewModel.updateGoal(goal.copy(name = "Updated Name"))
        advanceUntilIdle()

        assertEquals("Updated Name", viewModel.uiState.value.activeGoal?.name)
    }

    // 27. archive goal success
    @Test
    fun `archive goal success sets status to ARCHIVED and resets active goal`() = runTest {
        val goal = SavingsGoal(id = "g-1", userId = "user-123", name = "Active Goal", status = GoalStatus.ACTIVE)
        savingsGoalRepository.goals.value = listOf(goal)
        advanceUntilIdle()

        viewModel.archiveGoal("g-1")
        advanceUntilIdle()

        assertNull(viewModel.uiState.value.activeGoal)
    }

    // 28. delete goal success
    @Test
    fun `delete goal success removes goal from repository`() = runTest {
        val goal = SavingsGoal(id = "g-1", userId = "user-123", name = "Goal", status = GoalStatus.ACTIVE)
        savingsGoalRepository.goals.value = listOf(goal)
        advanceUntilIdle()

        viewModel.deleteGoal("g-1")
        advanceUntilIdle()

        assertNull(viewModel.uiState.value.activeGoal)
    }

    // 29. contribution creation success
    @Test
    fun `contribution creation adds positive transaction to repository`() = runTest {
        val goal = SavingsGoal(id = "g-1", userId = "user-123", name = "Goal", status = GoalStatus.ACTIVE)
        savingsGoalRepository.goals.value = listOf(goal)
        advanceUntilIdle()

        viewModel.addContribution(amountMinor = 15_000L, note = "Salary cut")
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(1, state.transactions.size)
        val tx = state.transactions.first()
        assertEquals(15_000L, tx.amountMinor)
        assertEquals(GoalTransactionType.CONTRIBUTION, tx.transactionType)
        assertEquals("Salary cut", tx.note)
        assertEquals("2026-10-17", tx.date)
    }

    // 30. withdrawal creation success
    @Test
    fun `withdrawal creation adds withdrawal transaction with positive amount`() = runTest {
        val goal = SavingsGoal(id = "g-1", userId = "user-123", name = "Goal", status = GoalStatus.ACTIVE)
        savingsGoalRepository.goals.value = listOf(goal)
        advanceUntilIdle()

        viewModel.addWithdrawal(amountMinor = 5_000L, note = "Emergency")
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(1, state.transactions.size)
        val tx = state.transactions.first()
        assertEquals(5_000L, tx.amountMinor)
        assertEquals(GoalTransactionType.WITHDRAWAL, tx.transactionType)
    }

    // 31. transaction update success
    @Test
    fun `transaction update success updates repository record`() = runTest {
        val goal = SavingsGoal(id = "g-1", userId = "user-123", name = "Goal", status = GoalStatus.ACTIVE)
        val tx = GoalTransaction(id = "tx-1", goalId = "g-1", userId = "user-123", amountMinor = 10_000L)
        savingsGoalRepository.goals.value = listOf(goal)
        savingsGoalRepository.transactions.value = listOf(tx)
        advanceUntilIdle()

        viewModel.updateTransaction(tx.copy(amountMinor = 25_000L))
        advanceUntilIdle()

        assertEquals(25_000L, viewModel.uiState.value.transactions.first().amountMinor)
    }

    // 32. transaction delete success
    @Test
    fun `transaction delete success removes record from ledger`() = runTest {
        val goal = SavingsGoal(id = "g-1", userId = "user-123", name = "Goal", status = GoalStatus.ACTIVE)
        val tx = GoalTransaction(id = "tx-1", goalId = "g-1", userId = "user-123", amountMinor = 10_000L)
        savingsGoalRepository.goals.value = listOf(goal)
        savingsGoalRepository.transactions.value = listOf(tx)
        advanceUntilIdle()

        viewModel.deleteTransaction("tx-1")
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.transactions.isEmpty())
    }

    // 33. unauthenticated write blocked safely
    @Test
    fun `unauthenticated write blocked safely and surfaces auth error`() = runTest {
        authRepository.userFlow.value = null // Sign out
        val goal = SavingsGoal(id = "g-1", name = "Offline Goal", status = GoalStatus.ACTIVE)

        viewModel.createGoal(goal)
        advanceUntilIdle()

        assertEquals("Authentication required: no active user session", viewModel.uiState.value.errorMessage)
        assertTrue(savingsGoalRepository.goals.value.isEmpty())
    }

    // 34. repository failure exposed as UI-safe error
    @Test
    fun `repository failure exposed as UI-safe error`() = runTest {
        savingsGoalRepository.failureException = RuntimeException("Firestore connection reset")

        viewModel.createGoal(SavingsGoal(id = "g-1", status = GoalStatus.ACTIVE))
        advanceUntilIdle()

        assertEquals("Firestore connection reset", viewModel.uiState.value.errorMessage)
    }

    // 35. operationInProgress resets after success
    // 36. operationInProgress resets after failure
    @Test
    fun `operationInProgress resets to false after success and failure`() = runTest {
        val goal = SavingsGoal(id = "g-1", status = GoalStatus.ACTIVE)

        // Success
        viewModel.createGoal(goal)
        advanceUntilIdle()
        assertFalse(viewModel.uiState.value.operationInProgress)

        // Failure
        savingsGoalRepository.failureException = RuntimeException("Network timeout")
        viewModel.updateGoal(goal)
        advanceUntilIdle()
        assertFalse(viewModel.uiState.value.operationInProgress)
    }

    @Test
    fun `clearError clears transient error message`() = runTest {
        savingsGoalRepository.failureException = RuntimeException("Error")
        viewModel.createGoal(SavingsGoal(id = "g-1", status = GoalStatus.ACTIVE))
        advanceUntilIdle()

        assertEquals("Error", viewModel.uiState.value.errorMessage)

        viewModel.clearError()
        advanceUntilIdle()
        assertNull(viewModel.uiState.value.errorMessage)
    }
}

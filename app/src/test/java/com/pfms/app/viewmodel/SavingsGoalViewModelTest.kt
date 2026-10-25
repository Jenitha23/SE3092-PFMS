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
    fun `1 no active goal produces empty safe state`() = runTest {
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
    fun `2 active goal appears reactively`() = runTest {
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
    fun `3 transactions follow active goal`() = runTest {
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
    fun `4 switching goal cancels old transaction state`() = runTest {
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
    fun `5 current saved equals opening plus contributions minus withdrawals`() = runTest {
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
    @Test
    fun `6 progress percentage correct`() = runTest {
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

        assertEquals(20.0, viewModel.uiState.value.progressPercent, 0.001)
    }

    // 7. remaining amount correct
    @Test
    fun `7 remaining amount correct`() = runTest {
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

        assertEquals(800_000L, viewModel.uiState.value.remainingAmountMinor)
    }

    // 8. remaining months correct
    @Test
    fun `8 remaining months correct`() = runTest {
        val goal = SavingsGoal(
            id = "g-1",
            userId = "user-123",
            name = "Trip",
            targetAmountMinor = 1_000_000L,
            openingSavedAmountMinor = 200_000L,
            targetDate = "2027-08-17", // Oct 2026 to Aug 2027 = 10 months
            status = GoalStatus.ACTIVE
        )
        savingsGoalRepository.goals.value = listOf(goal)
        advanceUntilIdle()

        assertEquals(10L, viewModel.uiState.value.remainingMonths)
    }

    // 9. required monthly saving correct
    @Test
    fun `9 required monthly saving correct`() = runTest {
        val goal = SavingsGoal(
            id = "g-1",
            userId = "user-123",
            name = "Trip",
            targetAmountMinor = 1_000_000L,
            openingSavedAmountMinor = 200_000L,
            targetDate = "2027-08-17", // 800_000 / 10 = 80_000
            status = GoalStatus.ACTIVE
        )
        savingsGoalRepository.goals.value = listOf(goal)
        advanceUntilIdle()

        assertEquals(80_000L, viewModel.uiState.value.requiredMonthlySavingMinor)
    }

    // 10. current-month contribution calculation correct
    @Test
    fun `10 current-month contribution calculation correct`() = runTest {
        val goal = SavingsGoal(id = "g-1", userId = "user-123", name = "Goal", status = GoalStatus.ACTIVE)
        val currentMonthContrib1 = GoalTransaction(id = "tx-1", goalId = "g-1", amountMinor = 30_000L, transactionType = GoalTransactionType.CONTRIBUTION, date = "2026-10-05")
        val currentMonthContrib2 = GoalTransaction(id = "tx-2", goalId = "g-1", amountMinor = 20_000L, transactionType = GoalTransactionType.CONTRIBUTION, date = "2026-10-10")
        val previousMonthContrib = GoalTransaction(id = "tx-3", goalId = "g-1", amountMinor = 50_000L, transactionType = GoalTransactionType.CONTRIBUTION, date = "2026-09-20")

        savingsGoalRepository.goals.value = listOf(goal)
        savingsGoalRepository.transactions.value = listOf(currentMonthContrib1, currentMonthContrib2, previousMonthContrib)
        advanceUntilIdle()

        assertEquals(50_000L, viewModel.uiState.value.currentMonthContributionMinor)
    }

    // 11. withdrawals excluded from contribution total
    @Test
    fun `11 withdrawals excluded from increasing contribution total`() = runTest {
        val goal = SavingsGoal(id = "g-1", userId = "user-123", name = "Goal", status = GoalStatus.ACTIVE)
        val currentMonthContrib = GoalTransaction(id = "tx-1", goalId = "g-1", amountMinor = 50_000L, transactionType = GoalTransactionType.CONTRIBUTION, date = "2026-10-05")
        val currentMonthWithdrawal = GoalTransaction(id = "tx-2", goalId = "g-1", amountMinor = 10_000L, transactionType = GoalTransactionType.WITHDRAWAL, date = "2026-10-08")

        savingsGoalRepository.goals.value = listOf(goal)
        savingsGoalRepository.transactions.value = listOf(currentMonthContrib, currentMonthWithdrawal)
        advanceUntilIdle()

        // 50_000 deposit - 10_000 withdrawal = 40_000 net contribution
        assertEquals(40_000L, viewModel.uiState.value.currentMonthContributionMinor)
    }

    // 12. current-month net savings correct
    @Test
    fun `12 current-month net savings correct`() = runTest {
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
    fun `13 trailing 3-month average correct`() = runTest {
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
    @Test
    fun `14 pace ON_TRACK correct`() = runTest {
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

        assertEquals(GoalPaceStatus.ON_TRACK, viewModel.uiState.value.pace?.paceStatus)
    }

    // 15. pace BEHIND correct
    @Test
    fun `15 pace BEHIND correct`() = runTest {
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

        // Trailing avg = 20_000 (< 50_000) -> BEHIND
        val incLower = Income(id = "inc-sep", userId = "user-123", amountMinor = 60_000L, amountLkrMinor = 60_000L, receivedDate = "2026-09-01", paymentStatus = "Received")
        incomeRepository.incomes.value = listOf(incLower)
        advanceUntilIdle()

        assertEquals(GoalPaceStatus.BEHIND, viewModel.uiState.value.pace?.paceStatus)
    }

    // 16. shortfall correct
    @Test
    fun `16 shortfall correct`() = runTest {
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

        // Trailing avg = 20_000 (< 50_000) -> Shortfall = 50_000 - 20_000 = 30_000
        val incLower = Income(id = "inc-sep", userId = "user-123", amountMinor = 60_000L, amountLkrMinor = 60_000L, receivedDate = "2026-09-01", paymentStatus = "Received")
        incomeRepository.incomes.value = listOf(incLower)
        advanceUntilIdle()

        assertEquals(30_000L, viewModel.uiState.value.pace?.shortfallMinor)
    }

    // 17. projected completion populated when pace > 0
    @Test
    fun `17 projected completion populated when pace greater than 0`() = runTest {
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
    }

    // 18. projected completion safe when pace <= 0
    @Test
    fun `18 projected completion safe when pace less than or equal to 0`() = runTest {
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

        // Zero/negative pace: no income, high expense
        val exp = Expense(id = "exp-1", userId = "user-123", amountMinor = 500_000L, date = "2026-09-01")
        expenseRepository.addExpense(exp)
        advanceUntilIdle()

        assertNull(viewModel.uiState.value.pace?.projectedCompletionDate)
    }

    // 19. only discretionary expenses counted for FR-27
    @Test
    fun `19 only discretionary expenses counted for FR-27`() = runTest {
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
        expenseRepository.addExpense(discExp)
        advanceUntilIdle()

        val impact = viewModel.uiState.value.discretionaryImpact
        assertNotNull(impact)
        assertEquals(25_000L, impact?.discretionaryExpensesMinor)
        assertEquals(50.0, impact?.percentageOfRequiredSaving ?: 0.0, 0.001)
    }

    // 20. committed expenses excluded
    @Test
    fun `20 committed expenses excluded`() = runTest {
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
        // 25_000 / 50_000 = 50.0% (committed is excluded from the numerator)
        assertEquals(50.0, impact?.percentageOfRequiredSaving ?: 0.0, 0.001)
    }

    // 21. overdue unfinished goal sets isOverdue=true
    @Test
    fun `21 overdue unfinished goal sets isOverdue true`() = runTest {
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
    @Test
    fun `22 completed goal does not show overdue`() = runTest {
        val completedGoal = SavingsGoal(
            id = "g-done",
            userId = "user-123",
            name = "Done Goal",
            targetAmountMinor = 500_000L,
            openingSavedAmountMinor = 500_000L, // 100%
            targetDate = "2026-09-01", // In past
            status = GoalStatus.ACTIVE
        )
        savingsGoalRepository.goals.value = listOf(completedGoal)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertTrue(state.isCompleted)
        assertFalse(state.isOverdue)
    }

    // 23. completed goal state works at >=100%
    @Test
    fun `23 completed goal state works at greater than or equal to 100 percent`() = runTest {
        val surplusGoal = SavingsGoal(
            id = "g-surplus",
            userId = "user-123",
            name = "Surplus Goal",
            targetAmountMinor = 500_000L,
            openingSavedAmountMinor = 750_000L, // 150%
            targetDate = "2027-10-17",
            status = GoalStatus.ACTIVE
        )
        savingsGoalRepository.goals.value = listOf(surplusGoal)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertTrue(state.isCompleted)
        assertEquals(100.0, state.progressPercent, 0.001)
        assertEquals(0L, state.remainingAmountMinor)
        assertEquals(0L, state.requiredMonthlySavingMinor)
    }

    // 24. create goal success
    @Test
    fun `24 create goal success`() = runTest {
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
    fun `25 second active goal failure is surfaced`() = runTest {
        val existingActive = SavingsGoal(id = "g-1", userId = "user-123", name = "Goal 1", status = GoalStatus.ACTIVE)
        savingsGoalRepository.goals.value = listOf(existingActive)
        advanceUntilIdle()

        val secondActive = SavingsGoal(id = "g-2", userId = "user-123", name = "Goal 2", status = GoalStatus.ACTIVE)
        viewModel.createGoal(secondActive)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals("You already have an active savings goal. Edit or archive it before creating another.", state.errorMessage)
        assertEquals("g-1", state.activeGoal?.id)
    }

    // 26. update goal success
    @Test
    fun `26 update goal success`() = runTest {
        val goal = SavingsGoal(id = "g-1", userId = "user-123", name = "Old Name", status = GoalStatus.ACTIVE)
        savingsGoalRepository.goals.value = listOf(goal)
        advanceUntilIdle()

        viewModel.updateGoal(goal.copy(name = "Updated Name"))
        advanceUntilIdle()

        assertEquals("Updated Name", viewModel.uiState.value.activeGoal?.name)
    }

    // 27. archive goal success
    @Test
    fun `27 archive goal success`() = runTest {
        val goal = SavingsGoal(id = "g-1", userId = "user-123", name = "Active Goal", status = GoalStatus.ACTIVE)
        savingsGoalRepository.goals.value = listOf(goal)
        advanceUntilIdle()

        viewModel.archiveGoal("g-1")
        advanceUntilIdle()

        assertNull(viewModel.uiState.value.activeGoal)
    }

    // 28. delete goal success
    @Test
    fun `28 delete goal success`() = runTest {
        val goal = SavingsGoal(id = "g-1", userId = "user-123", name = "Goal", status = GoalStatus.ACTIVE)
        savingsGoalRepository.goals.value = listOf(goal)
        advanceUntilIdle()

        viewModel.deleteGoal("g-1")
        advanceUntilIdle()

        assertNull(viewModel.uiState.value.activeGoal)
    }

    // 29. contribution creation success
    @Test
    fun `29 contribution creation success`() = runTest {
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
    fun `30 withdrawal creation success`() = runTest {
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
    fun `31 transaction update success`() = runTest {
        val goal = SavingsGoal(id = "g-1", userId = "user-123", name = "Goal", status = GoalStatus.ACTIVE)
        val tx = GoalTransaction(id = "tx-1", goalId = "g-1", userId = "user-123", amountMinor = 10_000L)
        savingsGoalRepository.goals.value = listOf(goal)
        savingsGoalRepository.transactions.value = listOf(tx)
        advanceUntilIdle()

        viewModel.updateTransaction(tx.copy(amountMinor = 25_000L))
        advanceUntilIdle()

        assertEquals(25_000L, viewModel.uiState.value.transactions.first().amountMinor)
        assertNull(viewModel.uiState.value.errorMessage)
        assertFalse(viewModel.uiState.value.operationInProgress)
    }

    @Test
    fun `updateTransaction with zero amount is rejected and repository not called`() = runTest {
        val goal = SavingsGoal(id = "g-1", userId = "user-123", name = "Goal", status = GoalStatus.ACTIVE)
        val tx = GoalTransaction(id = "tx-1", goalId = "g-1", userId = "user-123", amountMinor = 10_000L)
        savingsGoalRepository.goals.value = listOf(goal)
        savingsGoalRepository.transactions.value = listOf(tx)
        advanceUntilIdle()

        viewModel.updateTransaction(tx.copy(amountMinor = 0L))
        advanceUntilIdle()

        assertEquals("Transaction amount must be greater than zero", viewModel.uiState.value.errorMessage)
        assertEquals(10_000L, savingsGoalRepository.transactions.value.first().amountMinor)
        assertEquals(10_000L, viewModel.uiState.value.transactions.first().amountMinor)
        assertFalse(viewModel.uiState.value.operationInProgress)
    }

    @Test
    fun `updateTransaction with negative amount is rejected and repository not called`() = runTest {
        val goal = SavingsGoal(id = "g-1", userId = "user-123", name = "Goal", status = GoalStatus.ACTIVE)
        val tx = GoalTransaction(id = "tx-1", goalId = "g-1", userId = "user-123", amountMinor = 10_000L)
        savingsGoalRepository.goals.value = listOf(goal)
        savingsGoalRepository.transactions.value = listOf(tx)
        advanceUntilIdle()

        val negativeTx = createGoalTransactionWithAmount(
            id = "tx-1",
            goalId = "g-1",
            userId = "user-123",
            amountMinor = -5_000L
        )
        viewModel.updateTransaction(negativeTx)
        advanceUntilIdle()

        assertEquals("Transaction amount must be greater than zero", viewModel.uiState.value.errorMessage)
        assertEquals(10_000L, savingsGoalRepository.transactions.value.first().amountMinor)
        assertEquals(10_000L, viewModel.uiState.value.transactions.first().amountMinor)
        assertFalse(viewModel.uiState.value.operationInProgress)
    }

    private fun createGoalTransactionWithAmount(
        id: String = "tx-1",
        goalId: String = "g-1",
        userId: String = "user-123",
        amountMinor: Long
    ): GoalTransaction {
        if (amountMinor >= 0L) {
            return GoalTransaction(id = id, goalId = goalId, userId = userId, amountMinor = amountMinor)
        }
        val unsafeField = sun.misc.Unsafe::class.java.getDeclaredField("theUnsafe").apply { isAccessible = true }
        val unsafe = unsafeField.get(null) as sun.misc.Unsafe
        val tx = unsafe.allocateInstance(GoalTransaction::class.java) as GoalTransaction
        val idField = GoalTransaction::class.java.getDeclaredField("id")
        val goalIdField = GoalTransaction::class.java.getDeclaredField("goalId")
        val userIdField = GoalTransaction::class.java.getDeclaredField("userId")
        val amountField = GoalTransaction::class.java.getDeclaredField("amountMinor")
        val typeField = GoalTransaction::class.java.getDeclaredField("transactionType")
        val dateField = GoalTransaction::class.java.getDeclaredField("date")

        unsafe.putObject(tx, unsafe.objectFieldOffset(idField), id)
        unsafe.putObject(tx, unsafe.objectFieldOffset(goalIdField), goalId)
        unsafe.putObject(tx, unsafe.objectFieldOffset(userIdField), userId)
        unsafe.putLong(tx, unsafe.objectFieldOffset(amountField), amountMinor)
        unsafe.putObject(tx, unsafe.objectFieldOffset(typeField), GoalTransactionType.CONTRIBUTION)
        unsafe.putObject(tx, unsafe.objectFieldOffset(dateField), "2026-10-17")
        return tx
    }

    // 32. transaction delete success
    @Test
    fun `32 transaction delete success`() = runTest {
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
    fun `33 unauthenticated write blocked safely`() = runTest {
        authRepository.userFlow.value = null // Sign out
        val goal = SavingsGoal(id = "g-1", name = "Offline Goal", status = GoalStatus.ACTIVE)

        viewModel.createGoal(goal)
        advanceUntilIdle()

        assertEquals("Please sign in again to continue.", viewModel.uiState.value.errorMessage)
        assertTrue(savingsGoalRepository.goals.value.isEmpty())
    }

    // 34. repository failure exposed as UI-safe error
    @Test
    fun `34 repository failure exposed as UI-safe error`() = runTest {
        savingsGoalRepository.failureException = RuntimeException("Firestore connection reset")

        viewModel.createGoal(SavingsGoal(id = "g-1", status = GoalStatus.ACTIVE))
        advanceUntilIdle()

        assertEquals("Something went wrong while saving. Please try again.", viewModel.uiState.value.errorMessage)
    }

    // 35. operationInProgress resets after success
    @Test
    fun `35 operationInProgress resets after success`() = runTest {
        val goal = SavingsGoal(id = "g-1", status = GoalStatus.ACTIVE)

        viewModel.createGoal(goal)
        advanceUntilIdle()
        assertFalse(viewModel.uiState.value.operationInProgress)
    }

    // 36. operationInProgress resets after failure
    @Test
    fun `36 operationInProgress resets after failure`() = runTest {
        val goal = SavingsGoal(id = "g-1", status = GoalStatus.ACTIVE)
        savingsGoalRepository.failureException = RuntimeException("Network timeout")

        viewModel.updateGoal(goal)
        advanceUntilIdle()
        assertFalse(viewModel.uiState.value.operationInProgress)
    }

    // Extra edge-case validations
    @Test
    fun `clearError clears transient error message`() = runTest {
        savingsGoalRepository.failureException = RuntimeException("Error")
        viewModel.createGoal(SavingsGoal(id = "g-1", status = GoalStatus.ACTIVE))
        advanceUntilIdle()

        assertEquals("Something went wrong while saving. Please try again.", viewModel.uiState.value.errorMessage)

        viewModel.clearError()
        advanceUntilIdle()
        assertNull(viewModel.uiState.value.errorMessage)
    }

    @Test
    fun `addContribution with negative or zero amount surfaces error`() = runTest {
        val goal = SavingsGoal(id = "g-1", userId = "user-123", name = "Goal", status = GoalStatus.ACTIVE)
        savingsGoalRepository.goals.value = listOf(goal)
        advanceUntilIdle()

        viewModel.addContribution(amountMinor = 0L)
        advanceUntilIdle()

        assertEquals("Contribution amount must be greater than zero", viewModel.uiState.value.errorMessage)
    }

    @Test
    fun `addWithdrawal with negative or zero amount surfaces error`() = runTest {
        val goal = SavingsGoal(id = "g-1", userId = "user-123", name = "Goal", status = GoalStatus.ACTIVE)
        savingsGoalRepository.goals.value = listOf(goal)
        advanceUntilIdle()

        viewModel.addWithdrawal(amountMinor = -500L)
        advanceUntilIdle()

        assertEquals("Withdrawal amount must be greater than zero", viewModel.uiState.value.errorMessage)
    }

    @Test
    fun `addContribution without active goal surfaces error`() = runTest {
        advanceUntilIdle()

        viewModel.addContribution(amountMinor = 10_000L)
        advanceUntilIdle()

        assertEquals("No active savings goal found", viewModel.uiState.value.errorMessage)
    }

    @Test
    fun `addWithdrawal without active goal surfaces error`() = runTest {
        advanceUntilIdle()

        viewModel.addWithdrawal(amountMinor = 10_000L)
        advanceUntilIdle()

        assertEquals("No active savings goal found", viewModel.uiState.value.errorMessage)
    }

    @Test fun newGoalWithDefaultIdCanRetryWithoutDuplicateCreation() = runTest {
        savingsGoalRepository.failureException = java.io.IOException("private Firestore host")
        val draft = SavingsGoal(name = "Emergency fund", targetAmountMinor = 500000,
            targetDate = "2027-10-01", openingSavedAmountMinor = 1200)
        viewModel.createGoal(draft)
        advanceUntilIdle()
        assertTrue(viewModel.uiState.value.canRetry)
        assertEquals(0L, viewModel.uiState.value.successVersion)
        savingsGoalRepository.failureException = null
        viewModel.retry(); viewModel.retry()
        advanceUntilIdle()
        val saved = savingsGoalRepository.goals.value.single()
        assertTrue(saved.id.isNotBlank())
        assertEquals("Emergency fund", saved.name)
        assertEquals(500000L, saved.targetAmountMinor)
        assertEquals("2027-10-01", saved.targetDate)
        assertEquals(1200L, saved.openingSavedAmountMinor)
        assertEquals(1L, viewModel.uiState.value.successVersion)
        assertNull(viewModel.uiState.value.errorMessage)
    }

    @Test fun contributionRetryPreservesDraftAndCreatesOneLedgerEntry() = runTest {
        savingsGoalRepository.goals.value = listOf(SavingsGoal(id = "goal", userId = testUser.uid, status = GoalStatus.ACTIVE))
        advanceUntilIdle()
        savingsGoalRepository.failureException = java.io.IOException("offline")
        viewModel.addContribution("goal", 12345, "Bonus", "2026-10-01")
        advanceUntilIdle()
        assertTrue(viewModel.uiState.value.canRetry)
        savingsGoalRepository.failureException = null
        viewModel.retry(); viewModel.retry()
        advanceUntilIdle()
        val saved = savingsGoalRepository.transactions.value.single()
        assertEquals(12345L, saved.amountMinor)
        assertEquals("Bonus", saved.note)
        assertEquals("2026-10-01", saved.date)
        assertEquals(GoalTransactionType.CONTRIBUTION, saved.transactionType)
        assertEquals(1L, viewModel.uiState.value.successVersion)
    }

    @Test fun accountSwitchNeverRetainsThePreviousGoalOrBalance() = runTest {
        savingsGoalRepository.goals.value = listOf(SavingsGoal(id = "private", userId = testUser.uid,
            openingSavedAmountMinor = 1000000))
        advanceUntilIdle()
        assertEquals(1000000L, viewModel.uiState.value.currentSavedMinor)
        authRepository.userFlow.value = testUser.copy(uid = "other-account")
        advanceUntilIdle()
        assertNull(viewModel.uiState.value.activeGoal)
        assertTrue(viewModel.uiState.value.transactions.isEmpty())
        assertEquals(0L, viewModel.uiState.value.currentSavedMinor)
        authRepository.userFlow.value = null
        advanceUntilIdle()
        assertNull(viewModel.uiState.value.activeGoal)
    }

    @Test fun restorationLoadingErrorDismissalAndRoomUpdatesReachTheScreenState() = runTest {
        advanceUntilIdle()
        savingsGoalRepository.restoration.value = com.pfms.app.domain.model.SavingsRestorationState(isRefreshing = true)
        advanceUntilIdle()
        assertTrue(viewModel.uiState.value.isLoading)
        savingsGoalRepository.restoration.value = com.pfms.app.domain.model.SavingsRestorationState(error = java.io.IOException("offline"))
        advanceUntilIdle()
        assertFalse(viewModel.uiState.value.isLoading)
        assertNotNull(viewModel.uiState.value.errorMessage)
        assertTrue(viewModel.uiState.value.canRetry)
        viewModel.clearError()
        advanceUntilIdle()
        assertNull(viewModel.uiState.value.errorMessage)
        val goal = SavingsGoal(id = "restored", userId = testUser.uid, openingSavedAmountMinor = 1000000,
            targetAmountMinor = 10000000, targetDate = "2027-10-25")
        savingsGoalRepository.goals.value = listOf(goal)
        savingsGoalRepository.transactions.value = listOf(GoalTransaction(id = "tx", goalId = goal.id,
            userId = testUser.uid, amountMinor = 500000, date = "2026-10-01"))
        advanceUntilIdle()
        assertEquals(goal, viewModel.uiState.value.activeGoal)
        assertEquals(1500000L, viewModel.uiState.value.currentSavedMinor)
        assertEquals(15.0, viewModel.uiState.value.progressPercent, 0.001)
        savingsGoalRepository.restoration.value = com.pfms.app.domain.model.SavingsRestorationState(isRefreshing = true)
        advanceUntilIdle()
        assertFalse(viewModel.uiState.value.isLoading) // Cached data remains usable while reconnecting.
    }

    @Test fun repeatedMonthlySurplusRejectionsStayObservableAndCorrectionCanSave() = runTest {
        savingsGoalRepository.goals.value = listOf(SavingsGoal(id = "goal", userId = testUser.uid,
            targetAmountMinor = 10000000, targetDate = "2027-10-01", openingSavedAmountMinor = 1000000))
        advanceUntilIdle()
        savingsGoalRepository.failureException = com.pfms.app.domain.usecase.savings.InsufficientMonthlySurplusException(1000000, 1500000, "2026-10")
        repeat(2) { index ->
            viewModel.addContribution("goal", 1500000, "Draft", "2026-10-01")
            advanceUntilIdle()
            assertEquals(index + 1L, viewModel.uiState.value.failureVersion)
            assertTrue(viewModel.uiState.value.errorMessage.orEmpty().contains("Available: LKR 10,000.00"))
            assertFalse(viewModel.uiState.value.operationInProgress)
            assertFalse(viewModel.uiState.value.canRetry)
            assertEquals(0L, viewModel.uiState.value.successVersion)
            assertTrue(savingsGoalRepository.transactions.value.isEmpty())
        }
        viewModel.clearError()
        savingsGoalRepository.failureException = null
        viewModel.addContribution("goal", 1000000, "Corrected", "2026-10-01")
        advanceUntilIdle()
        assertNull(viewModel.uiState.value.errorMessage)
        assertEquals(1L, viewModel.uiState.value.successVersion)
        assertEquals(2000000L, viewModel.uiState.value.currentSavedMinor)
        assertEquals(1, savingsGoalRepository.transactions.value.size)
    }
}

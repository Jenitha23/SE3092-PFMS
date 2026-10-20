package com.pfms.app.viewmodel

import com.pfms.app.domain.model.AuthUser
import com.pfms.app.domain.model.Income
import com.pfms.app.domain.model.UserProfile
import com.pfms.app.domain.model.analytics.EstimationStatus
import com.pfms.app.domain.repository.AuthRepository
import com.pfms.app.domain.repository.IncomeRepository
import com.pfms.app.domain.repository.UserRepository
import com.pfms.app.domain.usecase.analytics.GetIncomeAnalyticsUseCase
import com.pfms.app.domain.usecase.analytics.GetIncomeEstimationComparisonUseCase
import com.pfms.app.domain.usecase.analytics.GetIncomeTrendsUseCase
import com.pfms.app.domain.usecase.analytics.GetMonthlyIncomeSummaryUseCase
import com.pfms.app.domain.usecase.analytics.GetRegularVsVariableIncomeUseCase
import com.pfms.app.domain.usecase.analytics.GetTrailingThreeMonthAverageUseCase
import com.pfms.app.utils.MainDispatcherRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.time.YearMonth

@OptIn(ExperimentalCoroutinesApi::class)
class IncomeAnalyticsViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private lateinit var incomeRepository: IncomeRepository
    private lateinit var authRepository: AuthRepository
    private lateinit var userRepository: UserRepository

    private lateinit var getMonthlySummary: GetMonthlyIncomeSummaryUseCase
    private lateinit var getRegularVsVariable: GetRegularVsVariableIncomeUseCase
    private lateinit var getTrends: GetIncomeTrendsUseCase
    private lateinit var getTrailingAverage: GetTrailingThreeMonthAverageUseCase
    private lateinit var getEstimationComparison: GetIncomeEstimationComparisonUseCase
    private lateinit var getAnalytics: GetIncomeAnalyticsUseCase

    private lateinit var userFlow: MutableStateFlow<AuthUser?>
    private lateinit var profileFlow: MutableStateFlow<UserProfile?>
    private lateinit var incomeFlow: MutableSharedFlow<List<Income>>

    private val testUser = AuthUser(
        uid = "user-123",
        displayName = "Kasun Silva",
        email = "kasun@example.com",
        isEmailVerified = true
    )

    private val sampleIncomes = listOf(
        Income(id = "1", source = "Salary", amountMinor = 50000000L, receivedDate = "2026-07-25"),
        Income(id = "2", source = "Salary", amountMinor = 50000000L, receivedDate = "2026-08-25"),
        Income(id = "3", source = "Salary", amountMinor = 50000000L, receivedDate = "2026-09-25"),
        Income(id = "4", source = "Salary", amountMinor = 60000000L, receivedDate = "2026-10-25"),
        Income(id = "5", source = "Freelance", amountMinor = 15000000L, paymentStatus = "Received", receivedDate = "2026-10-10"),
        Income(id = "6", source = "Freelance", amountMinor = 10000000L, paymentStatus = "Expected", receivedDate = "2026-10-12"),
        Income(id = "7", source = "Crypto", cryptoType = "Loss", amountMinor = 5000000L, receivedDate = "2026-10-15")
    )

    @Before
    fun setup() {
        incomeRepository = mock()
        authRepository = mock()
        userRepository = mock()

        userFlow = MutableStateFlow(testUser)
        profileFlow = MutableStateFlow(
            UserProfile(
                uid = "user-123",
                displayName = "Kasun Silva",
                email = "kasun@example.com",
                estimatedMonthlyIncomeMinor = null
            )
        )
        incomeFlow = MutableSharedFlow(replay = 1)
        incomeFlow.tryEmit(sampleIncomes)

        whenever(authRepository.currentUser).thenReturn(userFlow)
        whenever(userRepository.observeProfile()).thenReturn(profileFlow)
        whenever(incomeRepository.observeIncome()).thenReturn(incomeFlow)
        runBlocking {
            whenever(userRepository.updateEstimatedMonthlyIncome(anyOrNull())).thenReturn(Result.success(Unit))
        }

        getMonthlySummary = GetMonthlyIncomeSummaryUseCase()
        getRegularVsVariable = GetRegularVsVariableIncomeUseCase(getMonthlySummary)
        getTrends = GetIncomeTrendsUseCase(getMonthlySummary)
        getTrailingAverage = GetTrailingThreeMonthAverageUseCase(getMonthlySummary)
        getEstimationComparison = GetIncomeEstimationComparisonUseCase(getTrailingAverage)
        getAnalytics = GetIncomeAnalyticsUseCase(
            getMonthlyIncomeSummaryUseCase = getMonthlySummary,
            getRegularVsVariableIncomeUseCase = getRegularVsVariable,
            getIncomeTrendsUseCase = getTrends,
            getIncomeEstimationComparisonUseCase = getEstimationComparison
        )
    }

    private fun createViewModel(): IncomeAnalyticsViewModel {
        return IncomeAnalyticsViewModel(
            incomeRepository = incomeRepository,
            authRepository = authRepository,
            userRepository = userRepository,
            getMonthlyIncomeSummaryUseCase = getMonthlySummary,
            getRegularVsVariableIncomeUseCase = getRegularVsVariable,
            getIncomeTrendsUseCase = getTrends,
            getIncomeEstimationComparisonUseCase = getEstimationComparison,
            getIncomeAnalyticsUseCase = getAnalytics
        ).also { mainDispatcherRule.track(it) }
    }

    @Test
    fun emptyUserWithMissingProfileGetsZeroAnalyticsAndAnAddIncomeState() = runTest {
        profileFlow.value = null
        incomeFlow.tryEmit(emptyList())
        val state = createViewModel().uiState.value
        assertFalse(state.isLoading)
        assertFalse(state.hasMonthlyIncome)
        assertEquals(0L, state.monthlySummary!!.totalRealizedMinor)
        assertEquals(0.0, state.regularVariable!!.regularPercentage, 0.0)
        assertEquals(0.0, state.regularVariable.variablePercentage, 0.0)
        assertTrue(state.trendPoints.all { it.amountMinor == 0L })
        assertNull(state.estimationComparison)
        assertNull(state.errorMessage)
    }

    @Test
    fun zeroNetIncomeWithRecordsIsNotAnEmptyMonth() = runTest {
        incomeFlow.tryEmit(listOf(
            Income(amountMinor = 100, receivedDate = "2026-10-01"),
            Income(amountMinor = 100, source = "Crypto", cryptoType = "Loss", receivedDate = "2026-10-01")
        ))
        val vm = createViewModel()
        vm.selectMonth(YearMonth.of(2026, 10))
        assertTrue(vm.uiState.value.hasMonthlyIncome)
        assertEquals(0L, vm.uiState.value.monthlySummary!!.totalRealizedMinor)
        vm.selectMonth(YearMonth.of(2026, 9))
        assertFalse(vm.uiState.value.hasMonthlyIncome)
    }

    @Test
    fun `1 Initial state has active selected month and not loading after flow emits`() = runTest {
        val viewModel = createViewModel()
        val state = viewModel.uiState.value

        assertFalse(state.isLoading)
        assertNotNull(state.selectedMonth)
        assertNull(state.errorMessage)
    }

    @Test
    fun `2 Authenticated user income is observed`() = runTest {
        val viewModel = createViewModel()
        val state = viewModel.uiState.value

        assertNotNull(state.monthlySummary)
        assertNotNull(state.regularVariable)
        assertTrue(state.trendPoints.isNotEmpty())
    }

    @Test
    fun `3 Monthly summary updates from repository data respecting realized rules`() = runTest {
        val viewModel = createViewModel()
        viewModel.selectMonth(YearMonth.of(2026, 10))

        val summary = viewModel.uiState.value.monthlySummary
        assertNotNull(summary)
        // 60m (salary) + 15m (received freelance) - 5m (crypto loss) = 70m
        assertEquals(70000000L, summary?.totalRealizedMinor)
        assertEquals(60000000L, summary?.salaryMinor)
        assertEquals(15000000L, summary?.freelanceMinor)
        assertEquals(-5000000L, summary?.cryptoNetMinor)
    }

    @Test
    fun `4 previousMonth updates selected month`() = runTest {
        val viewModel = createViewModel()
        viewModel.selectMonth(YearMonth.of(2026, 10))

        viewModel.previousMonth()

        assertEquals(YearMonth.of(2026, 9), viewModel.uiState.value.selectedMonth)
    }

    @Test
    fun `5 nextMonth updates selected month`() = runTest {
        val viewModel = createViewModel()
        viewModel.selectMonth(YearMonth.of(2026, 10))

        viewModel.nextMonth()

        assertEquals(YearMonth.of(2026, 11), viewModel.uiState.value.selectedMonth)
    }

    @Test
    fun `6 selectMonth recomputes analytics for target month`() = runTest {
        val viewModel = createViewModel()
        viewModel.selectMonth(YearMonth.of(2026, 9))

        val state = viewModel.uiState.value
        assertEquals(YearMonth.of(2026, 9), state.selectedMonth)
        assertEquals(50000000L, state.monthlySummary?.totalRealizedMinor)
        assertEquals(50000000L, state.monthlySummary?.salaryMinor)
    }

    @Test
    fun `7 Regular vs variable state is populated correctly`() = runTest {
        val viewModel = createViewModel()
        viewModel.selectMonth(YearMonth.of(2026, 10))

        val rv = viewModel.uiState.value.regularVariable
        assertNotNull(rv)
        assertEquals(6000000L * 10, rv?.regularMinor)
        // Variable = 15m (freelance) - 5m (crypto loss) = 10m
        assertEquals(10000000L, rv?.variableMinor)
        assertEquals(70000000L, rv?.totalMinor)
        assertTrue(rv!!.regularPercentage > 85.0)
    }

    @Test
    fun `8 Trends are populated with 6 points`() = runTest {
        val viewModel = createViewModel()
        viewModel.selectMonth(YearMonth.of(2026, 10))

        val trends = viewModel.uiState.value.trendPoints
        assertEquals(6, trends.size)
        assertEquals("2026-05", trends.first().yearMonth)
        assertEquals("2026-10", trends.last().yearMonth)
    }

    @Test
    fun `9 No stored estimate leaves estimate and comparison null`() = runTest {
        val viewModel = createViewModel()

        assertNull(viewModel.uiState.value.estimatedMonthlyIncomeMinor)
        assertNull(viewModel.uiState.value.estimationComparison)
    }

    @Test
    fun `10 Stored estimate loads on startup and computes comparison automatically`() = runTest {
        profileFlow.value = UserProfile(
            uid = "user-123",
            displayName = "Kasun Silva",
            email = "kasun@example.com",
            estimatedMonthlyIncomeMinor = 45000000L
        )

        val viewModel = createViewModel()
        viewModel.selectMonth(YearMonth.of(2026, 10))

        assertEquals(45000000L, viewModel.uiState.value.estimatedMonthlyIncomeMinor)
        val comp = viewModel.uiState.value.estimationComparison
        assertNotNull(comp)
        assertEquals(45000000L, comp?.estimatedMonthlyIncomeMinor)
        // Trailing 3-month average for Jul (50m), Aug (50m), Sep (50m) = 50m
        assertEquals(50000000L, comp?.trailingThreeMonthAverageMinor)
        assertEquals(5000000L, comp?.differenceMinor)
        assertEquals(EstimationStatus.ABOVE_ESTIMATE, comp?.status)
    }

    @Test
    fun `11 setEstimatedMonthlyIncome updates UI state and persists to repository`() = runTest {
        val viewModel = createViewModel()
        viewModel.selectMonth(YearMonth.of(2026, 10))

        viewModel.setEstimatedMonthlyIncome(55000000L)

        assertEquals(55000000L, viewModel.uiState.value.estimatedMonthlyIncomeMinor)
        assertNotNull(viewModel.uiState.value.estimationComparison)
        verify(userRepository).updateEstimatedMonthlyIncome(eq(55000000L))
    }

    @Test
    fun `12 setEstimatedMonthlyIncome recomputes comparison`() = runTest {
        val viewModel = createViewModel()
        viewModel.selectMonth(YearMonth.of(2026, 10))

        viewModel.setEstimatedMonthlyIncome(45000000L)

        val comp = viewModel.uiState.value.estimationComparison
        assertNotNull(comp)
        assertEquals(45000000L, comp?.estimatedMonthlyIncomeMinor)
        assertEquals(50000000L, comp?.trailingThreeMonthAverageMinor)
        assertEquals(5000000L, comp?.differenceMinor)
        assertEquals(EstimationStatus.ABOVE_ESTIMATE, comp?.status)
    }

    @Test
    fun `13 clearEstimatedMonthlyIncome clears state and removes persisted value`() = runTest {
        profileFlow.value = UserProfile(
            uid = "user-123",
            displayName = "Kasun Silva",
            email = "kasun@example.com",
            estimatedMonthlyIncomeMinor = 45000000L
        )
        val viewModel = createViewModel()
        assertEquals(45000000L, viewModel.uiState.value.estimatedMonthlyIncomeMinor)

        viewModel.clearEstimatedMonthlyIncome()

        assertNull(viewModel.uiState.value.estimatedMonthlyIncomeMinor)
        assertNull(viewModel.uiState.value.estimationComparison)
        verify(userRepository).updateEstimatedMonthlyIncome(eq(null))
    }

    @Test
    fun `14 Estimate zero is handled safely without crashing`() = runTest {
        val viewModel = createViewModel()
        viewModel.selectMonth(YearMonth.of(2026, 10))

        viewModel.setEstimatedMonthlyIncome(0L)

        val comp = viewModel.uiState.value.estimationComparison
        assertNotNull(comp)
        assertEquals(0L, comp?.estimatedMonthlyIncomeMinor)
        assertEquals(0.0, comp?.variancePercentage ?: -1.0, 0.0)
        verify(userRepository).updateEstimatedMonthlyIncome(eq(0L))
    }

    @Test
    fun `15 Negative estimate is rejected without mutating state or persisting`() = runTest {
        val viewModel = createViewModel()

        viewModel.setEstimatedMonthlyIncome(-50000L)

        assertNull(viewModel.uiState.value.estimatedMonthlyIncomeMinor)
        assertNull(viewModel.uiState.value.estimationComparison)
        assertEquals("Estimated income cannot be negative.", viewModel.uiState.value.errorMessage)
        verify(userRepository, never()).updateEstimatedMonthlyIncome(anyOrNull())
    }

    @Test
    fun `16 Persistence failure exposes error message`() = runTest {
        whenever(userRepository.updateEstimatedMonthlyIncome(anyOrNull())).thenReturn(
            Result.failure(RuntimeException("Firestore offline"))
        )

        val viewModel = createViewModel()
        viewModel.setEstimatedMonthlyIncome(50000000L)

        assertTrue(viewModel.uiState.value.errorMessage?.contains("Failed to save estimated income") == true)
    }

    @Test
    fun `17 User A estimate never leaks to User B on user switch`() = runTest {
        // User A signed in with estimate 50m
        profileFlow.value = UserProfile(
            uid = "user-123",
            displayName = "Kasun Silva",
            email = "kasun@example.com",
            estimatedMonthlyIncomeMinor = 50000000L
        )
        val viewModel = createViewModel()
        assertEquals(50000000L, viewModel.uiState.value.estimatedMonthlyIncomeMinor)

        // User A logs out
        userFlow.value = null
        profileFlow.value = null
        assertNull(viewModel.uiState.value.estimatedMonthlyIncomeMinor)
        assertNull(viewModel.uiState.value.estimationComparison)

        // User B logs in with estimate 75m
        userFlow.value = AuthUser(uid = "user-456", displayName = "Amara Perera", email = "amara@example.com", isEmailVerified = true)
        profileFlow.value = UserProfile(
            uid = "user-456",
            displayName = "Amara Perera",
            email = "amara@example.com",
            estimatedMonthlyIncomeMinor = 75000000L
        )

        assertEquals(75000000L, viewModel.uiState.value.estimatedMonthlyIncomeMinor)
    }

    @Test
    fun `18 Logout clears stale estimate state`() = runTest {
        profileFlow.value = UserProfile(
            uid = "user-123",
            displayName = "Kasun Silva",
            email = "kasun@example.com",
            estimatedMonthlyIncomeMinor = 50000000L
        )
        val viewModel = createViewModel()
        assertEquals(50000000L, viewModel.uiState.value.estimatedMonthlyIncomeMinor)

        // Logout
        userFlow.value = null
        profileFlow.value = null

        val state = viewModel.uiState.value
        assertNull(state.estimatedMonthlyIncomeMinor)
        assertNull(state.estimationComparison)
        assertNull(state.monthlySummary)
        assertTrue(state.trendPoints.isEmpty())
    }

    @Test
    fun `19 Repository data update recomputes analytics`() = runTest {
        val viewModel = createViewModel()
        viewModel.selectMonth(YearMonth.of(2026, 10))

        assertEquals(70000000L, viewModel.uiState.value.monthlySummary?.totalRealizedMinor)

        // Repository emits new income list
        val updatedList = sampleIncomes + listOf(
            Income(id = "8", source = "Salary", amountMinor = 10000000L, receivedDate = "2026-10-28")
        )
        incomeFlow.tryEmit(updatedList)

        assertEquals(80000000L, viewModel.uiState.value.monthlySummary?.totalRealizedMinor)
    }

    @Test
    fun `20 Repository Flow failure exposes error state`() = runTest {
        val failingRepo: IncomeRepository = mock()
        whenever(failingRepo.observeIncome()).thenReturn(flow {
            throw RuntimeException("Database error")
        })

        val viewModel = IncomeAnalyticsViewModel(
            incomeRepository = failingRepo,
            authRepository = authRepository,
            userRepository = userRepository,
            getMonthlyIncomeSummaryUseCase = getMonthlySummary,
            getRegularVsVariableIncomeUseCase = getRegularVsVariable,
            getIncomeTrendsUseCase = getTrends,
            getIncomeEstimationComparisonUseCase = getEstimationComparison,
            getIncomeAnalyticsUseCase = getAnalytics
        )

        val state = viewModel.uiState.value
        assertFalse(state.isLoading)
        assertEquals("Unable to load income records.", state.errorMessage)
    }

    @Test
    fun `21 Member 2 realized income rules are preserved via use cases`() {
        val viewModel = createViewModel()
        viewModel.selectMonth(YearMonth.of(2026, 10))

        val summary = viewModel.uiState.value.monthlySummary
        // Expected freelance (10m) must be excluded
        assertEquals(15000000L, summary?.freelanceMinor)
        // Crypto loss (5m) must subtract
        assertEquals(-5000000L, summary?.cryptoNetMinor)
    }
}

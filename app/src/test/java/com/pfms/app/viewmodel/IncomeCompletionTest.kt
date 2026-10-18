package com.pfms.app.viewmodel

import com.pfms.app.data.exchange.ExchangeRateService
import com.pfms.app.domain.model.*
import com.pfms.app.domain.repository.*
import com.pfms.app.domain.usecase.SalaryReminderUseCase
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.test.*
import org.junit.*
import org.junit.Assert.*
import org.mockito.kotlin.*
import java.time.LocalDate
import java.time.ZoneId

@OptIn(ExperimentalCoroutinesApi::class)
class IncomeCompletionTest {
    private val dispatcher = StandardTestDispatcher()
    @Before fun setup() = Dispatchers.setMain(dispatcher)
    @After fun close() = Dispatchers.resetMain()
    private val original = Income(id = "usd", userId = "a", currency = "USD", amountMinor = 10000,
        amountLkrMinor = 3054200, exchangeRate = "305.42", exchangeRateDate = "2025-01-10", receivedDate = "2025-01-10")

    private fun incomeVm(repo: IncomeRepository): IncomeViewModel {
        val auth = mock<AuthRepository>()
        whenever(auth.currentUser).thenReturn(flowOf(AuthUser("a", "A", "a@example.com", true)))
        whenever(repo.observeIncome()).thenReturn(flowOf(listOf(original)))
        return IncomeViewModel(repo, auth, mock<ExchangeRateService>())
    }

    @Test fun noteOnlyEditKeepsHistoricalConversionExactly() = runTest(dispatcher) {
        val repo = mock<IncomeRepository>()
        whenever(repo.getIncomeById("usd")).thenReturn(original)
        val vm = incomeVm(repo)
        vm.loadExistingRate(original)
        assertEquals("305.42", vm.uiState.value.exchangeRate?.toPlainString())
        vm.updateIncome("usd", "100", "USD", "Salary", original.receivedDate, "New note")
        advanceUntilIdle()
        val saved = argumentCaptor<Income>()
        verify(repo).updateIncome(saved.capture())
        assertEquals(original.exchangeRate, saved.firstValue.exchangeRate)
        assertEquals(original.exchangeRateDate, saved.firstValue.exchangeRateDate)
        assertEquals(original.amountLkrMinor, saved.firstValue.amountLkrMinor)
    }

    @Test fun changingAmountUsesOriginalRateWithoutRefetching() = runTest(dispatcher) {
        val repo = mock<IncomeRepository>()
        whenever(repo.getIncomeById("usd")).thenReturn(original)
        val vm = incomeVm(repo)
        vm.loadExistingRate(original)
        vm.updateIncome("usd", "200", "USD", "Salary", original.receivedDate, "")
        advanceUntilIdle()
        val saved = argumentCaptor<Income>()
        verify(repo).updateIncome(saved.capture())
        assertEquals(6108400L, saved.firstValue.amountLkrMinor)
        assertEquals("305.42", saved.firstValue.exchangeRate)
    }

    @Test fun changedDateRequiresMatchingRate() = runTest(dispatcher) {
        val repo = mock<IncomeRepository>()
        whenever(repo.getIncomeById("usd")).thenReturn(original)
        val vm = incomeVm(repo)
        vm.loadExistingRate(original)
        vm.updateIncome("usd", "100", "USD", "Salary", "2025-01-11", "")
        advanceUntilIdle()
        verify(repo, never()).updateIncome(any())
        assertNotNull(vm.uiState.value.errorMessage)
    }

    @Test fun invalidFreelancePaymentStatusCannotBeSaved() = runTest(dispatcher) {
        val repo = mock<IncomeRepository>()
        val vm = incomeVm(repo)
        vm.updateIncome("usd", "100", "LKR", "Freelance", "2025-01-10", "", projectName = "Client", paymentStatus = "Invalid")
        advanceUntilIdle()
        verify(repo, never()).updateIncome(any())
        assertEquals("Invalid freelance payment status.", vm.uiState.value.errorMessage)
    }

    @Test fun salaryDuePromptAndRapidConfirmationUseOneRepositoryCall() = runTest(dispatcher) {
        val repo = mock<SalaryTemplateRepository>()
        val today = LocalDate.now(ZoneId.of("Asia/Colombo"))
        val rows = MutableStateFlow<SalaryTemplate?>(SalaryTemplate("salary", "a", 10000, paymentDay = today.dayOfMonth))
        whenever(repo.observeSalaryTemplate()).thenReturn(rows)
        whenever(repo.confirmMonthlySalary(any(), any(), any())).thenAnswer { rows.value = rows.value!!.copy(lastConfirmedMonth = today.toString().take(7)); Unit }
        val vm = SalaryViewModel(repo, SalaryReminderUseCase())
        runCurrent()
        assertTrue(vm.uiState.value.showConfirmation)
        vm.confirmSalary("100")
        vm.confirmSalary("100")
        advanceUntilIdle()
        verify(repo, times(1)).confirmMonthlySalary("salary", today.toString().take(7), 10000)
        vm.refreshSalaryReminder()
        assertFalse(vm.uiState.value.showConfirmation)
    }

    @Test fun onboardingPersistsEstimateAndDoesNotRepeatAfterAnalyticsReset() = runTest(dispatcher) {
        val repo = mock<UserRepository>()
        val profile = MutableStateFlow<UserProfile?>(UserProfile("a", "A", "a@example.com"))
        whenever(repo.observeProfile()).thenReturn(profile)
        whenever(repo.updateEstimatedMonthlyIncome(15000000)).thenReturn(Result.success(Unit))
        val vm = IncomeOnboardingViewModel(repo)
        runCurrent()
        assertTrue(vm.uiState.value.required)
        vm.save("150000.00")
        advanceUntilIdle()
        verify(repo).updateEstimatedMonthlyIncome(15000000)
        assertFalse(vm.uiState.value.required)
        profile.value = profile.value!!.copy(incomeOnboardingComplete = true, estimatedMonthlyIncomeMinor = null)
        runCurrent()
        assertFalse(vm.uiState.value.required)
    }

    @Test fun onboardingFailureRetainsSetupAndAllowsRetry() = runTest(dispatcher) {
        val repo = mock<UserRepository>()
        whenever(repo.observeProfile()).thenReturn(flowOf(UserProfile("a", "A", "a@example.com")))
        whenever(repo.updateEstimatedMonthlyIncome(10000)).thenReturn(Result.failure(IllegalStateException("Offline")))
        val vm = IncomeOnboardingViewModel(repo)
        runCurrent()
        vm.save("100")
        advanceUntilIdle()
        assertTrue(vm.uiState.value.required)
        assertFalse(vm.uiState.value.saving)
        assertNotNull(vm.uiState.value.error)
        assertNull(IncomeOnboardingViewModel.parseEstimate("-1"))
        assertNull(IncomeOnboardingViewModel.parseEstimate("1.001"))
        assertNull(IncomeOnboardingViewModel.parseEstimate("9999999999999999999999"))
        assertEquals(0L, IncomeOnboardingViewModel.parseEstimate("0"))
    }
}

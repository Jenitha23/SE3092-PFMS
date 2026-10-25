package com.pfms.app.viewmodel

import androidx.lifecycle.SavedStateHandle
import com.pfms.app.data.FakeAuthRepository
import com.pfms.app.data.FakeIncomeRepository
import com.pfms.app.data.exchange.*
import com.pfms.app.domain.model.*
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import org.junit.*
import org.junit.Assert.*
import org.mockito.kotlin.*
import java.math.BigDecimal
import java.time.LocalDate
import java.time.ZoneId
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

@OptIn(ExperimentalCoroutinesApi::class)
class IncomeExchangeRateStateTest {
    private val dispatcher = StandardTestDispatcher()
    private val repository = FakeIncomeRepository()
    private val user = AuthUser("user-a", "Test", "test@example.com", true)
    private val auth = FakeAuthRepository(user)
    private val service = mock<ExchangeRateService>()
    private val today = LocalDate.now(ZoneId.of("Asia/Colombo")).toString()
    @Before fun setup() { Dispatchers.setMain(dispatcher) }
    @After fun cleanup() { Dispatchers.resetMain() }
    private fun vm() = IncomeViewModel(repository, auth, service)
    private suspend fun TestScope.rateReady(vm: IncomeViewModel) {
        runCurrent()
        withContext(Dispatchers.Default) { withTimeout(5000) { while (vm.uiState.value.isRateFetching) { dispatcher.scheduler.runCurrent(); yield() } } }
        runCurrent()
    }
    private fun backend(vm: IncomeViewModel) = vm.uiState.value.let { IncomeFormBackend(rate = it.exchangeRate,
        rateDate = it.exchangeRateDate, fetching = it.isRateFetching, rateError = it.rateFetchError,
        manual = it.isManualRateMode, cached = it.isCachedExchangeRate, savedRate = it.isSavedExchangeRate) }

    @Test fun fetchedCachedAndManualRatesHaveDistinctLabelsAndAnExplicitDate() = runTest(dispatcher) {
        val vm = vm(); runCurrent()
        val draft = IncomeFormViewModel(SavedStateHandle())
        draft.currencyChanged("USD", {}, {}); draft.amountChanged("100")
        whenever(service.fetchLatestRate()).thenReturn(ExchangeRateResult.Success(BigDecimal("305.42"), today))
        vm.fetchExchangeRate(today); rateReady(vm); draft.updateBackend(backend(vm))
        assertEquals("Fetched rate", draft.state.value.rateLabel)
        assertEquals("Exchange rate date: $today", draft.state.value.rateDateLabel)
        assertEquals("LKR 30,542.00", draft.state.value.convertedAmount)
        whenever(service.fetchLatestRate()).thenReturn(ExchangeRateResult.Success(BigDecimal("300.25"), today, true))
        vm.fetchExchangeRate(today); rateReady(vm); draft.updateBackend(backend(vm))
        assertEquals("Cached rate", draft.state.value.rateLabel)
        assertTrue(draft.state.value.rateNotice.orEmpty().contains("may not reflect the current rate"))
        vm.enableManualRate(); draft.updateBackend(backend(vm)); draft.manualRateChanged("310.50")
        assertEquals("Manual rate", draft.state.value.rateLabel)
        assertFalse(draft.state.value.rateNotice.orEmpty().contains("Cached"))
        assertEquals("LKR 31,050.00", draft.state.value.convertedAmount)
        assertTrue(vm.setManualExchangeRate("310.50", today))
    }

    @Test fun networkFailureAndInvalidDatedResponsesEnableManualEntry() = runTest(dispatcher) {
        val vm = vm(); runCurrent()
        for (result in listOf(ExchangeRateResult.Failure("Offline"),
            ExchangeRateResult.Success(BigDecimal("300"), ""), ExchangeRateResult.Success(BigDecimal.ZERO, today))) {
            whenever(service.fetchLatestRate()).thenReturn(result)
            vm.fetchExchangeRate(today); rateReady(vm)
            assertTrue(vm.uiState.value.isManualRateMode)
            assertNull(vm.uiState.value.exchangeRate)
            assertFalse(vm.uiState.value.isRateFetching)
        }
    }

    @Test fun wrongHistoricalDateIsRejectedAndExactQuoteAccepted() = runTest(dispatcher) {
        val date = LocalDate.parse(today).minusDays(30).toString()
        val vm = vm(); runCurrent()
        whenever(service.fetchHistoricalRate(date)).thenReturn(ExchangeRateResult.Success(BigDecimal("300"), today, true))
        vm.fetchExchangeRate(date); rateReady(vm)
        assertTrue(vm.uiState.value.isManualRateMode); assertNull(vm.uiState.value.exchangeRate)
        whenever(service.fetchHistoricalRate(date)).thenReturn(ExchangeRateResult.Success(BigDecimal("301.12"), date, true))
        vm.fetchExchangeRate(date); rateReady(vm)
        assertEquals(date, vm.uiState.value.exchangeRateDate); assertTrue(vm.uiState.value.isCachedExchangeRate)
    }

    @Test fun savedConversionSurvivesReconnectAndNoteEditUntilAnExplicitRateChange() = runTest(dispatcher) {
        val original = Income("saved", user.uid, 10000, "USD", receivedDate = today,
            exchangeRate = "300.25", exchangeRateDate = LocalDate.parse(today).minusDays(1).toString(), amountLkrMinor = 3002501)
        repository.addIncome(original)
        val vm = vm(); runCurrent(); vm.loadExistingRate(original)
        assertTrue(vm.uiState.value.isSavedExchangeRate)
        vm.updateIncome(original.id, "100", "USD", "Salary", today, "Edited note"); runCurrent()
        assertEquals(original.amountLkrMinor, repository.getIncomeById(original.id)!!.amountLkrMinor)
        vm.consumeSaveSuccess()
        whenever(service.fetchLatestRate()).thenReturn(ExchangeRateResult.Success(BigDecimal("300.25"), today))
        vm.fetchExchangeRate(today); rateReady(vm)
        assertFalse(vm.uiState.value.isSavedExchangeRate)
        assertEquals(original.amountLkrMinor, repository.getIncomeById(original.id)!!.amountLkrMinor)
        val draft = IncomeFormViewModel(SavedStateHandle(), original); draft.updateBackend(backend(vm))
        assertEquals("Fetched rate", draft.state.value.rateLabel)
        assertEquals("LKR 30,025.00", draft.state.value.convertedAmount)
        vm.updateIncome(original.id, "100", "USD", "Salary", today, "Explicit refreshed quote"); runCurrent()
        assertEquals(3002500L, repository.getIncomeById(original.id)!!.amountLkrMinor)
    }

    @Test fun accountChangesClearDraftRateButDoNotChangeSavedRecords() = runTest(dispatcher) {
        val vm = vm(); runCurrent()
        vm.setManualExchangeRate("300.25", today)
        auth.userFlow.value = null; runCurrent()
        assertNull(vm.uiState.value.exchangeRate); assertFalse(vm.uiState.value.isManualRateMode)
        auth.userFlow.value = user.copy(uid = "user-b"); runCurrent()
        assertNull(vm.uiState.value.exchangeRate)
        whenever(service.fetchLatestRate()).thenReturn(ExchangeRateResult.Success(BigDecimal("300.25"), today, true))
        vm.fetchExchangeRate(today); rateReady(vm)
        assertTrue(vm.uiState.value.isCachedExchangeRate)
    }

    @Test fun invalidManualInputIsVisibleAndCannotSaveUntilCorrected() {
        val draft = IncomeFormViewModel(SavedStateHandle())
        draft.currencyChanged("USD", {}, {}); draft.amountChanged("100")
        draft.updateBackend(IncomeFormBackend(manual = true))
        for (value in listOf("", "0", "-1", "abc")) {
            draft.manualRateChanged(value)
            assertNotNull(draft.state.value.manualRateError)
            assertNull(draft.state.value.convertedAmount)
            draft.submit({ _, _ -> false }, { fail("Invalid rate must not save") })
        }
        draft.manualRateChanged("305.42")
        assertNull(draft.state.value.manualRateError)
        var saved = false
        draft.submit({ rate, date -> rate == "305.42" && date == today }, { saved = true })
        assertTrue(saved)
    }

    @Test fun inFlightResponseCannotRestoreAClearedRateOrOverwriteAManualRate() = runTest(dispatcher) {
        val vm = vm(); runCurrent()
        for (switchToManual in listOf(false, true)) {
            val started = CountDownLatch(1)
            val release = CountDownLatch(1)
            val finished = CountDownLatch(1)
            whenever(service.fetchLatestRate()).thenAnswer {
                started.countDown()
                try {
                    check(release.await(5, TimeUnit.SECONDS))
                    ExchangeRateResult.Success(BigDecimal("300"), today)
                } finally { finished.countDown() }
            }
            vm.fetchExchangeRate(today); runCurrent()
            withContext(Dispatchers.Default) { assertTrue(started.await(5, TimeUnit.SECONDS)) }
            assertTrue(vm.uiState.value.isRateFetching)
            if (switchToManual) {
                vm.enableManualRate()
                assertTrue(vm.setManualExchangeRate("310.50", today))
            } else vm.clearExchangeRate()
            release.countDown()
            withContext(Dispatchers.Default) { assertTrue(finished.await(5, TimeUnit.SECONDS)) }
            runCurrent()
            assertFalse(vm.uiState.value.isRateFetching)
            assertEquals(if (switchToManual) BigDecimal("310.50") else null, vm.uiState.value.exchangeRate)
            assertEquals(switchToManual, vm.uiState.value.isManualRateMode)
        }
    }
}

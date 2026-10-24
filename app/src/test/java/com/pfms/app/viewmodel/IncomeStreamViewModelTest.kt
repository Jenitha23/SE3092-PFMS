package com.pfms.app.viewmodel

import com.pfms.app.data.exchange.ExchangeRateService
import com.pfms.app.domain.model.*
import com.pfms.app.domain.repository.*
import com.pfms.app.utils.MainDispatcherRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.mockito.kotlin.*
import java.time.YearMonth

@OptIn(ExperimentalCoroutinesApi::class)
class IncomeStreamViewModelTest {
    @get:Rule val main = MainDispatcherRule()
    private val repository: IncomeRepository = mock()
    private val auth: AuthRepository = mock()
    private val records = MutableStateFlow(listOf(
        Income(id = "salary", userId = "u", amountMinor = 30_000_00, receivedDate = "2026-10-18"),
        Income(id = "invoice", userId = "u", amountMinor = 400_00, source = "Freelance", projectName = "Website redesign",
            receivedDate = "2026-10-08", paymentStatus = "Expected"),
        Income(id = "old", userId = "u", amountMinor = 20_000_00, receivedDate = "2026-09-18")))

    private fun TestScope.create(): IncomeViewModel {
        whenever(repository.observeIncome()).thenReturn(records)
        whenever(auth.currentUser).thenReturn(flowOf(AuthUser("u", "Alex", "alex@example.com", true)))
        val vm = main.track(IncomeViewModel(repository, auth, mock<ExchangeRateService>()))
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.streamState.collect() }
        vm.selectStreamMonth(YearMonth.of(2026, 10)); runCurrent()
        return vm
    }

    @Test fun filteringAndSortingDoNotChangeMonthlySummary() = runTest {
        val vm = create()
        assertEquals("LKR 30,000.00", vm.streamState.value.total)
        assertEquals(listOf("salary", "invoice"), vm.streamState.value.rows.map { it.id })
        vm.filterStream(IncomeStreamFilter.FREELANCE); runCurrent()
        assertEquals(listOf("invoice"), vm.streamState.value.rows.map { it.id })
        assertEquals("LKR 30,000.00", vm.streamState.value.total)
        vm.filterStream(IncomeStreamFilter.ALL); vm.toggleStreamSort(); runCurrent()
        assertEquals(listOf("invoice", "salary"), vm.streamState.value.rows.map { it.id })
        vm.previousStreamMonth(); runCurrent()
        assertEquals("LKR 20,000.00", vm.streamState.value.total)
        assertEquals(listOf("old"), vm.streamState.value.rows.map { it.id })
    }

    @Test fun deletingRequiresConfirmationAndDismissalDoesNotWrite() = runTest {
        val vm = create()
        vm.requestStreamDelete("salary"); runCurrent()
        assertEquals("salary", vm.streamState.value.deleteRecord?.id)
        verify(repository, never()).deleteIncome(any())
        vm.requestStreamDelete(null); vm.confirmStreamDelete(); runCurrent()
        verify(repository, never()).deleteIncome(any())
        vm.requestStreamDelete("salary"); vm.confirmStreamDelete(); runCurrent()
        verify(repository).deleteIncome("salary")
        assertNull(vm.streamState.value.deleteRecord)
    }

    @Test fun receivingInvoiceRefreshesTotalsFromRepository() = runTest {
        val vm = create()
        whenever(repository.getIncomeById("invoice")).thenReturn(records.value[1])
        whenever(repository.updateIncome(any())).thenAnswer { invocation ->
            val updated = invocation.arguments[0] as Income
            records.value = records.value.map { if (it.id == updated.id) updated else it }; Unit
        }
        vm.markFreelanceAsReceived("invoice"); runCurrent()
        assertEquals("LKR 30,400.00", vm.streamState.value.total)
        assertNull(vm.streamState.value.pending)
        assertFalse(vm.streamState.value.rows.first { it.id == "invoice" }.isPending)
    }

    @Test fun lossAndUnconvertedRecordPresentationRetainSignsAndWarnings() {
        val row = IncomeStreamPresentation.row(Income(id = "loss", source = "Crypto", cryptoType = "Loss", currency = "USD",
            amountMinor = 10_000_00, amountLkrMinor = 3_309_801_89))
        assertEquals("−USD 10,000.00", row.amount)
        assertEquals("−LKR 3,309,801.89", row.conversion)
        assertTrue(row.isLoss)
        assertEquals("LKR conversion unavailable", IncomeStreamPresentation.row(Income(currency = "USD")).conversion)
    }

    @Test fun cryptoAssetIsPreparedAsMetadataWithoutReplacingTheNote() {
        val income = Income(source = "Crypto", cryptoAsset = " btc ", note = "Exchange settlement")
        val row = IncomeStreamPresentation.row(income)
        assertEquals("BTC", row.assetLabel)
        assertEquals(IncomeRecordIcon.BTC, row.icon)
        assertEquals("Exchange settlement", row.note)
        assertEquals("", IncomeStreamPresentation.row(income.copy(note = "BTC")).note)
        assertEquals("", IncomeStreamPresentation.row(income.copy(note = "")).note)
        assertNull(IncomeStreamPresentation.row(income.copy(source = "Salary")).assetLabel)
    }

    @Test fun cryptocurrencyIconsFollowTheAssetWithAGenericFallback() {
        val crypto = Income(source = "Crypto", cryptoAsset = "ETH")
        assertEquals(IncomeRecordIcon.ETH, IncomeStreamPresentation.row(crypto).icon)
        assertEquals(IncomeRecordIcon.USDT, IncomeStreamPresentation.row(crypto.copy(cryptoAsset = "USDT")).icon)
        assertEquals(IncomeRecordIcon.CRYPTO, IncomeStreamPresentation.row(crypto.copy(cryptoAsset = "CUSTOM")).icon)
        assertEquals("CUSTOM", IncomeStreamPresentation.row(crypto.copy(cryptoAsset = "CUSTOM")).assetLabel)
    }
}

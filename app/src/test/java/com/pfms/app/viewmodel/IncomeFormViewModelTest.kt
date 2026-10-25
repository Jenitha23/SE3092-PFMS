package com.pfms.app.viewmodel

import androidx.lifecycle.SavedStateHandle
import com.pfms.app.domain.model.Income
import org.junit.Assert.*
import org.junit.Test
import java.math.BigDecimal
import java.time.LocalDate
import java.time.ZoneOffset

class IncomeFormViewModelTest {
    private val original = Income("draft", "a", 12345, "USD", source = "Freelance", receivedDate = "2026-09-15",
        projectName = "Client A", paymentStatus = "Expected", note = "Invoice 01", exchangeRate = "300.25", amountLkrMinor = 3706586)
    private fun backend() = IncomeFormBackend(rate = BigDecimal("300.25"), rateDate = original.receivedDate)

    @Test fun suppliedHistoricalRateAndReconfirmedDateDoNotFetchAgain() {
        val vm = IncomeFormViewModel(SavedStateHandle(), original)
        vm.updateBackend(backend())
        var fetches = 0; var clears = 0
        vm.ensureRate { fetches++ }
        vm.dateSelected(vm.state.value.dateMillis, { fetches++ }, { clears++ })
        assertEquals(0, fetches)
        assertEquals(0, clears)
        assertEquals("LKR 37,065.86", vm.state.value.convertedAmount)
        val changedDate = LocalDate.of(2026, 9, 16).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
        vm.dateSelected(changedDate, { fetches++ }, { clears++ })
        assertEquals(1, fetches)
        assertEquals(1, clears)
        assertNull(vm.state.value.convertedAmount)
    }

    @Test fun failureAndRecreationRetainDraftAndRetrySubmitsCurrentValues() {
        val saved = SavedStateHandle()
        val first = IncomeFormViewModel(saved, original)
        first.amountChanged("987.65")
        first.noteChanged("Revised invoice")
        first.updateBackend(backend().copy(error = "Try again", canRetry = true))
        val restored = IncomeFormViewModel(saved, original)
        restored.updateBackend(backend().copy(error = "Try again", canRetry = true))
        var result: IncomeFormValues? = null
        restored.submit({ _, _ -> fail("Historical rate must not be replaced"); false }, { result = it })
        assertEquals("Retry", restored.state.value.saveLabel)
        assertEquals("987.65", result?.amount)
        assertEquals("Revised invoice", result?.note)
        assertEquals("Expected", result?.paymentStatus)
        assertEquals("Client A", result?.projectName)
        assertEquals(original.receivedDate, result?.receivedDate)
    }

    @Test fun manualRateEditsDrivePreviewAndAreAppliedBeforeSave() {
        val vm = IncomeFormViewModel(SavedStateHandle(), original)
        vm.updateBackend(backend().copy(manual = true))
        vm.manualRateChanged("310.50")
        assertEquals("LKR 38,331.23", vm.state.value.convertedAmount)
        val events = mutableListOf<String>()
        vm.submit({ rate, date -> events += "$rate $date"; true }, { events += "save" })
        assertEquals(listOf("310.50 2026-09-15", "save"), events)
        vm.manualRateChanged("0")
        vm.submit({ _, _ -> false }, { fail("Cannot save an invalid rate") })
        assertNotNull(vm.state.value.error)
    }

    @Test fun sourceSpecificValidationAndCurrencyIncrementsStayConsistent() {
        val vm = IncomeFormViewModel(SavedStateHandle())
        vm.increment(5000)
        assertEquals("5000.00", vm.state.value.amount)
        vm.sourceChanged("Freelance")
        vm.submit({ _, _ -> false }, { fail("Client is required") })
        assertNotNull(vm.state.value.projectError)
        vm.sourceChanged("Crypto")
        vm.cryptoTypeChanged("Loss")
        vm.assetChanged("btc")
        var result: IncomeFormValues? = null
        vm.submit({ _, _ -> false }, { result = it })
        assertEquals("Loss", result?.cryptoType)
        assertEquals("BTC", result?.cryptoAsset)
        assertEquals("5000.00", result?.amount)
        assertNull(result?.projectName)
        vm.currencyChanged("USD", {}, {})
        vm.increment(100)
        assertEquals("5100.00", vm.state.value.amount)
        assertEquals("USD 5,100.00", vm.state.value.amountSummary)
    }

    @Test fun savingBlocksDraftMutationsAndDuplicateSubmission() {
        val vm = IncomeFormViewModel(SavedStateHandle(), original)
        vm.updateBackend(backend().copy(isSaving = true))
        vm.amountChanged("900")
        vm.increment(50)
        vm.currencyChanged("LKR", { fail("No rate request while saving") }, {})
        vm.submit({ _, _ -> false }, { fail("Duplicate submit") })
        assertEquals("123.45", vm.state.value.amount)
        assertEquals("USD", vm.state.value.currency)
    }
}

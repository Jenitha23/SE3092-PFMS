package com.pfms.app.viewmodel

import com.pfms.app.data.export.IncomeInsightsReport
import com.pfms.app.domain.model.Income
import com.pfms.app.domain.usecase.analytics.*
import org.junit.Test
import org.junit.Assert.*
import java.time.YearMonth
import java.time.ZoneOffset

class IncomeInsightsScreenStateTest {
    private val month = YearMonth.of(2026, 10)
    private fun source() = IncomeAnalyticsUiState(isLoading = false, selectedMonth = month, hasMonthlyIncome = true,
        insights = IncomeInsightsCalculator.calculate(listOf(Income(source = "Salary", amountMinor = 357396932, receivedDate = "2026-10-01"),
            Income(source = "Crypto", cryptoType = "Loss", amountMinor = 330850189, receivedDate = "2026-10-10")), month))

    @Test fun displayPreservesFullAmountsAndCreatesAReconciledReport() {
        val vm = IncomeInsightsScreenViewModel()
        vm.sync(source())
        assertEquals("LKR 265,467.43", vm.state.value.display?.total)
        assertEquals("LKR 3,573,969.32", vm.state.value.display?.gross)
        assertEquals("− LKR 3,308,501.89", vm.state.value.display?.losses)
        val csv = vm.report().csv()
        assertTrue(csv.contains("\"Net realized income\",\"LKR 265,467.43\""))
        assertTrue(csv.contains("- LKR 3,308,501.89"))
        assertTrue(csv.contains("Expected freelance is excluded"))
    }
    @Test fun invalidEstimateNeverSavesAndSourceRefreshKeepsTheEnteredDraft() {
        val vm = IncomeInsightsScreenViewModel()
        vm.sync(source())
        var saved: Long? = null
        vm.estimate("150000.001"); vm.applyEstimate { saved = it }
        assertNull(saved); assertNotNull(vm.state.value.estimateError)
        vm.estimate("150000.50")
        vm.sync(source().copy(errorMessage = "Try again", canRetry = true))
        assertEquals("150000.50", vm.state.value.estimate)
        vm.applyEstimate { saved = it }
        assertEquals(15000050L, saved)
        var cleared = false
        vm.clearEstimate { cleared = true }
        assertTrue(cleared); assertEquals("", vm.state.value.estimate)
    }
    @Test fun calendarAndChartSelectionRemainPresentationState() {
        val vm = IncomeInsightsScreenViewModel()
        vm.sync(source()); vm.calendar(true)
        var selected: YearMonth? = null
        val millis = month.minusMonths(1).atDay(20).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
        vm.selectDate(millis) { selected = it }
        assertEquals(month.minusMonths(1), selected)
        assertFalse(vm.state.value.calendar)
        vm.trend("2026-09")
        assertEquals("2026-09", vm.state.value.selectedTrend)
    }
    @Test fun csvQuotesCommasAndEmbeddedQuotesWithoutLosingValues() {
        val csv = IncomeInsightsReport("October", listOf(listOf("Value, LKR", "1,000.00", "A \"quoted\" label"))).csv()
        assertEquals("\"Value, LKR\",\"1,000.00\",\"A \"\"quoted\"\" label\"\r\n", csv)
    }
    @Test fun comparisonKeepsDifferenceVarianceAndReadableMonthsAsSeparateDisplayValues() {
        val vm = IncomeInsightsScreenViewModel()
        vm.sync(source().copy(estimationComparison = com.pfms.app.domain.model.analytics.IncomeEstimationComparison(
            25000000, 0, -25000000, -100.0, com.pfms.app.domain.model.analytics.EstimationStatus.BELOW_ESTIMATE,
            listOf("2026-07", "2026-08", "2026-09"))))
        val comparison = vm.state.value.display!!.comparison!!
        assertEquals("LKR 250,000.00", comparison.estimated)
        assertEquals("- LKR 250,000.00", comparison.difference)
        assertEquals("-100.0%", comparison.variance)
        assertEquals(listOf("Jul 2026", "Aug 2026", "Sep 2026"), comparison.months)
        assertTrue(comparison.below)
    }
}

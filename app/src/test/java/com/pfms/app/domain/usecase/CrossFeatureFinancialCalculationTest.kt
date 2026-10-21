package com.pfms.app.domain.usecase

import com.pfms.app.domain.model.Expense
import com.pfms.app.domain.model.Income
import com.pfms.app.domain.usecase.analytics.GetMonthlyIncomeSummaryUseCase
import com.pfms.app.domain.usecase.dashboard.MonthlyFinanceCalculator
import com.pfms.app.domain.usecase.savings.CalculateMonthlyNetSavingsUseCase
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.time.YearMonth

class CrossFeatureFinancialCalculationTest {
    private val month = YearMonth.of(2026, 9)
    private val analytics = GetMonthlyIncomeSummaryUseCase()
    private val netSavings = CalculateMonthlyNetSavingsUseCase(analytics)

    @Test fun historicalMixedCaseRecordsHaveTheSameTotalsAcrossAllFinancialViews() {
        val incomes = listOf(
            Income(amountMinor = 10000, receivedDate = "2026-09-01", source = "salary"),
            Income(amountMinor = 1200, receivedDate = "2026-09-02", source = "crypto", cryptoType = "loss"),
            Income(amountMinor = 300, receivedDate = "2026-09-03", source = "CRYPTO", cryptoType = "GAIN"),
            Income(amountMinor = 10, amountLkrMinor = 3500, currency = "USD", receivedDate = "2026-09-04",
                source = "freelance", paymentStatus = "received"),
            Income(amountMinor = 4000, receivedDate = "2026-09-05", source = "FREELANCE", paymentStatus = "Expected"),
            Income(amountMinor = 9000, receivedDate = "2026-10-01", source = "Salary")
        )
        val expenses = listOf(Expense(amountMinor = 2300, date = "2026-09-12"))
        val dashboard = MonthlyFinanceCalculator.summarize(incomes, expenses, month)
        assertEquals(12600L, dashboard.totalIncomeMinor)
        assertEquals(12600L, analytics(incomes, month).totalRealizedMinor)
        assertEquals(12600L, IncomeSummaryCalculator.calculate(incomes, LocalDate.of(2026, 9, 30)).monthlyRealizedMinor)
        // The raw currency breakdown excludes crypto, which has its own asset section.
        assertEquals(23000L, IncomeSummaryCalculator.currencyTotals(incomes).lkrMinor)
        assertEquals(10300L, netSavings(incomes, expenses, month))
        assertEquals(dashboard.totalIncomeMinor - dashboard.totalExpenseMinor, netSavings(incomes, expenses, month))
    }

    @Test fun legacyMonthOnlyExpenseDatesAreHandledConsistentlyByDashboardAndGoalPace() {
        val incomes = listOf(Income(amountMinor = 10000, receivedDate = "2026-09-01"))
        val expenses = listOf(Expense(amountMinor = 2300, date = "2026-09"), Expense(amountMinor = 500, date = "unknown"))
        val dashboard = MonthlyFinanceCalculator.summarize(incomes, expenses, month)
        assertEquals(2300L, dashboard.totalExpenseMinor)
        assertEquals(7700L, netSavings(incomes, expenses, month))
        assertEquals(dashboard.totalIncomeMinor - dashboard.totalExpenseMinor, netSavings(incomes, expenses, month))
    }

    @Test fun emptyAndUnconvertedIncomeNeverInventMoneyAcrossViews() {
        for (incomes in listOf(emptyList(), listOf(Income(amountMinor = 100, currency = "USD", receivedDate = "2026-09-01")))) {
            assertEquals(0L, MonthlyFinanceCalculator.summarize(incomes, emptyList(), month).totalIncomeMinor)
            assertEquals(0L, analytics(incomes, month).totalRealizedMinor)
            assertEquals(0L, IncomeSummaryCalculator.calculate(incomes, LocalDate.of(2026, 9, 30)).monthlyRealizedMinor)
            assertEquals(0L, netSavings(incomes, emptyList(), month))
        }
    }

    @Test fun legacyPendingFreelanceIsUnrealizedAndCountedAsPending() {
        val rows = listOf(Income(amountMinor = 100, source = "freelance", paymentStatus = "Pending", receivedDate = "2026-09-01"))
        val summary = IncomeSummaryCalculator.calculate(rows, LocalDate.of(2026, 9, 30))
        assertEquals(0L, summary.realizedMinor)
        assertEquals(1, summary.pendingFreelanceCount)
        assertEquals(100L, summary.pendingFreelanceMinor)
        assertEquals(0L, analytics(rows, month).totalRealizedMinor)
    }
}

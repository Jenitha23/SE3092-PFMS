package com.pfms.app.domain.usecase.dashboard

import com.pfms.app.domain.model.Expense
import com.pfms.app.domain.model.Income
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate
import java.time.YearMonth

class DashboardOverviewCalculatorTest {
    private val month = YearMonth.of(2026, 10)
    private val today = LocalDate.of(2026, 10, 20)

    @Test
    fun emptyDataDoesNotInventRatiosOrComparisons() {
        val result = DashboardOverviewCalculator.calculate(emptyList(), emptyList(), month, today)
        assertNull(result.coveragePercent)
        assertNull(result.expenseToIncomePercent)
        assertNull(result.inflowChangePercent)
        assertNull(result.dailySpendChangePercent)
        assertEquals(0L, result.dailySpendMinor)
        assertEquals(0L, result.historicalAverageNetMinor)
        assertEquals(5, result.trend.size)
        assertTrue(result.trend.all { it.incomeMinor == 0L && it.expenseMinor == 0L })
    }

    @Test
    fun dailySpendingExcludesFutureDatedEntriesAndUsesElapsedDays() {
        val expenses = listOf(Expense(amountMinor = 10_000L, date = "2026-10-10"),
            Expense(amountMinor = 90_000L, date = "2026-10-25"))
        val result = DashboardOverviewCalculator.calculate(emptyList(), expenses, month, today)
        assertEquals(100_000L, result.summary.totalExpenseMinor)
        assertEquals(500L, result.dailySpendMinor)
        assertEquals(20, result.elapsedDays)
        assertEquals(11, result.daysRemaining)
        assertEquals(500L, result.dailyAverageHistory.last())
    }

    @Test
    fun deficitCoverageAndInflowChangeUseActualPreviousMonth() {
        val incomes = listOf(Income(amountMinor = 100_000L, receivedDate = "2026-09-01"),
            Income(amountMinor = 150_000L, receivedDate = "2026-10-01"))
        val expenses = listOf(Expense(amountMinor = 300_000L, date = "2026-10-10"))
        val result = DashboardOverviewCalculator.calculate(incomes, expenses, month, today)
        assertEquals(-150_000L, result.summary.netSavingsMinor)
        assertEquals(50.0, result.coveragePercent!!, 0.001)
        assertEquals(200.0, result.expenseToIncomePercent!!, 0.001)
        assertEquals(50.0, result.inflowChangePercent!!, 0.001)
        assertEquals(25_000L, result.historicalAverageNetMinor)
    }

    @Test
    fun historicalMonthUsesItsFullLengthIncludingLeapDay() {
        val result = DashboardOverviewCalculator.calculate(emptyList(),
            listOf(Expense(amountMinor = 29_000L, date = "2024-02-29")), YearMonth.of(2024, 2), today)
        assertEquals(29, result.elapsedDays)
        assertEquals(0, result.daysRemaining)
        assertFalse(result.isCurrentMonth)
        assertEquals(1_000L, result.dailySpendMinor)
    }

    @Test
    fun pendingIncomeAndCryptoLossKeepExistingRealizationRules() {
        val result = DashboardOverviewCalculator.calculate(listOf(
            Income(amountMinor = 100_000L, receivedDate = "2026-10-01"),
            Income(amountMinor = 80_000L, source = "Freelance", paymentStatus = "Expected", receivedDate = "2026-10-02"),
            Income(amountMinor = 20_000L, source = "Crypto", cryptoType = "Loss", receivedDate = "2026-10-03")
        ), emptyList(), month, today)
        assertEquals(80_000L, result.summary.totalIncomeMinor)
    }
}

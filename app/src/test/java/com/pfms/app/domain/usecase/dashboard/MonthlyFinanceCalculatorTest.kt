package com.pfms.app.domain.usecase.dashboard

import com.pfms.app.domain.model.ExpenseClassification
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Clock
import java.time.Instant
import java.time.YearMonth
import java.time.ZoneOffset

class MonthlyFinanceCalculatorTest {

    private val october = YearMonth.of(2026, 10)

    private val incomes = listOf(
        income("i1", "2026-10-25", 13_000_000L),                                   // salary
        income("i2", "2026-10-10", 5_000_000L, "Freelance", status = "Expected"),  // not realised
        income("i3", "2026-10-12", 2_000_000L, "Freelance", status = "Received"),
        income("i4", "2026-10-05", 100_000L, "Crypto", cryptoType = "Loss"),       // reduces income
        income("i5", "2026-10-15", 5_000L, "Google AdSense", currency = "USD", lkrMinor = 1_600_000L),
        income("i6", "2026-09-25", 12_000_000L)                                    // other month
    )

    private val expenses = listOf(
        expense("e1", "2026-10-01", 3_400_000L, "Rent", ExpenseClassification.COMMITTED),
        expense("e2", "2026-10-20", 250_000L),
        expense("e3", "2026-10-21", 150_000L),
        expense("e4", "2026-09-30", 999_999L)                                      // other month
    )

    @Test
    fun incomeTotalExcludesExpectedFreelanceAndSubtractsCryptoLoss() {
        val summary = MonthlyFinanceCalculator.summarize(incomes, expenses, october)
        // 130,000 + 20,000 - 1,000 + 16,000 (stored LKR for the USD entry) = 165,000.00
        assertEquals(16_500_000L, summary.totalIncomeMinor)
    }

    @Test
    fun expenseTotalAndNetSavingsOnlyCountTheSelectedMonth() {
        val summary = MonthlyFinanceCalculator.summarize(incomes, expenses, october)
        assertEquals(3_800_000L, summary.totalExpenseMinor)
        assertEquals(12_700_000L, summary.netSavingsMinor)
        assertTrue(summary.isSurplus)
    }

    @Test
    fun categoryTotalsAddUpToTotalExpensesAndAreSortedLargestFirst() {
        val summary = MonthlyFinanceCalculator.summarize(incomes, expenses, october)
        assertEquals(summary.totalExpenseMinor, summary.categories.sumOf { it.amountMinor })
        assertEquals(listOf("Rent", "Coffee & Dining Out"), summary.categories.map { it.categoryName })
        assertEquals(89.5, summary.categories[0].percent, 0.0)
        assertEquals(10.5, summary.categories[1].percent, 0.0)
    }

    @Test
    fun committedDiscretionarySplitMatchesExpenses() {
        val split = MonthlyFinanceCalculator.summarize(incomes, expenses, october).split
        assertEquals(3_400_000L, split.committedMinor)
        assertEquals(400_000L, split.discretionaryMinor)
        assertEquals(89.5, split.committedPercent, 0.0)
        assertEquals(10.5, split.discretionaryPercent, 0.0)
    }

    @Test
    fun deficitIsReportedWhenExpensesExceedIncome() {
        val summary = MonthlyFinanceCalculator.summarize(
            listOf(income("a", "2026-10-02", 100_000L)),
            listOf(expense("b", "2026-10-03", 300_000L)),
            october
        )
        assertEquals(-200_000L, summary.netSavingsMinor)
        assertFalse(summary.isSurplus)
    }

    @Test
    fun emptyMonthGivesZerosAndNeverDividesByZero() {
        val summary = MonthlyFinanceCalculator.summarize(emptyList(), emptyList(), october)
        assertEquals(0L, summary.totalIncomeMinor)
        assertEquals(0L, summary.totalExpenseMinor)
        assertEquals(0.0, summary.split.committedPercent, 0.0)
        assertEquals(0.0, summary.split.discretionaryPercent, 0.0)
        assertTrue(summary.categories.isEmpty())
        assertFalse(summary.hasData)
    }

    @Test
    fun percentOfRoundsHalfUpToOneDecimalAndGuardsZero() {
        assertEquals(33.3, MonthlyFinanceCalculator.percentOf(1, 3), 0.0)
        assertEquals(66.7, MonthlyFinanceCalculator.percentOf(2, 3), 0.0)
        assertEquals(0.0, MonthlyFinanceCalculator.percentOf(5, 0), 0.0)
        assertEquals(0.0, MonthlyFinanceCalculator.percentOf(0, 100), 0.0)
    }

    @Test
    fun trendHasSixMonthsOldestFirstWithZeroForEmptyMonths() {
        val trend = MonthlyFinanceCalculator.trend(incomes, expenses, october, months = 6)
        assertEquals(6, trend.size)
        assertEquals(YearMonth.of(2026, 5), trend.first().month)
        assertEquals(october, trend.last().month)
        assertEquals(0L, trend.first().incomeMinor)
        assertEquals(16_500_000L, trend.last().incomeMinor)
        assertEquals(3_800_000L, trend.last().expenseMinor)
        val september = trend[4]
        assertEquals(12_000_000L, september.incomeMinor)
        assertEquals(999_999L, september.expenseMinor)
    }

    @Test
    fun monthsAreDefinedInColomboTime() {
        // 19:00 UTC on 31 Oct is already 00:30 on 1 Nov in Sri Lanka (UTC+5:30).
        val clock = Clock.fixed(Instant.parse("2026-10-31T19:00:00Z"), ZoneOffset.UTC)
        assertEquals(YearMonth.of(2026, 11), AppClock.currentMonth(clock))
    }
}

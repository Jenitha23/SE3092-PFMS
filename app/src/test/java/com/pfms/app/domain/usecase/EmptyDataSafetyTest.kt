package com.pfms.app.domain.usecase

import com.pfms.app.domain.model.*
import com.pfms.app.domain.model.savings.PaceBenchmark
import com.pfms.app.domain.usecase.analytics.*
import com.pfms.app.domain.usecase.dashboard.*
import com.pfms.app.domain.usecase.savings.*
import com.pfms.app.ui.component.formatPercent
import com.pfms.app.ui.component.formatTransactionDate
import com.pfms.app.ui.screen.savings.SavingsGoalUiFormatter
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate
import java.time.YearMonth

class EmptyDataSafetyTest {
    private val month = YearMonth.of(2026, 10)
    private val monthlyIncome = GetMonthlyIncomeSummaryUseCase()
    private val progress = CalculateGoalProgressUseCase(CalculateSavedAmountUseCase())
    private val netSavings = CalculateMonthlyNetSavingsUseCase(monthlyIncome)
    private val trailingSavings = CalculateTrailingNetSavingsAverageUseCase(netSavings)
    private val pace = CalculateGoalPaceUseCase(progress, CalculateCurrentMonthGoalContributionUseCase(), netSavings, trailingSavings)

    @Test fun newUserHasZeroMonthlyFiguresAndContinuousZeroTrends() {
        val summary = MonthlyFinanceCalculator.summarize(emptyList(), emptyList(), month)
        assertFalse(summary.hasData)
        assertEquals(0L, summary.totalIncomeMinor)
        assertEquals(0L, summary.totalExpenseMinor)
        assertEquals(0L, summary.netSavingsMinor)
        assertEquals(0.0, summary.split.committedPercent, 0.0)
        assertEquals(0.0, summary.split.discretionaryPercent, 0.0)
        assertTrue(summary.categories.isEmpty())
        val trend = MonthlyFinanceCalculator.trend(emptyList(), emptyList(), month)
        assertEquals(6, trend.size)
        assertTrue(trend.all { it.incomeMinor == 0L && it.expenseMinor == 0L })
        assertTrue(MonthlyFinanceCalculator.trend(emptyList(), emptyList(), month, 0).isEmpty())
        assertEquals(0L, IncomeSummaryCalculator.calculate(emptyList(), month.atDay(1)).realizedMinor)
        assertEquals(IncomeCurrencyTotals(), IncomeSummaryCalculator.currencyTotals(emptyList()))
        assertEquals(0L, RecurringExpenseCalculator.calculateMonthlySubscriptionTotal(emptyList()))
    }

    @Test fun zeroDenominatorsAndNonFiniteInputsNeverEscapeRatioGuards() {
        for (denominator in listOf(0.0, -1.0, Double.NaN, Double.POSITIVE_INFINITY)) {
            assertEquals(0.0, SafeCalculations.ratio(100.0, denominator), 0.0)
        }
        for (value in listOf(Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY)) {
            assertEquals(0.0, SafeCalculations.ratio(value, 100.0), 0.0)
            assertEquals(0f, SafeCalculations.progressFraction(value), 0f)
            assertEquals("0%", formatPercent(value))
        }
        assertEquals(0.0, SafeCalculations.ratio(Double.MAX_VALUE, Double.MIN_VALUE), 0.0)
        assertEquals(1f, SafeCalculations.progressFraction(150.0), 0f)
        assertEquals(0f, SafeCalculations.progressFraction(-50.0), 0f)
        assertEquals(-50.0, SafeCalculations.percentage(-50, 100), 0.0)
        assertEquals(0.0, MonthlyFinanceCalculator.percentOf(100, 0), 0.0)
    }

    @Test fun zeroValueExpensesKeepCategoryPercentagesAtZero() {
        val summary = MonthlyFinanceCalculator.summarize(emptyList(), listOf(Expense(date = "2026-10-01")), month)
        assertTrue(summary.hasData)
        assertEquals(0.0, summary.categories.single().percent, 0.0)
        assertEquals(0.0, summary.split.discretionaryPercent, 0.0)
        assertEquals(MonthlyFinanceCalculator.UNCATEGORISED, summary.categories.single().categoryName)
    }

    @Test fun nullConversionAndMissingOrMalformedDatesProduceNoInventedIncome() {
        assertEquals(0L, IncomeRealizedCalculator.getEffectiveLkrMinor(Income(currency = "USD", amountMinor = 10000)))
        assertEquals(25000L, IncomeRealizedCalculator.getEffectiveLkrMinor(Income(currency = "USD", amountLkrMinor = 25000)))
        for (date in listOf(null, "", "bad", "2026-10-99", "2026-10-invalid")) {
            assertNull(IncomeRealizedCalculator.parseYearMonth(date))
        }
        val summary = MonthlyFinanceCalculator.summarize(
            listOf(Income(amountMinor = 100, receivedDate = "")),
            listOf(Expense(amountMinor = 100, date = "2026-10-99")), month
        )
        assertFalse(summary.hasData)
        assertEquals("Date unavailable", formatTransactionDate(""))
    }

    @Test fun cancellingIncomeAndLossHaveFiniteZeroPercentages() {
        val regularVariable = GetRegularVsVariableIncomeUseCase(monthlyIncome)(listOf(
            Income(amountMinor = 100), Income(amountMinor = 100, source = "Crypto", cryptoType = "Loss")
        ))
        assertEquals(0L, regularVariable.totalMinor)
        assertEquals(0.0, regularVariable.regularPercentage, 0.0)
        assertEquals(0.0, regularVariable.variablePercentage, 0.0)
        val comparison = GetIncomeEstimationComparisonUseCase(GetTrailingThreeMonthAverageUseCase(monthlyIncome))(
            0L, emptyList(), month
        )
        assertEquals(0.0, comparison.variancePercentage, 0.0)
    }

    @Test fun missingGoalTargetAndDateAreSafeAndZeroValued() {
        val result = progress(SavingsGoal(), emptyList(), month.atDay(1))
        assertEquals(0.0, result.progressPercentage, 0.0)
        assertEquals(0L, result.remainingMonths)
        assertEquals(0L, result.requiredMonthlySavingMinor)
        assertEquals(0L, netSavings(emptyList(), emptyList(), month))
        assertEquals(0L, trailingSavings(emptyList(), emptyList(), month).averageMinor)
        assertEquals(0.0, CalculateDiscretionaryImpactUseCase()(emptyList(), month, 0L).percentageOfRequiredSaving, 0.0)
    }

    @Test fun missingDateAndZeroPaceDoNotInventACompletionDate() {
        val result = pace(SavingsGoal(id = "goal", targetAmountMinor = 100), emptyList(), emptyList(), emptyList(), month.atDay(1))
        assertNull(result.projectedCompletionDate)
    }

    @Test fun enormousGoalProjectionCannotOverflowTheCalendar() {
        val result = pace(
            SavingsGoal(id = "goal", targetAmountMinor = Long.MAX_VALUE, targetDate = "2026-11-01"),
            emptyList(), listOf(Income(amountMinor = 1, receivedDate = "2026-10-01")), emptyList(),
            LocalDate.of(2026, 10, 1), PaceBenchmark.CURRENT_MONTH_NET_SAVINGS
        )
        assertNull(result.projectedCompletionDate)
        assertEquals(Long.MAX_VALUE, result.requiredMonthlySavingMinor)
    }

    @Test fun savingsInputRejectsAmountsOutsideTheStoredMoneyRange() {
        assertNull(SavingsGoalUiFormatter.parseLkrToMinor("92233720368547758.08"))
        assertNull(SavingsGoalUiFormatter.parseLkrToMinor("NaN"))
        assertNull(SavingsGoalUiFormatter.parseLkrToMinor("Infinity"))
        assertNull(SavingsGoalUiFormatter.parseLkrToMinor("1.001"))
        assertEquals(0L, SavingsGoalUiFormatter.parseLkrToMinor("0.00"))
    }
}

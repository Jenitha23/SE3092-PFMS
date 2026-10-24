package com.pfms.app.domain.usecas

import com.pfms.app.domain.model.*
import com.pfms.app.domain.usecase.savings.CalculateSavedAmountUseCase
import java.time.LocalDate
import java.time.YearMonth
import org.junit.Assert.*
import org.junit.Test
import com.pfms.app.domain.usecase.DetectSpendingPatternsUseCase
import com.pfms.app.domain.usecase.CalculateSavingsSimulationUseCase

class InnovationUseCasesTest {
    private val detector = DetectSpendingPatternsUseCase()
    private fun coffee(date: String, minor: Long) = Expense(
        id = date, date = date, amountMinor = minor,
        categoryId = "coffee", categoryName = "Coffee & Dining Out"
    )

    @Test fun detectsIncreaseAgainstConfirmedMonths() {
        val data = listOf(coffee("2026-07-10", 600_000), coffee("2026-08-10", 750_000),
            coffee("2026-09-10", 600_000), coffee("2026-10-10", 1_000_000))
        val result = detector.analyze(data, LocalDate.parse("2026-10-24"),
            setOf(YearMonth.parse("2026-07"), YearMonth.parse("2026-08"), YearMonth.parse("2026-09")))
        assertEquals(SpendingPatternStatus.READY, result.status)
        assertEquals(650_000L, result.insights.single().baselineAmountMinor)
        assertEquals(54, result.insights.single().increasePercent)
        assertTrue(result.insights.single().isMonthToDateComparison)
    }

    @Test fun insufficientHistoryDoesNotInventBaseline() {
        val result = detector.analyze(listOf(coffee("2026-10-10", 1_000_000)),
            LocalDate.parse("2026-10-24"), emptySet())
        assertEquals(SpendingPatternStatus.INSUFFICIENT_HISTORY, result.status)
        assertTrue(result.insights.isEmpty())
    }

    @Test fun categorySpendingBelowThresholdHasNoInsight() {
        val data = listOf(coffee("2026-08-10", 1_000_000), coffee("2026-09-10", 1_000_000), coffee("2026-10-10", 1_100_000))
        val result = detector.analyze(data, LocalDate.parse("2026-10-24"),
            setOf(YearMonth.parse("2026-08"), YearMonth.parse("2026-09")))
        assertTrue(result.insights.isEmpty())
    }

    private val simulator = CalculateSavingsSimulationUseCase(CalculateSavedAmountUseCase())
    @Test fun simulatorDoesNotAlterLedgerAndCalculatesExtra() {
        val goal = SavingsGoal(id="g", targetAmountMinor=49_000_000, openingSavedAmountMinor=1_120_000, targetDate="2027-10-24")
        val result = simulator.simulate(goal, emptyList(), monthlyReductionMinor=350_000,
            plannedMonthlyContributionMinor=0, asOfDate=LocalDate.parse("2026-10-24"))
        assertEquals(4_200_000L, result.potentialExtraByDeadlineMinor)
        assertEquals(5_320_000L, result.projectedBalanceAtDeadlineMinor)
        assertEquals(1_120_000L, goal.openingSavedAmountMinor)
    }

    @Test fun simulatorValidatesNegativeReduction() {
        val goal = SavingsGoal(id="g", targetAmountMinor=49_000_000, targetDate="2027-10-24")
        assertThrows(IllegalArgumentException::class.java) {
            simulator.simulate(goal, emptyList(), -1, asOfDate=LocalDate.parse("2026-10-24"))
        }
    }
}

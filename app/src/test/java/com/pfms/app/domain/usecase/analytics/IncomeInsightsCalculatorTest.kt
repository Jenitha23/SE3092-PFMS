package com.pfms.app.domain.usecase.analytics

import com.pfms.app.domain.model.Income
import org.junit.Test
import org.junit.Assert.*
import java.time.YearMonth

class IncomeInsightsCalculatorTest {
    private val month = YearMonth.of(2026, 10)
    private val records = listOf(
        Income(id = "salary", source = "Salary", currency = "USD", amountMinor = 1000000, amountLkrMinor = 357396932, receivedDate = "2026-10-10"),
        Income(id = "loss", source = "Crypto", cryptoType = "Loss", amountMinor = 330850189, receivedDate = "2026-10-20"),
        Income(id = "pending", source = "Freelance", paymentStatus = "Expected", amountMinor = 100000, receivedDate = "2026-10-21"))

    @Test fun waterfallReconcilesHistoricalFxAndRealizedLossesWithoutPendingInvoices() {
        val result = IncomeInsightsCalculator.calculate(records, month)
        assertEquals(357396932L, result.grossMinor)
        assertEquals(330850189L, result.lossesMinor)
        assertEquals(26546743L, result.netMinor)
        assertEquals(result.netMinor, result.grossMinor - result.lossesMinor)
        assertEquals(100000L, result.pendingMinor); assertEquals(1, result.pendingCount)
        assertEquals(2, result.realizedCount)
        assertEquals(100.0, result.regularShare, .0001)
        assertEquals(0.0, result.variableShare, .0001)
        assertEquals(-330850189L, result.sources.first { it.key == "Crypto" }.amountMinor)
        assertEquals(26546743L, result.accumulation.last().amountMinor)
        assertTrue(result.accumulation.all { it.x in 0f..1f && it.y in 0f..1f })
    }
    @Test fun rangeSelectionUsesCalendarMonthsAndComparesThePreviousEqualPeriod() {
        val extra = listOf(
            Income(source = "Salary", amountMinor = 100000, receivedDate = "2026-08-01"),
            Income(source = "Salary", amountMinor = 200000, receivedDate = "2026-09-01"),
            Income(source = "Salary", amountMinor = 150000, receivedDate = "2026-07-01"))
        val result = IncomeInsightsCalculator.calculate(extra, month, IncomeInsightRange.QUARTER)
        assertEquals(YearMonth.of(2026, 8), result.start)
        assertEquals(300000L, result.netMinor)
        assertEquals(100.0, result.changePercent!!, .0001)
        assertEquals(listOf("2026-08", "2026-09", "2026-10"), result.trend.map { it.key })
        assertEquals(3, result.accumulation.size)
        val all = IncomeInsightsCalculator.calculate(extra, month, IncomeInsightRange.ALL)
        assertEquals(YearMonth.of(2026, 7), all.start)
        assertEquals(450000L, all.netMinor)
        assertEquals(4, all.trend.size)
    }
    @Test fun zeroAndNegativeOnlyPeriodsHaveFiniteChartsAndNoInventedGrowth() {
        val empty = IncomeInsightsCalculator.calculate(emptyList(), month)
        assertEquals(0L, empty.netMinor); assertEquals(6, empty.trend.size)
        assertEquals(0, empty.sourceCount); assertNull(empty.changePercent)
        assertTrue(empty.trend.all { it.y.isFinite() })
        val negative = IncomeInsightsCalculator.calculate(listOf(records[1]), month)
        assertEquals(-330850189L, negative.netMinor)
        assertEquals(0L, negative.grossMinor)
        assertEquals(0f, negative.trendZero, 0f)
        assertTrue(negative.trend.all { it.x.isFinite() && it.y in 0f..1f })
        assertNull(negative.biggestSource)
    }
    @Test fun grossSharesDoNotUseTheSmallNetAfterTradingLossesAsTheDenominator() {
        val result = IncomeInsightsCalculator.calculate(records + Income(source = "Crypto", cryptoType = "Gain", amountMinor = 2000000, receivedDate = "2026-10-05"), month)
        assertEquals(359396932L, result.grossMinor)
        assertEquals(28546743L, result.netMinor)
        assertTrue(result.regularShare < 100)
        assertEquals(100.0, result.regularShare + result.variableShare, .0001)
        assertTrue(result.margin < 10)
    }
    @Test fun unknownDailyDatesNeverProduceAnInventedAccumulationCurve() {
        val result = IncomeInsightsCalculator.calculate(listOf(Income(source = "Salary", amountMinor = 100000, receivedDate = "2026-10")), month)
        assertEquals(100000L, result.netMinor)
        assertTrue(result.accumulation.isEmpty())
    }
    @Test fun emptyMonthsExcludePendingInvoicesButZeroNetReceivedMonthsRemainRecorded() {
        val data = listOf(
            Income(source = "Freelance", paymentStatus = "Expected", amountMinor = 100000, receivedDate = "2026-09-10"),
            Income(source = "Salary", amountMinor = 100000, receivedDate = "2026-10-01"),
            Income(source = "Crypto", cryptoType = "Loss", amountMinor = 100000, receivedDate = "2026-10-02"))
        val result = IncomeInsightsCalculator.calculate(data, month)
        assertTrue(result.trend.dropLast(1).all { it.empty && it.amountMinor == 0L })
        assertFalse(result.trend.last().empty)
        assertEquals(0L, result.trend.last().amountMinor)
        assertTrue(IncomeInsightsCalculator.calculate(emptyList(), month).trend.all { it.empty })
    }
    @Test(expected = ArithmeticException::class) fun unsupportedTotalsCannotWrapToAFalseBalance() {
        IncomeInsightsCalculator.calculate(listOf(Income(amountMinor = Long.MAX_VALUE, receivedDate = "2026-10-01"),
            Income(amountMinor = 1, receivedDate = "2026-10-01")), month)
    }
}

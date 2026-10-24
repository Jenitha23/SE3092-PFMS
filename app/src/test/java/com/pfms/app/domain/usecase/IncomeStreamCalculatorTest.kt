package com.pfms.app.domain.usecase

import com.pfms.app.domain.model.Income
import org.junit.Assert.*
import org.junit.Test
import java.time.YearMonth

class IncomeStreamCalculatorTest {
    private val month = YearMonth.of(2026, 10)
    private fun income(id: String, amount: Long, source: String = "Salary", date: String = "2026-10-18",
        currency: String = "LKR", converted: Long? = null, status: String = "Received", type: String? = null) =
        Income(id = id, amountMinor = amount, source = source, receivedDate = date, currency = currency,
            amountLkrMinor = converted, paymentStatus = status, cryptoType = type)

    @Test fun monthTotalsExcludePendingAndCountUsdCryptoOnce() {
        val result = IncomeStreamCalculator.calculate(listOf(
            income("local", 100_00), income("usd", 10_00, currency = "USD", converted = 3_000_00),
            income("gain", 2_00, "Crypto", currency = "USD", converted = 600_00, type = "Gain"),
            income("loss", 3_00, "Crypto", currency = "USD", converted = 900_00, type = "Loss"),
            income("invoice", 400_00, "Freelance", status = "Expected"),
            income("old", 100_000_00, date = "2026-09-18")), month)
        assertEquals(2_800_00L, result.realizedMinor)
        assertEquals(100_00L, result.lkrMinor)
        assertEquals(3_000_00L, result.usdInLkrMinor)
        assertEquals(10_00L, result.usdMinor)
        assertEquals(-300_00L, result.cryptoNetMinor)
        assertEquals(400_00L, result.pendingMinor)
        assertEquals(1, result.pendingCount)
        assertEquals(5, result.records.size)
        assertEquals(1f, result.sources.sumOf { it.fraction.toDouble() }.toFloat(), .0001f)
    }

    @Test fun emptyAndNegativeMonthsNeverFallBackToAllTimeIncome() {
        val records = listOf(income("old", 100_000_00, date = "2026-09-18"), income("loss", 300_00, "Crypto", type = "Loss"))
        val negative = IncomeStreamCalculator.calculate(records, month)
        assertEquals(-300_00L, negative.realizedMinor)
        assertTrue(negative.sources.isEmpty())
        assertEquals(0L, IncomeStreamCalculator.calculate(records, month.plusMonths(1)).realizedMinor)
    }

    @Test fun comparisonRequiresPositiveAndFullyConvertedPreviousMonth() {
        val records = listOf(income("current", 150_00), income("previous", 100_00, date = "2026-09-18"))
        assertEquals(50.0, IncomeStreamCalculator.calculate(records, month).changePercent!!, .0001)
        val incomplete = records + income("unknown", 99_00, date = "2026-09-19", currency = "USD")
        assertNull(IncomeStreamCalculator.calculate(incomplete, month).changePercent)
        assertNull(IncomeStreamCalculator.calculate(listOf(income("only", 50_00)), month).changePercent)
    }

    @Test fun missingConversionsAreFlaggedAndNeverTreatedAsLkr() {
        val result = IncomeStreamCalculator.calculate(listOf(income("unknown", 500_00, currency = "USD"),
            income("local", 100_00), income("invalid", 50_00, date = "bad-date")), month)
        assertEquals(1, result.unconvertedCount)
        assertEquals(100_00L, result.realizedMinor)
        assertEquals(0L, result.usdInLkrMinor)
        assertEquals(500_00L, result.usdMinor)
        assertEquals(2, result.records.size)
    }

    @Test fun legacyCryptoWithoutTypeReconcilesWithTheEstablishedRealizedRule() {
        val result = IncomeStreamCalculator.calculate(listOf(income("legacy", 100_00, "Crypto")), month)
        assertEquals(100_00L, result.realizedMinor)
        assertEquals(result.realizedMinor, result.lkrMinor + result.usdInLkrMinor + result.cryptoNetMinor)
    }
}

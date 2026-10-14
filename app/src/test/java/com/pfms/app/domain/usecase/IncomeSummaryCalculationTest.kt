package com.pfms.app.domain.usecase

import com.pfms.app.domain.model.Income
import org.junit.Assert.assertEquals
import org.junit.Test
import java.math.BigDecimal
import java.time.LocalDate

/** Tests actual production business logic rather than a copy of its implementation. */
class IncomeSummaryCalculatorTest {
    private val today = LocalDate.of(2026, 10, 13)
    private fun income(
        amount: Long, source: String, date: String = "2026-10-10",
        status: String = "Received", cryptoType: String? = null,
        currency: String = "LKR", converted: Long? = null
    ) = Income(
        amountMinor = amount, currency = currency, amountLkrMinor = converted,
        source = source, receivedDate = date, paymentStatus = status,
        cryptoType = cryptoType
    )

    @Test fun `expected freelance does not count as realized monthly income`() {
        val result = IncomeSummaryCalculator.calculate(listOf(
            income(900_00, "Freelance", status = "Expected"),
            income(300_00, "Salary")
        ), today)
        assertEquals(300_00, result.realizedMinor)
        assertEquals(300_00, result.monthlyRealizedMinor)
        assertEquals(900_00, result.pendingFreelanceMinor)
        assertEquals(1, result.pendingFreelanceCount)
    }

    @Test fun `invoice marked received moves to actual income`() {
        val pending = income(900_00, "Freelance", status = "Expected")
        val before = IncomeSummaryCalculator.calculate(listOf(pending), today)
        val after = IncomeSummaryCalculator.calculate(listOf(pending.copy(paymentStatus = "Received")), today)
        assertEquals(0, before.realizedMinor)
        assertEquals(900_00, after.realizedMinor)
        assertEquals(0, after.pendingFreelanceCount)
    }

    @Test fun `crypto losses reduce net realized income`() {
        val result = IncomeSummaryCalculator.calculate(listOf(
            income(1000_00, "Salary"), income(250_00, "Crypto", cryptoType = "Gain"),
            income(400_00, "Crypto", cryptoType = "Loss")
        ), today)
        assertEquals(850_00, result.realizedMinor)
        assertEquals(-150_00, result.netCryptoMinor)
    }

    @Test fun `ytd excludes prior year and future dated transactions`() {
        val result = IncomeSummaryCalculator.calculate(listOf(
            income(100_00, "Crypto", "2025-09-01", cryptoType = "Gain"),
            income(200_00, "Crypto", "2026-01-02", cryptoType = "Gain"),
            income(50_00, "Crypto", "2026-10-03", cryptoType = "Loss"),
            income(400_00, "Crypto", "2026-12-01", cryptoType = "Gain")
        ), today)
        assertEquals(650_00, result.netCryptoMinor) // all time
        assertEquals(150_00, result.ytdNetCryptoMinor)
    }

    @Test fun `editing crypto loss updates summaries`() {
        val loss = income(50_00, "Crypto", cryptoType = "Loss")
        val old = IncomeSummaryCalculator.calculate(listOf(loss), today)
        val updated = IncomeSummaryCalculator.calculate(listOf(loss.copy(amountMinor = 90_00)), today)
        assertEquals(-50_00, old.netCryptoMinor)
        assertEquals(-90_00, updated.netCryptoMinor)
    }

    @Test fun `unconverted usd is never counted as lkr`() {
        val result = IncomeSummaryCalculator.calculate(listOf(
            income(150_00, "Salary", currency = "USD", converted = null)
        ), today)
        assertEquals(0, result.realizedMinor)
    }

    @Test fun `converted usd and lkr are totaled in lkr minor units`() {
        val result = IncomeSummaryCalculator.calculate(listOf(
            income(100_00, "Salary", currency = "USD", converted = 30500_00),
            income(900_00, "Salary")
        ), today)
        assertEquals(31400_00, result.realizedMinor)
    }

    @Test fun `monthly total excludes previous months`() {
        val result = IncomeSummaryCalculator.calculate(listOf(
            income(500_00, "Salary", "2026-09-15"),
            income(800_00, "Salary", "2026-10-09")
        ), today)
        assertEquals(1300_00, result.realizedMinor)
        assertEquals(800_00, result.monthlyRealizedMinor)
    }

    @Test fun `currency converter rounds usd to lkr once`() {
        assertEquals(3069471L,
            CurrencyConverter.convertUsdToLkrMinor(10050, BigDecimal("305.42")))
    }
}

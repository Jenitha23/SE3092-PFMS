package com.pfms.app.domain.usecase

import com.pfms.app.domain.model.Income
import com.pfms.app.domain.usecase.analytics.IncomeRealizedCalculator
import java.time.LocalDate
import java.time.YearMonth

/** Pure calculations shared by ViewModel and unit tests. Amounts are LKR minor units. */
data class IncomeSummary(
    val realizedMinor: Long,
    val monthlyRealizedMinor: Long,
    val pendingFreelanceMinor: Long,
    val pendingFreelanceCount: Int,
    val cryptoGainsMinor: Long,
    val cryptoLossesMinor: Long,
    val ytdCryptoGainsMinor: Long,
    val ytdCryptoLossesMinor: Long
) {
    val netCryptoMinor get() = cryptoGainsMinor - cryptoLossesMinor
    val ytdNetCryptoMinor get() = ytdCryptoGainsMinor - ytdCryptoLossesMinor
}

data class IncomeCurrencyTotals(val lkrMinor: Long = 0L, val usdInLkrMinor: Long = 0L, val usdMinor: Long = 0L)

object IncomeSummaryCalculator {
    fun currencyTotals(records: List<Income>) = IncomeCurrencyTotals(
        lkrMinor = records.filter { it.currency == "LKR" && !it.source.equals("Crypto", true) }.sumOf { it.amountMinor },
        usdInLkrMinor = records.filter { it.currency == "USD" }.sumOf { it.amountLkrMinor ?: 0L },
        usdMinor = records.filter { it.currency == "USD" }.sumOf { it.amountMinor }
    )

    fun lkrAmount(income: Income): Long = when (income.currency) {
        "LKR" -> income.amountMinor
        "USD" -> income.amountLkrMinor ?: 0L
        else -> 0L
    }

    fun calculate(records: List<Income>, today: LocalDate): IncomeSummary {
        val pending = records.filter { it.source.equals("Freelance", true) &&
            (it.paymentStatus.equals("Expected", true) || it.paymentStatus.equals("Pending", true)) }
        val realized = records.filter(IncomeRealizedCalculator::isRealized)
        fun signedAmount(income: Income) = IncomeRealizedCalculator.getRealizedSignedAmountMinor(income)
        val crypto = records.filter { it.source.equals("Crypto", true) }
        val ytd = crypto.filter {
            val date = runCatching { LocalDate.parse(it.receivedDate) }.getOrNull()
            date != null && date.year == today.year && !date.isAfter(today)
        }
        val month = YearMonth.from(today)
        return IncomeSummary(
            realizedMinor = realized.sumOf(::signedAmount),
            monthlyRealizedMinor = realized.filter {
                IncomeRealizedCalculator.matchesMonth(it, month)
            }.sumOf(::signedAmount),
            pendingFreelanceMinor = pending.sumOf(::lkrAmount),
            pendingFreelanceCount = pending.size,
            cryptoGainsMinor = crypto.filter { it.cryptoType.equals("Gain", true) }.sumOf(::lkrAmount),
            cryptoLossesMinor = crypto.filter { it.cryptoType.equals("Loss", true) }.sumOf(::lkrAmount),
            ytdCryptoGainsMinor = ytd.filter { it.cryptoType.equals("Gain", true) }.sumOf(::lkrAmount),
            ytdCryptoLossesMinor = ytd.filter { it.cryptoType.equals("Loss", true) }.sumOf(::lkrAmount)
        )
    }
}

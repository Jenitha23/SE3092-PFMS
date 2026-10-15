package com.pfms.app.domain.usecase

import com.pfms.app.domain.model.Income
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

object IncomeSummaryCalculator {
    fun lkrAmount(income: Income): Long = when (income.currency) {
        "LKR" -> income.amountMinor
        "USD" -> income.amountLkrMinor ?: 0L
        else -> 0L
    }

    fun calculate(records: List<Income>, today: LocalDate): IncomeSummary {
        val pending = records.filter { it.source == "Freelance" && it.paymentStatus == "Expected" }
        val realized = records.filterNot { it.source == "Freelance" && it.paymentStatus != "Received" }
        fun signedAmount(income: Income): Long =
            if (income.source == "Crypto" && income.cryptoType == "Loss") -lkrAmount(income)
            else lkrAmount(income)
        val crypto = records.filter { it.source == "Crypto" }
        val ytd = crypto.filter {
            val date = runCatching { LocalDate.parse(it.receivedDate) }.getOrNull()
            date != null && date.year == today.year && !date.isAfter(today)
        }
        val month = YearMonth.from(today)
        return IncomeSummary(
            realizedMinor = realized.sumOf(::signedAmount),
            monthlyRealizedMinor = realized.filter {
                runCatching { YearMonth.from(LocalDate.parse(it.receivedDate)) == month }.getOrDefault(false)
            }.sumOf(::signedAmount),
            pendingFreelanceMinor = pending.sumOf(::lkrAmount),
            pendingFreelanceCount = pending.size,
            cryptoGainsMinor = crypto.filter { it.cryptoType == "Gain" }.sumOf(::lkrAmount),
            cryptoLossesMinor = crypto.filter { it.cryptoType == "Loss" }.sumOf(::lkrAmount),
            ytdCryptoGainsMinor = ytd.filter { it.cryptoType == "Gain" }.sumOf(::lkrAmount),
            ytdCryptoLossesMinor = ytd.filter { it.cryptoType == "Loss" }.sumOf(::lkrAmount)
        )
    }
}

package com.pfms.app.domain.usecase

import com.pfms.app.domain.model.Income
import com.pfms.app.domain.usecase.analytics.IncomeRealizedCalculator
import java.time.YearMonth

enum class IncomeStreamSource { SALARY, FREELANCE, ADSENSE, CRYPTO, OTHER }
data class IncomeSourceShare(val source: IncomeStreamSource, val amountMinor: Long, val fraction: Float)
data class IncomeStreamOverview(
    val month: YearMonth,
    val records: List<Income>,
    val realizedMinor: Long,
    val changePercent: Double?,
    val lkrMinor: Long,
    val usdInLkrMinor: Long,
    val usdMinor: Long,
    val cryptoNetMinor: Long,
    val pendingMinor: Long,
    val pendingCount: Int,
    val cryptoLossesMinor: Long,
    val unconvertedCount: Int,
    val sources: List<IncomeSourceShare>
)

/** Uses the same realized-income and historical FX rules as the analytics and dashboard. */
object IncomeStreamCalculator {
    fun source(income: Income): IncomeStreamSource = when {
        income.source.equals("Salary", true) -> IncomeStreamSource.SALARY
        income.source.equals("Freelance", true) -> IncomeStreamSource.FREELANCE
        income.source.contains("AdSense", true) -> IncomeStreamSource.ADSENSE
        income.source.equals("Crypto", true) -> IncomeStreamSource.CRYPTO
        else -> IncomeStreamSource.OTHER
    }

    fun calculate(incomes: List<Income>, month: YearMonth): IncomeStreamOverview {
        val records = incomes.filter { IncomeRealizedCalculator.matchesMonth(it, month) }
        val previous = incomes.filter { IncomeRealizedCalculator.matchesMonth(it, month.minusMonths(1)) }
        val summary = IncomeSummaryCalculator.calculate(records, month.atEndOfMonth())
        val previousTotal = previous.sumOf(IncomeRealizedCalculator::getRealizedSignedAmountMinor)
        val realized = records.filter(IncomeRealizedCalculator::isRealized)
        fun unconverted(income: Income) = income.currency != "LKR" &&
            (income.currency != "USD" || income.amountLkrMinor == null)
        val comparable = records.none(::unconverted) && previous.none(::unconverted)
        val change = if (previousTotal > 0 && comparable)
            (summary.realizedMinor.toDouble() - previousTotal) / previousTotal * 100.0 else null
        val ordinary = realized.filter { source(it) != IncomeStreamSource.CRYPTO }
        // Shares describe positive realized inflow. Losses are displayed separately, never as a negative segment.
        val contributions = realized.groupBy(::source).mapValues { (_, rows) ->
            rows.sumOf { IncomeRealizedCalculator.getRealizedSignedAmountMinor(it).coerceAtLeast(0) }
        }.filterValues { it > 0 }
        val positiveTotal = contributions.values.sum().toDouble()
        val shares = IncomeStreamSource.entries.mapNotNull { category -> contributions[category]?.let { amount ->
            IncomeSourceShare(category, amount, (amount / positiveTotal).toFloat().coerceIn(0f, 1f))
        } }
        return IncomeStreamOverview(month, records, summary.realizedMinor, change,
            ordinary.filter { it.currency == "LKR" }.sumOf { it.amountMinor },
            ordinary.filter { it.currency == "USD" }.sumOf { it.amountLkrMinor ?: 0 },
            ordinary.filter { it.currency == "USD" }.sumOf { it.amountMinor },
            realized.filter { source(it) == IncomeStreamSource.CRYPTO }.sumOf(IncomeRealizedCalculator::getRealizedSignedAmountMinor),
            summary.pendingFreelanceMinor, summary.pendingFreelanceCount,
            summary.cryptoLossesMinor, records.count(::unconverted), shares)
    }
}

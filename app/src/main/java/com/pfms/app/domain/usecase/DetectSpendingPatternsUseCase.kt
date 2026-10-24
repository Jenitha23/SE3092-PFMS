package com.pfms.app.domain.usecase

import com.pfms.app.domain.model.Expense
import com.pfms.app.domain.model.SavingsSimulation
import com.pfms.app.domain.model.SpendingIncreaseLevel
import com.pfms.app.domain.model.SpendingPatternInsight
import com.pfms.app.domain.model.SpendingPatternResult
import com.pfms.app.domain.model.SpendingPatternStatus
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate
import java.time.YearMonth
import javax.inject.Inject

/**
 * Pure, offline-compatible category comparison.
 * Caller must supply months known to have sufficiently complete data: absence of
 * entries is NOT evidence of zero spending. No notifications or writes occur here.
 */
class DetectSpendingPatternsUseCase @Inject constructor() {
    fun analyze(
        expenses: List<Expense>,
        today: LocalDate,
        confirmedHistoryMonths: Set<YearMonth>,
        historyMonths: Int = 3,
        minimumHistoryMonths: Int = 2,
        thresholdPercent: Int = 30,
        minimumIncreaseMinor: Long = 100_000L
    ): SpendingPatternResult {
        require(historyMonths in 1..24)
        require(minimumHistoryMonths in 1..historyMonths)
        require(thresholdPercent in 1..1000)
        require(minimumIncreaseMinor >= 0L)
        val currentMonth = YearMonth.from(today)
        val historical = (1..historyMonths).map { currentMonth.minusMonths(it.toLong()) }
            .filter { it in confirmedHistoryMonths }
        if (historical.size < minimumHistoryMonths) {
            return SpendingPatternResult(SpendingPatternStatus.INSUFFICIENT_HISTORY, emptyList(), historical.size)
        }
        val monthToDate = today.dayOfMonth < currentMonth.lengthOfMonth()
        val acceptedMonths = historical.toSet() + currentMonth
        val grouped = expenses.asSequence()
            .filter { it.currency == "LKR" && it.amountMinor > 0 && it.categoryId.isNotBlank() }
            .mapNotNull { expense ->
                val date = runCatching { LocalDate.parse(expense.date) }.getOrNull()
                if (date == null || YearMonth.from(date) !in acceptedMonths) null
                else if (date.isAfter(today) && YearMonth.from(date) == currentMonth) null
                else (expense to date)
            }.groupBy { it.first.categoryId }

        val results = grouped.mapNotNull { (categoryId, dated) ->
            val current = dated.filter { YearMonth.from(it.second) == currentMonth }
                .sumOfSafe { it.first.amountMinor }
            if (current == 0L) return@mapNotNull null
            val historicTotals = historical.map { month ->
                dated.filter { YearMonth.from(it.second) == month &&
                        (!monthToDate || it.second.dayOfMonth <= today.dayOfMonth) }
                    .sumOfSafe { it.first.amountMinor }
            }
            val baseline = historicTotals.map { BigDecimal.valueOf(it) }
                .fold(BigDecimal.ZERO, BigDecimal::add)
                .divide(BigDecimal.valueOf(historical.size.toLong()), 0, RoundingMode.HALF_UP)
            if (baseline.signum() <= 0) return@mapNotNull null
            val difference = BigDecimal.valueOf(current).subtract(baseline)
            if (difference < BigDecimal.valueOf(minimumIncreaseMinor)) return@mapNotNull null
            val percent = difference.multiply(BigDecimal.valueOf(100L))
                .divide(baseline, 0, RoundingMode.HALF_UP)
            if (percent < BigDecimal.valueOf(thresholdPercent.toLong())) return@mapNotNull null
            val increase = percent.coerceAtMost(BigDecimal.valueOf(Int.MAX_VALUE.toLong())).toInt()
            SpendingPatternInsight(
                categoryId = categoryId,
                categoryName = dated.lastOrNull { it.first.categoryName.isNotBlank() }?.first?.categoryName
                    ?: categoryId,
                month = currentMonth,
                currentAmountMinor = current,
                baselineAmountMinor = baseline.longValueExact(),
                differenceMinor = difference.longValueExact(),
                increasePercent = increase,
                level = if (increase >= 50) SpendingIncreaseLevel.HIGH else SpendingIncreaseLevel.NOTICEABLE,
                comparisonMonths = historical.size,
                isMonthToDateComparison = monthToDate
            )
        }.sortedWith(compareByDescending<SpendingPatternInsight> { it.increasePercent }.thenBy { it.categoryId })
        return SpendingPatternResult(SpendingPatternStatus.READY, results, historical.size)
    }

    private fun <T> Iterable<T>.sumOfSafe(selector: (T) -> Long): Long =
        fold(0L) { total, item -> Math.addExact(total, selector(item)) }
}

package com.pfms.app.domain.model

import java.time.YearMonth

enum class SpendingPatternStatus { READY, INSUFFICIENT_HISTORY }
enum class SpendingIncreaseLevel { NOTICEABLE, HIGH }

data class SpendingPatternInsight(
    val categoryId: String,
    val categoryName: String,
    val month: YearMonth,
    val currentAmountMinor: Long,
    val baselineAmountMinor: Long,
    val differenceMinor: Long,
    val increasePercent: Int,
    val level: SpendingIncreaseLevel,
    val comparisonMonths: Int,
    val isMonthToDateComparison: Boolean
)

data class SpendingPatternResult(
    val status: SpendingPatternStatus,
    val insights: List<SpendingPatternInsight>,
    val eligibleHistoryMonths: Int
)

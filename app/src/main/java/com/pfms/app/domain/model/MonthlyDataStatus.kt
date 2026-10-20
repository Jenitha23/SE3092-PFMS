package com.pfms.app.domain.model

import java.time.YearMonth

enum class MonthlyDataReason { NO_ENTRIES, UNREVIEWED, MONTH_IN_PROGRESS, MISSING_CONVERSION, INVALID_RECORD, REVIEWED }

data class MonthlyDataStatus(
    val month: YearMonth,
    val reason: MonthlyDataReason,
    val recordCount: Int,
    val signature: String,
    val canReview: Boolean
) {
    val isPartial: Boolean get() = reason != MonthlyDataReason.REVIEWED
}

/** Repository snapshot. User identity accompanies the data so a review cannot cross accounts. */
data class MonthlyFinancialRecords(
    val userId: String,
    val month: YearMonth,
    val incomes: List<Income> = emptyList(),
    val expenses: List<Expense> = emptyList(),
    val reviewedSignature: String? = null,
    val isReady: Boolean = true
)

data class WelcomeBackActivity(val userId: String? = null, val lastRecordedAt: Long? = null, val dismissed: Boolean = true)

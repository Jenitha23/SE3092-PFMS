package com.pfms.app.domain.usecase

import com.pfms.app.domain.model.SpendingPatternResult
import com.pfms.app.domain.repository.ExpenseRepository
import java.time.LocalDate
import java.time.YearMonth
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** UI-independent adapter; caller supplies explicitly verified complete months. */
class ObserveSpendingPatternsUseCase @Inject constructor(
    private val expenses: ExpenseRepository,
    private val detector: DetectSpendingPatternsUseCase
) {
    fun observe(today: LocalDate, confirmedHistoryMonths: Set<YearMonth>): Flow<SpendingPatternResult> =
        expenses.observeExpenses().map { detector.analyze(it, today, confirmedHistoryMonths) }
}

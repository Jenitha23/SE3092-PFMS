package com.pfms.app.domain.repository

import com.pfms.app.domain.model.Expense
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

/**
 * Read side used by the dashboard, spending analytics and transaction history.
 * Income comes from the existing [IncomeRepository] (already Room-backed); this repository adds the
 * expense read cache and the savings-goal summary.
 */
interface DashboardRepository {

    /** All of the signed-in user's expenses, newest first, from the offline cache. */
    fun observeExpenses(): Flow<List<Expense>>

    /** Expenses dated between [start] and [end] inclusive, newest first, from the offline cache. */
    fun observeExpensesBetween(start: LocalDate, end: LocalDate): Flow<List<Expense>>
}

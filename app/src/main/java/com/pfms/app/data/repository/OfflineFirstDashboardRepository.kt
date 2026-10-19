package com.pfms.app.data.repository

import com.pfms.app.domain.model.Expense
import com.pfms.app.domain.repository.DashboardRepository
import com.pfms.app.domain.repository.ExpenseRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Read side for the dashboard, spending analytics and history.
 *
 * Expenses come from [ExpenseRepository] (OfflineFirstExpenseRepository): Room is the on-device
 * store, so inserts and edits (including offline ones) show up instantly and the dashboard can
 * never disagree with the expense list. Income comes from IncomeRepository. The savings-goal card
 * is driven by the savings feature (SavingsGoalViewModel), not by this repository.
 *
 * Dates are ISO yyyy-MM-dd strings, so a range check is a plain string comparison.
 */
@Singleton
class OfflineFirstDashboardRepository @Inject constructor(
    private val expenseRepository: ExpenseRepository
) : DashboardRepository {

    override fun observeExpenses(): Flow<List<Expense>> = expenseRepository.observeExpenses()

    override fun observeExpensesBetween(start: LocalDate, end: LocalDate): Flow<List<Expense>> {
        val from = start.toString()
        val to = end.toString()
        return expenseRepository.observeExpenses().map { list ->
            list.filter { it.date >= from && it.date <= to }
        }
    }
}

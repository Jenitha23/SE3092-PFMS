package com.pfms.app.data.repository

import com.pfms.app.data.remote.firestore.GoalSummaryRemoteDataSource
import com.pfms.app.domain.model.Expense
import com.pfms.app.domain.model.dashboard.ActiveGoalSnapshot
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
 * store, inserts and edits are visible instantly (including pending offline writes), and the
 * Firestore listener reconciles in the background. Reading through it, instead of keeping a second
 * copy of the expenses, means the dashboard can never disagree with the expense list.
 * Income comes from the existing IncomeRepository, and the goal card from a read-only Firestore listener.
 *
 * Dates are ISO yyyy-MM-dd strings, so a range check is a plain string comparison.
 */
@Singleton
class OfflineFirstDashboardRepository @Inject constructor(
    private val expenseRepository: ExpenseRepository,
    private val goalRemoteDataSource: GoalSummaryRemoteDataSource
) : DashboardRepository {

    override fun observeExpenses(): Flow<List<Expense>> = expenseRepository.observeExpenses()

    override fun observeExpensesBetween(start: LocalDate, end: LocalDate): Flow<List<Expense>> {
        val from = start.toString()
        val to = end.toString()
        return expenseRepository.observeExpenses().map { list ->
            list.filter { it.date >= from && it.date <= to }
        }
    }

    override fun observeActiveGoal(): Flow<ActiveGoalSnapshot?> =
        goalRemoteDataSource.observeActiveGoal()
}

package com.pfms.app.data.repository

import androidx.room.withTransaction
import com.pfms.app.data.local.PfmsDatabase
import com.pfms.app.data.local.mapper.toDomain
import com.pfms.app.domain.model.*
import com.pfms.app.domain.repository.AuthRepository
import com.pfms.app.domain.repository.FinancialReminderRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import javax.inject.Inject

/** Reads the same Room cache as existing financial repositories, without transient UI-flow empty emissions. */
class CachedFinancialReminderRepository @Inject constructor(private val db: PfmsDatabase, private val auth: AuthRepository) : FinancialReminderRepository {
    override suspend fun snapshot(userId: String, type: ReminderType): ReminderFinancialRecords = withContext(Dispatchers.IO) {
        require(userId.isNotBlank() && auth.currentUser.first()?.uid == userId)
        val result = db.withTransaction {
            val needsMoney = type in setOf(ReminderType.WEEKLY_SUMMARY, ReminderType.GOAL_BEHIND)
            val goal = if (type == ReminderType.GOAL_BEHIND) db.savingsGoalDao().getActiveGoal(userId)?.toDomain() else null
            ReminderFinancialRecords(userId,
                incomes = if (needsMoney || type == ReminderType.FREELANCE_OVERDUE) db.incomeDao().getActiveForReminders(userId).map { it.toDomain() } else emptyList(),
                expenses = if (needsMoney) db.expenseDao().getActiveForReminders(userId).map { it.toDomain() } else emptyList(),
                salary = if (type == ReminderType.SALARY_DAY) db.salaryTemplateDao().getSalaryTemplate(userId)?.toDomain() else null,
                recurring = if (type in setOf(ReminderType.DUE_RECURRING, ReminderType.SUBSCRIPTION_REVIEW)) db.recurringExpenseDao().getActiveTemplates(userId).map { it.toDomain() } else emptyList(),
                goal = goal, goalTransactions = goal?.let { db.goalTransactionDao().getTransactionsForGoal(userId, it.id).map { row -> row.toDomain() } } ?: emptyList())
        }
        require(auth.currentUser.first()?.uid == userId)
        result
    }
}

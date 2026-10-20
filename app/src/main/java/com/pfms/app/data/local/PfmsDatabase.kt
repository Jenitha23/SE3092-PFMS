package com.pfms.app.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import com.pfms.app.data.local.dao.ExpenseCategoryDao
import com.pfms.app.data.local.dao.ExpenseDao
import com.pfms.app.data.local.dao.GoalTransactionDao
import com.pfms.app.data.local.dao.IncomeDao
import com.pfms.app.data.local.dao.RecurringExpenseDao
import com.pfms.app.data.local.dao.SalaryTemplateDao
import com.pfms.app.data.local.dao.SavingsGoalDao
import com.pfms.app.data.local.entity.ExpenseCategoryEntity
import com.pfms.app.data.local.entity.ExpenseEntity
import com.pfms.app.data.local.entity.GoalTransactionEntity
import com.pfms.app.data.local.entity.IncomeEntity
import com.pfms.app.data.local.entity.RecurringExpenseEntity
import com.pfms.app.data.local.entity.SalaryTemplateEntity
import com.pfms.app.data.local.entity.SavingsGoalEntity
import com.pfms.app.data.local.migration.HISTORICAL_EXPENSE_MIGRATION_1_2
import com.pfms.app.data.local.migration.HISTORICAL_EXPENSE_MIGRATION_2_3

/**
 * Main Room database for PFMS. Offline cache and sync persistence for income, expenses, salary
 * templates, savings goals, goal transactions, expense categories and recurring expenses.
 * All migration SQL lives in the `migration` package; the properties below are aliases so callers
 * and tests can keep using `PfmsDatabase.MIGRATION_x_y`.
 */
@Database(
    entities = [
        IncomeEntity::class,
        ExpenseEntity::class,
        SalaryTemplateEntity::class,
        SavingsGoalEntity::class,
        GoalTransactionEntity::class,
        ExpenseCategoryEntity::class,
        RecurringExpenseEntity::class
    ],
    version = 5,
    exportSchema = false
)
abstract class PfmsDatabase : RoomDatabase() {
    abstract fun expenseDao(): ExpenseDao
    abstract fun incomeDao(): IncomeDao
    abstract fun salaryTemplateDao(): SalaryTemplateDao
    abstract fun savingsGoalDao(): SavingsGoalDao
    abstract fun goalTransactionDao(): GoalTransactionDao
    abstract fun expenseCategoryDao(): ExpenseCategoryDao
    abstract fun recurringExpenseDao(): RecurringExpenseDao

    companion object {
        val MIGRATION_1_4: Migration = com.pfms.app.data.local.migration.MIGRATION_1_4
        val MIGRATION_2_4: Migration = com.pfms.app.data.local.migration.MIGRATION_2_4
        val MIGRATION_3_4: Migration = com.pfms.app.data.local.migration.MIGRATION_3_4
        val MIGRATION_4_5: Migration = com.pfms.app.data.local.migration.MIGRATION_4_5

        // Retained for backward compatibility and tests.
        val MIGRATION_1_2: Migration = HISTORICAL_EXPENSE_MIGRATION_1_2
        val MIGRATION_2_3: Migration = HISTORICAL_EXPENSE_MIGRATION_2_3
    }
}

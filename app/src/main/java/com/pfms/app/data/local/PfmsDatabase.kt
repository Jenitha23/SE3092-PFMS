
package com.pfms.app.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration

import com.pfms.app.data.local.dao.CryptoTradeDao
import com.pfms.app.data.local.dao.ExpenseCategoryDao
import com.pfms.app.data.local.dao.ExpenseDao
import com.pfms.app.data.local.dao.FinancialActivityDao
import com.pfms.app.data.local.dao.GoalTransactionDao
import com.pfms.app.data.local.dao.IncomeDao
import com.pfms.app.data.local.dao.PendingChangesDao
import com.pfms.app.data.local.dao.RecurringExpenseDao
import com.pfms.app.data.local.dao.SalaryTemplateDao
import com.pfms.app.data.local.dao.SavingsGoalDao

import com.pfms.app.data.local.entity.CryptoTradeEntity
import com.pfms.app.data.local.entity.ExpenseCategoryEntity
import com.pfms.app.data.local.entity.ExpenseEntity
import com.pfms.app.data.local.entity.GoalTransactionEntity
import com.pfms.app.data.local.entity.IncomeEntity
import com.pfms.app.data.local.entity.RecurringExpenseEntity
import com.pfms.app.data.local.entity.SalaryTemplateEntity
import com.pfms.app.data.local.entity.SavingsGoalEntity

import com.pfms.app.data.local.migration.HISTORICAL_EXPENSE_MIGRATION_1_2
import com.pfms.app.data.local.migration.HISTORICAL_EXPENSE_MIGRATION_2_3
import com.pfms.app.data.local.migration.MIGRATION_6_7_CRYPTO

/**
 * Main Room database for PFMS.
 *
 * Provides local persistence for:
 * - Income
 * - Expenses
 * - Salary templates
 * - Savings goals and contributions
 * - Expense categories
 * - Recurring expenses
 * - Cryptocurrency Buy/Sell trades
 *
 * Version 7 adds cryptocurrency trade storage.
 */
@Database(
    entities = [
        IncomeEntity::class,
        ExpenseEntity::class,
        SalaryTemplateEntity::class,
        SavingsGoalEntity::class,
        GoalTransactionEntity::class,
        ExpenseCategoryEntity::class,
        RecurringExpenseEntity::class,
        CryptoTradeEntity::class
    ],
    version = 7,
    exportSchema = false
)
abstract class PfmsDatabase : RoomDatabase() {

    // Existing DAOs
    abstract fun expenseDao(): ExpenseDao

    abstract fun incomeDao(): IncomeDao

    abstract fun salaryTemplateDao(): SalaryTemplateDao

    abstract fun savingsGoalDao(): SavingsGoalDao

    abstract fun goalTransactionDao(): GoalTransactionDao

    abstract fun expenseCategoryDao(): ExpenseCategoryDao

    abstract fun recurringExpenseDao(): RecurringExpenseDao

    abstract fun pendingChangesDao(): PendingChangesDao

    abstract fun financialActivityDao(): FinancialActivityDao

    // New cryptocurrency DAO
    abstract fun cryptoTradeDao(): CryptoTradeDao

    companion object {

        // Existing migrations
        val MIGRATION_1_4: Migration =
            com.pfms.app.data.local.migration.MIGRATION_1_4

        val MIGRATION_2_4: Migration =
            com.pfms.app.data.local.migration.MIGRATION_2_4

        val MIGRATION_3_4: Migration =
            com.pfms.app.data.local.migration.MIGRATION_3_4

        val MIGRATION_4_5: Migration =
            com.pfms.app.data.local.migration.MIGRATION_4_5

        val MIGRATION_5_6: Migration =
            com.pfms.app.data.local.migration.MIGRATION_5_6

        // New crypto migration
        val MIGRATION_6_7: Migration =
            MIGRATION_6_7_CRYPTO

        // Backward compatibility
        val MIGRATION_1_2: Migration =
            HISTORICAL_EXPENSE_MIGRATION_1_2

        val MIGRATION_2_3: Migration =
            HISTORICAL_EXPENSE_MIGRATION_2_3
    }
}

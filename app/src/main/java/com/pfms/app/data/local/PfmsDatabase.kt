package com.pfms.app.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import com.pfms.app.data.local.dao.GoalTransactionDao
import com.pfms.app.data.local.dao.IncomeDao
import com.pfms.app.data.local.dao.SalaryTemplateDao
import com.pfms.app.data.local.dao.SavingsGoalDao
import com.pfms.app.data.local.entity.GoalTransactionEntity
import com.pfms.app.data.local.entity.IncomeEntity
import com.pfms.app.data.local.entity.SalaryTemplateEntity
import com.pfms.app.data.local.entity.SavingsGoalEntity

/**
 * Main Room database for the Personal Financial Management System (PFMS).
 * Provides offline caching and sync persistence for income, salary template, and savings goal data.
 */
@Database(
    entities = [
        IncomeEntity::class,
        SalaryTemplateEntity::class,
        SavingsGoalEntity::class,
        GoalTransactionEntity::class
    ],
    version = 2,
    exportSchema = false
)
abstract class PfmsDatabase : RoomDatabase() {
    abstract fun incomeDao(): IncomeDao
    abstract fun salaryTemplateDao(): SalaryTemplateDao
    abstract fun savingsGoalDao(): SavingsGoalDao
    abstract fun goalTransactionDao(): GoalTransactionDao
}

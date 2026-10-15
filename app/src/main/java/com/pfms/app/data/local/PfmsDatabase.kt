package com.pfms.app.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import com.pfms.app.data.local.dao.IncomeDao
import com.pfms.app.data.local.dao.SalaryTemplateDao
import com.pfms.app.data.local.entity.IncomeEntity
import com.pfms.app.data.local.entity.SalaryTemplateEntity

/**
 * Main Room database for the Personal Financial Management System (PFMS).
 * Provides offline caching and sync persistence for income and salary template data.
 */
@Database(
    entities = [
        IncomeEntity::class,
        SalaryTemplateEntity::class
    ],
    version = 1,
    exportSchema = false
)
abstract class PfmsDatabase : RoomDatabase() {
    abstract fun incomeDao(): IncomeDao
    abstract fun salaryTemplateDao(): SalaryTemplateDao
}

package com.pfms.app.di

import android.content.Context
import androidx.room.Room
import com.pfms.app.data.local.PfmsDatabase
import com.pfms.app.data.local.dao.GoalTransactionDao
import com.pfms.app.data.local.dao.IncomeDao
import com.pfms.app.data.local.dao.SalaryTemplateDao
import com.pfms.app.data.local.dao.SavingsGoalDao
import com.pfms.app.data.local.migration.MIGRATION_1_2
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun providePfmsDatabase(
        @ApplicationContext context: Context
    ): PfmsDatabase {
        return Room.databaseBuilder(
            context,
            PfmsDatabase::class.java,
            "pfms_database"
        )
            .addMigrations(MIGRATION_1_2)
            .build()
    }

    @Provides
    fun provideIncomeDao(database: PfmsDatabase): IncomeDao {
        return database.incomeDao()
    }

    @Provides
    fun provideSalaryTemplateDao(database: PfmsDatabase): SalaryTemplateDao {
        return database.salaryTemplateDao()
    }

    @Provides
    fun provideSavingsGoalDao(database: PfmsDatabase): SavingsGoalDao {
        return database.savingsGoalDao()
    }

    @Provides
    fun provideGoalTransactionDao(database: PfmsDatabase): GoalTransactionDao {
        return database.goalTransactionDao()
    }
}

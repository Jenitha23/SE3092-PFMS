package com.pfms.app.di

import android.content.Context
import androidx.room.Room
import com.pfms.app.data.local.PfmsDatabase
import com.pfms.app.data.local.dao.IncomeDao
import com.pfms.app.data.local.dao.SalaryTemplateDao
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
        ).build()
    }

    @Provides
    fun provideIncomeDao(database: PfmsDatabase): IncomeDao {
        return database.incomeDao()
    }

    @Provides
    fun provideSalaryTemplateDao(database: PfmsDatabase): SalaryTemplateDao {
        return database.salaryTemplateDao()
    }
}

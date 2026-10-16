package com.pfms.app.di

import com.pfms.app.data.remote.firestore.FirestoreIncomeRemoteDataSource
import com.pfms.app.data.remote.firestore.FirestoreSalaryTemplateRemoteDataSource
import com.pfms.app.data.remote.firestore.FirestoreSavingsGoalRemoteDataSource
import com.pfms.app.data.remote.firestore.IncomeRemoteDataSource
import com.pfms.app.data.remote.firestore.SalaryTemplateRemoteDataSource
import com.pfms.app.data.remote.firestore.SavingsGoalRemoteDataSource
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class RemoteDataSourceModule {

    @Binds
    @Singleton
    abstract fun bindIncomeRemoteDataSource(
        dataSource: FirestoreIncomeRemoteDataSource
    ): IncomeRemoteDataSource

    @Binds
    @Singleton
    abstract fun bindSalaryTemplateRemoteDataSource(
        dataSource: FirestoreSalaryTemplateRemoteDataSource
    ): SalaryTemplateRemoteDataSource

    @Binds
    @Singleton
    abstract fun bindSavingsGoalRemoteDataSource(
        dataSource: FirestoreSavingsGoalRemoteDataSource
    ): SavingsGoalRemoteDataSource
}

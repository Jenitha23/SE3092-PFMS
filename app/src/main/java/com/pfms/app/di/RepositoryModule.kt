package com.pfms.app.di

import com.pfms.app.data.repository.FirebaseAuthRepository
import com.pfms.app.data.repository.FirestoreLocalDataRepository
import com.pfms.app.data.repository.FirestoreUserRepository
import com.pfms.app.data.repository.OfflineFirstIncomeRepository
import com.pfms.app.data.repository.OfflineFirstSavingsGoalRepository
import com.pfms.app.data.repository.SalaryTemplateRepositoryImpl
import com.pfms.app.domain.repository.AuthRepository
import com.pfms.app.domain.repository.IncomeRepository
import com.pfms.app.domain.repository.LocalDataRepository
import com.pfms.app.domain.repository.SalaryTemplateRepository
import com.pfms.app.domain.repository.SavingsGoalRepository
import com.pfms.app.domain.repository.UserRepository
import com.pfms.app.data.repository.FirestoreAdvancedExpenseRepository
import com.pfms.app.domain.repository.AdvancedExpenseRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton
import com.pfms.app.data.repository.OfflineFirstExpenseRepository
import com.pfms.app.data.remote.firestore.FirestoreExpenseRemoteDataSource
import com.pfms.app.data.remote.firestore.ExpenseRemoteDataSource
import com.pfms.app.domain.repository.ExpenseRepository
import com.pfms.app.data.repository.OfflineFirstDashboardRepository
import com.pfms.app.domain.repository.DashboardRepository

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {
    @Binds @Singleton
    abstract fun bindAdvancedExpenseRepository(repository: FirestoreAdvancedExpenseRepository): AdvancedExpenseRepository

    @Binds @Singleton
    abstract fun bindExpenseRepository(repository: OfflineFirstExpenseRepository): ExpenseRepository

    @Binds @Singleton
    abstract fun bindExpenseRemote(repository: FirestoreExpenseRemoteDataSource): ExpenseRemoteDataSource


    @Binds
    @Singleton
    abstract fun bindAuthRepository(repository: FirebaseAuthRepository): AuthRepository

    @Binds
    @Singleton
    abstract fun bindUserRepository(repository: FirestoreUserRepository): UserRepository

    @Binds
    @Singleton
    abstract fun bindLocalDataRepository(repository: FirestoreLocalDataRepository): LocalDataRepository

    @Binds
    @Singleton
    abstract fun bindIncomeRepository(repository: OfflineFirstIncomeRepository): IncomeRepository

    @Binds
    @Singleton
    abstract fun bindSalaryTemplateRepository(repository: SalaryTemplateRepositoryImpl): SalaryTemplateRepository

    @Binds
    @Singleton
    abstract fun bindSavingsGoalRepository(repository: OfflineFirstSavingsGoalRepository): SavingsGoalRepository
    // Dashboard / analytics / history read side
    @Binds
    @Singleton
    abstract fun bindDashboardRepository(repository: OfflineFirstDashboardRepository): DashboardRepository
}


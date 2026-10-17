package com.pfms.app.di

import com.pfms.app.data.repository.OfflineFirstIncomeRepository
import com.pfms.app.data.repository.SalaryTemplateRepositoryImpl
import com.pfms.app.data.sync.IncomePendingSyncable
import com.pfms.app.data.sync.PendingSyncCoordinator
import com.pfms.app.data.sync.PendingSyncCoordinatorImpl
import com.pfms.app.data.sync.SalaryTemplatePendingSyncable
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class SyncModule {
    @Binds
    abstract fun bindExpenseSyncScheduler(scheduler: com.pfms.app.data.sync.WorkManagerExpenseSyncScheduler): com.pfms.app.data.sync.ExpenseSyncScheduler

    @Binds
    @Singleton
    abstract fun bindIncomePendingSyncable(repository: OfflineFirstIncomeRepository): IncomePendingSyncable

    @Binds
    @Singleton
    abstract fun bindSalaryTemplatePendingSyncable(repository: SalaryTemplateRepositoryImpl): SalaryTemplatePendingSyncable

    @Binds
    @Singleton
    abstract fun bindPendingSyncCoordinator(coordinator: PendingSyncCoordinatorImpl): PendingSyncCoordinator
}

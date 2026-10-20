package com.pfms.app.di

import com.pfms.app.data.reminder.*
import com.pfms.app.domain.repository.ReminderSettingsRepository
import com.pfms.app.domain.repository.FinancialReminderRepository
import com.pfms.app.data.repository.CachedFinancialReminderRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class ReminderModule {
    @Binds @Singleton abstract fun settings(implementation: LocalReminderSettingsRepository): ReminderSettingsRepository
    @Binds @Singleton abstract fun scheduler(implementation: ReminderWorkScheduler): ReminderScheduler
    @Binds @Singleton abstract fun notifier(implementation: AndroidReminderNotifier): ReminderNotifier
    @Binds abstract fun content(implementation: FinancialReminderContentSource): ReminderContentSource
    @Binds abstract fun records(implementation: CachedFinancialReminderRepository): FinancialReminderRepository
}

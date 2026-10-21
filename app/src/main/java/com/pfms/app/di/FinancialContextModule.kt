package com.pfms.app.di

import com.pfms.app.data.repository.RoomFinancialContextRepository
import com.pfms.app.domain.repository.FinancialContextRepository
import com.pfms.app.domain.usecase.dashboard.AppClock
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import java.time.Clock
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class FinancialContextModule {
    @Binds @Singleton abstract fun repository(implementation: RoomFinancialContextRepository): FinancialContextRepository
    companion object {
        @Provides @Singleton fun clock(): Clock = Clock.system(AppClock.zone)
    }
}

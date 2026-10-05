package com.pfms.app.di

import com.pfms.app.data.repository.FirebaseAuthRepository
import com.pfms.app.domain.repository.AuthRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {

    @Binds
    @Singleton
    abstract fun bindAuthRepository(
        repository: FirebaseAuthRepository
    ): AuthRepository
}
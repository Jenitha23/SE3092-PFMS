package com.pfms.app.di

import com.pfms.app.data.repository.FirebaseAuthRepository
import com.pfms.app.data.repository.FirestoreLocalDataRepository
import com.pfms.app.data.repository.FirestoreUserRepository
import com.pfms.app.domain.repository.AuthRepository
import com.pfms.app.domain.repository.LocalDataRepository
import com.pfms.app.domain.repository.UserRepository
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
    abstract fun bindAuthRepository(repository: FirebaseAuthRepository): AuthRepository

    @Binds
    @Singleton
    abstract fun bindUserRepository(repository: FirestoreUserRepository): UserRepository

    @Binds
    @Singleton
    abstract fun bindLocalDataRepository(repository: FirestoreLocalDataRepository): LocalDataRepository
}

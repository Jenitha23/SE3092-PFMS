package com.pfms.app.domain.repository

interface LocalDataRepository {

    suspend fun hasUnsynchronizedData(): Boolean

    suspend fun clearLocalData()
}
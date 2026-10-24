package com.pfms.app.domain.repository

interface RememberedLoginRepository {
    suspend fun getEmail(): String?
    suspend fun setEmail(email: String?)
}

package com.pfms.app.domain.repository

import com.pfms.app.domain.model.UserProfile
import kotlinx.coroutines.flow.Flow

interface UserRepository {
    /** Real-time profile of the signed-in user; null while signed out or not yet created. */
    fun observeProfile(): Flow<UserProfile?>
}

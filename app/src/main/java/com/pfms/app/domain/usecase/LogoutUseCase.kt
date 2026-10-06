package com.pfms.app.domain.usecase

import com.pfms.app.domain.model.LogoutResult
import com.pfms.app.domain.repository.AuthRepository
import com.pfms.app.domain.repository.LocalDataRepository
import javax.inject.Inject

/** FR-05: warn about unsynchronised entries, then end the session and clear local data. */
class LogoutUseCase @Inject constructor(
    private val authRepository: AuthRepository,
    private val localDataRepository: LocalDataRepository
) {
    /** @param force true once the user has confirmed they want to log out despite unsynced data. */
    suspend operator fun invoke(force: Boolean = false): LogoutResult {
        if (!force && localDataRepository.hasUnsynchronizedData()) {
            return LogoutResult.UnsynchronizedDataWarning
        }
        return authRepository.logout().fold(
            onSuccess = {
                localDataRepository.clearLocalData()
                LogoutResult.Success
            },
            onFailure = { LogoutResult.Failure(it.message ?: "Could not log out. Please try again.") }
        )
    }
}

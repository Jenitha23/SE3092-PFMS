package com.pfms.app.domain.usecase

import com.pfms.app.domain.model.ReauthCredential
import com.pfms.app.domain.repository.AuthRepository
import com.pfms.app.domain.repository.LocalDataRepository
import javax.inject.Inject

/** FR-06: re-authenticate, delete cloud data + Firebase account, then wipe the local cache. */
class DeleteAccountUseCase @Inject constructor(
    private val authRepository: AuthRepository,
    private val localDataRepository: LocalDataRepository
) {
    suspend operator fun invoke(credential: ReauthCredential): Result<Unit> {
        authRepository.reauthenticate(credential).onFailure { return Result.failure(it) }
        return authRepository.deleteAccount().onSuccess { localDataRepository.clearLocalData() }
    }
}

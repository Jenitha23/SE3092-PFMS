package com.pfms.app.domain.usecase

import com.pfms.app.domain.model.AuthException
import com.pfms.app.domain.model.AuthErrorType
import com.pfms.app.domain.model.ReauthCredential
import com.pfms.app.domain.repository.AuthRepository
import com.pfms.app.domain.repository.LocalDataRepository
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

/**
 * Unit tests for [DeleteAccountUseCase] (FR-06).
 *
 * Verifies: re-authenticate first → delete account → clear local cache.
 * Uses mock repositories so the tests run locally without Firebase.
 */
class DeleteAccountUseCaseTest {

    private lateinit var authRepository: AuthRepository
    private lateinit var localDataRepository: LocalDataRepository
    private lateinit var deleteAccountUseCase: DeleteAccountUseCase

    private val passwordCredential = ReauthCredential.Password("password123")
    private val googleCredential = ReauthCredential.Google("fake-id-token")

    @Before
    fun setUp() {
        authRepository = mock()
        localDataRepository = mock()
        deleteAccountUseCase = DeleteAccountUseCase(authRepository, localDataRepository)
    }

    // ------------------------------------------------------------------ happy path

    @Test
    fun `successful deletion clears local data`() = runTest {
        whenever(authRepository.reauthenticate(any())).thenReturn(Result.success(Unit))
        whenever(authRepository.deleteAccount()).thenReturn(Result.success(Unit))

        val result = deleteAccountUseCase(passwordCredential)

        assertTrue(result.isSuccess)
        verify(localDataRepository).clearLocalData()
    }

    @Test
    fun `successful deletion with google credential clears local data`() = runTest {
        whenever(authRepository.reauthenticate(any())).thenReturn(Result.success(Unit))
        whenever(authRepository.deleteAccount()).thenReturn(Result.success(Unit))

        val result = deleteAccountUseCase(googleCredential)

        assertTrue(result.isSuccess)
        verify(localDataRepository).clearLocalData()
    }

    // ------------------------------------------------------------------ reauthentication failure

    @Test
    fun `reauthentication failure stops deletion`() = runTest {
        val error = AuthException("Incorrect password.", AuthErrorType.INVALID_CREDENTIALS)
        whenever(authRepository.reauthenticate(any())).thenReturn(Result.failure(error))

        val result = deleteAccountUseCase(passwordCredential)

        assertTrue(result.isFailure)
        assertEquals("Incorrect password.", result.exceptionOrNull()?.message)
    }

    @Test
    fun `reauthentication failure does not call deleteAccount`() = runTest {
        whenever(authRepository.reauthenticate(any()))
            .thenReturn(Result.failure(RuntimeException("auth fail")))

        deleteAccountUseCase(passwordCredential)

        verify(authRepository, never()).deleteAccount()
    }

    @Test
    fun `reauthentication failure does not clear local data`() = runTest {
        whenever(authRepository.reauthenticate(any()))
            .thenReturn(Result.failure(RuntimeException("auth fail")))

        deleteAccountUseCase(passwordCredential)

        verify(localDataRepository, never()).clearLocalData()
    }

    // ------------------------------------------------------------------ deleteAccount failure

    @Test
    fun `deleteAccount failure propagates error`() = runTest {
        whenever(authRepository.reauthenticate(any())).thenReturn(Result.success(Unit))
        whenever(authRepository.deleteAccount())
            .thenReturn(Result.failure(AuthException("Server error", AuthErrorType.UNKNOWN)))

        val result = deleteAccountUseCase(passwordCredential)

        assertTrue(result.isFailure)
        assertEquals("Server error", result.exceptionOrNull()?.message)
    }

    @Test
    fun `deleteAccount failure does not clear local data`() = runTest {
        whenever(authRepository.reauthenticate(any())).thenReturn(Result.success(Unit))
        whenever(authRepository.deleteAccount())
            .thenReturn(Result.failure(RuntimeException("delete fail")))

        deleteAccountUseCase(passwordCredential)

        verify(localDataRepository, never()).clearLocalData()
    }
}

package com.pfms.app.domain.usecase

import com.pfms.app.domain.model.LogoutResult
import com.pfms.app.domain.repository.AuthRepository
import com.pfms.app.domain.repository.LocalDataRepository
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

/**
 * Unit tests for [LogoutUseCase] (FR-05).
 *
 * Uses test doubles (mocks) for AuthRepository and LocalDataRepository so
 * we can verify the business rules without touching Firebase or the device.
 */
class LogoutUseCaseTest {

    private lateinit var authRepository: AuthRepository
    private lateinit var localDataRepository: LocalDataRepository
    private lateinit var logoutUseCase: LogoutUseCase

    @Before
    fun setUp() {
        authRepository = mock()
        localDataRepository = mock()
        logoutUseCase = LogoutUseCase(authRepository, localDataRepository)
    }

    // ------------------------------------------------------------------ unsynchronized data warning

    @Test
    fun `non-forced logout warns when unsynced data exists`() = runTest {
        whenever(localDataRepository.hasUnsynchronizedData()).thenReturn(true)

        val result = logoutUseCase(force = false)

        assertTrue(result is LogoutResult.UnsynchronizedDataWarning)
    }

    @Test
    fun `non-forced logout does not call auth logout when unsynced data exists`() = runTest {
        whenever(localDataRepository.hasUnsynchronizedData()).thenReturn(true)

        logoutUseCase(force = false)

        verify(authRepository, never()).logout()
    }

    // ------------------------------------------------------------------ forced logout

    @Test
    fun `forced logout succeeds and clears local data`() = runTest {
        whenever(localDataRepository.hasUnsynchronizedData()).thenReturn(true)
        whenever(authRepository.logout()).thenReturn(Result.success(Unit))

        val result = logoutUseCase(force = true)

        assertTrue(result is LogoutResult.Success)
        verify(localDataRepository).clearLocalData()
    }

    // ------------------------------------------------------------------ no unsynced data

    @Test
    fun `non-forced logout succeeds when no unsynced data`() = runTest {
        whenever(localDataRepository.hasUnsynchronizedData()).thenReturn(false)
        whenever(authRepository.logout()).thenReturn(Result.success(Unit))

        val result = logoutUseCase(force = false)

        assertTrue(result is LogoutResult.Success)
        verify(localDataRepository).clearLocalData()
    }

    // ------------------------------------------------------------------ logout failure

    @Test
    fun `logout failure returns Failure with message`() = runTest {
        whenever(localDataRepository.hasUnsynchronizedData()).thenReturn(false)
        whenever(authRepository.logout()).thenReturn(Result.failure(RuntimeException("Network error")))

        val result = logoutUseCase(force = false)

        assertTrue(result is LogoutResult.Failure)
        assertEquals("Network error", (result as LogoutResult.Failure).message)
    }

    @Test
    fun `logout failure with null message uses default`() = runTest {
        whenever(localDataRepository.hasUnsynchronizedData()).thenReturn(false)
        whenever(authRepository.logout()).thenReturn(Result.failure(RuntimeException()))

        val result = logoutUseCase(force = false)

        assertTrue(result is LogoutResult.Failure)
        assertEquals("Could not log out. Please try again.", (result as LogoutResult.Failure).message)
    }

    @Test
    fun `local data is not cleared when logout fails`() = runTest {
        whenever(localDataRepository.hasUnsynchronizedData()).thenReturn(false)
        whenever(authRepository.logout()).thenReturn(Result.failure(RuntimeException("fail")))

        logoutUseCase(force = false)

        verify(localDataRepository, never()).clearLocalData()
    }
}

package com.pfms.app.data.sync

import com.pfms.app.domain.model.AuthUser
import com.pfms.app.domain.repository.AuthRepository
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class PendingSyncCoordinatorTest {

    private lateinit var authRepository: AuthRepository
    private lateinit var incomeSyncable: IncomePendingSyncable
    private lateinit var templateSyncable: SalaryTemplatePendingSyncable
    private lateinit var coordinator: PendingSyncCoordinatorImpl

    private val testUser = AuthUser(
        uid = "user-123",
        displayName = "Test User",
        email = "test@example.com",
        isEmailVerified = true
    )

    @Before
    fun setup() {
        authRepository = mock()
        incomeSyncable = mock()
        templateSyncable = mock()

        coordinator = PendingSyncCoordinatorImpl(
            authRepository = authRepository,
            incomeSyncable = incomeSyncable,
            templateSyncable = templateSyncable
        )
    }

    @Test
    fun `no authenticated user exits safely with success and does not trigger sync`() = runTest {
        whenever(authRepository.currentUser).thenReturn(flowOf(null))

        val result = coordinator.syncAllPending()

        assertTrue(result.isSuccess)
        verify(incomeSyncable, never()).syncPendingIncome()
        verify(templateSyncable, never()).syncPendingTemplate()
    }

    @Test
    fun `blank user uid exits safely with success and does not trigger sync`() = runTest {
        whenever(authRepository.currentUser).thenReturn(
            flowOf(AuthUser(uid = "", displayName = null, email = null, isEmailVerified = false))
        )

        val result = coordinator.syncAllPending()

        assertTrue(result.isSuccess)
        verify(incomeSyncable, never()).syncPendingIncome()
        verify(templateSyncable, never()).syncPendingTemplate()
    }

    @Test
    fun `authenticated user triggers both income and salary-template sync`() = runTest {
        whenever(authRepository.currentUser).thenReturn(flowOf(testUser))
        whenever(incomeSyncable.syncPendingIncome()).thenReturn(Result.success(2))
        whenever(templateSyncable.syncPendingTemplate()).thenReturn(Result.success(Unit))

        val result = coordinator.syncAllPending()

        assertTrue(result.isSuccess)
        verify(incomeSyncable).syncPendingIncome()
        verify(templateSyncable).syncPendingTemplate()
    }

    @Test
    fun `failure in income sync returns failure for retry`() = runTest {
        whenever(authRepository.currentUser).thenReturn(flowOf(testUser))
        whenever(incomeSyncable.syncPendingIncome()).thenReturn(Result.failure(RuntimeException("Network error")))
        whenever(templateSyncable.syncPendingTemplate()).thenReturn(Result.success(Unit))

        val result = coordinator.syncAllPending()

        assertFalse(result.isSuccess)
        verify(incomeSyncable).syncPendingIncome()
        verify(templateSyncable).syncPendingTemplate()
    }

    @Test
    fun `failure in salary-template sync returns failure for retry`() = runTest {
        whenever(authRepository.currentUser).thenReturn(flowOf(testUser))
        whenever(incomeSyncable.syncPendingIncome()).thenReturn(Result.success(1))
        whenever(templateSyncable.syncPendingTemplate()).thenReturn(Result.failure(RuntimeException("Remote timeout")))

        val result = coordinator.syncAllPending()

        assertFalse(result.isSuccess)
        verify(incomeSyncable).syncPendingIncome()
        verify(templateSyncable).syncPendingTemplate()
    }

    @Test
    fun `session expiration mid-sync exits safely with success`() = runTest {
        whenever(authRepository.currentUser).thenReturn(flowOf(testUser))
        whenever(incomeSyncable.syncPendingIncome()).thenThrow(
            IllegalStateException("Authentication required: no active user session")
        )

        val result = coordinator.syncAllPending()

        assertTrue(result.isSuccess)
    }
}

package com.pfms.app.data.sync

import com.pfms.app.domain.model.AuthUser
import com.pfms.app.domain.repository.AuthRepository
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
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
    private lateinit var savingsGoalSyncable: SavingsGoalPendingSyncable
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
        savingsGoalSyncable = mock()

        coordinator = PendingSyncCoordinatorImpl(
            authRepository = authRepository,
            incomeSyncable = incomeSyncable,
            templateSyncable = templateSyncable,
            savingsGoalSyncable = savingsGoalSyncable
        )
    }

    @Test
    fun `no authenticated user exits safely with success and does not trigger sync`() = runTest {
        whenever(authRepository.currentUser).thenReturn(flowOf(null))

        val result = coordinator.syncAllPending()

        assertTrue(result.isSuccess)
        verify(incomeSyncable, never()).syncPendingIncome()
        verify(templateSyncable, never()).syncPendingTemplate()
        verify(savingsGoalSyncable, never()).syncPendingGoals()
        verify(savingsGoalSyncable, never()).syncPendingTransactions()
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
        verify(savingsGoalSyncable, never()).syncPendingGoals()
        verify(savingsGoalSyncable, never()).syncPendingTransactions()
    }

    @Test
    fun `authenticated user triggers income, template, savings-goal, and savings-tx sync`() = runTest {
        whenever(authRepository.currentUser).thenReturn(flowOf(testUser))
        whenever(incomeSyncable.syncPendingIncome()).thenReturn(Result.success(2))
        whenever(templateSyncable.syncPendingTemplate()).thenReturn(Result.success(Unit))
        whenever(savingsGoalSyncable.syncPendingGoals()).thenReturn(Result.success(1))
        whenever(savingsGoalSyncable.syncPendingTransactions()).thenReturn(Result.success(3))

        val result = coordinator.syncAllPending()

        assertTrue(result.isSuccess)
        verify(incomeSyncable).syncPendingIncome()
        verify(templateSyncable).syncPendingTemplate()
        verify(savingsGoalSyncable).syncPendingGoals()
        verify(savingsGoalSyncable).syncPendingTransactions()
    }

    @Test
    fun `failure in income sync returns failure for retry`() = runTest {
        whenever(authRepository.currentUser).thenReturn(flowOf(testUser))
        whenever(incomeSyncable.syncPendingIncome()).thenReturn(Result.failure(RuntimeException("Network error")))
        whenever(templateSyncable.syncPendingTemplate()).thenReturn(Result.success(Unit))
        whenever(savingsGoalSyncable.syncPendingGoals()).thenReturn(Result.success(1))
        whenever(savingsGoalSyncable.syncPendingTransactions()).thenReturn(Result.success(1))

        val result = coordinator.syncAllPending()

        assertFalse(result.isSuccess)
        verify(incomeSyncable).syncPendingIncome()
        verify(templateSyncable).syncPendingTemplate()
        verify(savingsGoalSyncable).syncPendingGoals()
        verify(savingsGoalSyncable).syncPendingTransactions()
    }

    @Test
    fun `failure in salary-template sync returns failure for retry`() = runTest {
        whenever(authRepository.currentUser).thenReturn(flowOf(testUser))
        whenever(incomeSyncable.syncPendingIncome()).thenReturn(Result.success(1))
        whenever(templateSyncable.syncPendingTemplate()).thenReturn(Result.failure(RuntimeException("Remote timeout")))
        whenever(savingsGoalSyncable.syncPendingGoals()).thenReturn(Result.success(1))
        whenever(savingsGoalSyncable.syncPendingTransactions()).thenReturn(Result.success(1))

        val result = coordinator.syncAllPending()

        assertFalse(result.isSuccess)
        verify(incomeSyncable).syncPendingIncome()
        verify(templateSyncable).syncPendingTemplate()
        verify(savingsGoalSyncable).syncPendingGoals()
        verify(savingsGoalSyncable).syncPendingTransactions()
    }

    @Test
    fun `failure in savings-goal sync returns failure for retry`() = runTest {
        whenever(authRepository.currentUser).thenReturn(flowOf(testUser))
        whenever(incomeSyncable.syncPendingIncome()).thenReturn(Result.success(1))
        whenever(templateSyncable.syncPendingTemplate()).thenReturn(Result.success(Unit))
        whenever(savingsGoalSyncable.syncPendingGoals()).thenReturn(Result.failure(RuntimeException("Firestore error")))
        whenever(savingsGoalSyncable.syncPendingTransactions()).thenReturn(Result.success(1))

        val result = coordinator.syncAllPending()

        assertFalse(result.isSuccess)
        verify(incomeSyncable).syncPendingIncome()
        verify(templateSyncable).syncPendingTemplate()
        verify(savingsGoalSyncable).syncPendingGoals()
        verify(savingsGoalSyncable).syncPendingTransactions()
    }

    @Test
    fun `failure in savings-transaction sync returns failure for retry`() = runTest {
        whenever(authRepository.currentUser).thenReturn(flowOf(testUser))
        whenever(incomeSyncable.syncPendingIncome()).thenReturn(Result.success(1))
        whenever(templateSyncable.syncPendingTemplate()).thenReturn(Result.success(Unit))
        whenever(savingsGoalSyncable.syncPendingGoals()).thenReturn(Result.success(1))
        whenever(savingsGoalSyncable.syncPendingTransactions()).thenReturn(Result.failure(RuntimeException("Firestore tx error")))

        val result = coordinator.syncAllPending()

        assertFalse(result.isSuccess)
        verify(incomeSyncable).syncPendingIncome()
        verify(templateSyncable).syncPendingTemplate()
        verify(savingsGoalSyncable).syncPendingGoals()
        verify(savingsGoalSyncable).syncPendingTransactions()
    }

    @Test
    fun `session expiration mid-sync on income exits safely with success`() = runTest {
        whenever(authRepository.currentUser).thenReturn(flowOf(testUser))
        whenever(incomeSyncable.syncPendingIncome()).thenThrow(
            IllegalStateException("Authentication required: no active user session")
        )

        val result = coordinator.syncAllPending()

        assertTrue(result.isSuccess)
    }

    @Test
    fun `session expiration mid-sync on savings goal exits safely with success`() = runTest {
        whenever(authRepository.currentUser).thenReturn(flowOf(testUser))
        whenever(incomeSyncable.syncPendingIncome()).thenReturn(Result.success(1))
        whenever(templateSyncable.syncPendingTemplate()).thenReturn(Result.success(Unit))
        whenever(savingsGoalSyncable.syncPendingGoals()).thenReturn(
            Result.failure(IllegalStateException("Authentication required: no active user session"))
        )

        val result = coordinator.syncAllPending()

        assertTrue(result.isSuccess)
    }

    @Test
    fun `arbitrary IllegalStateException in income sync returns failure for retry`() = runTest {
        whenever(authRepository.currentUser).thenReturn(flowOf(testUser))
        whenever(incomeSyncable.syncPendingIncome()).thenReturn(
            Result.failure(IllegalStateException("Active database constraint violated"))
        )
        whenever(templateSyncable.syncPendingTemplate()).thenReturn(Result.success(Unit))
        whenever(savingsGoalSyncable.syncPendingGoals()).thenReturn(Result.success(1))
        whenever(savingsGoalSyncable.syncPendingTransactions()).thenReturn(Result.success(1))

        val result = coordinator.syncAllPending()

        assertFalse(result.isSuccess)
        assertTrue(result.exceptionOrNull() is IllegalStateException)
        assertEquals("Active database constraint violated", result.exceptionOrNull()?.message)
    }

    @Test
    fun `arbitrary IllegalStateException in savings goal sync returns failure for retry`() = runTest {
        whenever(authRepository.currentUser).thenReturn(flowOf(testUser))
        whenever(incomeSyncable.syncPendingIncome()).thenReturn(Result.success(1))
        whenever(templateSyncable.syncPendingTemplate()).thenReturn(Result.success(Unit))
        whenever(savingsGoalSyncable.syncPendingGoals()).thenReturn(
            Result.failure(IllegalStateException("An active savings goal already exists"))
        )
        whenever(savingsGoalSyncable.syncPendingTransactions()).thenReturn(Result.success(1))

        val result = coordinator.syncAllPending()

        assertFalse(result.isSuccess)
        assertTrue(result.exceptionOrNull() is IllegalStateException)
        assertEquals("An active savings goal already exists", result.exceptionOrNull()?.message)
    }

    @Test
    fun `mid-sync auth session loss when currentUser flow emits null exits safely with success`() = runTest {
        val userFlow = kotlinx.coroutines.flow.MutableStateFlow<AuthUser?>(testUser)
        whenever(authRepository.currentUser).thenReturn(userFlow)
        whenever(incomeSyncable.syncPendingIncome()).thenAnswer {
            userFlow.value = null
            throw IllegalStateException("Session terminated")
        }

        val result = coordinator.syncAllPending()

        assertTrue(result.isSuccess)
    }
}

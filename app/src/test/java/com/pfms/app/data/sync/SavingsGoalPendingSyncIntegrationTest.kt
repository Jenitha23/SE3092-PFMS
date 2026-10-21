package com.pfms.app.data.sync

import com.pfms.app.data.local.dao.GoalTransactionDao
import com.pfms.app.data.local.dao.SavingsGoalDao
import com.pfms.app.data.local.entity.GoalTransactionEntity
import com.pfms.app.data.local.entity.SavingsGoalEntity
import com.pfms.app.data.local.entity.SyncStatus
import com.pfms.app.data.remote.firestore.SavingsGoalRemoteDataSource
import com.pfms.app.data.repository.OfflineFirstSavingsGoalRepository
import com.pfms.app.domain.model.AuthUser
import com.pfms.app.domain.repository.AuthRepository
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class SavingsGoalPendingSyncIntegrationTest {

    private lateinit var savingsGoalDao: SavingsGoalDao
    private lateinit var goalTransactionDao: GoalTransactionDao
    private lateinit var remoteDataSource: SavingsGoalRemoteDataSource
    private lateinit var authRepository: AuthRepository
    private lateinit var repository: OfflineFirstSavingsGoalRepository
    private lateinit var syncable: SavingsGoalPendingSyncable

    private val testUser = AuthUser(
        uid = "user-123",
        displayName = "Test User",
        email = "test@example.com",
        isEmailVerified = true
    )

    @Before
    fun setup() {
        savingsGoalDao = mock()
        goalTransactionDao = mock()
        remoteDataSource = mock()
        authRepository = mock()

        whenever(authRepository.currentUser).thenReturn(flowOf(testUser))

        repository = OfflineFirstSavingsGoalRepository(
            savingsGoalDao = savingsGoalDao,
            goalTransactionDao = goalTransactionDao,
            savingsGoalRemoteDataSource = remoteDataSource,
            authRepository = authRepository
        )
        // Verify contract implementation
        syncable = repository
    }

    @Test
    fun `syncable interface correctly casts repository`() {
        assertTrue(repository is SavingsGoalPendingSyncable)
    }

    @Test
    fun `syncPendingGoals with PENDING_UPDATE syncs to remote and marks SYNCED`() = runTest {
        val pendingGoal = SavingsGoalEntity(
            id = "g-update",
            userId = "user-123",
            name = "Updated Goal",
            syncStatus = SyncStatus.PENDING_UPDATE
        )
        whenever(savingsGoalDao.getPendingGoals("user-123")).thenReturn(listOf(pendingGoal))
        whenever(remoteDataSource.upsertGoal(eq("user-123"), any())).thenReturn(Result.success(Unit))

        val result = syncable.syncPendingGoals()

        assertTrue(result.isSuccess)
        assertEquals(1, result.getOrNull())
        verify(savingsGoalDao).completeSync(pendingGoal)
    }

    @Test
    fun `syncPendingTransactions with PENDING_INSERT syncs to remote and marks SYNCED`() = runTest {
        val pendingTx = GoalTransactionEntity(
            id = "tx-insert",
            goalId = "g-1",
            userId = "user-123",
            syncStatus = SyncStatus.PENDING_INSERT
        )
        whenever(goalTransactionDao.getPendingTransactions("user-123")).thenReturn(listOf(pendingTx))
        whenever(remoteDataSource.upsertTransaction(eq("user-123"), any())).thenReturn(Result.success(Unit))

        val result = syncable.syncPendingTransactions()

        assertTrue(result.isSuccess)
        assertEquals(1, result.getOrNull())
        verify(goalTransactionDao).completeSync(pendingTx)
    }

    @Test
    fun `syncPendingTransactions with PENDING_DELETE hard deletes locally on remote success`() = runTest {
        val pendingDeleteTx = GoalTransactionEntity(
            id = "tx-del",
            goalId = "g-1",
            userId = "user-123",
            isDeleted = true,
            syncStatus = SyncStatus.PENDING_DELETE
        )
        whenever(goalTransactionDao.getPendingTransactions("user-123")).thenReturn(listOf(pendingDeleteTx))
        whenever(remoteDataSource.deleteTransaction("user-123", "tx-del")).thenReturn(Result.success(Unit))

        val result = syncable.syncPendingTransactions()

        assertTrue(result.isSuccess)
        assertEquals(1, result.getOrNull())
        verify(goalTransactionDao).completeSync(pendingDeleteTx)
    }

    @Test
    fun `syncPendingTransactions failure leaves record pending`() = runTest {
        val pendingTx = GoalTransactionEntity(
            id = "tx-fail",
            goalId = "g-1",
            userId = "user-123",
            syncStatus = SyncStatus.PENDING_INSERT
        )
        whenever(goalTransactionDao.getPendingTransactions("user-123")).thenReturn(listOf(pendingTx))
        whenever(remoteDataSource.upsertTransaction(any(), any())).thenReturn(Result.failure(RuntimeException("Firestore offline")))

        val result = syncable.syncPendingTransactions()

        assertFalse(result.isSuccess)
        verify(goalTransactionDao, never()).completeSync(any())
    }

    @Test
    fun `unauthenticated syncPendingGoals fails safely with IllegalStateException`() = runTest {
        whenever(authRepository.currentUser).thenReturn(flowOf(null))

        val result = syncable.syncPendingGoals()

        assertFalse(result.isSuccess)
        assertTrue(result.exceptionOrNull() is IllegalStateException)
        org.mockito.kotlin.verifyNoInteractions(savingsGoalDao)
    }

    @Test
    fun `unauthenticated syncPendingTransactions fails safely with IllegalStateException`() = runTest {
        whenever(authRepository.currentUser).thenReturn(flowOf(null))

        val result = syncable.syncPendingTransactions()

        assertFalse(result.isSuccess)
        assertTrue(result.exceptionOrNull() is IllegalStateException)
        org.mockito.kotlin.verifyNoInteractions(goalTransactionDao)
    }

    @Test
    fun `user isolation strictly queries only current user records`() = runTest {
        whenever(savingsGoalDao.getPendingGoals("user-123")).thenReturn(emptyList())
        whenever(goalTransactionDao.getPendingTransactions("user-123")).thenReturn(emptyList())

        val goalResult = syncable.syncPendingGoals()
        val txResult = syncable.syncPendingTransactions()

        assertTrue(goalResult.isSuccess)
        assertTrue(txResult.isSuccess)
        verify(savingsGoalDao).getPendingGoals("user-123")
        verify(goalTransactionDao).getPendingTransactions("user-123")
        verify(savingsGoalDao, never()).getPendingGoals("other-user")
        verify(goalTransactionDao, never()).getPendingTransactions("other-user")
    }
}

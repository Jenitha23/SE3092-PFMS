package com.pfms.app.data.repository

import com.pfms.app.data.local.dao.FakeSavingsGoalDao
import com.pfms.app.data.local.dao.GoalTransactionDao
import com.pfms.app.data.local.dao.SavingsGoalDao
import com.pfms.app.data.local.entity.GoalTransactionEntity
import com.pfms.app.data.local.entity.SavingsGoalEntity
import com.pfms.app.data.local.entity.SyncStatus
import com.pfms.app.data.local.mapper.toEntity
import com.pfms.app.data.remote.firestore.SavingsGoalRemoteDataSource
import com.pfms.app.domain.model.AuthUser
import com.pfms.app.domain.model.GoalStatus
import com.pfms.app.domain.model.GoalTransaction
import com.pfms.app.domain.model.GoalTransactionType
import com.pfms.app.domain.model.SavingsGoal
import com.pfms.app.domain.repository.AuthRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class OfflineFirstSavingsGoalRepositoryTest {

    private lateinit var savingsGoalDao: SavingsGoalDao
    private lateinit var goalTransactionDao: GoalTransactionDao
    private lateinit var remoteDataSource: SavingsGoalRemoteDataSource
    private lateinit var authRepository: AuthRepository
    private lateinit var repository: OfflineFirstSavingsGoalRepository

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
    }

    // 1. create active goal succeeds when none exists
    @Test
    fun createGoal_succeedsWhenNoActiveGoalExists() = runTest {
        whenever(remoteDataSource.upsertGoal(any(), any())).thenReturn(Result.success(Unit))

        val goal = SavingsGoal(id = "g-1", userId = "user-123", name = "MacBook", status = GoalStatus.ACTIVE)
        val result = repository.createGoal(goal)

        assertTrue(result.isSuccess)
        verify(savingsGoalDao).insertActiveGoalGuarded(any())
    }

    // 2. second active goal is rejected
    @Test
    fun createGoal_secondActiveGoalIsRejected() = runTest {
        whenever(savingsGoalDao.insertActiveGoalGuarded(any())).thenThrow(
            IllegalStateException("An active savings goal already exists")
        )

        val newGoal = SavingsGoal(id = "g-new", userId = "user-123", name = "Another Active", status = GoalStatus.ACTIVE)
        val result = repository.createGoal(newGoal)

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is IllegalStateException)
        assertEquals("An active savings goal already exists", result.exceptionOrNull()?.message)
    }

    // 3. archived goal allows new active goal
    @Test
    fun createGoal_creatingArchivedGoalDoesNotTriggerActiveCheck() = runTest {
        whenever(remoteDataSource.upsertGoal(any(), any())).thenReturn(Result.success(Unit))

        val archivedGoal = SavingsGoal(id = "g-archived", userId = "user-123", name = "Old Goal", status = GoalStatus.ARCHIVED)
        val result = repository.createGoal(archivedGoal)

        assertTrue(result.isSuccess)
        verify(savingsGoalDao).insertActiveGoalGuarded(any())
    }

    // 4. activating another goal while active exists is rejected
    @Test
    fun updateGoal_activatingGoalWhenActiveExistsIsRejected() = runTest {
        val goalToActivate = SavingsGoal(id = "g-archived", userId = "user-123", name = "Was Archived", status = GoalStatus.ACTIVE)
        val existingEntity = SavingsGoalEntity(id = "g-archived", userId = "user-123", name = "Was Archived", status = "ARCHIVED")

        whenever(savingsGoalDao.getGoalById("user-123", "g-archived")).thenReturn(existingEntity)
        whenever(savingsGoalDao.updateActiveGoalGuarded(any())).thenThrow(
            IllegalStateException("An active savings goal already exists")
        )

        val result = repository.updateGoal(goalToActivate)

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is IllegalStateException)
        assertEquals("An active savings goal already exists", result.exceptionOrNull()?.message)
    }

    // 5. offline create remains PENDING_INSERT
    @Test
    fun createGoal_offlineFailureLeavesGoalPendingInsert() = runTest {
        whenever(remoteDataSource.upsertGoal(any(), any())).thenReturn(Result.failure(RuntimeException("No internet")))

        val goal = SavingsGoal(id = "g-1", userId = "user-123", name = "Goal 1", status = GoalStatus.ACTIVE)
        val result = repository.createGoal(goal)

        assertTrue("Local write must succeed even if remote fails", result.isSuccess)

        val captor = argumentCaptor<SavingsGoalEntity>()
        verify(savingsGoalDao).insertActiveGoalGuarded(captor.capture())
        assertEquals(SyncStatus.PENDING_INSERT, captor.firstValue.syncStatus)
        verify(savingsGoalDao, never()).completeSync(any())
    }

    // 6. online create becomes SYNCED
    @Test
    fun createGoal_onlineSuccessMarksGoalSynced() = runTest {
        whenever(savingsGoalDao.getActiveGoal("user-123")).thenReturn(null)
        whenever(remoteDataSource.upsertGoal(any(), any())).thenReturn(Result.success(Unit))

        val goal = SavingsGoal(id = "g-1", userId = "user-123", name = "Goal 1", status = GoalStatus.ACTIVE)
        val result = repository.createGoal(goal)

        assertTrue(result.isSuccess)
        verify(savingsGoalDao).completeSync(org.mockito.kotlin.argThat { userId == "user-123" && id == "g-1" && syncStatus == SyncStatus.PENDING_INSERT })
    }

    // 7. update uses PENDING_UPDATE
    @Test
    fun updateGoal_persistsWithPendingUpdate() = runTest {
        val existing = SavingsGoalEntity(id = "g-1", userId = "user-123", name = "Old Name", status = "ACTIVE", createdAt = 1000L)
        whenever(savingsGoalDao.getGoalById("user-123", "g-1")).thenReturn(existing)
        whenever(savingsGoalDao.getActiveGoal("user-123")).thenReturn(existing)
        whenever(remoteDataSource.upsertGoal(any(), any())).thenReturn(Result.failure(RuntimeException("Offline")))

        val updated = SavingsGoal(id = "g-1", userId = "user-123", name = "New Name", status = GoalStatus.ACTIVE)
        val result = repository.updateGoal(updated)

        assertTrue(result.isSuccess)
        val captor = argumentCaptor<SavingsGoalEntity>()
        verify(savingsGoalDao).updateActiveGoalGuarded(captor.capture())
        assertEquals(SyncStatus.PENDING_UPDATE, captor.firstValue.syncStatus)
        assertEquals("New Name", captor.firstValue.name)
    }

    // 8. offline delete remains PENDING_DELETE
    @Test
    fun deleteGoal_offlineFailureLeavesGoalPendingDelete() = runTest {
        val existing = SavingsGoalEntity(id = "g-1", userId = "user-123", name = "Goal")
        whenever(savingsGoalDao.getGoalById("user-123", "g-1")).thenReturn(existing)
        whenever(remoteDataSource.deleteGoal("user-123", "g-1")).thenReturn(Result.failure(RuntimeException("Offline")))

        val result = repository.deleteGoal("g-1")

        assertTrue(result.isSuccess)
        verify(savingsGoalDao).markGoalDeleted(eq("user-123"), eq("g-1"), eq(SyncStatus.PENDING_DELETE), any())
        verify(savingsGoalDao, never()).completeSync(any())
    }

    // 9. online delete hard-deletes after remote success
    @Test
    fun deleteGoal_onlineSuccessHardDeletesLocally() = runTest {
        val existing = SavingsGoalEntity(id = "g-1", userId = "user-123", name = "Goal")
        whenever(savingsGoalDao.getGoalById("user-123", "g-1")).thenReturn(existing)
        whenever(remoteDataSource.deleteGoal("user-123", "g-1")).thenReturn(Result.success(Unit))

        val result = repository.deleteGoal("g-1")

        assertTrue(result.isSuccess)
        verify(savingsGoalDao).markGoalDeleted(eq("user-123"), eq("g-1"), eq(SyncStatus.PENDING_DELETE), any())
        verify(savingsGoalDao).completeSync(org.mockito.kotlin.argThat { userId == "user-123" && id == "g-1" && isDeleted })
    }

    // 10. add contribution persists locally first
    @Test
    fun addTransaction_contributionPersistsLocallyFirst() = runTest {
        val goalEntity = SavingsGoalEntity(id = "g-1", userId = "user-123", name = "Goal")
        whenever(savingsGoalDao.getGoalById("user-123", "g-1")).thenReturn(goalEntity)
        whenever(remoteDataSource.upsertTransaction(any(), any())).thenReturn(Result.failure(RuntimeException("Offline")))

        val tx = GoalTransaction(
            id = "tx-1",
            goalId = "g-1",
            userId = "user-123",
            amountMinor = 10_000L,
            transactionType = GoalTransactionType.CONTRIBUTION
        )
        val result = repository.addTransaction(tx)

        assertTrue(result.isSuccess)
        val captor = argumentCaptor<GoalTransactionEntity>()
        verify(goalTransactionDao).upsertTransaction(captor.capture())
        assertEquals(SyncStatus.PENDING_INSERT, captor.firstValue.syncStatus)
        assertEquals("CONTRIBUTION", captor.firstValue.transactionType)
    }

    // 11. add withdrawal persists locally first
    @Test
    fun addTransaction_withdrawalPersistsLocallyFirst() = runTest {
        val goalEntity = SavingsGoalEntity(id = "g-1", userId = "user-123", name = "Goal")
        whenever(savingsGoalDao.getGoalById("user-123", "g-1")).thenReturn(goalEntity)
        whenever(remoteDataSource.upsertTransaction(any(), any())).thenReturn(Result.failure(RuntimeException("Offline")))

        val tx = GoalTransaction(
            id = "tx-2",
            goalId = "g-1",
            userId = "user-123",
            amountMinor = 5_000L,
            transactionType = GoalTransactionType.WITHDRAWAL
        )
        val result = repository.addTransaction(tx)

        assertTrue(result.isSuccess)
        val captor = argumentCaptor<GoalTransactionEntity>()
        verify(goalTransactionDao).upsertTransaction(captor.capture())
        assertEquals("WITHDRAWAL", captor.firstValue.transactionType)
    }

    // 12. ledger does not change openingSavedAmount
    @Test
    fun addTransaction_doesNotModifyGoalOpeningAmount() = runTest {
        val goalEntity = SavingsGoalEntity(
            id = "g-1",
            userId = "user-123",
            name = "Goal",
            openingSavedAmountMinor = 5_000_000L
        )
        whenever(savingsGoalDao.getGoalById("user-123", "g-1")).thenReturn(goalEntity)

        val tx = GoalTransaction(id = "tx-1", goalId = "g-1", userId = "user-123", amountMinor = 100_000L)
        repository.addTransaction(tx)

        // Goal table is NOT updated by adding a ledger transaction
        verify(savingsGoalDao, never()).upsertGoal(any())
        assertEquals(5_000_000L, goalEntity.openingSavedAmountMinor)
    }

    // 13. monthly surplus is never auto-added
    @Test
    fun repository_neverAutoAddsSurplusToLedger() = runTest {
        // Checking observeTransactions does not generate synthetic surplus rows
        whenever(goalTransactionDao.observeTransactionsForGoal("user-123", "g-1"))
            .thenReturn(flowOf(emptyList()))

        val list = repository.observeTransactions("g-1").first()
        assertTrue("Repository must not fabricate ledger surplus rows", list.isEmpty())
    }

    // 14. transaction soft delete disappears from normal ledger
    @Test
    fun deleteTransaction_softDeletesWithPendingDelete() = runTest {
        val txEntity = GoalTransactionEntity(id = "tx-1", goalId = "g-1", userId = "user-123")
        whenever(goalTransactionDao.getTransactionById("user-123", "tx-1")).thenReturn(txEntity)
        whenever(remoteDataSource.deleteTransaction("user-123", "tx-1")).thenReturn(Result.failure(RuntimeException("Offline")))

        val result = repository.deleteTransaction("tx-1")

        assertTrue(result.isSuccess)
        verify(goalTransactionDao).markTransactionDeleted(eq("user-123"), eq("tx-1"), eq(SyncStatus.PENDING_DELETE), any())
        verify(goalTransactionDao, never()).completeSync(any())
    }

    // 15. pending goal sync succeeds and marks SYNCED
    @Test
    fun syncPendingGoals_succeedsAndMarksSynced() = runTest {
        val pendingGoal = SavingsGoalEntity(id = "g-p1", userId = "user-123", name = "Pending Goal", syncStatus = SyncStatus.PENDING_INSERT)
        whenever(savingsGoalDao.getPendingGoals("user-123")).thenReturn(listOf(pendingGoal))
        whenever(remoteDataSource.upsertGoal(eq("user-123"), any())).thenReturn(Result.success(Unit))

        val result = repository.syncPendingGoals()

        assertTrue(result.isSuccess)
        assertEquals(1, result.getOrNull())
        verify(savingsGoalDao).completeSync(pendingGoal)
    }

    // 16. pending transaction sync succeeds and marks SYNCED
    @Test
    fun syncPendingTransactions_succeedsAndMarksSynced() = runTest {
        val pendingTx = GoalTransactionEntity(id = "tx-p1", goalId = "g-1", userId = "user-123", syncStatus = SyncStatus.PENDING_UPDATE)
        whenever(goalTransactionDao.getPendingTransactions("user-123")).thenReturn(listOf(pendingTx))
        whenever(remoteDataSource.upsertTransaction(eq("user-123"), any())).thenReturn(Result.success(Unit))

        val result = repository.syncPendingTransactions()

        assertTrue(result.isSuccess)
        assertEquals(1, result.getOrNull())
        verify(goalTransactionDao).completeSync(pendingTx)
    }

    // 17. PENDING_DELETE remote success hard-deletes locally
    @Test
    fun syncPendingGoals_pendingDeleteSuccessHardDeletes() = runTest {
        val pendingDeleteGoal = SavingsGoalEntity(id = "g-del", userId = "user-123", name = "Delete Goal", isDeleted = true, syncStatus = SyncStatus.PENDING_DELETE)
        whenever(savingsGoalDao.getPendingGoals("user-123")).thenReturn(listOf(pendingDeleteGoal))
        whenever(remoteDataSource.deleteGoal("user-123", "g-del")).thenReturn(Result.success(Unit))

        val result = repository.syncPendingGoals()

        assertTrue(result.isSuccess)
        verify(savingsGoalDao).completeSync(pendingDeleteGoal)
    }

    // 18. failed pending sync remains pending
    @Test
    fun syncPendingGoals_failureLeavesPendingState() = runTest {
        val pendingGoal = SavingsGoalEntity(id = "g-fail", userId = "user-123", name = "Fail Goal", syncStatus = SyncStatus.PENDING_INSERT)
        whenever(savingsGoalDao.getPendingGoals("user-123")).thenReturn(listOf(pendingGoal))
        whenever(remoteDataSource.upsertGoal(any(), any())).thenReturn(Result.failure(RuntimeException("Remote fail")))

        val result = repository.syncPendingGoals()

        assertTrue(result.isFailure)
        verify(savingsGoalDao, never()).completeSync(any())
    }

    // 19. user A cannot mutate user B goal
    @Test
    fun crossUserWrite_userACannotMutateUserBGoal() = runTest {
        val goalUserB = SavingsGoal(id = "g-1", userId = "victim-user-B", name = "Secret")

        val resultCreate = repository.createGoal(goalUserB)
        assertTrue(resultCreate.isFailure)
        assertTrue(resultCreate.exceptionOrNull() is IllegalArgumentException)

        val resultUpdate = repository.updateGoal(goalUserB)
        assertTrue(resultUpdate.isFailure)
        assertTrue(resultUpdate.exceptionOrNull() is IllegalArgumentException)

        verify(savingsGoalDao, never()).insertActiveGoalGuarded(any())
        verify(savingsGoalDao, never()).updateActiveGoalGuarded(any())
    }

    // 20. user A cannot mutate user B transaction
    @Test
    fun crossUserWrite_userACannotMutateUserBTransaction() = runTest {
        val txUserB = GoalTransaction(id = "tx-1", goalId = "g-1", userId = "victim-user-B", amountMinor = 1000L)

        val result = repository.addTransaction(txUserB)
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is IllegalArgumentException)
        verify(goalTransactionDao, never()).upsertTransaction(any())
    }

    // 21. remote refresh does not overwrite PENDING_UPDATE local goal
    @Test
    fun refreshRemoteSavingsData_doesNotOverwritePendingUpdateLocalGoal() = runTest {
        val pendingLocal = SavingsGoalEntity(id = "g-1", userId = "user-123", name = "Local Pending", syncStatus = SyncStatus.PENDING_UPDATE)
        whenever(savingsGoalDao.getPendingGoals("user-123")).thenReturn(listOf(pendingLocal))

        val remoteGoal = SavingsGoal(id = "g-1", userId = "user-123", name = "Remote Stale")
        whenever(remoteDataSource.fetchGoals("user-123")).thenReturn(Result.success(listOf(remoteGoal)))
        whenever(goalTransactionDao.getPendingTransactions("user-123")).thenReturn(emptyList())

        val result = repository.refreshRemoteSavingsData()

        assertTrue(result.isSuccess)
        // Upsert must NOT be called for g-1
        verify(savingsGoalDao, never()).upsertGoal(any())
    }

    // 22. remote refresh does not resurrect PENDING_DELETE goal
    @Test
    fun refreshRemoteSavingsData_doesNotResurrectPendingDeleteGoal() = runTest {
        val pendingDelete = SavingsGoalEntity(id = "g-del", userId = "user-123", name = "Delete Goal", isDeleted = true, syncStatus = SyncStatus.PENDING_DELETE)
        whenever(savingsGoalDao.getPendingGoals("user-123")).thenReturn(listOf(pendingDelete))

        val remoteGoal = SavingsGoal(id = "g-del", userId = "user-123", name = "Remote Old")
        whenever(remoteDataSource.fetchGoals("user-123")).thenReturn(Result.success(listOf(remoteGoal)))
        whenever(goalTransactionDao.getPendingTransactions("user-123")).thenReturn(emptyList())

        val result = repository.refreshRemoteSavingsData()

        assertTrue(result.isSuccess)
        verify(savingsGoalDao, never()).upsertGoal(any())
    }

    // 23. remote newer SYNCED goal updates local
    @Test
    fun refreshRemoteSavingsData_newerRemoteSyncedGoalUpdatesLocal() = runTest {
        val localSynced = SavingsGoalEntity(id = "g-1", userId = "user-123", name = "Old Local", updatedAt = 1000L, syncStatus = SyncStatus.SYNCED)
        whenever(savingsGoalDao.getPendingGoals("user-123")).thenReturn(emptyList())
        whenever(savingsGoalDao.getGoalById("user-123", "g-1")).thenReturn(localSynced)

        val remoteGoal = SavingsGoal(id = "g-1", userId = "user-123", name = "New Remote", updatedAt = 2000L)
        whenever(remoteDataSource.fetchGoals("user-123")).thenReturn(Result.success(listOf(remoteGoal)))
        whenever(goalTransactionDao.getPendingTransactions("user-123")).thenReturn(emptyList())
        whenever(remoteDataSource.fetchTransactionsForGoal("user-123", "g-1")).thenReturn(Result.success(emptyList()))

        val result = repository.refreshRemoteSavingsData()

        assertTrue(result.isSuccess)
        val captor = argumentCaptor<SavingsGoalEntity>()
        verify(savingsGoalDao).upsertGoal(captor.capture())
        assertEquals("New Remote", captor.firstValue.name)
        assertEquals(SyncStatus.SYNCED, captor.firstValue.syncStatus)
    }

    // 24. remote new goal inserts locally as SYNCED
    @Test
    fun refreshRemoteSavingsData_newRemoteGoalInsertsLocallyAsSynced() = runTest {
        whenever(savingsGoalDao.getPendingGoals("user-123")).thenReturn(emptyList())
        whenever(savingsGoalDao.getGoalById("user-123", "g-new")).thenReturn(null)

        val remoteGoal = SavingsGoal(id = "g-new", userId = "user-123", name = "Brand New Remote")
        whenever(remoteDataSource.fetchGoals("user-123")).thenReturn(Result.success(listOf(remoteGoal)))
        whenever(goalTransactionDao.getPendingTransactions("user-123")).thenReturn(emptyList())
        whenever(remoteDataSource.fetchTransactionsForGoal("user-123", "g-new")).thenReturn(Result.success(emptyList()))

        val result = repository.refreshRemoteSavingsData()

        assertTrue(result.isSuccess)
        val captor = argumentCaptor<SavingsGoalEntity>()
        verify(savingsGoalDao).upsertGoal(captor.capture())
        assertEquals("g-new", captor.firstValue.id)
        assertEquals(SyncStatus.SYNCED, captor.firstValue.syncStatus)
    }

    // Additional: archiveGoal sets ARCHIVED and updates
    @Test
    fun archiveGoal_setsArchivedAndSyncs() = runTest {
        val existing = SavingsGoalEntity(id = "g-1", userId = "user-123", name = "Active Goal", status = "ACTIVE")
        whenever(savingsGoalDao.getGoalById("user-123", "g-1")).thenReturn(existing)
        whenever(remoteDataSource.upsertGoal(any(), any())).thenReturn(Result.success(Unit))

        val result = repository.archiveGoal("g-1")

        assertTrue(result.isSuccess)
        val captor = argumentCaptor<SavingsGoalEntity>()
        verify(savingsGoalDao).upsertGoal(captor.capture())
        assertEquals("ARCHIVED", captor.firstValue.status)
        assertEquals(SyncStatus.PENDING_UPDATE, captor.firstValue.syncStatus)
        verify(savingsGoalDao).completeSync(captor.firstValue)
    }

    // Requirements tests for atomic one-active-goal enforcement:

    // 1. Concurrent creation of two active goals: exactly one succeeds
    @Test
    fun concurrentCreate_twoActiveGoals_onlyOneSucceeds() = runTest {
        val fakeDao = FakeSavingsGoalDao()
        val repo = OfflineFirstSavingsGoalRepository(
            savingsGoalDao = fakeDao,
            goalTransactionDao = goalTransactionDao,
            savingsGoalRemoteDataSource = remoteDataSource,
            authRepository = authRepository
        )
        whenever(remoteDataSource.upsertGoal(any(), any())).thenReturn(Result.success(Unit))

        val goal1 = SavingsGoal(id = "g-concurrent-1", userId = "user-123", name = "Goal 1", status = GoalStatus.ACTIVE)
        val goal2 = SavingsGoal(id = "g-concurrent-2", userId = "user-123", name = "Goal 2", status = GoalStatus.ACTIVE)

        val deferred1 = async(Dispatchers.Default) { repo.createGoal(goal1) }
        val deferred2 = async(Dispatchers.Default) { repo.createGoal(goal2) }

        val res1 = deferred1.await()
        val res2 = deferred2.await()

        val successes = listOf(res1, res2).count { it.isSuccess }
        val failures = listOf(res1, res2).count { it.isFailure }

        assertEquals("Exactly one concurrent create must succeed", 1, successes)
        assertEquals("Exactly one concurrent create must fail", 1, failures)

        val failure = listOf(res1, res2).first { it.isFailure }
        assertTrue(failure.exceptionOrNull() is IllegalStateException)
        assertEquals("An active savings goal already exists", failure.exceptionOrNull()?.message)
    }

    // 2. Activating archived goal when another active exists is rejected
    @Test
    fun activatingArchivedGoal_whenAnotherActiveExists_isRejected() = runTest {
        val fakeDao = FakeSavingsGoalDao()
        val repo = OfflineFirstSavingsGoalRepository(
            savingsGoalDao = fakeDao,
            goalTransactionDao = goalTransactionDao,
            savingsGoalRemoteDataSource = remoteDataSource,
            authRepository = authRepository
        )
        whenever(remoteDataSource.upsertGoal(any(), any())).thenReturn(Result.success(Unit))

        val activeGoal = SavingsGoal(id = "g-active", userId = "user-123", name = "Active Goal", status = GoalStatus.ACTIVE)
        val archivedGoal = SavingsGoal(id = "g-archived", userId = "user-123", name = "Archived Goal", status = GoalStatus.ARCHIVED)

        assertTrue(repo.createGoal(activeGoal).isSuccess)
        assertTrue(repo.createGoal(archivedGoal).isSuccess)

        val reactivateResult = repo.updateGoal(archivedGoal.copy(status = GoalStatus.ACTIVE))
        assertTrue(reactivateResult.isFailure)
        assertTrue(reactivateResult.exceptionOrNull() is IllegalStateException)
        assertEquals("An active savings goal already exists", reactivateResult.exceptionOrNull()?.message)
    }

    // 3. Archived existing goal allows creating new active goal
    @Test
    fun archivedExistingGoal_allowsCreatingNewActiveGoal() = runTest {
        val fakeDao = FakeSavingsGoalDao()
        val repo = OfflineFirstSavingsGoalRepository(
            savingsGoalDao = fakeDao,
            goalTransactionDao = goalTransactionDao,
            savingsGoalRemoteDataSource = remoteDataSource,
            authRepository = authRepository
        )
        whenever(remoteDataSource.upsertGoal(any(), any())).thenReturn(Result.success(Unit))

        val goal1 = SavingsGoal(id = "g-1", userId = "user-123", name = "Goal 1", status = GoalStatus.ACTIVE)
        assertTrue(repo.createGoal(goal1).isSuccess)

        assertTrue(repo.archiveGoal("g-1").isSuccess)

        val goal2 = SavingsGoal(id = "g-2", userId = "user-123", name = "Goal 2", status = GoalStatus.ACTIVE)
        val result = repo.createGoal(goal2)
        assertTrue(result.isSuccess)
    }
}

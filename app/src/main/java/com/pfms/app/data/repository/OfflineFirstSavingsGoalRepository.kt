package com.pfms.app.data.repository

import com.pfms.app.data.local.dao.GoalTransactionDao
import com.pfms.app.data.local.dao.SavingsGoalDao
import com.pfms.app.data.local.entity.SyncStatus
import com.pfms.app.data.local.mapper.toDomain
import com.pfms.app.data.local.mapper.toEntity
import com.pfms.app.data.remote.firestore.SavingsGoalRemoteDataSource
import com.pfms.app.data.sync.SavingsGoalPendingSyncable
import com.pfms.app.domain.model.AuthUser
import com.pfms.app.domain.model.GoalStatus
import com.pfms.app.domain.model.GoalTransaction
import com.pfms.app.domain.model.SavingsGoal
import com.pfms.app.domain.model.SavingsRestorationState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import com.pfms.app.domain.repository.AuthRepository
import com.pfms.app.domain.repository.SavingsGoalRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.TimeoutCancellationException
import com.pfms.app.data.error.repositoryResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton
import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import com.pfms.app.data.sync.IncomeSyncWorker
import androidx.work.ExistingWorkPolicy

/**
 * Offline-first implementation of [SavingsGoalRepository].
 *
 * Core Architecture & Design Principles:
 * - Single Source of Truth (SSOT): Room local database is the single source of truth for UI/domain state.
 * - Reactive Isolation: Observation queries stream directly from Room scoped to the active [AuthUser].
 * - One-Active-Goal Enforcement: Repository prevents creating or activating a second goal when an ACTIVE goal already exists (FR-23).
 * - Ledger Immutability & Derivation: Transactions do not alter goal opening amounts; goal balances are strictly calculated (FR-24).
 * - Optimistic Local Persistence: All writes are committed to Room with appropriate [SyncStatus] before Firestore sync is attempted.
 * - Local-First Conflict Resolution: Local pending changes (PENDING_INSERT, PENDING_UPDATE, PENDING_DELETE) always win over remote state during reconciliation.
 */
@Singleton
class OfflineFirstSavingsGoalRepository @Inject constructor(
    private val savingsGoalDao: SavingsGoalDao,
    private val goalTransactionDao: GoalTransactionDao,
    private val savingsGoalRemoteDataSource: SavingsGoalRemoteDataSource,
    private val authRepository: AuthRepository
) : SavingsGoalRepository, SavingsGoalPendingSyncable {
    private val restoration = MutableStateFlow("" to SavingsRestorationState())
    private val restorationMutex = Mutex()

    override fun observeRestoration(): Flow<SavingsRestorationState> =
        combine(authRepository.currentUser, restoration) { user, (uid, state) ->
            if (user?.uid == uid) state else SavingsRestorationState()
        }

    override fun clearRestorationError() {
        val (uid, state) = restoration.value
        if (!state.isRefreshing) restoration.value = uid to SavingsRestorationState()
    }

    @Inject @ApplicationContext lateinit var syncContext: Context
    private fun scheduleSync() {
        if (::syncContext.isInitialized) runCatching { IncomeSyncWorker.enqueue(syncContext, ExistingWorkPolicy.APPEND_OR_REPLACE) }
    }

    private suspend fun requireUser(): AuthUser {
        val user = authRepository.currentUser.first()
        if (user == null || user.uid.isBlank()) {
            throw IllegalStateException("Authentication required: no active user session")
        }
        return user
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    override fun observeActiveGoal(): Flow<SavingsGoal?> {
        return authRepository.currentUser.flatMapLatest { user ->
            if (user == null || user.uid.isBlank()) {
                flowOf(null)
            } else {
                savingsGoalDao.observeActiveGoal(user.uid).map { entity ->
                    entity?.toDomain()
                }
            }
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    override fun observeTransactions(goalId: String): Flow<List<GoalTransaction>> {
        return authRepository.currentUser.flatMapLatest { user ->
            if (user == null || user.uid.isBlank()) {
                flowOf(emptyList())
            } else {
                goalTransactionDao.observeTransactionsForGoal(user.uid, goalId).map { entities ->
                    entities.toDomain()
                }
            }
        }
    }

    override suspend fun getGoalById(goalId: String): Result<SavingsGoal?> = withContext(Dispatchers.IO) {
        repositoryResult {
            val user = requireUser()
            require(goalId.isNotBlank()) { "Goal ID must not be blank" }

            val entity = savingsGoalDao.getGoalById(user.uid, goalId)
            if (entity != null && !entity.isDeleted) {
                entity.toDomain()
            } else {
                null
            }
        }
    }

    override suspend fun createGoal(goal: SavingsGoal): Result<Unit> = withContext(Dispatchers.IO) {
        repositoryResult {
            val user = requireUser()
            require(goal.userId == user.uid) {
                "Cross-user write prevented: authenticated user '${user.uid}' does not match goal.userId '${goal.userId}'"
            }
            require(goal.id.isNotBlank()) { "Goal ID must not be blank" }

            val now = if (goal.createdAt > 0L) goal.createdAt else System.currentTimeMillis()
            val goalToSave = goal.copy(
                createdAt = now,
                updatedAt = if (goal.updatedAt > 0L) goal.updatedAt else now
            )

            // 1. Write Room first atomically with one-active-goal guard (FR-23)
            val localEntity = goalToSave.toEntity(
                syncStatus = SyncStatus.PENDING_INSERT,
                isDeleted = false
            )
            savingsGoalDao.insertActiveGoalGuarded(localEntity)
            scheduleSync()

            // 2. Immediately attempt remote Firestore upsert
            try {
                val remoteResult = withTimeoutOrNull(1_000) { savingsGoalRemoteDataSource.upsertGoal(user.uid, goalToSave) }
                if (remoteResult?.isSuccess == true) {
                    savingsGoalDao.completeSync(localEntity)
                }
            } catch (error: Exception) {
                if (error is CancellationException) throw error
                // Offline-first: failure keeps PENDING_INSERT locally, returns cleanly
            }
        }
    }

    override suspend fun updateGoal(goal: SavingsGoal): Result<Unit> = withContext(Dispatchers.IO) {
        repositoryResult {
            val user = requireUser()
            require(goal.userId == user.uid) {
                "Cross-user write prevented: authenticated user '${user.uid}' does not match goal.userId '${goal.userId}'"
            }
            require(goal.id.isNotBlank()) { "Goal ID must not be blank" }

            val existing = savingsGoalDao.getGoalById(user.uid, goal.id)
            if (existing == null || existing.isDeleted) {
                throw NoSuchElementException("Goal not found with id: ${goal.id}")
            }

            val originalCreatedAt = existing.createdAt.takeIf { it > 0L } ?: goal.createdAt
            val updatedAt = if (goal.updatedAt > 0L && goal.updatedAt > existing.updatedAt) goal.updatedAt else System.currentTimeMillis()

            val goalToUpdate = goal.copy(
                createdAt = originalCreatedAt,
                updatedAt = updatedAt
            )

            // 1. Write Room first atomically with one-active-goal guard
            val updatedEntity = goalToUpdate.toEntity(
                syncStatus = SyncStatus.PENDING_UPDATE,
                isDeleted = false
            )
            savingsGoalDao.updateActiveGoalGuarded(updatedEntity)
            scheduleSync()

            // 2. Immediately attempt remote upsert
            try {
                val remoteResult = withTimeoutOrNull(1_000) { savingsGoalRemoteDataSource.upsertGoal(user.uid, goalToUpdate) }
                if (remoteResult?.isSuccess == true) {
                    savingsGoalDao.completeSync(updatedEntity)
                }
            } catch (error: Exception) {
                if (error is CancellationException) throw error
                // Offline-first: remains PENDING_UPDATE on failure
            }
        }
    }

    override suspend fun archiveGoal(goalId: String): Result<Unit> = withContext(Dispatchers.IO) {
        repositoryResult {
            val user = requireUser()
            require(goalId.isNotBlank()) { "Goal ID must not be blank" }

            val existing = savingsGoalDao.getGoalById(user.uid, goalId)
            if (existing == null || existing.isDeleted) {
                throw NoSuchElementException("Goal not found with id: $goalId")
            }

            val archived = existing.toDomain().copy(
                status = GoalStatus.ARCHIVED,
                updatedAt = System.currentTimeMillis()
            )

            val updatedEntity = archived.toEntity(
                syncStatus = SyncStatus.PENDING_UPDATE,
                isDeleted = false
            )
            savingsGoalDao.upsertGoal(updatedEntity)
            scheduleSync()

            try {
                val remoteResult = withTimeoutOrNull(1_000) { savingsGoalRemoteDataSource.upsertGoal(user.uid, archived) }
                if (remoteResult?.isSuccess == true) {
                    savingsGoalDao.completeSync(updatedEntity)
                }
            } catch (error: Exception) {
                if (error is CancellationException) throw error
                // Offline-first: remains PENDING_UPDATE on failure
            }
        }
    }

    override suspend fun deleteGoal(goalId: String): Result<Unit> = withContext(Dispatchers.IO) {
        repositoryResult {
            val user = requireUser()
            require(goalId.isNotBlank()) { "Goal ID must not be blank" }

            val existing = savingsGoalDao.getGoalById(user.uid, goalId)
            if (existing == null || existing.isDeleted) {
                throw NoSuchElementException("Goal not found with id: $goalId")
            }

            // 1. Soft delete locally with PENDING_DELETE so it immediately disappears from active views
            val deletedAt = com.pfms.app.data.sync.SyncVersion.nextTimestamp(existing.updatedAt)
            savingsGoalDao.markGoalDeleted(
                userId = user.uid,
                goalId = goalId,
                syncStatus = SyncStatus.PENDING_DELETE,
                updatedAt = deletedAt
            )

            scheduleSync()
            // 2. Attempt remote deletion
            try {
                val remoteResult = withTimeoutOrNull(1_000) { savingsGoalRemoteDataSource.deleteGoal(user.uid, goalId) }
                if (remoteResult?.isSuccess == true) {
                    savingsGoalDao.completeSync(existing.copy(isDeleted = true, syncStatus = SyncStatus.PENDING_DELETE, updatedAt = deletedAt))
                }
            } catch (error: Exception) {
                if (error is CancellationException) throw error
                // Remains soft-deleted with PENDING_DELETE for retry
            }
        }
    }

    override suspend fun addTransaction(transaction: GoalTransaction): Result<Unit> = withContext(Dispatchers.IO) {
        repositoryResult {
            val user = requireUser()
            require(transaction.userId == user.uid) {
                "Cross-user write prevented: authenticated user '${user.uid}' does not match transaction.userId '${transaction.userId}'"
            }
            require(transaction.id.isNotBlank()) { "Transaction ID must not be blank" }

            // Validate that the goal exists and belongs to the user
            val goal = savingsGoalDao.getGoalById(user.uid, transaction.goalId)
            if (goal == null || goal.isDeleted) {
                throw NoSuchElementException("Savings goal not found with id: ${transaction.goalId}")
            }

            val now = if (transaction.createdAt > 0L) transaction.createdAt else System.currentTimeMillis()
            val txToSave = transaction.copy(
                createdAt = now,
                updatedAt = if (transaction.updatedAt > 0L) transaction.updatedAt else now
            )

            // 1. Write Room first with PENDING_INSERT
            val localEntity = txToSave.toEntity(
                syncStatus = SyncStatus.PENDING_INSERT,
                isDeleted = false
            )
            goalTransactionDao.insertTransactionGuarded(localEntity)
            scheduleSync()

            // 2. Immediately attempt remote upsert
            try {
                val remoteResult = withTimeoutOrNull(1_000) { savingsGoalRemoteDataSource.upsertTransaction(user.uid, txToSave) }
                if (remoteResult?.isSuccess == true) {
                    goalTransactionDao.completeSync(localEntity)
                }
            } catch (error: Exception) {
                if (error is CancellationException) throw error
                // Offline-first: remains PENDING_INSERT locally
            }
        }
    }

    override suspend fun updateTransaction(transaction: GoalTransaction): Result<Unit> = withContext(Dispatchers.IO) {
        repositoryResult {
            val user = requireUser()
            require(transaction.userId == user.uid) {
                "Cross-user write prevented: authenticated user '${user.uid}' does not match transaction.userId '${transaction.userId}'"
            }
            require(transaction.id.isNotBlank()) { "Transaction ID must not be blank" }

            val existing = goalTransactionDao.getTransactionById(user.uid, transaction.id)
            if (existing == null || existing.isDeleted) {
                throw NoSuchElementException("Transaction not found with id: ${transaction.id}")
            }

            val goal = savingsGoalDao.getGoalById(user.uid, transaction.goalId)
            if (goal == null || goal.isDeleted) {
                throw NoSuchElementException("Savings goal not found with id: ${transaction.goalId}")
            }

            val originalCreatedAt = existing.createdAt.takeIf { it > 0L } ?: transaction.createdAt
            val updatedAt = if (transaction.updatedAt > 0L && transaction.updatedAt > existing.updatedAt) transaction.updatedAt else System.currentTimeMillis()

            val txToUpdate = transaction.copy(
                createdAt = originalCreatedAt,
                updatedAt = updatedAt
            )

            val updatedEntity = txToUpdate.toEntity(
                syncStatus = SyncStatus.PENDING_UPDATE,
                isDeleted = false
            )
            goalTransactionDao.updateTransactionGuarded(updatedEntity)
            scheduleSync()

            try {
                val remoteResult = withTimeoutOrNull(1_000) { savingsGoalRemoteDataSource.upsertTransaction(user.uid, txToUpdate) }
                if (remoteResult?.isSuccess == true) {
                    goalTransactionDao.completeSync(updatedEntity)
                }
            } catch (error: Exception) {
                if (error is CancellationException) throw error
                // Offline-first: remains PENDING_UPDATE on failure
            }
        }
    }

    override suspend fun deleteTransaction(transactionId: String): Result<Unit> = withContext(Dispatchers.IO) {
        repositoryResult {
            val user = requireUser()
            require(transactionId.isNotBlank()) { "Transaction ID must not be blank" }

            val existing = goalTransactionDao.getTransactionById(user.uid, transactionId)
            if (existing == null || existing.isDeleted) {
                throw NoSuchElementException("Transaction not found with id: $transactionId")
            }

            // 1. Soft-delete locally with PENDING_DELETE
            val deletedAt = com.pfms.app.data.sync.SyncVersion.nextTimestamp(existing.updatedAt)
            goalTransactionDao.deleteTransactionGuarded(user.uid, transactionId, deletedAt)

            scheduleSync()
            // 2. Attempt remote delete
            try {
                val remoteResult = withTimeoutOrNull(1_000) { savingsGoalRemoteDataSource.deleteTransaction(user.uid, transactionId) }
                if (remoteResult?.isSuccess == true) {
                    goalTransactionDao.completeSync(existing.copy(isDeleted = true, syncStatus = SyncStatus.PENDING_DELETE, updatedAt = deletedAt))
                }
            } catch (error: Exception) {
                if (error is CancellationException) throw error
                // Remains PENDING_DELETE for retry
            }
        }
    }

    override suspend fun syncPendingGoals(): Result<Int> = withContext(Dispatchers.IO) {
        repositoryResult {
            val user = requireUser()
            val pendingList = savingsGoalDao.getPendingGoals(user.uid)
            var syncedCount = 0
            var failureException: Throwable? = null

            for (record in pendingList) {
                if (authRepository.currentUser.first()?.uid != user.uid) break
                if (savingsGoalDao.getPendingGoals(user.uid).none { it == record }) continue
                try {
                    when (record.syncStatus) {
                        SyncStatus.PENDING_INSERT,
                        SyncStatus.PENDING_UPDATE -> {
                            val res = savingsGoalRemoteDataSource.upsertGoal(user.uid, record.toDomain())
                            if (res.isSuccess) {
                                savingsGoalDao.completeSync(record)
                                syncedCount++
                            } else {
                                failureException = res.exceptionOrNull()
                            }
                        }
                        SyncStatus.PENDING_DELETE -> {
                            val res = savingsGoalRemoteDataSource.deleteGoal(user.uid, record.id)
                            if (res.isSuccess) {
                                savingsGoalDao.completeSync(record)
                                syncedCount++
                            } else {
                                failureException = res.exceptionOrNull()
                            }
                        }
                        SyncStatus.SYNCED -> Unit
                    }
                } catch (e: Exception) {
                    if (e is CancellationException) throw e
                    failureException = e
                }
            }

            if (failureException != null) {
                throw failureException
            } else {
                syncedCount
            }
        }
    }

    override suspend fun syncPendingTransactions(): Result<Int> = withContext(Dispatchers.IO) {
        repositoryResult {
            val user = requireUser()
            val pendingList = goalTransactionDao.getPendingTransactions(user.uid)
            var syncedCount = 0
            var failureException: Throwable? = null

            for (record in pendingList) {
                if (authRepository.currentUser.first()?.uid != user.uid) break
                if (goalTransactionDao.getPendingTransactions(user.uid).none { it == record }) continue
                try {
                    when (record.syncStatus) {
                        SyncStatus.PENDING_INSERT,
                        SyncStatus.PENDING_UPDATE -> {
                            val res = savingsGoalRemoteDataSource.upsertTransaction(user.uid, record.toDomain())
                            if (res.isSuccess) {
                                goalTransactionDao.completeSync(record)
                                syncedCount++
                            } else {
                                failureException = res.exceptionOrNull()
                            }
                        }
                        SyncStatus.PENDING_DELETE -> {
                            val res = savingsGoalRemoteDataSource.deleteTransaction(user.uid, record.id)
                            if (res.isSuccess) {
                                goalTransactionDao.completeSync(record)
                                syncedCount++
                            } else {
                                failureException = res.exceptionOrNull()
                            }
                        }
                        SyncStatus.SYNCED -> Unit
                    }
                } catch (e: Exception) {
                    if (e is CancellationException) throw e
                    failureException = e
                }
            }

            if (failureException != null) {
                throw failureException
            } else {
                syncedCount
            }
        }
    }

    override suspend fun refreshRemoteSavingsData(): Result<Unit> = withContext(Dispatchers.IO) {
        restorationMutex.withLock {
            val uid = repositoryResult { requireUser().uid }.getOrElse { return@withLock Result.failure(it) }
            restoration.value = uid to SavingsRestorationState(isRefreshing = true)
            try {
                val result = try {
                    withTimeout(20_000L) { repositoryResult { restoreSavingsForUser(uid) } }
                } catch (timeout: TimeoutCancellationException) {
                    Result.failure(timeout)
                }
                restoration.value = uid to SavingsRestorationState(error = result.exceptionOrNull())
                result
            } catch (cancelled: CancellationException) {
                restoration.value = uid to SavingsRestorationState()
                throw cancelled
            }
        }
    }

    private suspend fun requireRestorationSession(uid: String) {
        check(requireUser().uid == uid) { "Authentication required: session changed during savings restoration" }
    }

    private suspend fun restoreSavingsForUser(uid: String) {
        val goals = savingsGoalRemoteDataSource.fetchGoals(uid).getOrThrow()
        requireRestorationSession(uid)
        require(goals.all { it.userId == uid && it.id.isNotBlank() }) {
            "Remote savings goals do not belong to the authenticated account"
        }

        // Fetch every ledger before merging anything: a failed fetch cannot masquerade as an empty ledger.
        val ledgers = goals.associate { goal ->
            val transactions = savingsGoalRemoteDataSource.fetchTransactionsForGoal(uid, goal.id).getOrThrow()
            require(transactions.all { it.userId == uid && it.goalId == goal.id && it.id.isNotBlank() }) {
                "Remote savings ledger does not belong to the requested goal and account"
            }
            goal.id to transactions
        }
        requireRestorationSession(uid)

        for (goal in goals) {
            for (transaction in ledgers.getValue(goal.id)) {
                requireRestorationSession(uid)
                // Re-read pending state after network waits. Local edits/deletions always win.
                if (goalTransactionDao.getPendingTransactions(uid).any { it.id == transaction.id }) continue
                val local = goalTransactionDao.getTransactionById(uid, transaction.id)
                if (local == null || (local.syncStatus == SyncStatus.SYNCED && transaction.updatedAt >= local.updatedAt)) {
                    goalTransactionDao.upsertTransaction(transaction.toEntity(SyncStatus.SYNCED, false))
                }
            }
            // Publish a newly restored goal only after its ledger is present in Room.
            requireRestorationSession(uid)
            if (savingsGoalDao.getPendingGoals(uid).any { it.id == goal.id }) continue
            val local = savingsGoalDao.getGoalById(uid, goal.id)
            if (local == null || (local.syncStatus == SyncStatus.SYNCED && goal.updatedAt >= local.updatedAt)) {
                savingsGoalDao.upsertGoal(goal.toEntity(SyncStatus.SYNCED, false))
            }
        }
    }
}

package com.pfms.app.data.repository

import com.pfms.app.data.local.dao.IncomeDao
import com.pfms.app.data.local.entity.SyncStatus
import com.pfms.app.data.local.mapper.toDomain
import com.pfms.app.data.local.mapper.toEntity
import com.pfms.app.data.remote.firestore.IncomeRemoteDataSource
import com.pfms.app.data.sync.IncomePendingSyncable
import com.pfms.app.domain.model.AuthUser
import com.pfms.app.domain.model.Income
import com.pfms.app.domain.repository.AuthRepository
import com.pfms.app.domain.repository.IncomeRepository
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
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.retryWhen

/**
 * Offline-first implementation of [IncomeRepository].
 *
 * Architecture Principles:
 * - Room local database is the Single Source of Truth (SSOT).
 * - Read queries stream directly from Room.
 * - Writes are persisted immediately to Room with appropriate [SyncStatus] before attempting remote sync.
 * - Network failures leave records in pending state without breaking local user workflows.
 * - Strict user isolation ensures users can only access and mutate their own records.
 */
@Singleton
class OfflineFirstIncomeRepository @Inject constructor(
    private val incomeDao: IncomeDao,
    private val incomeRemoteDataSource: IncomeRemoteDataSource,
    private val authRepository: AuthRepository
) : IncomeRepository, IncomePendingSyncable {
    @Inject @ApplicationContext lateinit var syncContext: Context
    private fun scheduleSync() {
        if (::syncContext.isInitialized) IncomeSyncWorker.enqueue(syncContext, ExistingWorkPolicy.APPEND_OR_REPLACE)
    }

    private suspend fun requireUser(): AuthUser {
        val user = authRepository.currentUser.first()
        if (user == null || user.uid.isBlank()) {
            throw IllegalStateException("Authentication required: no active user session")
        }
        return user
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    override fun observeIncome(): Flow<List<Income>> {
        return authRepository.currentUser.flatMapLatest { user ->
            if (user == null || user.uid.isBlank()) {
                flowOf(emptyList())
            } else {
                channelFlow {
                    launch {
                        incomeRemoteDataSource.observeSnapshots(user.uid).retryWhen { e, _ ->
                            if (e is CancellationException) throw e
                            delay(2_000); true
                        }.collect { snapshot -> incomeDao.reconcile(user.uid, snapshot.rows.map { it.copy(userId = user.uid).toEntity() }, snapshot.authoritative) }
                    }
                    incomeDao.observeIncome(user.uid).collect { send(it.toDomain()) }
                }
            }
        }
    }

    override suspend fun addIncome(income: Income) = withContext(Dispatchers.IO) {
        val user = requireUser()
        require(income.userId == user.uid) {
            "Cross-user write prevented: authenticated user '${user.uid}' does not match income.userId '${income.userId}'"
        }
        require(income.id.isNotBlank()) { "Income ID must not be blank" }

        val now = if (income.createdAt > 0L) income.createdAt else System.currentTimeMillis()
        val incomeToSave = income.copy(
            createdAt = now,
            updatedAt = if (income.updatedAt > 0L) income.updatedAt else now
        )

        // 1. Save immediately to local Room SSOT with PENDING_INSERT
        val localEntity = incomeToSave.toEntity(
            syncStatus = SyncStatus.PENDING_INSERT,
            isDeleted = false
        )
        incomeDao.upsertIncome(localEntity)
        scheduleSync()

        // 2. Attempt remote Firestore upsert
        try {
            withTimeout(10_000) { incomeRemoteDataSource.upsertIncome(user.uid, incomeToSave) }
            // 3. Mark SYNCED on success
            incomeDao.updateSyncStatus(user.uid, incomeToSave.id, SyncStatus.SYNCED)
        } catch (e: Exception) {
            // Offline-first: failure keeps PENDING_INSERT in Room, returns cleanly
        }
    }

    override suspend fun updateIncome(income: Income) = withContext(Dispatchers.IO) {
        val user = requireUser()
        require(income.userId == user.uid) {
            "Cross-user write prevented: authenticated user '${user.uid}' does not match income.userId '${income.userId}'"
        }
        require(income.id.isNotBlank()) { "Income ID must not be blank" }

        val existing = incomeDao.getIncomeById(user.uid, income.id)
        require(existing != null && !existing.isDeleted) {
            "Income record not found with id: ${income.id}"
        }

        val originalCreatedAt = existing.createdAt.takeIf { it > 0L } ?: income.createdAt
        val updatedAt = if (income.updatedAt > 0L) income.updatedAt else System.currentTimeMillis()

        val incomeToUpdate = income.copy(
            createdAt = originalCreatedAt,
            updatedAt = updatedAt
        )

        // 1. Update local Room SSOT with PENDING_UPDATE
        val updatedEntity = incomeToUpdate.toEntity(
            syncStatus = SyncStatus.PENDING_UPDATE,
            isDeleted = false
        )
        incomeDao.upsertIncome(updatedEntity)
        scheduleSync()

        // 2. Attempt remote Firestore upsert
        try {
            withTimeout(10_000) { incomeRemoteDataSource.upsertIncome(user.uid, incomeToUpdate) }
            // 3. Mark SYNCED on success
            incomeDao.updateSyncStatus(user.uid, incomeToUpdate.id, SyncStatus.SYNCED)
        } catch (e: Exception) {
            // Offline-first: remains PENDING_UPDATE on failure
        }
    }

    override suspend fun deleteIncome(incomeId: String) = withContext(Dispatchers.IO) {
        val user = requireUser()
        require(incomeId.isNotBlank()) { "Income ID must not be blank" }

        // 1. Soft delete locally with PENDING_DELETE so it disappears from UI while tracking sync
        incomeDao.softDeleteIncome(
            userId = user.uid,
            incomeId = incomeId,
            syncStatus = SyncStatus.PENDING_DELETE,
            updatedAt = System.currentTimeMillis()
        )

        // 2. Attempt remote Firestore deletion
        scheduleSync()
        try {
            withTimeout(10_000) { incomeRemoteDataSource.deleteIncome(user.uid, incomeId) }
            // 3. Remote confirmed: safely remove local row
            incomeDao.hardDeleteIncome(user.uid, incomeId)
        } catch (e: Exception) {
            // Offline-first: remains soft-deleted with PENDING_DELETE for retry
        }
    }

    override suspend fun getIncomeById(incomeId: String): Income? = withContext(Dispatchers.IO) {
        val user = requireUser()
        require(incomeId.isNotBlank()) { "Income ID must not be blank" }

        val entity = incomeDao.getIncomeById(user.uid, incomeId)
        if (entity != null && !entity.isDeleted) {
            entity.toDomain()
        } else {
            null
        }
    }

    /**
     * Synchronizes all locally queued pending changes for the authenticated user to Firestore.
     * Failed records remain in their pending state for subsequent retry attempts.
     */
    override suspend fun syncPendingIncome(): Result<Int> = withContext(Dispatchers.IO) {
        try {
            val user = requireUser()
            val pendingList = incomeDao.getPendingSync(user.uid)
            var syncedCount = 0
            var failureException: Exception? = null

            for (record in pendingList) {
                try {
                    when (record.syncStatus) {
                        SyncStatus.PENDING_INSERT,
                        SyncStatus.PENDING_UPDATE -> {
                            incomeRemoteDataSource.upsertIncome(user.uid, record.toDomain())
                            incomeDao.updateSyncStatus(user.uid, record.id, SyncStatus.SYNCED)
                            syncedCount++
                        }
                        SyncStatus.PENDING_DELETE -> {
                            incomeRemoteDataSource.deleteIncome(user.uid, record.id)
                            incomeDao.hardDeleteIncome(user.uid, record.id)
                            syncedCount++
                        }
                        SyncStatus.SYNCED -> Unit
                    }
                } catch (e: Exception) {
                    // Record remains in pending state for next sync pass
                    failureException = e
                }
            }
            if (failureException != null) {
                Result.failure(failureException)
            } else {
                Result.success(syncedCount)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Reconciles remote items into the local Room database.
     *
     * Conflict Resolution Rule:
     * - Local pending changes (PENDING_INSERT, PENDING_UPDATE, PENDING_DELETE) always win over remote state.
     * - For records in SYNCED state, remote record replaces local only if remote.updatedAt >= local.updatedAt.
     */
    suspend fun refreshRemoteIncome(): Result<Int> = withContext(Dispatchers.IO) {
        try {
            val user = requireUser()
            val remoteList = incomeRemoteDataSource.getAllIncome(user.uid)
            var updatedCount = 0

            for (remoteItem in remoteList) {
                val local = incomeDao.getIncomeById(user.uid, remoteItem.id)
                if (local == null) {
                    // New remote item: insert into local database as SYNCED
                    incomeDao.upsertIncome(
                        remoteItem.toEntity(
                            syncStatus = SyncStatus.SYNCED,
                            isDeleted = false
                        )
                    )
                    updatedCount++
                } else if (local.syncStatus == SyncStatus.SYNCED) {
                    // If local has no pending edits, newer remote item updates local
                    if (remoteItem.updatedAt >= local.updatedAt) {
                        incomeDao.upsertIncome(
                            remoteItem.toEntity(
                                syncStatus = SyncStatus.SYNCED,
                                isDeleted = false
                            )
                        )
                        updatedCount++
                    }
                }
                // Pending local mutations win and are preserved untouched until synced
            }
            Result.success(updatedCount)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}

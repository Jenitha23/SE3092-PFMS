package com.pfms.app.data.repository

import android.content.Context
import androidx.work.ExistingWorkPolicy
import com.pfms.app.data.local.dao.IncomeDao
import com.pfms.app.data.local.entity.IncomeEntity
import com.pfms.app.data.local.entity.SyncStatus
import com.pfms.app.data.local.mapper.toDomain
import com.pfms.app.data.local.mapper.toEntity
import com.pfms.app.data.remote.firestore.IncomeRemoteDataSource
import com.pfms.app.data.sync.IncomePendingSyncable
import com.pfms.app.data.sync.IncomeSyncWorker
import com.pfms.app.data.sync.SyncVersion
import com.pfms.app.domain.model.AuthUser
import com.pfms.app.domain.model.Income
import com.pfms.app.domain.repository.AuthRepository
import com.pfms.app.domain.repository.IncomeRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/** Room is the durable queue and source of truth. Only background sync performs network writes. */
@Singleton
@OptIn(ExperimentalCoroutinesApi::class)
class OfflineFirstIncomeRepository @Inject constructor(
    private val incomeDao: IncomeDao,
    private val incomeRemoteDataSource: IncomeRemoteDataSource,
    private val authRepository: AuthRepository
) : IncomeRepository, IncomePendingSyncable {
    @Inject @ApplicationContext lateinit var syncContext: Context
    private val mutations = Mutex()
    private val syncing = Mutex()

    private fun scheduleSync() {
        if (::syncContext.isInitialized) IncomeSyncWorker.enqueue(syncContext, ExistingWorkPolicy.APPEND_OR_REPLACE)
    }

    private suspend fun requireUser(): AuthUser = authRepository.currentUser.first()
        ?.takeIf { it.uid.isNotBlank() } ?: error("Authentication required: no active user session")

    override fun observeIncome(): Flow<List<Income>> = authRepository.currentUser
        .map { it?.uid?.takeIf(String::isNotBlank) }.distinctUntilChanged().flatMapLatest { uid ->
            if (uid == null) flowOf(emptyList()) else channelFlow {
                launch {
                    incomeRemoteDataSource.observeSnapshots(uid).retryWhen { error, _ ->
                        if (error is CancellationException) throw error
                        delay(2_000); true
                    }.collect { snapshot ->
                        val rows = snapshot.rows.map {
                            it.copy(userId = uid).toEntity().copy(revision = snapshot.versions[it.id]?.revision.orEmpty())
                        } + snapshot.deletions.map {
                            IncomeEntity(id = it.id, userId = uid, updatedAt = it.version.updatedAt,
                                isDeleted = true, revision = it.version.revision)
                        }
                        incomeDao.reconcile(uid, rows, snapshot.authoritative)
                    }
                }
                incomeDao.observeIncome(uid).collect { send(it.toDomain()) }
            }
        }.flowOn(Dispatchers.IO)

    override suspend fun addIncome(income: Income) = withContext(Dispatchers.IO) {
        val user = requireUser()
        require(income.userId == user.uid) { "Cross-user write prevented" }
        require(income.id.isNotBlank()) { "Income ID must not be blank" }
        mutations.withLock {
            val previous = incomeDao.getIncomeById(user.uid, income.id)
            val now = SyncVersion.nextTimestamp(previous?.updatedAt ?: 0L, income.updatedAt)
            incomeDao.upsertIncome(income.copy(createdAt = previous?.createdAt ?: income.createdAt.takeIf { it > 0 } ?: now,
                updatedAt = now).toEntity(SyncStatus.PENDING_INSERT).copy(revision = UUID.randomUUID().toString()))
        }
        scheduleSync()
    }

    override suspend fun updateIncome(income: Income) = withContext(Dispatchers.IO) {
        val user = requireUser()
        require(income.userId == user.uid) { "Cross-user write prevented" }
        require(income.id.isNotBlank()) { "Income ID must not be blank" }
        mutations.withLock {
            val old = incomeDao.getIncomeById(user.uid, income.id)?.takeUnless { it.isDeleted }
                ?: error("Income record not found with id: ${income.id}")
            incomeDao.upsertIncome(income.copy(createdAt = old.createdAt,
                updatedAt = SyncVersion.nextTimestamp(old.updatedAt, income.updatedAt))
                .toEntity(if (old.syncStatus == SyncStatus.PENDING_INSERT) SyncStatus.PENDING_INSERT else SyncStatus.PENDING_UPDATE)
                .copy(revision = UUID.randomUUID().toString()))
        }
        scheduleSync()
    }

    override suspend fun deleteIncome(incomeId: String) = withContext(Dispatchers.IO) {
        val user = requireUser()
        require(incomeId.isNotBlank()) { "Income ID must not be blank" }
        mutations.withLock {
            incomeDao.getIncomeById(user.uid, incomeId)?.let { old ->
                incomeDao.upsertIncome(old.copy(isDeleted = true, syncStatus = SyncStatus.PENDING_DELETE,
                    updatedAt = SyncVersion.nextTimestamp(old.updatedAt), revision = UUID.randomUUID().toString()))
            }
        }
        scheduleSync()
    }

    override suspend fun getIncomeById(incomeId: String): Income? = withContext(Dispatchers.IO) {
        val user = requireUser()
        require(incomeId.isNotBlank()) { "Income ID must not be blank" }
        incomeDao.getIncomeById(user.uid, incomeId)?.takeUnless { it.isDeleted }?.toDomain()
    }

    override suspend fun syncPendingIncome(): Result<Int> = withContext(Dispatchers.IO) {
        syncing.withLock {
            try {
                val user = requireUser()
                var count = 0
                var failure: Exception? = null
                for (queued in incomeDao.getPendingSync(user.uid)) {
                    if (authRepository.currentUser.first()?.uid != user.uid) break
                    try {
                        val row = if (queued.revision.isBlank()) {
                            incomeDao.assignLegacyRevision(user.uid, queued.id, queued.updatedAt, UUID.randomUUID().toString())
                            incomeDao.getIncomeById(user.uid, queued.id) ?: continue
                        } else queued
                        if (row.syncStatus == SyncStatus.SYNCED) continue
                        val winner = withTimeout(15_000) {
                            incomeRemoteDataSource.synchronize(user.uid, row.toDomain(), SyncVersion(row.updatedAt, row.revision, row.isDeleted))
                        }
                        val entity = winner.row?.copy(userId = user.uid)?.toEntity() ?: row.copy(isDeleted = true)
                        incomeDao.completeSync(row, entity.copy(updatedAt = winner.version.updatedAt,
                            revision = winner.version.revision, isDeleted = winner.version.deleted, syncStatus = SyncStatus.SYNCED))
                        count++
                    } catch (error: Exception) {
                        if (error is CancellationException && error !is TimeoutCancellationException) throw error
                        failure = error
                    }
                }
                failure?.let { Result.failure(it) } ?: Result.success(count)
            } catch (error: Exception) {
                if (error is CancellationException && error !is TimeoutCancellationException) throw error
                Result.failure(error)
            }
        }
    }

    suspend fun refreshRemoteIncome(): Result<Int> = withContext(Dispatchers.IO) {
        try {
            val user = requireUser()
            var count = 0
            for (income in incomeRemoteDataSource.getAllIncome(user.uid)) {
                val local = incomeDao.getIncomeById(user.uid, income.id)
                if (local == null || (local.syncStatus == SyncStatus.SYNCED && income.updatedAt >= local.updatedAt)) {
                    incomeDao.upsertIncome(income.copy(userId = user.uid).toEntity())
                    count++
                }
            }
            Result.success(count)
        } catch (error: Exception) {
            if (error is CancellationException) throw error
            Result.failure(error)
        }
    }
}

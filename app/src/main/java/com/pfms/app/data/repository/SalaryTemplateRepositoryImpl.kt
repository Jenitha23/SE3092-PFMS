package com.pfms.app.data.repository

import androidx.room.withTransaction
import com.pfms.app.data.local.PfmsDatabase
import com.pfms.app.data.local.dao.IncomeDao
import com.pfms.app.data.local.dao.SalaryTemplateDao
import com.pfms.app.data.local.entity.SyncStatus
import com.pfms.app.data.local.mapper.toDomain
import com.pfms.app.data.local.mapper.toEntity
import com.pfms.app.data.remote.firestore.IncomeRemoteDataSource
import com.pfms.app.data.remote.firestore.SalaryTemplateRemoteDataSource
import com.pfms.app.data.sync.SalaryTemplatePendingSyncable
import com.pfms.app.domain.model.AuthUser
import com.pfms.app.domain.model.Income
import com.pfms.app.domain.model.SalaryTemplate
import com.pfms.app.domain.repository.AuthRepository
import com.pfms.app.domain.repository.SalaryTemplateRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.ZoneId
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
 * Offline-first implementation of [SalaryTemplateRepository].
 *
 * Architecture Principles:
 * - Room is the Single Source of Truth for recurring salary templates.
 * - Monthly confirmation creates a deterministic income record to guarantee zero duplicate
 *   confirmations across offline retries and concurrent sync passes.
 * - Multi-table operations (template update + income creation) are executed in an atomic Room transaction.
 */
@Singleton
class SalaryTemplateRepositoryImpl @Inject constructor(
    private val salaryTemplateDao: SalaryTemplateDao,
    private val salaryTemplateRemoteDataSource: SalaryTemplateRemoteDataSource,
    private val incomeDao: IncomeDao,
    private val incomeRemoteDataSource: IncomeRemoteDataSource,
    private val database: PfmsDatabase,
    private val authRepository: AuthRepository
) : SalaryTemplateRepository, SalaryTemplatePendingSyncable {
    @Inject lateinit var issues: com.pfms.app.data.sync.SyncIssueStore
    @Inject @ApplicationContext lateinit var syncContext: Context
    private fun scheduleSync() {
        if (::syncContext.isInitialized) runCatching { IncomeSyncWorker.enqueue(syncContext, ExistingWorkPolicy.APPEND_OR_REPLACE) }
    }

    internal var transactionRunner: suspend (suspend () -> Unit) -> Unit = { block ->
        database.withTransaction(block)
    }

    private suspend fun requireUser(): AuthUser {
        val user = authRepository.currentUser.first()
        if (user == null || user.uid.isBlank()) {
            throw IllegalStateException("Authentication required: no active user session")
        }
        return user
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    override fun observeSalaryTemplate(): Flow<SalaryTemplate?> {
        return authRepository.currentUser.flatMapLatest { user ->
            if (user == null || user.uid.isBlank()) {
                flowOf(null)
            } else {
                channelFlow {
                    launch {
                        salaryTemplateRemoteDataSource.observeSnapshots(user.uid).retryWhen { e, _ ->
                            if (e is CancellationException) throw e
                            if (::issues.isInitialized) issues.failed(user.uid, "salary-load", e)
                            delay(2_000); true
                        }.collect { snapshot ->
                            if (snapshot.authoritative && ::issues.isInitialized) issues.recovered(user.uid, "salary-load")
                            salaryTemplateDao.reconcile(user.uid,
                                snapshot.rows.map { it.copy(userId = user.uid).toEntity() }, snapshot.authoritative) }
                    }
                    salaryTemplateDao.observeSalaryTemplate(user.uid).collect { send(it?.takeIf { row -> !row.isDeleted }?.toDomain()) }
                }
            }
        }
    }

    override suspend fun saveSalaryTemplate(template: SalaryTemplate) = withContext(Dispatchers.IO) {
        val user = requireUser()
        require(template.userId.isBlank() || template.userId == user.uid) {
            "Cross-user write prevented: authenticated user '${user.uid}' does not match template.userId '${template.userId}'"
        }
        require(template.id.isNotBlank()) { "Template ID must not be blank" }

        val now = System.currentTimeMillis()
        val templateToSave = template.copy(
            userId = user.uid,
            id = if (template.userId.isBlank()) "salary_${user.uid}" else template.id,
            createdAt = if (template.createdAt > 0L) template.createdAt else now,
            updatedAt = if (template.updatedAt > 0L) template.updatedAt else now
        )

        // 1. Save locally with pending state
        val entity = templateToSave.toEntity(
            syncStatus = SyncStatus.PENDING_UPDATE,
            isDeleted = false
        )
        salaryTemplateDao.upsertSalaryTemplate(entity)
        scheduleSync()

        // 2. Attempt remote Firestore upsert
        try {
            withTimeout(10_000) { salaryTemplateRemoteDataSource.upsertSalaryTemplate(user.uid, templateToSave) }
            // 3. Mark synced on success
            salaryTemplateDao.completeSync(entity)
        } catch (e: Exception) {
            if (e is CancellationException && e !is TimeoutCancellationException) throw e
            // Offline-first: remains pending on failure
        }
    }

    /**
     * Confirms the recurring salary for [monthKey] (e.g. "2026-10"):
     * 1. Validates authenticated user.
     * 2. Guards against duplicate confirmations for the given month.
     * 3. Creates a new Income record with a deterministic ID: "salary_{templateId}_{monthKey}".
     * 4. Updates template's amountMinor and lastConfirmedMonth in an atomic Room transaction.
     * 5. Syncs both records to Firestore (or keeps pending state on offline failure).
     */
    override suspend fun confirmMonthlySalary(
        templateId: String,
        monthKey: String,
        amountMinor: Long
    ) = withContext(Dispatchers.IO) {
        val user = requireUser()
        require(templateId.isNotBlank()) { "Template ID must not be blank" }
        require(Regex("""\d{4}-\d{2}""").matches(monthKey)) { "Invalid salary month" }
        require(amountMinor > 0L) { "Confirmed salary amount must be greater than zero" }

        // The salary's deterministic ID is also the idempotency key.
        val incomeId = "salary_${templateId}_${monthKey}"
        val now = System.currentTimeMillis()
        val receivedDate = LocalDate.now(ZoneId.of("Asia/Colombo")).toString()
        var templateChanged = false

        // Read AND validate within the same Room transaction; never create duplicates.
        transactionRunner {
            val current = salaryTemplateDao.getSalaryTemplate(user.uid)
                ?: throw IllegalStateException("Salary template not found for this account.")

            check(current.id == templateId) {
                "Salary template has changed. Please reopen the reminder."
            }

            val existing = incomeDao.getIncomeById(user.uid, incomeId)

            when {
                existing != null && existing.isDeleted -> {
                    // A deletion tombstone must not be silently resurrected.
                    throw IllegalStateException(
                        "This month's salary was previously deleted. Review Income before confirming."
                    )
                }

                existing != null -> {
                    // The ID links the record to this template/month. Check the contents as well.
                    check(
                        existing.userId == user.uid &&
                                existing.source == "Salary" &&
                                existing.currency == current.currency
                    ) {
                        "Existing salary record conflicts with this salary template. Review Income."
                    }
                    check(existing.amountMinor == amountMinor) {
                        "Salary already recorded for $monthKey with a different amount. " +
                                "Please review or edit the existing income entry."
                    }
                    // Do not overwrite the already-recorded salary (amount, date or metadata).
                    // Repair only the template's missing confirmation marker.
                    if (current.lastConfirmedMonth != monthKey) {
                        salaryTemplateDao.upsertSalaryTemplate(
                            current.copy(
                                amountMinor = existing.amountMinor,
                                lastConfirmedMonth = monthKey,
                                updatedAt = now,
                                syncStatus = SyncStatus.PENDING_UPDATE
                            )
                        )
                        templateChanged = true
                    }
                }

                else -> {
                    check(current.lastConfirmedMonth != monthKey) {
                        "Salary is marked confirmed for $monthKey, but its income record is missing. " +
                                "Please review Income before trying again."
                    }

                    val income = Income(
                        id = incomeId,
                        userId = user.uid,
                        amountMinor = amountMinor,
                        currency = current.currency,
                        source = "Salary",
                        receivedDate = receivedDate,
                        note = "Monthly salary confirmation for $monthKey",
                        amountLkrMinor = if (current.currency == "LKR") amountMinor else null,
                        paymentStatus = "Received",
                        createdAt = now,
                        updatedAt = now
                    )
                    incomeDao.upsertIncome(
                        income.toEntity(
                            syncStatus = SyncStatus.PENDING_INSERT,
                            isDeleted = false
                        )
                    )
                    salaryTemplateDao.upsertSalaryTemplate(
                        current.copy(
                            amountMinor = amountMinor,
                            lastConfirmedMonth = monthKey,
                            updatedAt = now,
                            syncStatus = SyncStatus.PENDING_UPDATE
                        )
                    )
                    templateChanged = true
                }
            }
        }

        // The local transaction has succeeded. The worker handles the income upload.
        // A failed remote attempt must not turn a successful local confirmation into an error.
        scheduleSync()
        val submitted = salaryTemplateDao.getSalaryTemplate(user.uid)
        if (submitted != null && submitted.lastConfirmedMonth == monthKey &&
            submitted.syncStatus != SyncStatus.SYNCED && templateChanged
        ) {
            try {
                withTimeout(10_000) {
                    salaryTemplateRemoteDataSource.upsertSalaryTemplate(
                        user.uid,
                        submitted.toDomain()
                    )
                }
                salaryTemplateDao.completeSync(submitted)
            } catch (e: Exception) {
                if (e is CancellationException && e !is TimeoutCancellationException) throw e
                // The locally confirmed template remains pending for later sync.
            }
        }
    }

    /**
     * Synchronizes any pending template updates to Firestore.
     */
    override suspend fun syncPendingTemplate(): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val user = requireUser()
            val pendingList = salaryTemplateDao.getPendingSync(user.uid)
            var failureException: Exception? = null

            for (record in pendingList) {
                if (authRepository.currentUser.first()?.uid != user.uid) break
                if (salaryTemplateDao.getPendingSync(user.uid).none { it == record }) continue
                try {
                    when (record.syncStatus) {
                        SyncStatus.PENDING_INSERT,
                        SyncStatus.PENDING_UPDATE -> {
                            salaryTemplateRemoteDataSource.upsertSalaryTemplate(user.uid, record.toDomain())
                            salaryTemplateDao.completeSync(record)
                        }
                        SyncStatus.PENDING_DELETE -> {
                            salaryTemplateRemoteDataSource.deleteSalaryTemplate(user.uid, record.id)
                            salaryTemplateDao.completeSync(record)
                        }
                        SyncStatus.SYNCED -> Unit
                    }
                } catch (e: Exception) {
                    if (e is CancellationException) throw e
                    // Retains pending state
                    failureException = e
                }
            }
            if (failureException != null) {
                Result.failure(failureException)
            } else {
                Result.success(Unit)
            }
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            Result.failure(e)
        }
    }

    /**
     * Reconciles remote salary template into the local Room database.
     */
    suspend fun refreshRemoteTemplate(): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val user = requireUser()
            val remoteTemplates = salaryTemplateRemoteDataSource.getAllSalaryTemplates(user.uid)
            val remoteItem = remoteTemplates.firstOrNull() ?: return@withContext Result.success(Unit)

            val local = salaryTemplateDao.getSalaryTemplate(user.uid)
            if (local == null) {
                salaryTemplateDao.upsertSalaryTemplate(
                    remoteItem.toEntity(syncStatus = SyncStatus.SYNCED, isDeleted = false)
                )
            } else if (local.syncStatus == SyncStatus.SYNCED) {
                if (remoteItem.updatedAt >= local.updatedAt) {
                    salaryTemplateDao.upsertSalaryTemplate(
                        remoteItem.toEntity(syncStatus = SyncStatus.SYNCED, isDeleted = false)
                    )
                }
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}

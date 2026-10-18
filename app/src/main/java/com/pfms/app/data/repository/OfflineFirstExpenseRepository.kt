package com.pfms.app.data.repository

import android.content.Context
import androidx.room.withTransaction
import com.pfms.app.data.local.PfmsDatabase
import com.pfms.app.data.local.entity.toEntity
import com.pfms.app.data.local.entity.ExpenseEntity
import com.pfms.app.data.remote.firestore.ExpenseRemoteDataSource
import com.pfms.app.data.sync.ExpenseSyncWorker
import com.pfms.app.domain.model.*
import com.pfms.app.domain.repository.ExpenseRepository
import com.pfms.app.domain.repository.AuthRepository
import com.pfms.app.data.sync.ExpenseSyncScheduler
import com.pfms.app.domain.usecase.ExpenseRules
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
@OptIn(ExperimentalCoroutinesApi::class)
class OfflineFirstExpenseRepository @Inject constructor(
    private val db: PfmsDatabase,
    private val remote: ExpenseRemoteDataSource,
    private val auth: AuthRepository,
    @ApplicationContext private val context: Context,
    private val scheduler: ExpenseSyncScheduler
) : ExpenseRepository {
    private val dao get() = db.expenseDao()
    private val prefs = context.getSharedPreferences("expense_defaults", Context.MODE_PRIVATE)
    private val preferenceChanges = MutableStateFlow(0)
    private suspend fun uid() = auth.currentUser.first()?.uid ?: error("Authentication required")

    internal var transactionRunner: suspend (suspend () -> Unit) -> Unit = {
        db.withTransaction(it)
    }

    override fun observeLastPaymentMethod(): Flow<ExpensePaymentMethod> = auth.currentUser.map { it?.uid }.distinctUntilChanged().flatMapLatest { user ->
        preferenceChanges.map {
            ExpensePaymentMethod.entries.firstOrNull { it.name == prefs.getString("payment_$user", "CASH") }
                ?: ExpensePaymentMethod.CASH
        }
    }

    override fun observeExpenses(): Flow<List<Expense>> = auth.currentUser.map { it?.uid }.distinctUntilChanged().flatMapLatest { user ->
        if (user == null) flowOf(emptyList()) else channelFlow {
            scheduler.enqueue()
            launch {
                remote.observe(user).retryWhen { error, _ ->
                    if (error is CancellationException) throw error
                    delay(2_000); true
                }.collect { snapshot -> dao.reconcile(user, snapshot.rows.map { it.toEntity() }, snapshot.authoritative) }
            }
            dao.observe(user).collect { send(it.map { row -> row.toDomain() }) }
        }.onStart { emit(emptyList()) }
    }.flowOn(Dispatchers.IO)

    override fun observeDuplicateConflict(): Flow<Expense?> = auth.currentUser.map { it?.uid }.distinctUntilChanged().flatMapLatest { user ->
        if (user == null) flowOf(null) else dao.observeConflict(user).map { it?.toDomain() }
    }

    override suspend fun getExpenseById(expenseId: String): Expense? = withContext(Dispatchers.IO) {
        dao.get(uid(), expenseId)?.takeUnless { it.isDeleted }?.toDomain()
    }

    override suspend fun getExpensesCreatedSince(timestampMillis: Long): List<Expense> = withContext(Dispatchers.IO) {
        dao.recent(uid(), timestampMillis).map { it.toDomain() }
    }

    override suspend fun addExpense(expense: Expense) = addExpenseChecked(expense, false)

    override suspend fun addExpenseChecked(expense: Expense, allowDuplicate: Boolean) = withContext(Dispatchers.IO) {
        val user = uid()
        val row = expense.copy(userId = user, id = expense.id.ifBlank { UUID.randomUUID().toString() })
        transactionRunner {
            val existing = dao.get(user, row.id)
            if (existing?.syncStatus == "SYNCED") return@transactionRunner
            if (!allowDuplicate && ExpenseRules.isDuplicateWithin60Seconds(row,
                    dao.recent(user, row.createdAt - 60_000).filter { it.id != row.id }.map { it.toDomain() }, row.createdAt)) {
                throw DuplicateExpenseException()
            }
            dao.upsert(row.toEntity().copy(syncStatus = "PENDING_INSERT", revision = UUID.randomUUID().toString(), allowDuplicate = allowDuplicate))
        }
        savePayment(user, row.paymentMethod)
        scheduler.enqueue()
    }

    override suspend fun updateExpense(expense: Expense) = withContext(Dispatchers.IO) {
        val user = uid()
        transactionRunner {
            val old = dao.get(user, expense.id)?.takeUnless { it.isDeleted } ?: error("Expense not found")
            dao.upsert(expense.copy(userId = user, createdAt = old.createdAt).toEntity().copy(
                syncStatus = if (old.syncStatus == "PENDING_INSERT") "PENDING_INSERT" else "PENDING_UPDATE",
                revision = UUID.randomUUID().toString(), allowDuplicate = old.allowDuplicate))
        }
        savePayment(user, expense.paymentMethod)
        scheduler.enqueue()
    }

    override suspend fun deleteExpense(expenseId: String) = withContext(Dispatchers.IO) {
        val user = uid()
        transactionRunner {
            val old = dao.get(user, expenseId) ?: return@transactionRunner
            dao.upsert(old.copy(isDeleted = true, needsConfirmation = false,
                syncStatus = "PENDING_DELETE", revision = UUID.randomUUID().toString()))
        }
        scheduler.enqueue()
    }

    override suspend fun dismissDuplicate(expenseId: String) = withContext(Dispatchers.IO) {
        val user = uid()
        transactionRunner {
            val old = dao.get(user, expenseId)
            if (old?.needsConfirmation == true) dao.deleteRevision(user, expenseId, old.revision)
        }
    }

    private fun savePayment(user: String, payment: ExpensePaymentMethod) {
        check(prefs.edit().putString("payment_$user", payment.name).commit()) { "Unable to store payment default" }
        preferenceChanges.value++
    }

    suspend fun syncPending(): Boolean = withContext(Dispatchers.IO) {
        val user = auth.currentUser.first()?.uid ?: return@withContext true
        var success = true
        for (row in dao.pending(user)) {
            if (auth.currentUser.first()?.uid != user) return@withContext true
            try {
                withTimeout(15_000) {
                    when (row.syncStatus) {
                        "PENDING_DELETE" -> remote.delete(user, row.id)
                        "PENDING_INSERT" -> remote.insert(user, row.toDomain(), row.allowDuplicate)
                        "PENDING_UPDATE" -> remote.update(user, row.toDomain())
                    }
                }
                if (row.isDeleted) dao.deleteRevision(user, row.id, row.revision)
                else dao.acknowledge(user, row.id, row.revision)
            } catch (e: Exception) {
                if (e is CancellationException && e !is TimeoutCancellationException) throw e
                if (generateSequence<Throwable>(e) { it.cause }.any { it is DuplicateExpenseException }) {
                    dao.markConflict(user, row.id, row.revision)
                } else success = false
            }
        }
        success
    }
}

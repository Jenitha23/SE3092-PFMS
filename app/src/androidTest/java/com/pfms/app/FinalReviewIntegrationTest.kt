package com.pfms.app

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.pfms.app.data.local.PfmsDatabase
import com.pfms.app.data.local.entity.*
import com.pfms.app.data.local.mapper.toEntity
import com.pfms.app.data.remote.firestore.*
import com.pfms.app.data.repository.*
import com.pfms.app.data.sync.ExpenseSyncScheduler
import com.pfms.app.domain.model.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Test

/** Real Room/repository regressions found while reviewing FR-34 through FR-38 together. */
class FinalReviewIntegrationTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val db = Room.inMemoryDatabaseBuilder(context, PfmsDatabase::class.java).build()
    private val auth = ExpenseTestAuth()
    private val uid = auth.users.value!!.uid
    private val remote = SavingsRemote()
    private val savings = OfflineFirstSavingsGoalRepository(db.savingsGoalDao(), db.goalTransactionDao(), remote, auth)
    private fun goal() = SavingsGoal(id = "goal", userId = uid, name = "Emergency fund", targetAmountMinor = 100000,
        targetDate = "2027-10-01")
    @After fun close() { db.close() }

    @Test fun foregroundGoalAcknowledgementCannotConsumeANewerEdit() = runBlocking {
        remote.onGoal = {
            val current = db.savingsGoalDao().getGoalById(uid, it.id)!!
            db.savingsGoalDao().upsertGoal(current.copy(targetAmountMinor = 300000,
                updatedAt = current.updatedAt + 1, syncStatus = SyncStatus.PENDING_UPDATE))
        }
        assertTrue(savings.createGoal(goal()).isSuccess)
        assertEquals(300000L, db.savingsGoalDao().getGoalById(uid, "goal")!!.targetAmountMinor)
        assertEquals(SyncStatus.PENDING_UPDATE, db.savingsGoalDao().getGoalById(uid, "goal")!!.syncStatus)
        remote.onGoal = null
        assertTrue(savings.syncPendingGoals().isSuccess)
        assertEquals(SyncStatus.SYNCED, db.savingsGoalDao().getGoalById(uid, "goal")!!.syncStatus)
        assertEquals(300000L, remote.goals.getValue("goal").targetAmountMinor)
        assertEquals(1, remote.goals.size)
    }

    @Test fun backgroundGoalAcknowledgementCannotConsumeANewerEdit() = runBlocking {
        db.savingsGoalDao().upsertGoal(goal().toEntity(SyncStatus.PENDING_UPDATE))
        remote.onGoal = {
            val current = db.savingsGoalDao().getGoalById(uid, it.id)!!
            db.savingsGoalDao().upsertGoal(current.copy(name = "Edited during upload", updatedAt = current.updatedAt + 1))
        }
        assertTrue(savings.syncPendingGoals().isSuccess)
        assertEquals("Edited during upload", db.savingsGoalDao().getPendingGoals(uid).single().name)
        remote.onGoal = null
        assertTrue(savings.syncPendingGoals().isSuccess)
        assertTrue(db.savingsGoalDao().getPendingGoals(uid).isEmpty())
        assertEquals("Edited during upload", remote.goals.getValue("goal").name)
    }

    @Test fun historicalGoalLedgerEditDuringUploadRemainsPendingUntilItsOwnAcknowledgement() = runBlocking {
        db.savingsGoalDao().upsertGoal(goal().toEntity())
        remote.onTransaction = {
            val current = db.goalTransactionDao().getTransactionById(uid, it.id)!!
            db.goalTransactionDao().upsertTransaction(current.copy(amountMinor = 12345, date = "2025-03-02",
                updatedAt = current.updatedAt + 1, syncStatus = SyncStatus.PENDING_UPDATE))
        }
        val transaction = GoalTransaction(id = "ledger", goalId = "goal", userId = uid, amountMinor = 1000, date = "2026-10-20")
        assertTrue(savings.addTransaction(transaction).isSuccess)
        assertEquals("2025-03-02", db.goalTransactionDao().getPendingTransactions(uid).single().date)
        remote.onTransaction = null
        assertTrue(savings.syncPendingTransactions().isSuccess)
        assertTrue(db.goalTransactionDao().getPendingTransactions(uid).isEmpty())
        assertEquals(12345L, remote.transactions.getValue("ledger").amountMinor)
        assertEquals("2025-03-02", remote.transactions.getValue("ledger").date)
    }

    @Test fun oldDeletionAcknowledgementsCannotRemoveRecreatedGoalOrLedgerRecords() = runBlocking {
        val deletedGoal = goal().toEntity(SyncStatus.PENDING_DELETE).copy(isDeleted = true, updatedAt = 100)
        val deletedTransaction = GoalTransactionEntity("ledger", "goal", uid, amountMinor = 1000, date = "2025-03-02",
            isDeleted = true, updatedAt = 100, syncStatus = SyncStatus.PENDING_DELETE)
        db.savingsGoalDao().upsertGoal(deletedGoal)
        db.goalTransactionDao().upsertTransaction(deletedTransaction)
        remote.onDeleteGoal = { db.savingsGoalDao().upsertGoal(deletedGoal.copy(isDeleted = false, updatedAt = 200, syncStatus = SyncStatus.PENDING_INSERT)) }
        remote.onDeleteTransaction = { db.goalTransactionDao().upsertTransaction(deletedTransaction.copy(isDeleted = false, updatedAt = 200, syncStatus = SyncStatus.PENDING_INSERT)) }
        assertTrue(savings.syncPendingGoals().isSuccess)
        assertTrue(savings.syncPendingTransactions().isSuccess)
        assertEquals(SyncStatus.PENDING_INSERT, db.savingsGoalDao().getGoalById(uid, "goal")!!.syncStatus)
        assertEquals(SyncStatus.PENDING_INSERT, db.goalTransactionDao().getTransactionById(uid, "ledger")!!.syncStatus)
        assertTrue(savings.syncPendingGoals().isSuccess)
        assertTrue(savings.syncPendingTransactions().isSuccess)
        assertEquals(0, db.pendingChangesDao().pendingCount(uid))
    }

    @Test fun salaryAcknowledgementCannotClearAnEditMadeDuringUpload() = runBlocking {
        var editDuringUpload = true
        var uploadedAmount = 0L
        val salaryRemote = object : SalaryTemplateRemoteDataSource {
            override fun observeSalaryTemplates(userId: String): Flow<List<SalaryTemplate>> = emptyFlow()
            override suspend fun getAllSalaryTemplates(userId: String) = emptyList<SalaryTemplate>()
            override suspend fun getSalaryTemplateById(userId: String, templateId: String): SalaryTemplate? = null
            override suspend fun deleteSalaryTemplate(userId: String, templateId: String) = Unit
            override suspend fun upsertSalaryTemplate(userId: String, template: SalaryTemplate) {
                uploadedAmount = template.amountMinor
                if (editDuringUpload) {
                    val current = db.salaryTemplateDao().getSalaryTemplate(uid)!!
                    db.salaryTemplateDao().upsertSalaryTemplate(current.copy(amountMinor = 222222,
                        updatedAt = current.updatedAt + 1, syncStatus = SyncStatus.PENDING_UPDATE))
                }
            }
        }
        val repository = SalaryTemplateRepositoryImpl(db.salaryTemplateDao(), salaryRemote, db.incomeDao(), UnusedIncomeRemote, db, auth)
        repository.saveSalaryTemplate(SalaryTemplate(id = "salary", userId = uid, amountMinor = 111111, paymentDay = 20))
        assertEquals(222222L, db.salaryTemplateDao().getPendingSync(uid).single().amountMinor)
        editDuringUpload = false
        assertTrue(repository.syncPendingTemplate().isSuccess)
        assertTrue(db.salaryTemplateDao().getPendingSync(uid).isEmpty())
        assertEquals(222222L, uploadedAmount)
    }

    @Test fun expenseSchedulingFailureStillReturnsSuccessfulDurableCrud() = runBlocking {
        val noNetwork = object : ExpenseRemoteDataSource {
            override fun observe(userId: String): Flow<ExpenseRemoteSnapshot> = emptyFlow()
            override suspend fun insert(userId: String, expense: Expense, allowDuplicate: Boolean): Unit = error("Offline")
            override suspend fun update(userId: String, expense: Expense): Unit = error("Offline")
            override suspend fun delete(userId: String, id: String): Unit = error("Offline")
        }
        val repository = OfflineFirstExpenseRepository(db, noNetwork, auth, context, ExpenseSyncScheduler { error("Scheduling unavailable") })
        repository.addExpense(Expense(id = "stable", amountMinor = 1234, date = "2025-01-02"))
        repository.updateExpense(repository.getExpenseById("stable")!!.copy(amountMinor = 5678))
        assertEquals(5678L, repository.getExpenseById("stable")!!.amountMinor)
        assertEquals("2025-01-02", repository.getExpenseById("stable")!!.date)
        assertEquals(1, db.pendingChangesDao().pendingCount(uid))
        repository.deleteExpense("stable")
        assertNull(repository.getExpenseById("stable"))
        assertEquals("PENDING_DELETE", db.expenseDao().pending(uid).single().syncStatus)
    }

    @Test fun accountChangeStopsOldUserSavingsQueueBeforeTheNextRecord() = runBlocking {
        db.savingsGoalDao().upsertGoal(goal().toEntity(SyncStatus.PENDING_UPDATE))
        db.savingsGoalDao().upsertGoal(goal().copy(id = "second", status = GoalStatus.ARCHIVED).toEntity(SyncStatus.PENDING_UPDATE))
        remote.onGoal = { auth.users.value = AuthUser("other", "Other", "other@example.test", true) }
        assertTrue(savings.syncPendingGoals().isSuccess)
        assertEquals(1, remote.goals.size)
        assertEquals(1, db.savingsGoalDao().getPendingGoals(uid).size)
        assertEquals(0, db.pendingChangesDao().pendingCount("other"))
    }

    @Test fun oldCachedSalarySnapshotCannotRollBackConfirmationOrAmount() = runBlocking {
        val confirmed = SalaryTemplateEntity("salary", uid, amountMinor = 250000, lastConfirmedMonth = "2026-10", updatedAt = 200)
        db.salaryTemplateDao().upsertSalaryTemplate(confirmed)
        db.salaryTemplateDao().reconcile(uid, listOf(confirmed.copy(amountMinor = 100000,
            lastConfirmedMonth = "2026-09", updatedAt = 100)), authoritative = false)
        assertEquals(confirmed, db.salaryTemplateDao().getSalaryTemplate(uid))
        val newer = confirmed.copy(amountMinor = 300000, updatedAt = 300)
        db.salaryTemplateDao().reconcile(uid, listOf(newer), authoritative = true)
        assertEquals(newer, db.salaryTemplateDao().getSalaryTemplate(uid))
    }

    @Test fun salaryDeletionAcknowledgementKeepsAReplacementTemplateForTheSameUser() = runBlocking {
        val deleted = SalaryTemplateEntity("old-salary", uid, amountMinor = 100000, isDeleted = true,
            syncStatus = SyncStatus.PENDING_DELETE, updatedAt = 100)
        val replacement = SalaryTemplateEntity("replacement-salary", uid, amountMinor = 250000,
            syncStatus = SyncStatus.PENDING_INSERT, updatedAt = 200)
        db.salaryTemplateDao().upsertSalaryTemplate(deleted)
        db.salaryTemplateDao().upsertSalaryTemplate(replacement)
        db.salaryTemplateDao().completeSync(deleted)
        assertEquals(replacement, db.salaryTemplateDao().getSalaryTemplate(uid))
        assertEquals(listOf(replacement), db.salaryTemplateDao().getPendingSync(uid))
    }

    @Test fun queueSnapshotSkipsAnEntryAlreadyEditedAndSyncedWhileAnotherEntryUploads() = runBlocking {
        val originals = listOf(goal().toEntity(SyncStatus.PENDING_UPDATE),
            goal().copy(id = "other", status = GoalStatus.ARCHIVED).toEntity(SyncStatus.PENDING_UPDATE))
        originals.forEach { db.savingsGoalDao().upsertGoal(it) }
        var writes = 0
        remote.onGoal = { first ->
            writes++
            val other = originals.single { it.id != first.id }
            val edited = other.copy(name = "Newer acknowledged edit", updatedAt = other.updatedAt + 1, syncStatus = SyncStatus.SYNCED)
            db.savingsGoalDao().upsertGoal(edited)
            remote.goals[other.id] = goal().copy(id = other.id, name = edited.name)
        }
        assertTrue(savings.syncPendingGoals().isSuccess)
        assertEquals(1, writes)
        assertEquals(1, remote.goals.values.count { it.name == "Newer acknowledged edit" })
        assertEquals(0, db.pendingChangesDao().pendingCount(uid))
    }

    private class SavingsRemote : SavingsGoalRemoteDataSource {
        val goals = linkedMapOf<String, SavingsGoal>()
        val transactions = linkedMapOf<String, GoalTransaction>()
        var onGoal: (suspend (SavingsGoal) -> Unit)? = null
        var onTransaction: (suspend (GoalTransaction) -> Unit)? = null
        var onDeleteGoal: (suspend () -> Unit)? = null
        var onDeleteTransaction: (suspend () -> Unit)? = null
        override suspend fun upsertGoal(userId: String, goal: SavingsGoal): Result<Unit> { onGoal?.invoke(goal); goals[goal.id] = goal; return Result.success(Unit) }
        override suspend fun deleteGoal(userId: String, goalId: String): Result<Unit> { onDeleteGoal?.invoke(); goals.remove(goalId); return Result.success(Unit) }
        override suspend fun fetchGoals(userId: String) = Result.success(goals.values.toList())
        override suspend fun fetchGoalById(userId: String, goalId: String) = Result.success(goals[goalId])
        override suspend fun upsertTransaction(userId: String, transaction: GoalTransaction): Result<Unit> { onTransaction?.invoke(transaction); transactions[transaction.id] = transaction; return Result.success(Unit) }
        override suspend fun deleteTransaction(userId: String, transactionId: String): Result<Unit> { onDeleteTransaction?.invoke(); transactions.remove(transactionId); return Result.success(Unit) }
        override suspend fun fetchTransactionsForGoal(userId: String, goalId: String) = Result.success(transactions.values.filter { it.goalId == goalId })
    }

    private object UnusedIncomeRemote : IncomeRemoteDataSource {
        override fun observeIncome(userId: String): Flow<List<Income>> = emptyFlow()
        override suspend fun getAllIncome(userId: String) = emptyList<Income>()
        override suspend fun getIncomeById(userId: String, incomeId: String): Income? = null
        override suspend fun upsertIncome(userId: String, income: Income): Unit = error("Unexpected network write")
        override suspend fun deleteIncome(userId: String, incomeId: String): Unit = error("Unexpected network write")
    }
}

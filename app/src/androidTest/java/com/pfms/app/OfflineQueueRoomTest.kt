package com.pfms.app

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.pfms.app.data.local.PfmsDatabase
import com.pfms.app.data.local.entity.SyncStatus
import com.pfms.app.data.local.mapper.toDomain
import com.pfms.app.data.remote.firestore.*
import com.pfms.app.data.repository.*
import com.pfms.app.data.sync.*
import com.pfms.app.domain.model.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Test
import java.util.UUID

class OfflineQueueRoomTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val name = "fr35-queue-${UUID.randomUUID()}.db"
    private val auth = ExpenseTestAuth().apply { users.value = AuthUser("fr35-a", "A", "a@example.test", true) }
    private val server = Server()
    private var db = open()
    private fun open() = Room.databaseBuilder(context, PfmsDatabase::class.java, name).build()
    private fun incomeRepo() = OfflineFirstIncomeRepository(db.incomeDao(), server, auth)
    private fun expenseRepo() = OfflineFirstExpenseRepository(db, server, auth, context, ExpenseSyncScheduler {})
    @After fun close() { db.close(); context.deleteDatabase(name) }

    private class Server : IncomeRemoteDataSource, ExpenseRemoteDataSource {
        var online = false
        var loseIncomeAcknowledgement = false
        var afterIncomeWrite: (suspend () -> Unit)? = null
        var afterExpenseWrite: (suspend () -> Unit)? = null
        val incomeRows = mutableMapOf<Pair<String, String>, FinancialSyncResult<Income>>()
        val expenseRows = mutableMapOf<Pair<String, String>, FinancialSyncResult<Expense>>()
        override fun observeIncome(userId: String): Flow<List<Income>> = emptyFlow()
        override fun observe(userId: String): Flow<ExpenseRemoteSnapshot> = emptyFlow()
        override suspend fun getAllIncome(userId: String) = incomeRows.filterKeys { it.first == userId }.values.mapNotNull { it.row }
        override suspend fun getIncomeById(userId: String, incomeId: String) = incomeRows[userId to incomeId]?.row
        override suspend fun upsertIncome(userId: String, income: Income) { error("Use versioned synchronization") }
        override suspend fun deleteIncome(userId: String, incomeId: String) { error("Use versioned synchronization") }
        override suspend fun insert(userId: String, expense: Expense, allowDuplicate: Boolean) { error("Use versioned synchronization") }
        override suspend fun update(userId: String, expense: Expense) { error("Use versioned synchronization") }
        override suspend fun delete(userId: String, id: String) { error("Use versioned synchronization") }
        override suspend fun synchronize(userId: String, income: Income, version: SyncVersion): FinancialSyncResult<Income> {
            check(online) { "Offline" }
            val key = userId to income.id
            val old = incomeRows[key]
            val winner = if (old != null && old.version >= version) old else FinancialSyncResult(income.takeUnless { version.deleted }, version)
            incomeRows[key] = winner
            afterIncomeWrite?.also { afterIncomeWrite = null }?.invoke()
            if (loseIncomeAcknowledgement) { loseIncomeAcknowledgement = false; error("Connection lost after commit") }
            return winner
        }
        override suspend fun synchronize(userId: String, expense: Expense, version: SyncVersion, insert: Boolean, allowDuplicate: Boolean): FinancialSyncResult<Expense> {
            check(online) { "Offline" }
            val key = userId to expense.id
            val old = expenseRows[key]
            val winner = if (old != null && old.version >= version) old else FinancialSyncResult(expense.takeUnless { version.deleted }, version)
            expenseRows[key] = winner
            afterExpenseWrite?.also { afterExpenseWrite = null }?.invoke()
            return winner
        }
    }

    @Test fun twentyOfflineMutationsSurviveDatabaseRestartAndSyncWithoutDuplicates() = runBlocking {
        var incomes = incomeRepo(); var expenses = expenseRepo()
        repeat(10) { n ->
            incomes.addIncome(Income("i$n", "fr35-a", 10000L + n, receivedDate = "2026-10-20"))
            expenses.addExpense(Expense(id = "e$n", amountMinor = 1000L + n, date = "2026-10-20"))
        }
        incomes.updateIncome(incomes.getIncomeById("i0")!!.copy(amountMinor = 55555))
        expenses.updateExpense(expenses.getExpenseById("e0")!!.copy(amountMinor = 4444))
        incomes.deleteIncome("i9"); expenses.deleteExpense("e9")
        assertEquals(20, db.pendingChangesDao().pendingCount("fr35-a"))
        assertTrue(incomes.syncPendingIncome().isFailure)
        assertFalse(expenses.syncPending())
        db.close(); db = open(); incomes = incomeRepo(); expenses = expenseRepo()
        assertEquals(20, db.pendingChangesDao().pendingCount("fr35-a"))
        assertEquals(55555L, incomes.getIncomeById("i0")!!.amountMinor)
        assertEquals(4444L, expenses.getExpenseById("e0")!!.amountMinor)
        assertNull(incomes.getIncomeById("i9")); assertNull(expenses.getExpenseById("e9"))
        server.online = true
        assertTrue(incomes.syncPendingIncome().isSuccess); assertTrue(expenses.syncPending())
        assertEquals(0, db.pendingChangesDao().pendingCount("fr35-a"))
        assertEquals(10, server.incomeRows.size); assertEquals(10, server.expenseRows.size)
        assertEquals(9, server.incomeRows.values.count { !it.version.deleted })
        assertEquals(9, server.expenseRows.values.count { !it.version.deleted })
        incomes.syncPendingIncome(); expenses.syncPending()
        assertEquals(10, server.incomeRows.size); assertEquals(10, server.expenseRows.size)
    }

    @Test fun lostServerAcknowledgementLeavesDurablePendingRevisionForIdempotentRetry() = runBlocking {
        val incomes = incomeRepo()
        incomes.addIncome(Income("income", "fr35-a", 100))
        val revision = db.incomeDao().getIncomeById("fr35-a", "income")!!.revision
        server.online = true; server.loseIncomeAcknowledgement = true
        assertTrue(incomes.syncPendingIncome().isFailure)
        assertEquals(revision, db.incomeDao().getPendingSync("fr35-a").single().revision)
        assertEquals(1, server.incomeRows.size)
        assertTrue(incomes.syncPendingIncome().isSuccess)
        assertEquals(1, server.incomeRows.size)
        assertEquals(0, db.pendingChangesDao().pendingCount("fr35-a"))
    }

    @Test fun staleAcknowledgementsCannotClearEditsMadeWhileSyncWasInFlight() = runBlocking {
        val incomes = incomeRepo(); val expenses = expenseRepo()
        incomes.addIncome(Income("income", "fr35-a", 100))
        expenses.addExpense(Expense(id = "expense", amountMinor = 200))
        server.online = true
        server.afterIncomeWrite = { incomes.updateIncome(incomes.getIncomeById("income")!!.copy(amountMinor = 999)) }
        server.afterExpenseWrite = { expenses.updateExpense(expenses.getExpenseById("expense")!!.copy(amountMinor = 888)) }
        incomes.syncPendingIncome(); expenses.syncPending()
        assertEquals(2, db.pendingChangesDao().pendingCount("fr35-a"))
        assertEquals(999L, incomes.getIncomeById("income")!!.amountMinor)
        assertEquals(888L, expenses.getExpenseById("expense")!!.amountMinor)
        incomes.syncPendingIncome(); expenses.syncPending()
        assertEquals(0, db.pendingChangesDao().pendingCount("fr35-a"))
        assertEquals(999L, server.incomeRows["fr35-a" to "income"]!!.row!!.amountMinor)
        assertEquals(888L, server.expenseRows["fr35-a" to "expense"]!!.row!!.amountMinor)
    }

    @Test fun newerRemoteVersionWinsAndADeletionCannotBeResurrectedByAnOlderQueue() = runBlocking {
        val incomes = incomeRepo(); val expenses = expenseRepo()
        incomes.addIncome(Income("income", "fr35-a", 100))
        expenses.addExpense(Expense(id = "expense", amountMinor = 200))
        val future = System.currentTimeMillis() + 60000
        server.incomeRows["fr35-a" to "income"] = FinancialSyncResult(Income("income", "fr35-a", 999, updatedAt = future), SyncVersion(future, "newer"))
        server.expenseRows["fr35-a" to "expense"] = FinancialSyncResult(null, SyncVersion(future, "deleted", true))
        server.online = true
        incomes.syncPendingIncome(); expenses.syncPending()
        assertEquals(999L, incomes.getIncomeById("income")!!.amountMinor)
        assertNull(expenses.getExpenseById("expense"))
        assertEquals(0, db.pendingChangesDao().pendingCount("fr35-a"))
    }

    @Test fun sameTransactionIdForTwoUsersCannotOverwriteOrExposeAnotherUsersCache() = runBlocking {
        val incomes = incomeRepo(); val expenses = expenseRepo()
        incomes.addIncome(Income("same-id", "fr35-a", 111))
        expenses.addExpense(Expense(id = "same-id", amountMinor = 222))
        auth.users.value = AuthUser("fr35-b", "B", "b@example.test", true)
        assertTrue(incomes.observeIncome().first().isEmpty())
        assertTrue(expenses.observeExpenses().first().isEmpty())
        assertEquals(0, db.pendingChangesDao().pendingCount("fr35-b"))
        incomes.addIncome(Income("same-id", "fr35-b", 333))
        assertEquals(333L, incomes.getIncomeById("same-id")!!.amountMinor)
        server.online = true; incomes.syncPendingIncome()
        assertFalse(server.incomeRows.containsKey("fr35-a" to "same-id"))
        auth.users.value = AuthUser("fr35-a", "A", "a@example.test", true)
        assertEquals(111L, incomes.getIncomeById("same-id")!!.amountMinor)
        assertEquals(222L, expenses.getExpenseById("same-id")!!.amountMinor)
        assertEquals(2, db.pendingChangesDao().pendingCount("fr35-a"))
    }
}

package com.pfms.app

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.pfms.app.data.local.*
import com.pfms.app.data.local.entity.IncomeEntity
import com.pfms.app.data.remote.firestore.*
import com.pfms.app.data.repository.*
import com.pfms.app.data.sync.*
import com.pfms.app.domain.model.*
import com.pfms.app.domain.usecase.*
import com.pfms.app.domain.usecase.dashboard.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Test
import java.time.*
import java.util.UUID

class RecordingContextRoomTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val name = "fr37-${UUID.randomUUID()}.db"
    private val uid = "fr37-${UUID.randomUUID()}"
    private val auth = ExpenseTestAuth().apply { users.value = AuthUser(uid, "Test", "test@example.test", true) }
    private val preferences = FinancialContextPreferences(context)
    private val today = LocalDate.of(2026, 10, 20)
    private val clock = Clock.fixed(today.atStartOfDay(AppClock.zone).toInstant(), AppClock.zone)
    private val month = YearMonth.of(2026, 9)
    private var db = open()
    private val remote = Server()
    private fun open() = Room.databaseBuilder(context, PfmsDatabase::class.java, name).build()
    private fun repository() = RoomFinancialContextRepository(db, auth, preferences, clock)
    private fun incomes() = OfflineFirstIncomeRepository(db.incomeDao(), remote, auth)
    private fun expenses() = OfflineFirstExpenseRepository(db, remote, auth, context, ExpenseSyncScheduler {})
    private suspend fun snapshot(repo: RoomFinancialContextRepository = repository()) =
        withTimeout(5_000) { repo.observeMonth(month).first { it.isReady } }
    @After fun close() { db.close(); context.deleteDatabase(name) }

    @Test fun backdatedOfflineEntriesRetainDatesRatesAndActualCreationAcrossRestartAndSync() = runBlocking {
        val before = System.currentTimeMillis()
        incomes().addIncome(Income("income", uid, 10000, "USD", receivedDate = "2026-09-15",
            amountLkrMinor = 3002500, exchangeRate = "300.25", exchangeRateDate = "2026-09-15"))
        expenses().addExpense(Expense(id = "expense", amountMinor = 25000, date = "2025-09-20"))
        assertEquals(2, db.pendingChangesDao().pendingCount(uid))
        assertTrue(incomes().syncPendingIncome().isFailure)
        assertFalse(expenses().syncPending())
        db.close(); db = open()
        val income = incomes().getIncomeById("income")!!
        val expense = expenses().getExpenseById("expense")!!
        assertEquals("2026-09-15", income.receivedDate)
        assertEquals("2025-09-20", expense.date)
        assertTrue(income.createdAt >= before); assertTrue(expense.createdAt >= before)
        assertEquals(3002500L, MonthlyFinanceCalculator.summarize(listOf(income), listOf(expense), month).totalIncomeMinor)
        assertEquals(25000L, MonthlyFinanceCalculator.summarize(listOf(income), listOf(expense), month.minusYears(1)).totalExpenseMinor)
        remote.online = true
        assertTrue(incomes().syncPendingIncome().isSuccess); assertTrue(expenses().syncPending())
        assertEquals(0, db.pendingChangesDao().pendingCount(uid))
        assertEquals(income, remote.income)
        assertEquals(expense, remote.expense)
        assertEquals("300.25", remote.income!!.exchangeRate)
        assertEquals("2026-09-15", remote.income!!.exchangeRateDate)
        incomes().syncPendingIncome(); expenses().syncPending()
        assertEquals(2, remote.writes)
        incomes().updateIncome(income.copy(receivedDate = "2026-08-31"))
        assertEquals(0L, MonthlyFinanceCalculator.summarize(snapshot().incomes, emptyList(), month).totalIncomeMinor)
        assertEquals(income.createdAt, incomes().getIncomeById("income")!!.createdAt)
    }

    @Test fun reviewPersistsAcrossDatabaseRestartAndFinancialEditsInvalidateIt() = runBlocking {
        incomes().addIncome(Income("income", uid, 10000, receivedDate = "2026-09-15"))
        var repo = repository()
        val signature = MonthlyDataPolicy.assess(snapshot(repo), today).signature
        assertTrue(repo.reviewMonth(uid, month, signature).isSuccess)
        db.close(); db = open(); repo = repository()
        assertEquals(MonthlyDataReason.REVIEWED, MonthlyDataPolicy.assess(snapshot(repo), today).reason)
        incomes().updateIncome(incomes().getIncomeById("income")!!.copy(amountMinor = 20000))
        assertEquals(MonthlyDataReason.UNREVIEWED, MonthlyDataPolicy.assess(snapshot(repo), today).reason)
        assertTrue(repo.reviewMonth(uid, month, signature).isFailure)
    }

    @Test fun newUserEmptyMonthReviewAndDismissalNeverLeakAcrossAccounts() = runBlocking {
        val repo = repository()
        assertNull(repo.observeWelcomeActivity().first { it.userId == uid && !it.dismissed }.lastRecordedAt)
        val empty = snapshot(repo)
        assertEquals(MonthlyDataReason.NO_ENTRIES, MonthlyDataPolicy.assess(empty, today).reason)
        assertTrue(repo.reviewMonth(uid, month, MonthlyDataPolicy.assess(empty, today).signature).isSuccess)
        repo.dismissWelcome()
        assertTrue(repo.observeWelcomeActivity().first { it.userId == uid && it.dismissed }.dismissed)
        val other = "$uid-other"
        auth.users.value = AuthUser(other, "Other", "other@example.test", true)
        val otherRecords = snapshot(repo)
        assertEquals(other, otherRecords.userId)
        assertNull(otherRecords.reviewedSignature); assertTrue(otherRecords.incomes.isEmpty())
        assertEquals(MonthlyDataReason.NO_ENTRIES, MonthlyDataPolicy.assess(otherRecords, today).reason)
        assertNull(repo.observeWelcomeActivity().first { it.userId == other && !it.dismissed }.lastRecordedAt)
        assertTrue(repo.reviewMonth(uid, month, MonthlyDataPolicy.assess(empty, today).signature).isFailure)
    }

    @Test fun inactivityUsesLoggingTimeNotHistoricalDateOrSyncAndRemembersRemovedCache() = runBlocking {
        val logged = today.minusDays(7).atTime(23, 59).atZone(AppClock.zone).toInstant().toEpochMilli()
        db.incomeDao().upsertIncome(IncomeEntity("old", uid, 100, receivedDate = "2020-01-01",
            createdAt = logged, updatedAt = clock.millis()))
        val repo = repository()
        val activity = withTimeout(5_000) { repo.observeWelcomeActivity().first { it.lastRecordedAt == logged && !it.dismissed } }
        assertTrue(WelcomeBackEligibility.isEligible(activity.lastRecordedAt, today))
        // onEach persists activity before emitting, so cache removal cannot erase it.
        db.incomeDao().clearAllForUser(uid)
        assertEquals(logged, withTimeout(5_000) { repository().observeWelcomeActivity().first { it.lastRecordedAt != null } }.lastRecordedAt)
        db.incomeDao().upsertIncome(IncomeEntity("backdated-now", uid, 100, receivedDate = "2020-01-01",
            createdAt = clock.millis(), updatedAt = clock.millis()))
        val recent = withTimeout(5_000) { repo.observeWelcomeActivity().first { it.lastRecordedAt == clock.millis() } }
        assertFalse(WelcomeBackEligibility.isEligible(recent.lastRecordedAt, today))
    }

    private class Server : IncomeRemoteDataSource, ExpenseRemoteDataSource {
        var online = false
        var income: Income? = null
        var expense: Expense? = null
        var writes = 0
        override fun observeIncome(userId: String): Flow<List<Income>> = emptyFlow()
        override fun observe(userId: String): Flow<ExpenseRemoteSnapshot> = emptyFlow()
        override suspend fun getAllIncome(userId: String) = listOfNotNull(income)
        override suspend fun getIncomeById(userId: String, incomeId: String) = income
        override suspend fun upsertIncome(userId: String, income: Income) { error("Use synchronization") }
        override suspend fun deleteIncome(userId: String, incomeId: String) { error("Use synchronization") }
        override suspend fun insert(userId: String, expense: Expense, allowDuplicate: Boolean) { error("Use synchronization") }
        override suspend fun update(userId: String, expense: Expense) { error("Use synchronization") }
        override suspend fun delete(userId: String, id: String) { error("Use synchronization") }
        override suspend fun synchronize(userId: String, income: Income, version: SyncVersion): FinancialSyncResult<Income> {
            check(online) { "Offline" }; writes++; this.income = income
            return FinancialSyncResult(income, version)
        }
        override suspend fun synchronize(userId: String, expense: Expense, version: SyncVersion, insert: Boolean, allowDuplicate: Boolean): FinancialSyncResult<Expense> {
            check(online) { "Offline" }; writes++; this.expense = expense
            return FinancialSyncResult(expense, version)
        }
    }
}

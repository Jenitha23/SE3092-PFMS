package com.pfms.app

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.pfms.app.data.local.PfmsDatabase
import com.pfms.app.data.remote.firestore.ExpenseRemoteDataSource
import com.pfms.app.data.remote.firestore.ExpenseRemoteSnapshot
import com.pfms.app.data.repository.OfflineFirstExpenseRepository
import com.pfms.app.data.sync.ExpenseSyncScheduler
import com.pfms.app.domain.model.*
import com.pfms.app.domain.usecase.DetectSpendingPatternsUseCase
import com.pfms.app.domain.usecase.ObserveSpendingPatternsUseCase
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.*
import org.junit.*
import org.junit.Assert.*
import java.io.IOException
import java.time.LocalDate
import java.time.YearMonth
import java.util.UUID

/** Isolated Room data and failing remote transport exercise the production offline path. */
class SpendingPatternsOfflineTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val name = "patterns-${UUID.randomUUID()}.db"
    private lateinit var db: PfmsDatabase
    private val auth = ExpenseTestAuth()
    private val remote = object : ExpenseRemoteDataSource {
        override fun observe(userId: String): Flow<ExpenseRemoteSnapshot> = flow { throw IOException("Offline") }
        override suspend fun insert(userId: String, expense: Expense, allowDuplicate: Boolean) { throw IOException("Offline") }
        override suspend fun update(userId: String, expense: Expense) { throw IOException("Offline") }
        override suspend fun delete(userId: String, id: String) { throw IOException("Offline") }
    }
    private fun open() = Room.databaseBuilder(context, PfmsDatabase::class.java, name).build()
    private fun repository() = OfflineFirstExpenseRepository(db, remote, auth, context, ExpenseSyncScheduler {})
    private fun expense(id: String, date: String, amount: Long) = Expense(id = id, categoryId = "food",
        categoryName = "Food & Dining", date = date, amountMinor = amount)
    @Before fun setup() { db = open() }
    @After fun cleanup() { db.close(); context.deleteDatabase(name) }

    @Test fun offlineRoomChangesAndReopenKeepPatternsCurrent() = runBlocking<Unit> {
        var repository = repository()
        repository.addExpense(expense("aug", "2026-08-10", 1000000))
        repository.addExpense(expense("sep", "2026-09-10", 1000000))
        val now = expense("current", "2026-10-10", 1500000)
        val results = Channel<SpendingPatternResult>(Channel.UNLIMITED)
        val months = setOf(YearMonth.of(2026, 8), YearMonth.of(2026, 9))
        fun observe() = launch(Dispatchers.Default) {
            ObserveSpendingPatternsUseCase(repository, DetectSpendingPatternsUseCase())
                .observe(LocalDate.of(2026, 10, 15), months).collect { results.send(it) }
        }
        suspend fun next(matches: (SpendingPatternResult) -> Boolean): SpendingPatternResult = withTimeout(5000) {
            var value = results.receive()
            while (!matches(value)) value = results.receive()
            value
        }
        var observer = observe()
        // Wait for actual cached history before testing additions, not the repository's initial empty emission.
        withTimeout(5000) { repository.observeExpenses().first { it.size == 2 } }
        repository.addExpense(now)
        assertEquals(50, next { it.insights.isNotEmpty() }.insights.single().increasePercent)
        repository.updateExpense(now.copy(amountMinor = 1100000))
        assertTrue(next { it.insights.isEmpty() }.insights.isEmpty())
        repository.updateExpense(now.copy(amountMinor = 1400000))
        assertEquals(40, next { it.insights.isNotEmpty() }.insights.single().increasePercent)
        observer.cancelAndJoin()
        while (results.tryReceive().isSuccess) { /* Discard old observer emissions before reopening. */ }
        db.close(); db = open(); repository = repository()
        observer = observe()
        assertEquals(1400000L, next { it.insights.isNotEmpty() }.insights.single().currentAmountMinor)
        repository.deleteExpense(now.id)
        assertTrue(next { it.insights.isEmpty() }.insights.isEmpty())
        assertEquals(2, withTimeout(5000) { repository.observeExpenses().first { it.size == 2 } }.size)
        observer.cancelAndJoin()
        results.close()
    }
}

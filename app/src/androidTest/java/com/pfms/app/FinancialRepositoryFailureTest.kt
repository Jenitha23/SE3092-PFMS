package com.pfms.app

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.google.firebase.firestore.FirebaseFirestoreException
import com.pfms.app.data.local.PfmsDatabase
import com.pfms.app.data.local.entity.SyncStatus
import com.pfms.app.data.remote.firestore.*
import com.pfms.app.data.repository.*
import com.pfms.app.data.sync.SyncIssueStore
import com.pfms.app.domain.model.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import org.junit.Assert.*
import org.junit.Test

class FinancialRepositoryFailureTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()

    @Test fun firestoreUnavailableKeepsRoomSaveAndPendingStatusUntilRetrySucceeds() = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(context, PfmsDatabase::class.java).build()
        try {
            val auth = ExpenseTestAuth()
            val uid = auth.users.value!!.uid
            var unavailable = true
            val server = linkedMapOf<String, Income>()
            val remote = object : IncomeRemoteDataSource {
                override fun observeIncome(userId: String): Flow<List<Income>> = emptyFlow()
                override suspend fun getAllIncome(userId: String) = server.values.toList()
                override suspend fun getIncomeById(userId: String, incomeId: String) = server[incomeId]
                override suspend fun upsertIncome(userId: String, income: Income) {
                    if (unavailable) throw FirebaseFirestoreException("internal private server path", FirebaseFirestoreException.Code.UNAVAILABLE)
                    server[income.id] = income
                }
                override suspend fun deleteIncome(userId: String, incomeId: String) { server.remove(incomeId) }
            }
            val issues = SyncIssueStore()
            val repository = OfflineFirstIncomeRepository(db.incomeDao(), remote, auth).also { it.issues = issues }
            repository.addIncome(Income(id = "stable", userId = uid, amountMinor = 12345, receivedDate = "2026-10-01"))
            assertEquals(1, db.pendingChangesDao().pendingCount(uid))
            assertTrue(repository.syncPendingIncome().isFailure)
            assertEquals(12345L, repository.getIncomeById("stable")?.amountMinor)
            val error = issues.observe(uid).first()!!
            assertTrue(error.canRetry)
            assertFalse(error.message.contains("private"))
            val connectivity = object : com.pfms.app.domain.repository.ConnectivityObserver {
                override val isOnline = MutableStateFlow(true)
            }
            val status = OfflineSyncStatusRepository(db, auth, connectivity).also { it.issues = issues }
                .status.first { it.pendingChanges == 1 && it.errorMessage != null }
            assertTrue(status.canRetry)
            unavailable = false
            assertTrue(repository.syncPendingIncome().isSuccess)
            assertTrue(repository.syncPendingIncome().isSuccess)
            assertEquals(1, server.size)
            assertEquals(0, db.pendingChangesDao().pendingCount(uid))
            assertNull(issues.observe(uid).first())
        } finally { db.close() }
    }

    @Test fun stalledSavingsNetworkWriteStillReturnsSuccessfulDurableLocalSave() = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(context, PfmsDatabase::class.java).build()
        try {
            val auth = ExpenseTestAuth()
            val uid = auth.users.value!!.uid
            val remote = object : SavingsGoalRemoteDataSource {
                override suspend fun upsertGoal(userId: String, goal: SavingsGoal): Result<Unit> = awaitCancellation()
                override suspend fun deleteGoal(userId: String, goalId: String) = Result.success(Unit)
                override suspend fun fetchGoals(userId: String) = Result.success(emptyList<SavingsGoal>())
                override suspend fun fetchGoalById(userId: String, goalId: String) = Result.success<SavingsGoal?>(null)
                override suspend fun upsertTransaction(userId: String, transaction: GoalTransaction) = Result.success(Unit)
                override suspend fun deleteTransaction(userId: String, transactionId: String) = Result.success(Unit)
                override suspend fun fetchTransactionsForGoal(userId: String, goalId: String) = Result.success(emptyList<GoalTransaction>())
            }
            val repository = OfflineFirstSavingsGoalRepository(db.savingsGoalDao(), db.goalTransactionDao(), remote, auth)
            val goal = SavingsGoal(id = "stable-goal", userId = uid, name = "Emergency fund", targetAmountMinor = 100000,
                targetDate = "2027-10-01")
            assertTrue(withTimeout(5_000) { repository.createGoal(goal) }.isSuccess)
            assertEquals(SyncStatus.PENDING_INSERT, db.savingsGoalDao().getGoalById(uid, goal.id)?.syncStatus)
            // A lost local acknowledgement can be retried with the same goal ID.
            assertTrue(withTimeout(5_000) { repository.createGoal(goal) }.isSuccess)
            assertEquals(1, db.pendingChangesDao().pendingCount(uid))
            assertEquals("Emergency fund", repository.observeActiveGoal().first()?.name)
        } finally { db.close() }
    }
}

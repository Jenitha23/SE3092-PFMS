package com.pfms.app

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.pfms.app.data.local.PfmsDatabase
import com.pfms.app.data.local.mapper.toEntity
import com.pfms.app.data.remote.firestore.SavingsGoalRemoteDataSource
import com.pfms.app.data.repository.OfflineFirstSavingsGoalRepository
import com.pfms.app.domain.model.*
import com.pfms.app.domain.usecase.savings.CalculateSavedAmountUseCase
import com.pfms.app.domain.usecase.savings.InsufficientGoalSavingsException
import com.pfms.app.domain.usecase.savings.ValidateGoalLedgerUseCase
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*

/** Exercises the real guarded Room writes, independently of frontend validation. */
class SavingsWithdrawalPersistenceTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private lateinit var db: PfmsDatabase
    private val auth = ExpenseTestAuth()
    private val uid = "offline-test-a"
    private lateinit var repository: OfflineFirstSavingsGoalRepository
    private var remoteWrites = 0
    private val remote = object : SavingsGoalRemoteDataSource {
        override suspend fun upsertGoal(userId: String, goal: SavingsGoal) = Result.failure<Unit>(java.io.IOException("Offline"))
        override suspend fun deleteGoal(userId: String, goalId: String) = Result.failure<Unit>(java.io.IOException("Offline"))
        override suspend fun fetchGoals(userId: String) = Result.success(emptyList<SavingsGoal>())
        override suspend fun fetchGoalById(userId: String, goalId: String) = Result.success<SavingsGoal?>(null)
        override suspend fun fetchTransactionsForGoal(userId: String, goalId: String) = Result.success(emptyList<GoalTransaction>())
        override suspend fun upsertTransaction(userId: String, transaction: GoalTransaction): Result<Unit> {
            remoteWrites++
            return Result.failure(java.io.IOException("Offline"))
        }
        override suspend fun deleteTransaction(userId: String, transactionId: String): Result<Unit> {
            remoteWrites++
            return Result.failure(java.io.IOException("Offline"))
        }
    }
    private val goal = SavingsGoal("goal", uid, "Emergency fund", 49000000, "2027-10-25", 1120000)
    private val deposit = GoalTransaction("deposit", goal.id, uid, 1500000, GoalTransactionType.CONTRIBUTION, "2026-10-01", createdAt = 1)
    private val withdrawal = GoalTransaction("withdrawal", goal.id, uid, 500000, GoalTransactionType.WITHDRAWAL, "2026-10-02", createdAt = 2)
    private fun newWithdrawal(amount: Long) = GoalTransaction("new", goal.id, uid, amount, GoalTransactionType.WITHDRAWAL, "2026-10-25", createdAt = 3)

    @Before fun setup() = runBlocking {
        db = Room.inMemoryDatabaseBuilder(context, PfmsDatabase::class.java).build()
        repository = OfflineFirstSavingsGoalRepository(db.savingsGoalDao(), db.goalTransactionDao(), remote, auth)
        db.savingsGoalDao().upsertGoal(goal.toEntity())
        // Existing confirmed ledger fixture; contribution funding policy is unchanged.
        db.goalTransactionDao().upsertAll(listOf(deposit.toEntity(), withdrawal.toEntity()))
    }
    @After fun cleanup() { db.close() }
    private suspend fun balance(): Long {
        val transactions = repository.observeTransactions(goal.id).first()
        val validated = ValidateGoalLedgerUseCase.validate(goal.openingSavedAmountMinor, transactions)
        assertEquals(validated, CalculateSavedAmountUseCase()(goal, transactions))
        return validated
    }

    @Test fun overdraftDoesNotChangeDatabasePendingQueueBalanceOrRemoteAndCorrectionSucceeds() = runBlocking {
        val before = db.goalTransactionDao().getTransactionsForGoal(uid, goal.id)
        val result = repository.addTransaction(newWithdrawal(2500000))
        val error = result.exceptionOrNull() as InsufficientGoalSavingsException
        assertEquals(2120000L, error.availableMinor)
        assertEquals(2500000L, error.requestedMinor)
        assertEquals(before, db.goalTransactionDao().getTransactionsForGoal(uid, goal.id))
        assertTrue(db.goalTransactionDao().getPendingTransactions(uid).isEmpty())
        assertEquals(2120000L, balance())
        assertEquals(0, remoteWrites)
        assertTrue(repository.addTransaction(newWithdrawal(150075)).isSuccess)
        assertEquals(1969925L, balance())
        assertEquals(3, repository.observeTransactions(goal.id).first().size)
    }

    @Test fun exactBalanceCanBeWithdrawnButZeroAndNegativeAmountsNeverWrite() = runBlocking {
        assertTrue(repository.addTransaction(newWithdrawal(0)).isFailure)
        assertThrows(IllegalArgumentException::class.java) { newWithdrawal(-1) }
        assertEquals(2120000L, balance())
        assertEquals(0, remoteWrites)
        assertTrue(repository.addTransaction(newWithdrawal(2120000)).isSuccess)
        assertEquals(0L, balance())
    }

    @Test fun editExcludesTheOriginalAndAnInvalidEditLeavesTheStoredWithdrawalUntouched() = runBlocking {
        val before = db.goalTransactionDao().getTransactionsForGoal(uid, goal.id)
        assertTrue(repository.updateTransaction(withdrawal.copy(amountMinor = 3000000)).isFailure)
        assertEquals(before, db.goalTransactionDao().getTransactionsForGoal(uid, goal.id))
        assertEquals(0, remoteWrites)
        assertTrue(repository.updateTransaction(withdrawal.copy(amountMinor = 2500000)).isSuccess)
        assertEquals(120000L, balance())
        assertEquals(2, repository.observeTransactions(goal.id).first().size)
        assertEquals(withdrawal.createdAt, db.goalTransactionDao().getTransactionById(uid, withdrawal.id)?.createdAt)
    }

    @Test fun deletingAWithdrawalRestoresItsAmountExactlyOnce() = runBlocking {
        assertTrue(repository.deleteTransaction(withdrawal.id).isSuccess)
        assertEquals(2620000L, balance())
        assertNull(db.goalTransactionDao().getTransactionById(uid, withdrawal.id))
        assertTrue(repository.deleteTransaction(withdrawal.id).isFailure)
        assertEquals(2620000L, balance())
    }

    @Test fun backdatedWithdrawalCannotUseALaterContribution() = runBlocking {
        val result = repository.addTransaction(newWithdrawal(1500000).copy(date = "2026-09-30"))
        assertTrue(result.exceptionOrNull() is InsufficientGoalSavingsException)
        assertEquals(2120000L, balance())
        assertEquals(2, repository.observeTransactions(goal.id).first().size)
        assertEquals(0, remoteWrites)
    }
}

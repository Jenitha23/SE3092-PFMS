package com.pfms.app

import android.content.Context
import androidx.lifecycle.ViewModelStore
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import com.pfms.app.data.local.PfmsDatabase
import com.pfms.app.data.local.mapper.toEntity
import com.pfms.app.data.remote.firestore.SavingsGoalRemoteDataSource
import com.pfms.app.data.repository.OfflineFirstSavingsGoalRepository
import com.pfms.app.domain.model.*
import com.pfms.app.domain.usecase.CalculateSavingsSimulationUseCase
import com.pfms.app.domain.usecase.savings.CalculateSavedAmountUseCase
import com.pfms.app.viewmodel.SavingsSimulatorViewModel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.*
import org.junit.Assert.*
import java.io.IOException
import java.time.LocalDate

/** The real simulator and repository must not alter isolated Room records or call remote writes. */
class SavingsSimulatorReadOnlyTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private lateinit var db: PfmsDatabase
    private val auth = ExpenseTestAuth()
    private val store = ViewModelStore()
    private var remoteWrites = 0
    private var remoteReads = 0
    private lateinit var vm: SavingsSimulatorViewModel
    private val remote = object : SavingsGoalRemoteDataSource {
        override suspend fun upsertGoal(userId: String, goal: SavingsGoal): Result<Unit> { remoteWrites++; return Result.failure(IOException("Offline")) }
        override suspend fun deleteGoal(userId: String, goalId: String): Result<Unit> { remoteWrites++; return Result.failure(IOException("Offline")) }
        override suspend fun upsertTransaction(userId: String, transaction: GoalTransaction): Result<Unit> { remoteWrites++; return Result.failure(IOException("Offline")) }
        override suspend fun deleteTransaction(userId: String, transactionId: String): Result<Unit> { remoteWrites++; return Result.failure(IOException("Offline")) }
        override suspend fun fetchGoals(userId: String): Result<List<SavingsGoal>> { remoteReads++; return Result.failure(IOException("Offline")) }
        override suspend fun fetchGoalById(userId: String, goalId: String): Result<SavingsGoal?> { remoteReads++; return Result.failure(IOException("Offline")) }
        override suspend fun fetchTransactionsForGoal(userId: String, goalId: String): Result<List<GoalTransaction>> { remoteReads++; return Result.failure(IOException("Offline")) }
    }
    @Before fun setup() {
        db = Room.inMemoryDatabaseBuilder(context, PfmsDatabase::class.java).build()
        val repository = OfflineFirstSavingsGoalRepository(db.savingsGoalDao(), db.goalTransactionDao(), remote, auth)
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            vm = SavingsSimulatorViewModel(repository, CalculateSavingsSimulationUseCase(CalculateSavedAmountUseCase()))
            vm.dateProvider = { LocalDate.of(2026, 10, 25) }
            store.put("simulator", vm)
        }
    }
    @After fun cleanup() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync { store.clear() }
        db.close()
    }

    @Test fun repeatedScenariosAndRetryNeverCreateOrModifyRealRecords() = runBlocking {
        val uid = auth.users.value!!.uid
        val goal = SavingsGoal("goal", uid, "Laptop", 10000000, "2027-10-25", 1000000)
        val deposit = GoalTransaction("deposit", goal.id, uid, 1500000, GoalTransactionType.CONTRIBUTION, "2026-10-01")
        val withdrawal = GoalTransaction("withdrawal", goal.id, uid, 500000, GoalTransactionType.WITHDRAWAL, "2026-10-02")
        db.savingsGoalDao().upsertGoal(goal.toEntity())
        db.goalTransactionDao().upsertAll(listOf(deposit.toEntity(), withdrawal.toEntity()))
        val beforeGoal = db.savingsGoalDao().getGoalById(uid, goal.id)
        val beforeLedger = db.goalTransactionDao().getTransactionsForGoal(uid, goal.id)
        val beforePending = db.pendingChangesDao().pendingCount(uid)
        for ((reduction, planned) in listOf(0L to 0L, 100000L to 50000L, 250075L to 0L, 0L to 0L)) {
            vm.setMonthlyReductionMinor(reduction); vm.setPlannedContributionMinor(planned)
            val state = withTimeout(5000) { vm.uiState.first {
                it.result != null && it.result.monthlyReductionMinor == reduction && it.result.monthlyPlannedContributionMinor == planned
            } }
            assertEquals(2000000L, state.result!!.currentSavedMinor)
            assertEquals(beforeGoal, db.savingsGoalDao().getGoalById(uid, goal.id))
            assertEquals(beforeLedger, db.goalTransactionDao().getTransactionsForGoal(uid, goal.id))
        }
        vm.retry()
        withTimeout(5000) { vm.uiState.first { it.result != null } }
        assertEquals(beforeGoal, db.savingsGoalDao().getGoalById(uid, goal.id))
        assertEquals(beforeLedger, db.goalTransactionDao().getTransactionsForGoal(uid, goal.id))
        assertEquals(beforePending, db.pendingChangesDao().pendingCount(uid))
        assertEquals(0, remoteWrites)
        assertEquals(0, remoteReads)
    }
}

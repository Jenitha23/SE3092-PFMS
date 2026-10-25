package com.pfms.app

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.pfms.app.data.local.PfmsDatabase
import com.pfms.app.data.local.entity.SyncStatus
import com.pfms.app.data.local.mapper.toEntity
import com.pfms.app.data.remote.firestore.SavingsGoalRemoteDataSource
import com.pfms.app.data.repository.OfflineFirstSavingsGoalRepository
import com.pfms.app.domain.model.*
import com.pfms.app.domain.usecase.savings.CalculateSavedAmountUseCase
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import org.junit.*
import org.junit.Assert.*
import java.io.IOException
import java.util.UUID

/** Real Room, simulated remote transport and authentication; never touches the user's database. */
class SavingsRestorationTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val name = "savings-restore-${UUID.randomUUID()}.db"
    private lateinit var db: PfmsDatabase
    private lateinit var repository: OfflineFirstSavingsGoalRepository
    private val auth = ExpenseTestAuth()
    private val user = auth.users.value!!
    private val goal = SavingsGoal("restored-goal", user.uid, "Emergency fund", 10000000, "2027-10-25", 1000000)
    private val deposit = GoalTransaction("deposit", goal.id, user.uid, 1500000, GoalTransactionType.CONTRIBUTION, "2026-10-01")
    private val withdrawal = GoalTransaction("withdrawal", goal.id, user.uid, 500000, GoalTransactionType.WITHDRAWAL, "2026-10-02")
    private var ledgerFailure = false
    private var online = true
    private var fetchGate: CompletableDeferred<Unit>? = null
    private var fetchStarted = CompletableDeferred<Unit>()
    private val remote = object : SavingsGoalRemoteDataSource {
        override suspend fun fetchGoals(userId: String): Result<List<SavingsGoal>> {
            fetchStarted.complete(Unit)
            fetchGate?.await()
            return if (!online) Result.failure(IOException("Offline"))
                else Result.success(if (userId == user.uid) listOf(goal) else emptyList())
        }
        override suspend fun fetchTransactionsForGoal(userId: String, goalId: String) =
            if (ledgerFailure || !online) Result.failure(IOException("Ledger unavailable"))
            else Result.success(listOf(deposit, withdrawal))
        override suspend fun upsertGoal(userId: String, goal: SavingsGoal) = Result.success(Unit)
        override suspend fun upsertTransaction(userId: String, transaction: GoalTransaction) = Result.success(Unit)
        override suspend fun deleteGoal(userId: String, goalId: String) = Result.success(Unit)
        override suspend fun deleteTransaction(userId: String, transactionId: String) = Result.success(Unit)
        override suspend fun fetchGoalById(userId: String, goalId: String) = Result.success<SavingsGoal?>(goal)
    }
    private fun open() {
        db = Room.databaseBuilder(context, PfmsDatabase::class.java, name).build()
        repository = OfflineFirstSavingsGoalRepository(db.savingsGoalDao(), db.goalTransactionDao(), remote, auth)
    }
    @Before fun setup() { open() }
    @After fun cleanup() { db.close(); context.deleteDatabase(name) }
    private suspend fun assertRestored() {
        val restored = repository.observeActiveGoal().first()!!
        val ledger = repository.observeTransactions(restored.id).first()
        assertEquals(2, ledger.size)
        assertEquals(2000000L, CalculateSavedAmountUseCase()(restored, ledger))
        assertTrue(db.goalTransactionDao().getPendingTransactions(user.uid).isEmpty())
    }

    @Test fun loginRestoresObservedGoalAndLedgerAndRepeatedRefreshNeverDuplicates() = runBlocking {
        val observed = async { withTimeout(5000) { repository.observeActiveGoal().first { it != null } } }
        assertTrue(repository.refreshRemoteSavingsData().isSuccess)
        assertEquals(goal, observed.await())
        assertRestored()
        repeat(3) { assertTrue(repository.refreshRemoteSavingsData().isSuccess) }
        assertRestored()
        // Reproduce the existing logout cache policy, then sign back into the same account.
        auth.users.value = null
        assertNull(repository.observeActiveGoal().first())
        db.clearAllTables()
        auth.users.value = user
        assertNull(repository.observeActiveGoal().first())
        assertTrue(repository.refreshRemoteSavingsData().isSuccess)
        assertRestored()
    }

    @Test fun offlineRestartUsesRoomAndReconnectRestoresWithoutDuplicates() = runBlocking {
        assertTrue(repository.refreshRemoteSavingsData().isSuccess)
        db.close(); online = false; open()
        assertRestored()
        assertTrue(repository.refreshRemoteSavingsData().isFailure)
        assertRestored()
        assertNotNull(repository.observeRestoration().first().error)
        online = true
        assertTrue(repository.refreshRemoteSavingsData().isSuccess)
        assertNull(repository.observeRestoration().first().error)
        assertRestored()
    }

    @Test fun ledgerFailureIsNotSuccessAndRetryRestoresCompleteBalance() = runBlocking {
        ledgerFailure = true
        assertTrue(repository.refreshRemoteSavingsData().isFailure)
        assertNotNull(repository.observeRestoration().first().error)
        assertNull(repository.observeActiveGoal().first())
        assertTrue(repository.observeTransactions(goal.id).first().isEmpty())
        ledgerFailure = false
        assertTrue(repository.refreshRemoteSavingsData().isSuccess)
        assertRestored()
    }

    @Test fun switchingAccountsDuringFetchCannotPublishThePreviousAccountsData() = runBlocking {
        fetchGate = CompletableDeferred()
        val refresh = async(Dispatchers.Default) { repository.refreshRemoteSavingsData() }
        withTimeout(5000) { fetchStarted.await() }
        assertTrue(repository.observeRestoration().first().isRefreshing)
        auth.users.value = user.copy(uid = "other-user")
        assertFalse(repository.observeRestoration().first().isRefreshing)
        fetchGate!!.complete(Unit)
        assertTrue(refresh.await().isFailure)
        assertNull(repository.observeActiveGoal().first())
        assertTrue(repository.observeTransactions(goal.id).first().isEmpty())
        auth.users.value = user
        assertNull(repository.observeActiveGoal().first())
        assertTrue(repository.refreshRemoteSavingsData().isSuccess)
        assertRestored()
        auth.users.value = user.copy(uid = "other-user")
        assertNull(repository.observeActiveGoal().first())
        assertTrue(repository.observeTransactions(goal.id).first().isEmpty())
        auth.users.value = user
        assertRestored()
    }

    @Test fun restorationPreservesPendingLocalGoalAndLedgerChanges() = runBlocking {
        val edited = goal.copy(name = "Offline edit", updatedAt = 9999)
        db.savingsGoalDao().upsertGoal(edited.toEntity(SyncStatus.PENDING_UPDATE))
        db.goalTransactionDao().upsertTransaction(deposit.copy(amountMinor = 2000000).toEntity(SyncStatus.PENDING_UPDATE))
        db.goalTransactionDao().upsertTransaction(withdrawal.toEntity(SyncStatus.PENDING_DELETE, true))
        assertTrue(repository.refreshRemoteSavingsData().isSuccess)
        assertEquals("Offline edit", repository.observeActiveGoal().first()!!.name)
        val ledger = repository.observeTransactions(goal.id).first()
        assertEquals(1, ledger.size)
        assertEquals(2000000L, ledger.single().amountMinor)
        assertEquals(2, db.goalTransactionDao().getPendingTransactions(user.uid).size)
    }
}

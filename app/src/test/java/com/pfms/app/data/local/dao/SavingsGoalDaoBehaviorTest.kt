package com.pfms.app.data.local.dao

import com.pfms.app.data.local.entity.GoalTransactionEntity
import com.pfms.app.data.local.entity.SavingsGoalEntity
import com.pfms.app.data.local.entity.SyncStatus
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Focused tests verifying SavingsGoalDao and GoalTransactionDao persistence semantics,
 * user isolation, active goal filtering, ledger scoping and ordering, soft/hard deletes,
 * and pending synchronization queries.
 */
class SavingsGoalDaoBehaviorTest {

    private lateinit var savingsGoalDao: FakeSavingsGoalDao
    private lateinit var goalTransactionDao: FakeGoalTransactionDao

    @Before
    fun setUp() {
        savingsGoalDao = FakeSavingsGoalDao()
        goalTransactionDao = FakeGoalTransactionDao()
    }

    // 3. ACTIVE goal retrieval ignores archived/completed goals
    @Test
    fun activeGoalRetrieval_ignoresArchivedAndCompletedGoals() = runTest {
        val activeGoal = SavingsGoalEntity(
            id = "g-active",
            userId = "user-1",
            name = "Active Goal",
            status = "ACTIVE",
            isDeleted = false
        )
        val completedGoal = SavingsGoalEntity(
            id = "g-completed",
            userId = "user-1",
            name = "Completed Goal",
            status = "COMPLETED",
            isDeleted = false
        )
        val archivedGoal = SavingsGoalEntity(
            id = "g-archived",
            userId = "user-1",
            name = "Archived Goal",
            status = "ARCHIVED",
            isDeleted = false
        )

        savingsGoalDao.upsertAll(listOf(completedGoal, archivedGoal, activeGoal))

        val retrieved = savingsGoalDao.getActiveGoal("user-1")
        assertNotNull(retrieved)
        assertEquals("g-active", retrieved?.id)
        assertEquals("ACTIVE", retrieved?.status)

        val observed = savingsGoalDao.observeActiveGoal("user-1").first()
        assertEquals("g-active", observed?.id)
    }

    // 4. deleted goal excluded from observeActiveGoal
    @Test
    fun deletedGoal_excludedFromObserveActiveGoal_and_getActiveGoal() = runTest {
        val deletedGoal = SavingsGoalEntity(
            id = "g-1",
            userId = "user-1",
            name = "Deleted Goal",
            status = "ACTIVE",
            isDeleted = true
        )
        savingsGoalDao.upsertGoal(deletedGoal)

        val retrieved = savingsGoalDao.getActiveGoal("user-1")
        assertNull(retrieved)

        val observed = savingsGoalDao.observeActiveGoal("user-1").first()
        assertNull(observed)
    }

    // 5. user A cannot retrieve user B goal
    @Test
    fun userIsolation_userACannotRetrieveUserBGoal() = runTest {
        val userBGoal = SavingsGoalEntity(
            id = "goal-user-b",
            userId = "user-B",
            name = "User B Secret Goal",
            status = "ACTIVE",
            isDeleted = false
        )
        savingsGoalDao.upsertGoal(userBGoal)

        // User A tries to get active goal
        val activeForUserA = savingsGoalDao.getActiveGoal("user-A")
        assertNull("User A must not observe User B's active goal", activeForUserA)

        // User A tries to get goal by ID
        val directLookup = savingsGoalDao.getGoalById(userId = "user-A", goalId = "goal-user-b")
        assertNull("User A must not access User B's goal by ID", directLookup)
    }

    // 6. transaction ledger scoped by userId + goalId
    @Test
    fun transactionLedger_strictlyScopedByUserIdAndGoalId() = runTest {
        val tx1 = GoalTransactionEntity(
            id = "tx-1",
            goalId = "goal-1",
            userId = "user-1",
            amountMinor = 10_000L,
            date = "2026-10-01"
        )
        val txDifferentGoal = GoalTransactionEntity(
            id = "tx-2",
            goalId = "goal-2",
            userId = "user-1",
            amountMinor = 20_000L,
            date = "2026-10-02"
        )
        val txDifferentUser = GoalTransactionEntity(
            id = "tx-3",
            goalId = "goal-1",
            userId = "user-2",
            amountMinor = 30_000L,
            date = "2026-10-03"
        )

        goalTransactionDao.upsertAll(listOf(tx1, txDifferentGoal, txDifferentUser))

        val result = goalTransactionDao.getTransactionsForGoal("user-1", "goal-1")
        assertEquals(1, result.size)
        assertEquals("tx-1", result[0].id)
    }

    // 7. deleted transactions excluded
    @Test
    fun deletedTransactions_excludedFromLedger() = runTest {
        val activeTx = GoalTransactionEntity(
            id = "tx-active",
            goalId = "goal-1",
            userId = "user-1",
            amountMinor = 5_000L,
            date = "2026-10-01",
            isDeleted = false
        )
        val deletedTx = GoalTransactionEntity(
            id = "tx-deleted",
            goalId = "goal-1",
            userId = "user-1",
            amountMinor = 15_000L,
            date = "2026-10-02",
            isDeleted = true
        )

        goalTransactionDao.upsertAll(listOf(activeTx, deletedTx))

        val list = goalTransactionDao.getTransactionsForGoal("user-1", "goal-1")
        assertEquals(1, list.size)
        assertEquals("tx-active", list[0].id)

        val observed = goalTransactionDao.observeTransactionsForGoal("user-1", "goal-1").first()
        assertEquals(1, observed.size)
        assertEquals("tx-active", observed[0].id)
    }

    // 8. ledger ordering by date/createdAt
    @Test
    fun ledgerOrdering_ordersByDateAsc_thenCreatedAtAsc() = runTest {
        val txDay2 = GoalTransactionEntity(
            id = "tx-day2",
            goalId = "goal-1",
            userId = "user-1",
            amountMinor = 1_000L,
            date = "2026-10-10",
            createdAt = 100L
        )
        val txDay1Later = GoalTransactionEntity(
            id = "tx-day1-later",
            goalId = "goal-1",
            userId = "user-1",
            amountMinor = 2_000L,
            date = "2026-10-01",
            createdAt = 200L
        )
        val txDay1Earlier = GoalTransactionEntity(
            id = "tx-day1-earlier",
            goalId = "goal-1",
            userId = "user-1",
            amountMinor = 3_000L,
            date = "2026-10-01",
            createdAt = 100L
        )

        // Insert in arbitrary order
        goalTransactionDao.upsertAll(listOf(txDay2, txDay1Later, txDay1Earlier))

        val ordered = goalTransactionDao.getTransactionsForGoal("user-1", "goal-1")
        assertEquals(3, ordered.size)
        assertEquals("tx-day1-earlier", ordered[0].id)
        assertEquals("tx-day1-later", ordered[1].id)
        assertEquals("tx-day2", ordered[2].id)
    }

    // 9. pending goal retrieval
    @Test
    fun pendingGoalsRetrieval_returnsOnlyNonSyncedGoalsForUser() = runTest {
        val syncedGoal = SavingsGoalEntity(
            id = "g-synced",
            userId = "user-1",
            name = "Synced",
            syncStatus = SyncStatus.SYNCED
        )
        val pendingInsertGoal = SavingsGoalEntity(
            id = "g-pending-ins",
            userId = "user-1",
            name = "Pending Ins",
            syncStatus = SyncStatus.PENDING_INSERT
        )
        val pendingUpdateGoal = SavingsGoalEntity(
            id = "g-pending-upd",
            userId = "user-1",
            name = "Pending Upd",
            syncStatus = SyncStatus.PENDING_UPDATE
        )
        val otherUserPending = SavingsGoalEntity(
            id = "g-user2",
            userId = "user-2",
            name = "User2 Pending",
            syncStatus = SyncStatus.PENDING_INSERT
        )

        savingsGoalDao.upsertAll(listOf(syncedGoal, pendingInsertGoal, pendingUpdateGoal, otherUserPending))

        val pendingUser1 = savingsGoalDao.getPendingGoals("user-1")
        assertEquals(2, pendingUser1.size)
        val ids = pendingUser1.map { it.id }.toSet()
        assertTrue(ids.contains("g-pending-ins"))
        assertTrue(ids.contains("g-pending-upd"))
    }

    // 10. pending transaction retrieval
    @Test
    fun pendingTransactionsRetrieval_returnsOnlyNonSyncedTransactionsForUser() = runTest {
        val syncedTx = GoalTransactionEntity(
            id = "tx-synced",
            goalId = "goal-1",
            userId = "user-1",
            syncStatus = SyncStatus.SYNCED
        )
        val pendingDeleteTx = GoalTransactionEntity(
            id = "tx-del",
            goalId = "goal-1",
            userId = "user-1",
            isDeleted = true,
            syncStatus = SyncStatus.PENDING_DELETE
        )

        goalTransactionDao.upsertAll(listOf(syncedTx, pendingDeleteTx))

        val pending = goalTransactionDao.getPendingTransactions("user-1")
        assertEquals(1, pending.size)
        assertEquals("tx-del", pending[0].id)
        assertEquals(SyncStatus.PENDING_DELETE, pending[0].syncStatus)
    }

    // 11. mark synced behavior
    @Test
    fun markSyncedBehavior_updatesSyncStatusCorrectly() = runTest {
        val goal = SavingsGoalEntity(
            id = "goal-1",
            userId = "user-1",
            name = "Goal 1",
            syncStatus = SyncStatus.PENDING_INSERT
        )
        savingsGoalDao.upsertGoal(goal)
        assertEquals(1, savingsGoalDao.getPendingGoals("user-1").size)

        savingsGoalDao.markGoalSynced("user-1", "goal-1")
        assertEquals(0, savingsGoalDao.getPendingGoals("user-1").size)
        assertEquals(SyncStatus.SYNCED, savingsGoalDao.getGoalById("user-1", "goal-1")?.syncStatus)

        val tx = GoalTransactionEntity(
            id = "tx-1",
            goalId = "goal-1",
            userId = "user-1",
            syncStatus = SyncStatus.PENDING_UPDATE
        )
        goalTransactionDao.upsertTransaction(tx)
        assertEquals(1, goalTransactionDao.getPendingTransactions("user-1").size)

        goalTransactionDao.markTransactionSynced("user-1", "tx-1")
        assertEquals(0, goalTransactionDao.getPendingTransactions("user-1").size)
        assertEquals(SyncStatus.SYNCED, goalTransactionDao.getTransactionById("user-1", "tx-1")?.syncStatus)
    }

    // 12. hard delete behavior
    @Test
    fun hardDeleteBehavior_permanentlyRemovesRecord() = runTest {
        val goal = SavingsGoalEntity(
            id = "goal-to-remove",
            userId = "user-1",
            name = "Goal",
            status = "ACTIVE"
        )
        savingsGoalDao.upsertGoal(goal)
        assertNotNull(savingsGoalDao.getGoalById("user-1", "goal-to-remove"))

        savingsGoalDao.hardDeleteGoal("user-1", "goal-to-remove")
        assertNull(savingsGoalDao.getGoalById("user-1", "goal-to-remove"))

        val tx = GoalTransactionEntity(
            id = "tx-to-remove",
            goalId = "goal-1",
            userId = "user-1"
        )
        goalTransactionDao.upsertTransaction(tx)
        assertNotNull(goalTransactionDao.getTransactionById("user-1", "tx-to-remove"))

        goalTransactionDao.hardDeleteTransaction("user-1", "tx-to-remove")
        assertNull(goalTransactionDao.getTransactionById("user-1", "tx-to-remove"))
    }

    // Soft delete behavior
    @Test
    fun softDeleteBehavior_marksDeletedAndPendingDelete() = runTest {
        val goal = SavingsGoalEntity(
            id = "goal-soft",
            userId = "user-1",
            name = "Goal",
            status = "ACTIVE",
            isDeleted = false,
            syncStatus = SyncStatus.SYNCED
        )
        savingsGoalDao.upsertGoal(goal)

        savingsGoalDao.markGoalDeleted("user-1", "goal-soft")

        // Excluded from direct lookup because isDeleted = 1
        assertNull(savingsGoalDao.getGoalById("user-1", "goal-soft"))

        // Excluded from active queries
        assertNull(savingsGoalDao.getActiveGoal("user-1"))

        // Included in pending sync queries
        val pending = savingsGoalDao.getPendingGoals("user-1")
        assertEquals(1, pending.size)
        assertTrue(pending[0].isDeleted)
        assertEquals(SyncStatus.PENDING_DELETE, pending[0].syncStatus)
    }

    @Test
    fun getGoalById_doesNotReturnSoftDeletedGoal() = runTest {
        val activeGoal = SavingsGoalEntity(
            id = "g-active",
            userId = "user-1",
            name = "Active",
            isDeleted = false
        )
        val deletedGoal = SavingsGoalEntity(
            id = "g-deleted",
            userId = "user-1",
            name = "Deleted",
            isDeleted = true
        )
        savingsGoalDao.upsertAll(listOf(activeGoal, deletedGoal))

        assertNotNull(savingsGoalDao.getGoalById("user-1", "g-active"))
        assertNull(savingsGoalDao.getGoalById("user-1", "g-deleted"))
    }

    @Test
    fun getTransactionById_doesNotReturnSoftDeletedTransaction() = runTest {
        val activeTx = GoalTransactionEntity(
            id = "tx-active",
            goalId = "goal-1",
            userId = "user-1",
            isDeleted = false
        )
        val deletedTx = GoalTransactionEntity(
            id = "tx-deleted",
            goalId = "goal-1",
            userId = "user-1",
            isDeleted = true
        )
        goalTransactionDao.upsertAll(listOf(activeTx, deletedTx))

        assertNotNull(goalTransactionDao.getTransactionById("user-1", "tx-active"))
        assertNull(goalTransactionDao.getTransactionById("user-1", "tx-deleted"))
    }
}

/**
 * In-memory test double matching SavingsGoalDao query contracts.
 */
class FakeSavingsGoalDao : SavingsGoalDao {
    private val storage = mutableMapOf<String, SavingsGoalEntity>()
    private val stateFlow = MutableStateFlow<List<SavingsGoalEntity>>(emptyList())

    private fun emit() {
        stateFlow.value = storage.values.toList()
    }

    override fun observeActiveGoal(userId: String): Flow<SavingsGoalEntity?> {
        return stateFlow.map { list ->
            list.firstOrNull { it.userId == userId && it.status == "ACTIVE" && !it.isDeleted }
        }
    }

    override suspend fun getActiveGoal(userId: String): SavingsGoalEntity? {
        return storage.values.firstOrNull { it.userId == userId && it.status == "ACTIVE" && !it.isDeleted }
    }

    override suspend fun getGoalById(userId: String, goalId: String): SavingsGoalEntity? {
        return storage[goalId]?.takeIf { it.userId == userId && !it.isDeleted }
    }

    override suspend fun insertGoal(entity: SavingsGoalEntity) {
        check(!storage.containsKey(entity.id)) { "Entity with id ${entity.id} already exists" }
        storage[entity.id] = entity
        emit()
    }

    override suspend fun updateGoal(entity: SavingsGoalEntity) {
        storage[entity.id] = entity
        emit()
    }

    override suspend fun upsertGoal(entity: SavingsGoalEntity) {
        storage[entity.id] = entity
        emit()
    }

    override suspend fun upsertAll(entities: List<SavingsGoalEntity>) {
        entities.forEach { storage[it.id] = it }
        emit()
    }

    override suspend fun markGoalDeleted(
        userId: String,
        goalId: String,
        syncStatus: SyncStatus,
        updatedAt: Long
    ) {
        val existing = storage[goalId]?.takeIf { it.userId == userId } ?: return
        storage[goalId] = existing.copy(
            isDeleted = true,
            syncStatus = syncStatus,
            updatedAt = updatedAt
        )
        emit()
    }

    override suspend fun hardDeleteGoal(userId: String, goalId: String) {
        if (storage[goalId]?.userId == userId) {
            storage.remove(goalId)
            emit()
        }
    }

    override suspend fun getPendingGoals(
        userId: String,
        syncedStatus: SyncStatus
    ): List<SavingsGoalEntity> {
        return storage.values.filter { it.userId == userId && it.syncStatus != syncedStatus }
    }

    override suspend fun markGoalSynced(
        userId: String,
        goalId: String,
        syncStatus: SyncStatus
    ) {
        val existing = storage[goalId]?.takeIf { it.userId == userId } ?: return
        storage[goalId] = existing.copy(syncStatus = syncStatus)
        emit()
    }

    override suspend fun clearAllForUser(userId: String) {
        storage.entries.removeAll { it.value.userId == userId }
        emit()
    }
}

/**
 * In-memory test double matching GoalTransactionDao query contracts.
 */
class FakeGoalTransactionDao : GoalTransactionDao {
    private val storage = mutableMapOf<String, GoalTransactionEntity>()
    private val stateFlow = MutableStateFlow<List<GoalTransactionEntity>>(emptyList())

    private val comparator = compareBy<GoalTransactionEntity> { it.date }.thenBy { it.createdAt }

    private fun emit() {
        stateFlow.value = storage.values.toList()
    }

    override fun observeTransactionsForGoal(
        userId: String,
        goalId: String
    ): Flow<List<GoalTransactionEntity>> {
        return stateFlow.map { list ->
            list.filter { it.userId == userId && it.goalId == goalId && !it.isDeleted }
                .sortedWith(comparator)
        }
    }

    override suspend fun getTransactionsForGoal(
        userId: String,
        goalId: String
    ): List<GoalTransactionEntity> {
        return storage.values
            .filter { it.userId == userId && it.goalId == goalId && !it.isDeleted }
            .sortedWith(comparator)
    }

    override suspend fun getTransactionById(
        userId: String,
        transactionId: String
    ): GoalTransactionEntity? {
        return storage[transactionId]?.takeIf { it.userId == userId && !it.isDeleted }
    }

    override suspend fun insertTransaction(entity: GoalTransactionEntity) {
        check(!storage.containsKey(entity.id)) { "Transaction with id ${entity.id} already exists" }
        storage[entity.id] = entity
        emit()
    }

    override suspend fun updateTransaction(entity: GoalTransactionEntity) {
        storage[entity.id] = entity
        emit()
    }

    override suspend fun upsertTransaction(entity: GoalTransactionEntity) {
        storage[entity.id] = entity
        emit()
    }

    override suspend fun upsertAll(entities: List<GoalTransactionEntity>) {
        entities.forEach { storage[it.id] = it }
        emit()
    }

    override suspend fun markTransactionDeleted(
        userId: String,
        transactionId: String,
        syncStatus: SyncStatus,
        updatedAt: Long
    ) {
        val existing = storage[transactionId]?.takeIf { it.userId == userId } ?: return
        storage[transactionId] = existing.copy(
            isDeleted = true,
            syncStatus = syncStatus,
            updatedAt = updatedAt
        )
        emit()
    }

    override suspend fun hardDeleteTransaction(userId: String, transactionId: String) {
        if (storage[transactionId]?.userId == userId) {
            storage.remove(transactionId)
            emit()
        }
    }

    override suspend fun getPendingTransactions(
        userId: String,
        syncedStatus: SyncStatus
    ): List<GoalTransactionEntity> {
        return storage.values.filter { it.userId == userId && it.syncStatus != syncedStatus }
    }

    override suspend fun markTransactionSynced(
        userId: String,
        transactionId: String,
        syncStatus: SyncStatus
    ) {
        val existing = storage[transactionId]?.takeIf { it.userId == userId } ?: return
        storage[transactionId] = existing.copy(syncStatus = syncStatus)
        emit()
    }

    override suspend fun clearAllForUser(userId: String) {
        storage.entries.removeAll { it.value.userId == userId }
        emit()
    }
}

package com.pfms.app.data.repository

import android.content.Context
import android.content.SharedPreferences
import com.pfms.app.data.local.PfmsDatabase
import com.pfms.app.data.local.dao.ExpenseDao
import com.pfms.app.data.local.entity.ExpenseEntity
import com.pfms.app.data.remote.firestore.ExpenseRemoteDataSource
import com.pfms.app.data.sync.ExpenseSyncScheduler
import com.pfms.app.domain.model.*
import com.pfms.app.domain.repository.AuthRepository
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.*

class OfflineFirstExpenseRepositoryTest {

    private val db = mock<PfmsDatabase>()
    private val dao = mock<ExpenseDao>()
    private val remote = mock<ExpenseRemoteDataSource>()
    private val auth = mock<AuthRepository>()
    private val context = mock<Context>()
    private val scheduler = mock<ExpenseSyncScheduler>()
    private val prefs = mock<SharedPreferences>()
    private val editor = mock<SharedPreferences.Editor>()

    private val testUser = AuthUser("user_abc", "Tester", "tester@example.com", true)
    private lateinit var repository: OfflineFirstExpenseRepository

    private val sampleExpense = Expense(
        id = "exp_1",
        userId = "user_abc",
        amountMinor = 250000L,
        currency = "LKR",
        categoryId = "food",
        categoryName = "Food",
        classification = ExpenseClassification.DISCRETIONARY,
        paymentMethod = ExpensePaymentMethod.CASH,
        date = "2026-10-18",
        merchant = "Supermarket",
        note = "Groceries",
        createdAt = 1700000000000L,
        updatedAt = 1700000000000L
    )

    @Before
    fun setup() {
        whenever(auth.currentUser).thenReturn(flowOf(testUser))
        whenever(db.expenseDao()).thenReturn(dao)
        whenever(context.getSharedPreferences(any(), any())).thenReturn(prefs)
        whenever(prefs.edit()).thenReturn(editor)
        whenever(editor.putString(any(), any())).thenReturn(editor)
        whenever(editor.commit()).thenReturn(true)

        repository = OfflineFirstExpenseRepository(db, remote, auth, context, scheduler).apply {
            transactionRunner = { it() }
        }
    }

    @Test
    fun addExpense_writesToRoomWithPendingInsert_andEnqueuesSync() = runTest {
        whenever(dao.get("user_abc", "exp_1")).thenReturn(null)
        whenever(dao.recent(eq("user_abc"), any())).thenReturn(emptyList())

        repository.addExpense(sampleExpense)

        // Verifies Room DAO receives entity with PENDING_INSERT
        argumentCaptor<ExpenseEntity>().apply {
            verify(dao).upsert(capture())
            assertEquals("exp_1", firstValue.id)
            assertEquals("user_abc", firstValue.userId)
            assertEquals(250000L, firstValue.amountMinor)
            assertEquals("PENDING_INSERT", firstValue.syncStatus)
            assertFalse(firstValue.isDeleted)
        }

        // Verifies WorkManager sync is scheduled
        verify(scheduler).enqueue()
    }

    @Test
    fun updateExpense_updatesRoomRecordWithPendingUpdate_andEnqueuesSync() = runTest {
        val existingEntity = ExpenseEntity(
            userId = "user_abc",
            id = "exp_1",
            amountMinor = 250000L,
            currency = "LKR",
            categoryId = "food",
            categoryName = "Food",
            classification = "DISCRETIONARY",
            paymentMethod = "CASH",
            date = "2026-10-18",
            merchant = "Supermarket",
            note = "Groceries",
            createdAt = 1700000000000L,
            updatedAt = 1700000000000L,
            syncStatus = "SYNCED"
        )
        whenever(dao.get("user_abc", "exp_1")).thenReturn(existingEntity)

        val updated = sampleExpense.copy(amountMinor = 300000L)
        repository.updateExpense(updated)

        argumentCaptor<ExpenseEntity>().apply {
            verify(dao).upsert(capture())
            assertEquals(300000L, firstValue.amountMinor)
            assertEquals("PENDING_UPDATE", firstValue.syncStatus)
        }

        verify(scheduler).enqueue()
    }

    @Test
    fun deleteExpense_softDeletesInRoomWithPendingDelete_andEnqueuesSync() = runTest {
        val existingEntity = ExpenseEntity(
            userId = "user_abc",
            id = "exp_1",
            amountMinor = 250000L,
            currency = "LKR",
            categoryId = "food",
            categoryName = "Food",
            classification = "DISCRETIONARY",
            paymentMethod = "CASH",
            date = "2026-10-18",
            merchant = "Supermarket",
            note = "Groceries",
            createdAt = 1700000000000L,
            updatedAt = 1700000000000L,
            syncStatus = "SYNCED"
        )
        whenever(dao.get("user_abc", "exp_1")).thenReturn(existingEntity)

        repository.deleteExpense("exp_1")

        argumentCaptor<ExpenseEntity>().apply {
            verify(dao).upsert(capture())
            assertTrue(firstValue.isDeleted)
            assertEquals("PENDING_DELETE", firstValue.syncStatus)
        }

        verify(scheduler).enqueue()
    }

    @Test
    fun syncPending_synchronizesInsertsUpdatesAndDeletesToRemote_andAcknowledgesRoom() = runTest {
        val pendingInsert = ExpenseEntity(
            userId = "user_abc", id = "exp_insert", amountMinor = 10000L, currency = "LKR",
            categoryId = "food", categoryName = "Food", classification = "DISCRETIONARY",
            paymentMethod = "CASH", date = "2026-10-18", merchant = "Cafe", note = "",
            createdAt = 100L, updatedAt = 100L, syncStatus = "PENDING_INSERT", revision = "rev_1"
        )
        val pendingDelete = ExpenseEntity(
            userId = "user_abc", id = "exp_delete", amountMinor = 50000L, currency = "LKR",
            categoryId = "bills", categoryName = "Bills", classification = "COMMITTED",
            paymentMethod = "CARD", date = "2026-10-18", merchant = "Water", note = "",
            createdAt = 200L, updatedAt = 200L, syncStatus = "PENDING_DELETE", isDeleted = true, revision = "rev_2"
        )

        whenever(dao.pending("user_abc")).thenReturn(listOf(pendingInsert, pendingDelete))

        val result = repository.syncPending()
        assertTrue("Sync should return true on success", result)

        // Verify remote calls
        verify(remote).insert(eq("user_abc"), argThat { id == "exp_insert" }, eq(false))
        verify(remote).delete("user_abc", "exp_delete")

        // Verify Room acknowledgment
        verify(dao).acknowledge("user_abc", "exp_insert", "rev_1")
        verify(dao).deleteRevision("user_abc", "exp_delete", "rev_2")
    }

    @Test
    fun syncPending_whenRemoteThrowsDuplicate_marksConflictInRoom() = runTest {
        val pendingInsert = ExpenseEntity(
            userId = "user_abc", id = "exp_dup", amountMinor = 10000L, currency = "LKR",
            categoryId = "food", categoryName = "Food", classification = "DISCRETIONARY",
            paymentMethod = "CASH", date = "2026-10-18", merchant = "Cafe", note = "",
            createdAt = 100L, updatedAt = 100L, syncStatus = "PENDING_INSERT", revision = "rev_dup"
        )
        whenever(dao.pending("user_abc")).thenReturn(listOf(pendingInsert))
        whenever(remote.insert(any(), any(), any())).thenThrow(DuplicateExpenseException())

        val result = repository.syncPending()
        assertTrue(result)

        // Verify conflict marked in Room
        verify(dao).markConflict("user_abc", "exp_dup", "rev_dup")
    }
}

package com.pfms.app

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.pfms.app.data.local.PfmsDatabase
import com.pfms.app.data.local.entity.toEntity
import com.pfms.app.data.remote.firestore.*
import com.pfms.app.data.repository.OfflineFirstExpenseRepository
import com.pfms.app.data.sync.ExpenseSyncScheduler
import com.pfms.app.domain.model.*
import com.pfms.app.domain.repository.AuthRepository
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import java.util.UUID

internal class ExpenseTestAuth : AuthRepository {
    val users = MutableStateFlow<AuthUser?>(AuthUser("offline-test-a", "Test", "test@example.com", true))
    override val currentUser: Flow<AuthUser?> = users
    override suspend fun register(displayName: String, email: String, password: String): Result<AuthUser> = error("Unused")
    override suspend fun login(email: String, password: String): Result<AuthUser> = error("Unused")
    override suspend fun sendPasswordResetEmail(email: String): Result<Unit> = error("Unused")
    override suspend fun signInWithGoogle(idToken: String): Result<AuthUser> = error("Unused")
    override suspend fun signUpWithGoogle(idToken: String): Result<AuthUser> = error("Unused")
    override suspend fun updateDisplayName(displayName: String): Result<Unit> = error("Unused")
    override suspend fun updateDefaultPaymentMethod(paymentMethod: String?): Result<Unit> = error("Unused")
    override suspend fun updatePassword(newPassword: String): Result<Unit> = error("Unused")
    override suspend fun deleteAccount(): Result<Unit> = error("Unused")
    override suspend fun reauthenticate(credential: ReauthCredential): Result<Unit> = error("Unused")
    override suspend fun logout(): Result<Unit> = error("Unused")
}

@RunWith(AndroidJUnit4::class)
class OfflineExpenseIntegrationTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val name = "offline-expense-${UUID.randomUUID()}.db"
    private lateinit var db: PfmsDatabase
    private val auth = ExpenseTestAuth()
    private val uid get() = auth.users.value!!.uid
    private val server = MemoryServer()
    private fun open() = Room.databaseBuilder(context, PfmsDatabase::class.java, name).build()
    private fun repo() = OfflineFirstExpenseRepository(db, server, auth, context, ExpenseSyncScheduler {})
    private fun expense(id: String = "one") = Expense(id = id, amountMinor = 125050, categoryId = "rent",
        categoryName = "Rent", paymentMethod = ExpensePaymentMethod.CARD, date = "2026-10-17",
        createdAt = System.currentTimeMillis(), updatedAt = System.currentTimeMillis())
    @Before fun setup() { db = open() }
    @After fun close() { db.close(); context.deleteDatabase(name) }

    private class MemoryServer : ExpenseRemoteDataSource {
        var online = false
        val rows = mutableMapOf<String, Expense>()
        private val guards = mutableMapOf<String, Expense>()
        private val lock = Mutex()
        override fun observe(userId: String): Flow<ExpenseRemoteSnapshot> = emptyFlow()
        override suspend fun insert(userId: String, expense: Expense, allowDuplicate: Boolean) = lock.withLock {
            check(online) { "Offline" }
            val key = "$userId:${FirestoreExpenseRemoteDataSource.fingerprint(expense)}"
            val old = guards[key]
            if (!rows.containsKey(expense.id) && !allowDuplicate && old != null && kotlin.math.abs(old.createdAt - expense.createdAt) <= 60000) throw DuplicateExpenseException()
            rows[expense.id] = expense
            guards[key] = expense
        }
        override suspend fun update(userId: String, expense: Expense) { check(online); rows[expense.id] = expense }
        override suspend fun delete(userId: String, id: String) { check(online); rows.remove(id) }
    }

    @Test fun offlineCrudSurvivesDatabaseReopenThenSynchronizes() = runBlocking {
        var repository = repo()
        repository.addExpense(expense())
        assertFalse(repository.syncPending())
        assertEquals(125050L, repository.getExpenseById("one")?.amountMinor)
        db.close(); db = open(); repository = repo()
        assertEquals(ExpensePaymentMethod.CARD, repository.observeLastPaymentMethod().first())
        repository.updateExpense(repository.getExpenseById("one")!!.copy(amountMinor = 200000, updatedAt = System.currentTimeMillis() + 1))
        server.online = true
        assertTrue(repository.syncPending())
        assertEquals(200000L, server.rows["one"]?.amountMinor)
        assertEquals("SYNCED", db.expenseDao().get(uid, "one")?.syncStatus)
        server.online = false
        repository.deleteExpense("one")
        assertTrue(db.expenseDao().observe(uid).first().isEmpty())
        db.close(); db = open(); repository = repo()
        assertFalse(repository.syncPending())
        assertNotNull(db.expenseDao().get(uid, "one"))
        server.online = true
        assertTrue(repository.syncPending())
        assertNull(db.expenseDao().get(uid, "one"))
        assertTrue(server.rows.isEmpty())
    }

    @Test fun authoritativeRemoteChangesNeverOverwritePendingLocalEdits() = runBlocking {
        val repository = repo()
        repository.addExpense(expense())
        val stale = expense().copy(userId = uid, amountMinor = 1).toEntity()
        db.expenseDao().reconcile(uid, listOf(stale), true)
        assertEquals(125050L, repository.getExpenseById("one")?.amountMinor)
        db.expenseDao().reconcile(uid, emptyList(), true)
        assertNotNull(repository.getExpenseById("one"))
        server.online = true; repository.syncPending()
        db.expenseDao().reconcile(uid, listOf(stale), true)
        assertEquals(1L, repository.getExpenseById("one")?.amountMinor)
        db.expenseDao().reconcile(uid, emptyList(), true)
        assertNull(repository.getExpenseById("one"))
    }

    @Test fun oldSyncAcknowledgementCannotClearNewerPendingRevision() = runBlocking {
        val repository = repo()
        repository.addExpense(expense())
        val old = db.expenseDao().get(uid, "one")!!
        repository.updateExpense(old.toDomain().copy(amountMinor = 50000))
        db.expenseDao().acknowledge(uid, "one", old.revision)
        assertEquals("PENDING_INSERT", db.expenseDao().get(uid, "one")?.syncStatus)
    }

    @Test fun concurrentLocalSavesNeedExplicitDuplicateConfirmation() = runBlocking {
        val repository = repo()
        val attempts = coroutineScope {
            listOf("one", "two").map { id -> async { runCatching { repository.addExpense(expense(id)) } } }.awaitAll()
        }
        assertEquals(1, attempts.count { it.isSuccess })
        assertEquals(1, attempts.count { it.exceptionOrNull() is DuplicateExpenseException })
        val duplicate = expense("confirmed")
        repository.addExpenseChecked(duplicate, true)
        assertEquals(2, db.expenseDao().observe(uid).first().size)
    }

    @Test fun remoteConflictSurvivesRestartAndCanBeConfirmedOrDismissed() = runBlocking {
        var repository = repo()
        val original = expense("other-device").copy(userId = uid)
        server.online = true; server.insert(uid, original, false)
        repository.addExpense(expense("one"))
        assertTrue(repository.syncPending())
        assertTrue(db.expenseDao().observe(uid).first().isEmpty())
        db.close(); db = open(); repository = repo()
        val conflict = repository.observeDuplicateConflict().first()!!
        assertEquals("one", conflict.id)
        repository.addExpenseChecked(conflict, true)
        assertTrue(repository.syncPending())
        assertEquals(2, server.rows.size)
        repository.addExpenseChecked(expense("dismiss"), true)
        val row = db.expenseDao().get(uid, "dismiss")!!
        db.expenseDao().markConflict(uid, row.id, row.revision)
        repository.dismissDuplicate("dismiss")
        assertNull(db.expenseDao().get(uid, "dismiss"))
    }

    @Test fun accountSwitchDoesNotExposeAnotherUsersDataOrPaymentDefault() = runBlocking {
        val repository = repo()
        repository.addExpense(expense())
        auth.users.value = AuthUser("offline-test-b", "B", "b@example.com", true)
        assertNull(repository.getExpenseById("one"))
        assertEquals(ExpensePaymentMethod.CASH, repository.observeLastPaymentMethod().first())
        assertTrue(repository.getExpensesCreatedSince(0).isEmpty())
        auth.users.value = AuthUser("offline-test-a", "A", "a@example.com", true)
        assertNotNull(repository.getExpenseById("one"))
    }

    @Test fun upgradeFromVersionThreePreservesExistingCategoryData() = runBlocking {
        db.expenseCategoryDao().upsertCategory(com.pfms.app.data.local.entity.ExpenseCategoryEntity(
            "rent", uid, "Rent", "COMMITTED", false, false))
        db.close()
        android.database.sqlite.SQLiteDatabase.openDatabase(context.getDatabasePath(name).path, null, 0).use {
            it.execSQL("DROP TABLE expenses")
            it.version = 3
        }
        db = Room.databaseBuilder(context, PfmsDatabase::class.java, name)
            .addMigrations(PfmsDatabase.MIGRATION_3_4, PfmsDatabase.MIGRATION_4_5).build()
        assertEquals("Rent", db.expenseCategoryDao().observeCategories(uid).first().single().name)
        assertTrue(db.expenseDao().observe(uid).first().isEmpty())
    }
}

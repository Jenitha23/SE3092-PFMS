package com.pfms.app

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import androidx.work.*
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Source
import com.google.firebase.firestore.FirebaseFirestoreException
import com.pfms.app.data.local.mapper.toDomain
import com.pfms.app.data.local.PfmsDatabase
import com.pfms.app.data.repository.*
import com.pfms.app.data.remote.firestore.*
import com.pfms.app.data.sync.*
import com.pfms.app.domain.model.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.tasks.await
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.util.UUID

/** Opt-in real SDK tests: demo-pfms-fr35 only. Run stage1, force-stop the app, then stage2. */
class OfflineFirestoreEmulatorTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val prefs = context.getSharedPreferences("fr35_emulator_test", Context.MODE_PRIVATE)
    private val dbName = "fr35-real-offline.db"
    private lateinit var firebase: FirebaseAuth
    private lateinit var firestore: FirebaseFirestore
    private lateinit var db: PfmsDatabase
    private lateinit var auth: ExpenseTestAuth
    private lateinit var incomes: OfflineFirstIncomeRepository
    private lateinit var expenses: OfflineFirstExpenseRepository
    private lateinit var connectivity: AndroidConnectivityObserver

    private fun enabled() {
        assumeTrue("Requires isolated local Firebase emulators", InstrumentationRegistry.getArguments().getString("fr35Emulators") == "true")
    }

    private fun clients() {
        val app = FirebaseApp.initializeApp(context, FirebaseOptions.Builder().setProjectId("demo-pfms-fr35")
            .setApplicationId("1:123456789:android:fr35test").setApiKey("fake-api-key").build(), "fr35-test")
        firebase = FirebaseAuth.getInstance(app).also { it.useEmulator("10.0.2.2", 9199) }
        firestore = FirebaseFirestore.getInstance(app).also { it.useEmulator("10.0.2.2", 8180) }
    }

    private fun repositories(startWorkers: Boolean = true) {
        db = Room.databaseBuilder(context, PfmsDatabase::class.java, dbName).build()
        auth = ExpenseTestAuth()
        auth.users.value = firebase.currentUser?.let { AuthUser(it.uid, "FR35", "fr35@example.test", true) }
        incomes = OfflineFirstIncomeRepository(db.incomeDao(), FirestoreIncomeRemoteDataSource(firestore, firebase), auth)
            .also { if (startWorkers) it.syncContext = context }
        expenses = OfflineFirstExpenseRepository(db, FirestoreExpenseRemoteDataSource(firestore, firebase), auth, context,
            if (startWorkers) WorkManagerExpenseSyncScheduler(context) else ExpenseSyncScheduler {})
        connectivity = AndroidConnectivityObserver(context)
        if (startWorkers) WorkManager.initialize(context, Configuration.Builder().setWorkerFactory(object : WorkerFactory() {
            override fun createWorker(appContext: Context, workerClassName: String, workerParameters: WorkerParameters): ListenableWorker? = when (workerClassName) {
                IncomeSyncWorker::class.java.name -> IncomeSyncWorker(appContext, workerParameters, object : PendingSyncCoordinator {
                    override suspend fun syncAllPending(): Result<Unit> = incomes.syncPendingIncome().map { Unit }
                })
                ExpenseSyncWorker::class.java.name -> ExpenseSyncWorker(appContext, workerParameters, expenses)
                else -> null
            }
        }).build())
    }

    private fun network(enabled: Boolean) {
        val mode = if (enabled) "enable" else "disable"
        listOf("svc wifi $mode", "svc data $mode").forEach { command ->
            InstrumentationRegistry.getInstrumentation().uiAutomation.executeShellCommand(command).close()
        }
    }

    @Test fun stage1CreateAndModifyTwentyRecordsOffline() = runBlocking {
        enabled(); clients()
        network(true)
        val email = "fr35-${UUID.randomUUID()}@example.test"
        val user = withTimeout(30_000) { firebase.createUserWithEmailAndPassword(email, "Fr35-Test-Password1!").await().user!! }
        prefs.edit().putString("uid", user.uid).putString("email", email).commit()
        context.deleteDatabase(dbName)
        repositories()
        // Establish cached records first, then prove they remain readable with radios disabled.
        incomes.addIncome(Income(id = "cached-income", userId = user.uid, amountMinor = 12345, receivedDate = "2026-10-20"))
        expenses.addExpense(Expense(id = "cached-expense", amountMinor = 123, date = "2026-10-20", merchant = "Cached"))
        withTimeout(60_000) { while (db.pendingChangesDao().pendingCount(user.uid) > 0) delay(100) }
        network(false)
        withTimeout(30_000) { connectivity.isOnline.first { !it } }
        assertEquals(12345L, incomes.getIncomeById("cached-income")!!.amountMinor)
        assertEquals(123L, expenses.getExpenseById("cached-expense")!!.amountMinor)
        repeat(10) { index ->
            incomes.addIncome(Income(id = "income-$index", userId = user.uid, amountMinor = 10000L + index, receivedDate = "2026-10-20"))
            expenses.addExpense(Expense(id = "expense-$index", amountMinor = 1000L + index, date = "2026-10-20", merchant = "Offline $index"))
        }
        incomes.updateIncome(incomes.getIncomeById("income-0")!!.copy(amountMinor = 77777, note = "Edited offline"))
        expenses.updateExpense(expenses.getExpenseById("expense-0")!!.copy(amountMinor = 8888, note = "Edited offline"))
        incomes.deleteIncome("income-9")
        expenses.deleteExpense("expense-9")
        assertEquals(20, db.pendingChangesDao().pendingCount(user.uid))
        assertEquals(10, db.incomeDao().observeIncome(user.uid).first().size)
        assertEquals(10, db.expenseDao().observe(user.uid).first().size)
        val state = OfflineSyncStatusRepository(db, auth, connectivity).status.first { it.pendingChanges == 20 }
        assertFalse(state.isOnline)
        db.close()
        // Leave the radios disabled. The host force-stops the process before stage2.
    }

    @Test fun stage2RestartOfflineThenReconnectAutomaticallyWithoutDuplicates() = runBlocking {
        enabled(); clients()
        assertEquals(prefs.getString("uid", null), firebase.currentUser?.uid)
        repositories()
        val uid = firebase.currentUser!!.uid
        try {
            withTimeout(30_000) { connectivity.isOnline.first { !it } }
            assertEquals(20, db.pendingChangesDao().pendingCount(uid))
            assertEquals(77777L, incomes.getIncomeById("income-0")!!.amountMinor)
            assertEquals(8888L, expenses.getExpenseById("expense-0")!!.amountMinor)
            assertNull(incomes.getIncomeById("income-9"))
            assertNull(expenses.getExpenseById("expense-9"))
            network(true)
            withTimeout(45_000) { connectivity.isOnline.first { it } }
            // No explicit sync call: the persisted network-constrained WorkManager jobs must drain Room.
            withTimeout(120_000) { while (db.pendingChangesDao().pendingCount(uid) > 0) delay(200) }
            val incomeDocs = firestore.collection("users").document(uid).collection("income").get(Source.SERVER).await()
            val expenseDocs = firestore.collection("users").document(uid).collection("expenses").get(Source.SERVER).await()
            assertEquals(11, incomeDocs.size()) // Ten live entries plus one durable deletion marker.
            assertEquals(11, expenseDocs.size())
            assertEquals(77777L, incomeDocs.documents.single { it.id == "income-0" }.getLong("amountMinor"))
            assertEquals(8888L, expenseDocs.documents.single { it.id == "expense-0" }.getLong("amountMinor"))
            assertTrue(incomeDocs.documents.single { it.id == "income-9" }.getBoolean("isDeleted") == true)
            assertTrue(expenseDocs.documents.single { it.id == "expense-9" }.getBoolean("isDeleted") == true)
            // Repeated synchronization leaves the exact same IDs and counts.
            assertTrue(incomes.syncPendingIncome().isSuccess)
            assertTrue(expenses.syncPending())
            assertEquals(11, firestore.collection("users").document(uid).collection("income").get(Source.SERVER).await().size())
            val synced = OfflineSyncStatusRepository(db, auth, connectivity).status.first { it.isOnline && it.pendingChanges == 0 }
            assertEquals(0, synced.pendingChanges)
        } finally { network(true); db.close() }
    }

    @Test fun stage3RealTransactionsRetryLwwAndFirebaseUserIsolation() = runBlocking {
        enabled(); clients(); repositories(startWorkers = false)
        val uid = firebase.currentUser!!.uid
        val actual = FirestoreIncomeRemoteDataSource(firestore, firebase)
        var loseAcknowledgement = true
        val interrupted = object : IncomeRemoteDataSource by actual {
            override suspend fun synchronize(userId: String, income: Income, version: SyncVersion): FinancialSyncResult<Income> {
                val result = actual.synchronize(userId, income, version)
                if (loseAcknowledgement) { loseAcknowledgement = false; throw java.io.IOException("Lost acknowledgement after server commit") }
                return result
            }
        }
        val repository = OfflineFirstIncomeRepository(db.incomeDao(), interrupted, auth)
        val id = "retry-${UUID.randomUUID()}"
        try {
            repository.addIncome(Income(id, uid, 12300, receivedDate = "2026-10-20"))
            assertTrue(repository.syncPendingIncome().isFailure)
            val queued = db.incomeDao().getPendingSync(uid).single()
            assertEquals(id, queued.id)
            assertTrue(firestore.collection("users").document(uid).collection("income").document(id).get(Source.SERVER).await().exists())
            assertTrue(repository.syncPendingIncome().isSuccess)
            assertEquals(0, db.pendingChangesDao().pendingCount(uid))
            val local = db.incomeDao().getIncomeById(uid, id)!!
            val later = local.updatedAt + 60000
            actual.synchronize(uid, local.toDomain().copy(amountMinor = 55500, updatedAt = later), SyncVersion(later, "remote-newer"))
            repository.updateIncome(repository.getIncomeById(id)!!.copy(amountMinor = 99900))
            assertTrue(repository.syncPendingIncome().isSuccess)
            assertEquals(55500L, repository.getIncomeById(id)!!.amountMinor)
            // A newer remote deletion also wins against a pending earlier edit.
            val latest = db.incomeDao().getIncomeById(uid, id)!!
            repository.updateIncome(latest.toDomain().copy(amountMinor = 77700))
            actual.synchronize(uid, latest.toDomain(), SyncVersion(later + 60000, "remote-delete", true))
            assertTrue(repository.syncPendingIncome().isSuccess)
            assertNull(repository.getIncomeById(id))

            val second = firebase.createUserWithEmailAndPassword("fr35-b-${UUID.randomUUID()}@example.test", "Fr35-Test-Password1!").await().user!!
            auth.users.value = AuthUser(second.uid, "B", "b@example.test", true)
            assertTrue(repository.observeIncome().first().isEmpty())
            assertNull(repository.getIncomeById("income-0"))
            val blocked = runCatching { firestore.collection("users").document(uid).collection("income").get(Source.SERVER).await() }.exceptionOrNull()
            assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED, (blocked as? FirebaseFirestoreException)?.code)
            repository.addIncome(Income("income-0", second.uid, 33300, receivedDate = "2026-10-20"))
            assertTrue(repository.syncPendingIncome().isSuccess)
            assertEquals(33300L, repository.getIncomeById("income-0")!!.amountMinor)
            assertEquals(77777L, db.incomeDao().getIncomeById(uid, "income-0")!!.amountMinor)
            firebase.signInWithEmailAndPassword(prefs.getString("email", null)!!, "Fr35-Test-Password1!").await()
            auth.users.value = AuthUser(uid, "A", "a@example.test", true)
            assertEquals(77777L, repository.getIncomeById("income-0")!!.amountMinor)
        } finally { network(true); db.close() }
    }
}

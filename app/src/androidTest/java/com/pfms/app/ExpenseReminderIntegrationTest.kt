package com.pfms.app

import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.work.*
import androidx.work.testing.TestListenableWorkerBuilder
import com.pfms.app.data.local.PfmsDatabase
import com.pfms.app.data.local.entity.RecurringExpenseEntity
import com.pfms.app.data.sync.ExpenseReminderWorker
import com.pfms.app.domain.model.*
import com.pfms.app.domain.repository.AdvancedExpenseRepository
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import java.time.LocalDate
import java.time.ZoneId

@RunWith(AndroidJUnit4::class)
class ExpenseReminderIntegrationTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private lateinit var db: PfmsDatabase
    private val manager get() = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    @Before fun setup() {
        if (Build.VERSION.SDK_INT >= 33) {
            InstrumentationRegistry.getInstrumentation().uiAutomation.executeShellCommand(
                "pm grant ${context.packageName} android.permission.POST_NOTIFICATIONS").let {
                    android.os.ParcelFileDescriptor.AutoCloseInputStream(it).use { stream -> stream.readBytes() }
                }
        }
        db = Room.inMemoryDatabaseBuilder(context, PfmsDatabase::class.java).build()
        manager.cancelAll()
    }
    @After fun close() { manager.cancelAll(); db.close() }
    private val offlineRepository = object : AdvancedExpenseRepository {
        override fun categories() = flowOf(emptyList<ExpenseCategory>())
        override fun templates() = flowOf(emptyList<RecurringExpense>())
        override suspend fun refreshTemplates(): Unit = error("Offline")
        override suspend fun initializeDefaults() = Unit
        override suspend fun createCategory(name: String, classification: ExpenseClassification) = Unit
        override suspend fun updateCategory(category: ExpenseCategory) = Unit
        override suspend fun saveTemplate(template: RecurringExpense) = Unit
        override suspend fun deleteTemplate(templateId: String) = Unit
        override suspend fun confirmDue(template: RecurringExpense) = Unit
        override suspend fun repeatExpense(expenseId: String) = Unit
    }
    private fun worker() = TestListenableWorkerBuilder<ExpenseReminderWorker>(context)
        .setWorkerFactory(object : WorkerFactory() {
            override fun createWorker(appContext: Context, workerClassName: String, workerParameters: WorkerParameters) =
                ExpenseReminderWorker(appContext, workerParameters, db.recurringExpenseDao(), ExpenseTestAuth(), offlineRepository)
        }).build()

    @Test fun cachedDueAndReviewRemindersPostOfflineWithoutNotifyingPausedOrFutureItems() = runBlocking {
        val today = LocalDate.now(ZoneId.of("Asia/Colombo"))
        val base = RecurringExpenseEntity("due", "offline-test-a", "Rent", "rent", "Rent", "COMMITTED", 10000,
            "CASH", "MONTHLY", today.toString(), true, false, "NONE", "")
        db.recurringExpenseDao().upsertTemplates(listOf(base,
            base.copy(id = "review", title = "Streaming", isSubscription = true, nextDueDate = today.plusDays(5).toString(), reminderDate = today.toString()),
            base.copy(id = "future", nextDueDate = today.plusDays(1).toString()),
            base.copy(id = "paused", isActive = false), base.copy(id = "cancelled", isSubscription = true, flag = "CANCEL")))
        assertEquals(ListenableWorker.Result.success(), worker().doWork())
        val titles = manager.activeNotifications.map { it.notification.extras.getString("android.title") }
        assertEquals(2, titles.size)
        assertTrue(titles.any { it?.startsWith("Recurring Expense Due") == true })
        assertTrue(titles.any { it?.startsWith("Subscription Review") == true })
        worker().doWork()
        assertEquals(2, manager.activeNotifications.size)
    }
}

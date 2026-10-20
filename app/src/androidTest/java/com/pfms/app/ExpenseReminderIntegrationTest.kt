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
import com.pfms.app.data.reminder.*
import com.pfms.app.data.repository.CachedFinancialReminderRepository
import com.pfms.app.domain.model.*
import com.pfms.app.domain.repository.AdvancedExpenseRepository
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeout
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import java.time.LocalDate
import java.time.ZoneId
import java.time.Clock
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class ExpenseReminderIntegrationTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private lateinit var db: PfmsDatabase
    private val uid = "expense-reminders-${UUID.randomUUID()}"
    private val auth = ExpenseTestAuth().apply { users.value = AuthUser(uid, "Test", "test@example.test", true) }
    private val store by lazy { ReminderPreferencesStore(context) }
    private val clock = Clock.fixed(LocalDate.now(ZoneId.of("Asia/Colombo")).atTime(12, 0)
        .atZone(ZoneId.of("Asia/Colombo")).toInstant(), ZoneId.of("Asia/Colombo"))
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
    private suspend fun awaitNotifications(count: Int) = withTimeout(5_000) {
        // NotificationManager processes enqueue/cancel requests asynchronously in the system service.
        while (manager.activeNotifications.size != count) delay(25)
    }
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
    private suspend fun worker(type: ReminderType): ExpenseReminderWorker {
        store.locked { store.update(uid, type, true, 0, null) }
        val revision = store.read(uid)[type].revision
        val source = FinancialReminderContentSource(CachedFinancialReminderRepository(db, auth), offlineRepository, store, clock, financialReminderRules())
        val delivery = ReminderDelivery(store, auth, AndroidReminderNotifier(context), source, clock)
        return TestListenableWorkerBuilder<ExpenseReminderWorker>(context)
        .setInputData(workDataOf(ReminderWorkScheduler.USER to uid, ReminderWorkScheduler.TYPE to type.key,
            ReminderWorkScheduler.REVISION to revision, ReminderWorkScheduler.NOT_BEFORE to 1L))
        .setWorkerFactory(object : WorkerFactory() {
            override fun createWorker(appContext: Context, workerClassName: String, workerParameters: WorkerParameters) =
                ExpenseReminderWorker(appContext, workerParameters, delivery)
        }).build()
    }

    @Test fun cachedDueAndReviewRemindersPostOfflineWithoutNotifyingPausedOrFutureItems() = runBlocking {
        val today = LocalDate.now(ZoneId.of("Asia/Colombo"))
        val base = RecurringExpenseEntity("due", uid, "Rent", "rent", "Rent", "COMMITTED", 10000,
            "CASH", "MONTHLY", today.toString(), true, false, "NONE", "")
        db.recurringExpenseDao().upsertTemplates(listOf(base,
            base.copy(id = "review", title = "Streaming", isSubscription = true, nextDueDate = today.plusDays(5).toString(), reminderDate = today.toString()),
            base.copy(id = "future", nextDueDate = today.plusDays(1).toString()),
            base.copy(id = "paused", isActive = false), base.copy(id = "cancelled", isSubscription = true, flag = "CANCEL")))
        assertEquals(ListenableWorker.Result.success(), worker(ReminderType.DUE_RECURRING).doWork())
        assertEquals(ListenableWorker.Result.success(), worker(ReminderType.SUBSCRIPTION_REVIEW).doWork())
        awaitNotifications(2)
        val titles = manager.activeNotifications.map { it.notification.extras.getString("android.title") }
        assertEquals(2, titles.size)
        assertTrue(titles.any { it?.startsWith("Recurring Expense Due") == true })
        assertTrue(titles.any { it?.startsWith("Subscription Review") == true })
        manager.cancelAll()
        awaitNotifications(0)
        worker(ReminderType.DUE_RECURRING).doWork()
        worker(ReminderType.SUBSCRIPTION_REVIEW).doWork()
        assertEquals(0, manager.activeNotifications.size)
    }

    @Test fun channelsAreIdempotentAndDisabledTypesCancelAlreadyVisibleNotifications() = runBlocking {
        val notifier = AndroidReminderNotifier(context)
        notifier.createChannels(); notifier.createChannels()
        ReminderType.entries.forEach { assertNotNull(manager.getNotificationChannel(AndroidReminderNotifier.channelId(it))) }
        val content = ReminderNotification("one", "Test", "Reminder", "recurring_expenses")
        assertTrue(notifier.post(uid, ReminderType.DUE_RECURRING, content))
        assertTrue(notifier.post(uid, ReminderType.SUBSCRIPTION_REVIEW, content))
        awaitNotifications(2)
        assertEquals(2, manager.activeNotifications.size)
        notifier.cancelType(uid, ReminderType.DUE_RECURRING)
        awaitNotifications(1)
        assertEquals(1, manager.activeNotifications.size)
        notifier.cancelOtherUsers("another-user")
        awaitNotifications(0)
        assertEquals(0, manager.activeNotifications.size)
    }

    @Test fun missingSalaryInvoicesAndGoalsDoNotInventFinancialEligibility() = runBlocking {
        val source = FinancialReminderContentSource(CachedFinancialReminderRepository(db, auth), offlineRepository, store, clock, financialReminderRules())
        for (type in listOf(ReminderType.SALARY_DAY, ReminderType.FREELANCE_OVERDUE, ReminderType.GOAL_BEHIND))
            assertTrue(source.candidates(uid, type).isEmpty())
        assertEquals(0, manager.activeNotifications.size)
    }
}

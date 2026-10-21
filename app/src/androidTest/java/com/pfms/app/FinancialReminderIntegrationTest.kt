package com.pfms.app

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.pfms.app.data.local.PfmsDatabase
import com.pfms.app.data.local.entity.RecurringExpenseEntity
import com.pfms.app.data.local.entity.toEntity
import com.pfms.app.data.local.mapper.toEntity
import com.pfms.app.data.reminder.*
import com.pfms.app.data.remote.firestore.*
import com.pfms.app.data.repository.*
import com.pfms.app.data.sync.ExpenseSyncScheduler
import com.pfms.app.domain.model.*
import com.pfms.app.domain.repository.AdvancedExpenseRepository
import com.pfms.app.domain.usecase.dashboard.AppClock
import com.pfms.app.ui.navigation.Routes
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Test
import java.time.*
import java.util.UUID

class FinancialReminderIntegrationTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val uid = "fr38-complete-${UUID.randomUUID()}"
    private val auth = ExpenseTestAuth().apply { users.value = AuthUser(uid, "Test", "test@example.test", true) }
    private val db = Room.inMemoryDatabaseBuilder(context, PfmsDatabase::class.java).build()
    private val store = ReminderPreferencesStore(context)
    private val clock = TestClock()
    private val records = CachedFinancialReminderRepository(db, auth)
    private val notifier = CaptureNotifier()
    private val advanced = object : AdvancedExpenseRepository {
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
    private val source = FinancialReminderContentSource(records, advanced, store, clock, financialReminderRules())
    private val delivery = ReminderDelivery(store, auth, notifier, source, clock)
    private fun invoice(status: String = "Expected") = Income("invoice", uid, 10000, source = "Freelance",
        receivedDate = "2026-10-05", paymentStatus = status, projectName = "Client")
    private fun goal() = SavingsGoal("goal", uid, "Laptop", 1200000, "2027-10-20")
    private suspend fun configure() = store.locked {
        ReminderType.entries.forEach { store.update(uid, it, true, 0, 2) }
    }
    private suspend fun run(type: ReminderType) = delivery.run(uid, type.key, store.read(uid)[type].revision, 1)
    private suspend fun seed() {
        db.incomeDao().upsertIncome(invoice().toEntity())
        db.incomeDao().upsertIncome(Income("salary-income", uid, 10000, receivedDate = "2026-10-15").toEntity())
        db.expenseDao().upsert(Expense("expense", uid, 2500, date = "2026-10-18").toEntity())
        db.salaryTemplateDao().upsertSalaryTemplate(SalaryTemplate("salary", uid, 50000, paymentDay = 20).toEntity())
        db.savingsGoalDao().upsertGoal(goal().toEntity())
        db.recurringExpenseDao().upsertTemplates(listOf(
            RecurringExpenseEntity("rent", uid, "Rent", "rent", "Rent", "COMMITTED", 10000,
                "CASH", "MONTHLY", "2026-10-20", true, false, "NONE", ""),
            RecurringExpenseEntity("sub", uid, "Streaming", "sub", "Subscription", "DISCRETIONARY", 1000,
                "CARD", "MONTHLY", "2026-11-01", true, true, "NONE", "2026-10-20")))
    }
    @After fun close() { db.close() }

    @Test fun allSevenTypesUseTheirRealDomainRulesAndExistingDestinations() = runBlocking {
        configure(); seed()
        for (type in ReminderType.entries) assertEquals(ReminderRunResult.COMPLETE, run(type))
        assertEquals(ReminderType.entries.toSet(), notifier.posted.map { it.first }.toSet())
        assertEquals(7, notifier.posted.size)
        assertTrue(notifier.posted.all { Routes.reminderDestination(it.second.destination) != null })
        assertTrue(notifier.posted.single { it.first == ReminderType.WEEKLY_SUMMARY }.second.body.contains("net savings LKR 75.00"))
        for (type in ReminderType.entries) run(type)
        assertEquals(7, notifier.posted.size)
    }
    @Test fun eachIndividualSwitchBlocksItsOwnEligibleReminderWithoutChangingTheOthers() = runBlocking {
        configure(); seed()
        for (type in ReminderType.entries) {
            val revision = store.read(uid)[type].revision
            store.locked { store.update(uid, type, false, null, null) }
            val before = notifier.posted.size
            delivery.run(uid, type.key, revision, 1); run(type)
            assertEquals(before, notifier.posted.size)
            assertTrue(ReminderType.entries.filter { it != type }.all { store.read(uid)[it].enabled })
            store.locked { store.update(uid, type, true, null, null) }
            run(type); assertEquals(before + 1, notifier.posted.size)
        }
    }
    @Test fun dailyPostsOncePerDayWhileSalaryAndUnchangedInvoicesDoNotRepeatTheNextDay() = runBlocking {
        configure(); seed()
        for (type in listOf(ReminderType.DAILY_LOG, ReminderType.SALARY_DAY, ReminderType.FREELANCE_OVERDUE)) run(type)
        clock.day = clock.day.plusDays(1)
        for (type in listOf(ReminderType.DAILY_LOG, ReminderType.SALARY_DAY, ReminderType.FREELANCE_OVERDUE)) run(type)
        assertEquals(2, notifier.posted.count { it.first == ReminderType.DAILY_LOG })
        assertEquals(1, notifier.posted.count { it.first == ReminderType.SALARY_DAY })
        assertEquals(1, notifier.posted.count { it.first == ReminderType.FREELANCE_OVERDUE })
        db.incomeDao().upsertIncome(invoice("Received").toEntity())
        run(ReminderType.FREELANCE_OVERDUE)
        assertFalse(notifier.active.keys.any { it.first == ReminderType.FREELANCE_OVERDUE })
    }
    @Test fun multipleDueItemsHaveIndependentClaimsAndConfirmedOccurrencesStopAlerting() = runBlocking {
        configure(); seed(); run(ReminderType.DUE_RECURRING)
        val rent = db.recurringExpenseDao().getTemplateById(uid, "rent")!!
        db.recurringExpenseDao().upsertTemplate(rent.copy(id = "power", title = "Power"))
        run(ReminderType.DUE_RECURRING)
        assertEquals(2, notifier.posted.size)
        db.recurringExpenseDao().upsertTemplate(rent.copy(nextDueDate = "2026-11-20"))
        clock.day = clock.day.plusDays(1); run(ReminderType.DUE_RECURRING)
        assertEquals(2, notifier.posted.size)
        assertFalse(notifier.active.keys.any { it.second == "recurring:rent" })
    }
    @Test fun aConfirmedSalaryAndDismissedSubscriptionReviewAreNoLongerEligible() = runBlocking {
        configure(); seed(); run(ReminderType.SALARY_DAY); run(ReminderType.SUBSCRIPTION_REVIEW)
        val salary = db.salaryTemplateDao().getSalaryTemplate(uid)!!
        db.salaryTemplateDao().upsertSalaryTemplate(salary.copy(lastConfirmedMonth = "2026-10"))
        val sub = db.recurringExpenseDao().getTemplateById(uid, "sub")!!
        db.recurringExpenseDao().upsertTemplate(sub.copy(reminderDate = "", flag = "NONE"))
        clock.day = clock.day.plusDays(1)
        run(ReminderType.SALARY_DAY); run(ReminderType.SUBSCRIPTION_REVIEW)
        assertEquals(2, notifier.posted.size); assertTrue(notifier.active.isEmpty())
    }
    @Test fun goalOnlyRealertsAfterObservedRecoveryOrAMeaningfulTargetChange() = runBlocking {
        configure(); seed(); run(ReminderType.GOAL_BEHIND)
        clock.day = clock.day.plusDays(1); run(ReminderType.GOAL_BEHIND)
        assertEquals(1, notifier.posted.size)
        for ((n, date) in listOf("2026-07-01", "2026-08-01", "2026-09-01").withIndex())
            db.incomeDao().upsertIncome(Income("trailing$n", uid, 200000, receivedDate = date).toEntity())
        run(ReminderType.GOAL_BEHIND)
        assertTrue(notifier.active.isEmpty())
        repeat(3) { db.incomeDao().hardDeleteIncome(uid, "trailing$it") }
        run(ReminderType.GOAL_BEHIND); run(ReminderType.GOAL_BEHIND)
        assertEquals(2, notifier.posted.size)
        db.savingsGoalDao().upsertGoal(goal().copy(targetAmountMinor = 1500000).toEntity())
        run(ReminderType.GOAL_BEHIND); assertEquals(3, notifier.posted.size)
        db.savingsGoalDao().upsertGoal(goal().copy(status = GoalStatus.COMPLETED).toEntity())
        run(ReminderType.GOAL_BEHIND); assertTrue(notifier.active.isEmpty())
    }
    @Test fun weeklySummaryWithNoTransactionsReportsRecordedZerosOncePerConfiguredWeek() = runBlocking {
        configure(); run(ReminderType.WEEKLY_SUMMARY); run(ReminderType.WEEKLY_SUMMARY)
        assertEquals(1, notifier.posted.size)
        assertTrue(notifier.posted.single().second.body.contains("No income or expenses recorded"))
        clock.day = clock.day.plusWeeks(1); run(ReminderType.WEEKLY_SUMMARY)
        assertEquals(2, notifier.posted.size)
    }
    @Test fun aPaymentMadeAfterCandidateLoadingPreventsDelivery() = runBlocking {
        configure(); seed()
        val racing = object : ReminderContentSource by source {
            override suspend fun isCurrent(uid: String, type: ReminderType, candidate: ReminderNotification): Boolean {
                db.incomeDao().upsertIncome(invoice("Received").toEntity())
                return source.isCurrent(uid, type, candidate)
            }
        }
        val runner = ReminderDelivery(store, auth, notifier, racing, clock)
        runner.run(uid, ReminderType.FREELANCE_OVERDUE.key, store.read(uid)[ReminderType.FREELANCE_OVERDUE].revision, 1)
        assertTrue(notifier.posted.isEmpty())
    }
    @Test fun cachedFinancialDataAndJobsCannotCrossAuthenticatedUsers() = runBlocking {
        configure(); seed()
        val oldRevision = store.read(uid)[ReminderType.FREELANCE_OVERDUE].revision
        val other = "$uid-other"; auth.users.value = AuthUser(other, "Other", "other@example.test", true)
        assertTrue(runCatching { records.snapshot(uid, ReminderType.FREELANCE_OVERDUE) }.isFailure)
        assertTrue(records.snapshot(other, ReminderType.FREELANCE_OVERDUE).incomes.isEmpty())
        delivery.run(uid, ReminderType.FREELANCE_OVERDUE.key, oldRevision, 1)
        assertTrue(notifier.posted.isEmpty())
    }
    @Test fun notificationIntentsUseAnExistingSafeScreenAndCarryTheExpectedUser() = runBlocking {
        configure(); seed()
        val android = AndroidReminderNotifier(context)
        for (type in ReminderType.entries) {
            val candidate = source.candidates(uid, type).single()
            val intent = android.reminderIntent(uid, type, candidate)
            assertEquals(MainActivity::class.java.name, intent.component?.className)
            assertEquals(candidate.destination, intent.getStringExtra(AndroidReminderNotifier.EXTRA_DESTINATION_ROUTE))
            assertEquals(uid, intent.getStringExtra(AndroidReminderNotifier.EXTRA_USER_ID))
            assertNotNull(Routes.reminderDestination(candidate.destination))
            assertFalse(Routes.reminderMatchesUser(uid, "$uid-other"))
        }
    }
    @Test fun notificationFailureCanRetryAndDoesNotBlockOfflineIncomeOrExpenseStorage() = runBlocking {
        configure(); seed(); notifier.accept = false
        assertEquals(ReminderRunResult.RETRY, run(ReminderType.FREELANCE_OVERDUE))
        val remote = OfflineRemote()
        val incomes = OfflineFirstIncomeRepository(db.incomeDao(), remote, auth)
        val expenses = OfflineFirstExpenseRepository(db, remote, auth, context, ExpenseSyncScheduler {})
        incomes.addIncome(Income("new-income", uid, 12345, receivedDate = "2026-10-20"))
        expenses.addExpense(Expense(id = "new-expense", amountMinor = 6789, date = "2026-10-20"))
        assertEquals(12345L, incomes.getIncomeById("new-income")!!.amountMinor)
        assertEquals(6789L, expenses.getExpenseById("new-expense")!!.amountMinor)
        notifier.accept = true; run(ReminderType.FREELANCE_OVERDUE); run(ReminderType.FREELANCE_OVERDUE)
        assertEquals(1, notifier.posted.size)
    }
    private class TestClock(var day: LocalDate = LocalDate.of(2026, 10, 20)) : Clock() {
        override fun getZone() = AppClock.zone
        override fun withZone(zone: ZoneId) = fixed(instant(), zone)
        override fun instant(): Instant = day.atTime(21, 0).atZone(AppClock.zone).toInstant()
    }
    private class CaptureNotifier : ReminderNotifier {
        val posted = mutableListOf<Pair<ReminderType, ReminderNotification>>()
        val active = mutableMapOf<Pair<ReminderType, String>, ReminderNotification>()
        var accept = true
        override fun access() = NotificationAccess(true)
        override fun createChannels() = Unit
        override fun canPost(type: ReminderType) = true
        override fun post(uid: String, type: ReminderType, notification: ReminderNotification): Boolean {
            if (!accept) return false
            posted += type to notification; active[type to notification.key] = notification; return true
        }
        override fun cancelType(uid: String, type: ReminderType) { active.keys.removeAll { it.first == type } }
        override fun cancelOtherUsers(uid: String?) = Unit
        override fun retainEligible(uid: String, type: ReminderType, keys: Set<String>) { active.keys.removeAll { it.first == type && it.second !in keys } }
        override fun cancelItem(uid: String, type: ReminderType, key: String) { active.remove(type to key) }
    }
    private class OfflineRemote : IncomeRemoteDataSource, ExpenseRemoteDataSource {
        override fun observeIncome(userId: String): Flow<List<Income>> = emptyFlow()
        override fun observe(userId: String): Flow<ExpenseRemoteSnapshot> = emptyFlow()
        override suspend fun getAllIncome(userId: String) = emptyList<Income>()
        override suspend fun getIncomeById(userId: String, incomeId: String): Income? = null
        override suspend fun upsertIncome(userId: String, income: Income): Unit = error("Offline")
        override suspend fun deleteIncome(userId: String, incomeId: String): Unit = error("Offline")
        override suspend fun insert(userId: String, expense: Expense, allowDuplicate: Boolean): Unit = error("Offline")
        override suspend fun update(userId: String, expense: Expense): Unit = error("Offline")
        override suspend fun delete(userId: String, id: String): Unit = error("Offline")
    }
}

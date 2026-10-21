package com.pfms.app

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.work.*
import androidx.work.testing.TestListenableWorkerBuilder
import com.pfms.app.data.reminder.*
import com.pfms.app.data.sync.ExpenseReminderWorker
import com.pfms.app.domain.model.*
import com.pfms.app.domain.usecase.dashboard.AppClock
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Test
import java.time.*
import java.time.Clock
import java.util.UUID

internal class ReminderFixture {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val uid = "fr38-${UUID.randomUUID()}"
    val auth = ExpenseTestAuth().apply { users.value = AuthUser(uid, "Test", "test@example.test", true) }
    val store = ReminderPreferencesStore(context)
    val clock = Clock.fixed(LocalDate.of(2026, 10, 20).atTime(21, 0).atZone(AppClock.zone).toInstant(), AppClock.zone)
    val notifier = Notifier()
    val source = Source()
    val delivery = ReminderDelivery(store, auth, notifier, source, clock)
    suspend fun change(type: ReminderType, enabled: Boolean? = null, time: Int? = null) =
        store.locked { store.update(uid, type, enabled, time, null) }
    suspend fun run(type: ReminderType = ReminderType.DUE_RECURRING, revision: String? = null,
        notBefore: Long = 1) = delivery.run(uid, type.key, revision ?: store.read(uid)[type].revision, notBefore)
    class Source : ReminderContentSource {
        var calls = 0
        var before: (suspend () -> Unit)? = null
        override suspend fun candidates(uid: String, type: ReminderType): List<ReminderNotification> {
            calls++; before?.invoke()
            return listOf(ReminderNotification("item", "Reminder", "Test", "recurring_expenses"))
        }
    }
    class Notifier : ReminderNotifier {
        var permission = true
        var accept = true
        var posted = 0
        override fun access() = NotificationAccess(permission, !permission)
        override fun createChannels() = Unit
        override fun canPost(type: ReminderType) = permission
        override fun post(uid: String, type: ReminderType, notification: ReminderNotification): Boolean {
            if (accept) posted++
            return accept
        }
        override fun cancelType(uid: String, type: ReminderType) = Unit
        override fun cancelOtherUsers(uid: String?) = Unit
    }
}

class ReminderFoundationTest {
    private val fixture = ReminderFixture()
    @After fun cancelWork() { WorkManager.getInstance(fixture.context).cancelAllWorkByTag(ReminderWorkScheduler.ALL_TAG).result.get() }

    @Test fun preferencesPersistAndStayIndependentBetweenUsersAndTypes() = runBlocking {
        val f = fixture
        f.change(ReminderType.DAILY_LOG, true, 1234)
        f.change(ReminderType.DUE_RECURRING, false)
        val reopened = ReminderPreferencesStore(f.context).read(f.uid)
        assertTrue(reopened[ReminderType.DAILY_LOG].enabled)
        assertEquals(1234, reopened[ReminderType.DAILY_LOG].minuteOfDay)
        assertFalse(reopened[ReminderType.DUE_RECURRING].enabled)
        assertTrue(reopened[ReminderType.SUBSCRIPTION_REVIEW].enabled)
        assertFalse(f.store.read("${f.uid}-other")[ReminderType.DAILY_LOG].enabled)
        assertNotEquals(reopened[ReminderType.DAILY_LOG].revision, "initial-v1")
    }

    @Test fun scheduledWorkIsUniqueUnchangedSettingsKeepItAndTimeChangesReplaceIt() = runBlocking {
        val f = fixture; val scheduler = ReminderWorkScheduler(f.context, f.clock, f.notifier)
        f.change(ReminderType.DAILY_LOG, true, 23 * 60)
        scheduler.reconcile(f.store.read(f.uid))
        val manager = WorkManager.getInstance(f.context)
        fun work() = manager.getWorkInfosForUniqueWork(ReminderWorkScheduler.workName(f.uid, ReminderType.DAILY_LOG)).get().single { !it.state.isFinished }
        val first = work()
        scheduler.reconcile(f.store.read(f.uid)); assertEquals(first.id, work().id)
        assertTrue(ReminderWorkScheduler.userTag(f.uid) in first.tags)
        f.change(ReminderType.DAILY_LOG, time = 23 * 60 + 30)
        scheduler.reconcile(f.store.read(f.uid))
        assertNotEquals(first.id, work().id)
        // CANCEL_AND_REENQUEUE may prune the replaced row instead of retaining CANCELLED history.
        val replaced = manager.getWorkInfoById(first.id).get()
        assertTrue(replaced == null || replaced.state.isFinished)
        f.change(ReminderType.DAILY_LOG, false); scheduler.reconcile(f.store.read(f.uid))
        assertTrue(manager.getWorkInfosForUniqueWork(ReminderWorkScheduler.workName(f.uid, ReminderType.DAILY_LOG)).get().all { it.state.isFinished })
    }

    @Test fun signOutCancelsAllReminderWorkAndAnotherUserGetsSeparateJobs() = runBlocking {
        val f = fixture; val scheduler = ReminderWorkScheduler(f.context, f.clock, f.notifier)
        scheduler.reconcile(f.store.read(f.uid))
        val manager = WorkManager.getInstance(f.context)
        val old = manager.getWorkInfosByTag(ReminderWorkScheduler.userTag(f.uid)).get().filter { !it.state.isFinished }
        assertEquals(2, old.size)
        scheduler.reconcile(UserReminderSettings.defaults("${f.uid}-other"))
        old.forEach { assertEquals(WorkInfo.State.CANCELLED, manager.getWorkInfoById(it.id).get()!!.state) }
        scheduler.reconcile(null)
        assertTrue(manager.getWorkInfosByTag(ReminderWorkScheduler.ALL_TAG).get().all { it.state.isFinished })
    }

    @Test fun disabledOrReenabledSettingsRejectPreviouslyScheduledWork() = runBlocking {
        val f = fixture; val old = f.store.read(f.uid)[ReminderType.DUE_RECURRING].revision
        f.change(ReminderType.DUE_RECURRING, false)
        assertEquals(ReminderRunResult.COMPLETE, f.run(revision = old)); assertEquals(0, f.notifier.posted)
        f.change(ReminderType.DUE_RECURRING, true)
        f.run(revision = old); assertEquals(0, f.notifier.posted)
        f.run(); assertEquals(1, f.notifier.posted)
    }

    @Test fun disablingDuringContentLoadingStopsDelivery() = runBlocking {
        val f = fixture
        f.source.before = { f.change(ReminderType.DUE_RECURRING, false) }
        f.run(); assertEquals(0, f.notifier.posted)
    }

    @Test fun accountChangeDuringContentLoadingStopsDelivery() = runBlocking {
        val f = fixture
        f.source.before = { f.auth.users.value = AuthUser("other", "Other", "other@example.test", true) }
        f.run(); assertEquals(0, f.notifier.posted)
    }

    @Test fun durableClaimsPreventRedeliveryEvenAfterNotificationDismissalAndNewRunner() = runBlocking {
        val f = fixture
        f.run(); assertEquals(1, f.notifier.posted)
        val reopened = ReminderDelivery(ReminderPreferencesStore(f.context), f.auth, f.notifier, f.source, f.clock)
        val revision = f.store.read(f.uid)[ReminderType.DUE_RECURRING].revision
        reopened.run(f.uid, ReminderType.DUE_RECURRING.key, revision, 1)
        assertEquals(1, f.notifier.posted)
    }

    @Test fun overlappingWorkersClaimOnlyOneNotification() = runBlocking {
        val f = fixture
        coroutineScope { listOf(async { f.run() }, async { f.run() }, async { f.run() }).awaitAll() }
        assertEquals(1, f.notifier.posted)
    }

    @Test fun allSevenTypesCanHaveIndependentWorkWithoutAddingEligibilityRules() = runBlocking {
        val f = fixture
        for (type in ReminderType.entries) f.change(type, true, 23 * 60)
        ReminderWorkScheduler(f.context, f.clock, f.notifier).reconcile(f.store.read(f.uid))
        val jobs = WorkManager.getInstance(f.context).getWorkInfosByTag(ReminderWorkScheduler.userTag(f.uid)).get()
            .filter { !it.state.isFinished }
        assertEquals(7, jobs.size)
        assertEquals(7, jobs.map { it.id }.toSet().size)
    }

    @Test fun deniedPermissionDoesNotReadFinancialDataOrConsumeTheDeliveryClaim() = runBlocking {
        val f = fixture; f.notifier.permission = false
        assertEquals(ReminderRunResult.COMPLETE, f.run()); assertEquals(0, f.source.calls)
        f.notifier.permission = true; f.run(); assertEquals(1, f.notifier.posted)
    }

    @Test fun earlyJobsRetryWithoutPostingAndFailedPostsCanBeRetriedSafely() = runBlocking {
        val f = fixture
        assertEquals(ReminderRunResult.RETRY, f.run(notBefore = f.clock.millis() + 60_000))
        assertEquals(0, f.source.calls)
        f.notifier.accept = false; assertEquals(ReminderRunResult.RETRY, f.run())
        f.notifier.accept = true; f.run(); f.run(); assertEquals(1, f.notifier.posted)
    }

    @Test fun oldGlobalWorkerWithoutUserAndRevisionCannotBypassPreferences() = runBlocking {
        val f = fixture
        val worker = TestListenableWorkerBuilder<ExpenseReminderWorker>(f.context).setWorkerFactory(object : WorkerFactory() {
            override fun createWorker(appContext: Context, workerClassName: String, workerParameters: WorkerParameters) =
                ExpenseReminderWorker(appContext, workerParameters, f.delivery)
        }).build()
        assertEquals(ListenableWorker.Result.success(), worker.doWork())
        assertEquals(0, f.source.calls); assertEquals(0, f.notifier.posted)
    }

    @Test fun repositoryRejectsCrossUserWritesAndKeepsPreferencesAfterSchedulingFails() = runBlocking {
        val f = fixture
        val failing = object : ReminderScheduler {
            override suspend fun reconcile(settings: UserReminderSettings?, force: Boolean) { error("Scheduler unavailable") }
        }
        val repo = LocalReminderSettingsRepository(f.auth, f.store, ReminderCoordinator(f.auth, f.store, failing), f.notifier)
        assertTrue(repo.update("other", ReminderType.DAILY_LOG, true).isFailure)
        val outcome = repo.update(f.uid, ReminderType.DAILY_LOG, true)
        assertTrue(outcome.exceptionOrNull() is ReminderSchedulingException)
        assertTrue(f.store.read(f.uid)[ReminderType.DAILY_LOG].enabled)
    }
}

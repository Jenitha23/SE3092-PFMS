package com.pfms.app

import android.content.*
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import androidx.work.*
import com.pfms.app.data.reminder.*
import com.pfms.app.domain.model.*
import com.pfms.app.domain.usecase.dashboard.AppClock
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.time.Clock
import java.util.UUID

/** Run each stage in a new instrumentation process, force-stopping the app between stages. */
class ReminderRestartTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val prefs = context.getSharedPreferences("fr38_restart_test", Context.MODE_PRIVATE)
    private fun enabled() = assumeTrue(InstrumentationRegistry.getArguments().getString("fr38PersistentWork") == "true")
    private fun setup(uid: String): Pair<ReminderPreferencesStore, ReminderWorkScheduler> {
        val auth = ExpenseTestAuth().apply { users.value = AuthUser(uid, "Test", "test@example.test", true) }
        val store = ReminderPreferencesStore(context)
        val scheduler = ReminderWorkScheduler(context, Clock.system(AppClock.zone), ReminderFixture.Notifier())
        val coordinator = ReminderCoordinator(auth, store, scheduler)
        WorkManager.initialize(context, Configuration.Builder().setWorkerFactory(object : WorkerFactory() {
            override fun createWorker(appContext: Context, workerClassName: String, workerParameters: WorkerParameters): ListenableWorker? =
                if (workerClassName == ReminderMaintenanceWorker::class.java.name)
                    ReminderMaintenanceWorker(appContext, workerParameters, coordinator) else null
        }).build())
        return store to scheduler
    }
    @Test fun stage1PersistPreferencesAndUniqueWork() = runBlocking {
        enabled()
        val uid = "fr38-restart-${UUID.randomUUID()}"
        val (store, scheduler) = setup(uid)
        store.locked {
            ReminderType.entries.forEach { store.update(uid, it, it == ReminderType.DAILY_LOG, 1439, null) }
        }
        scheduler.reconcile(store.read(uid))
        val jobs = WorkManager.getInstance(context).getWorkInfosForUniqueWork(ReminderWorkScheduler.workName(uid, ReminderType.DAILY_LOG))
            .get().filter { !it.state.isFinished }
        assertEquals(1, jobs.size)
        assertTrue(prefs.edit().putString("uid", uid).putString("work", jobs.single().id.toString()).commit())
    }
    @Test fun stage2RestoreThenRepairClockAndBootSchedulesAndCancel() = runBlocking {
        enabled()
        val uid = prefs.getString("uid", null)!!
        val (store, scheduler) = setup(uid)
        val settings = store.read(uid)
        assertTrue(settings[ReminderType.DAILY_LOG].enabled)
        assertEquals(1439, settings[ReminderType.DAILY_LOG].minuteOfDay)
        val manager = WorkManager.getInstance(context)
        fun current() = manager.getWorkInfosForUniqueWork(ReminderWorkScheduler.workName(uid, ReminderType.DAILY_LOG)).get()
            .single { !it.state.isFinished }.id.toString()
        assertEquals(prefs.getString("work", null), current())
        scheduler.reconcile(settings); assertEquals(prefs.getString("work", null), current())
        for (action in listOf(Intent.ACTION_TIME_CHANGED, Intent.ACTION_TIMEZONE_CHANGED, Intent.ACTION_BOOT_COMPLETED)) {
            val before = current()
            ReminderScheduleReceiver().onReceive(context, Intent(action))
            withTimeout(20_000) { while (current() == before) delay(100) }
            withTimeout(20_000) {
                while (manager.getWorkInfosForUniqueWork("pfms_reminder_clock_change").get().any { !it.state.isFinished }) delay(100)
            }
        }
        store.locked { store.update(uid, ReminderType.DAILY_LOG, false, null, null) }
        scheduler.reconcile(store.read(uid))
        assertTrue(manager.getWorkInfosForUniqueWork(ReminderWorkScheduler.workName(uid, ReminderType.DAILY_LOG)).get().all { it.state.isFinished })
    }
}

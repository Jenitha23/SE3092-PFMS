package com.pfms.app.data.reminder

import android.content.Context
import androidx.work.*
import com.pfms.app.data.sync.ExpenseReminderWorker
import com.pfms.app.domain.model.*
import com.pfms.app.domain.usecase.ReminderSchedulePolicy
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.time.Clock
import java.time.Duration
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

interface ReminderScheduler {
    suspend fun reconcile(settings: UserReminderSettings?, force: Boolean = false)
}

@Singleton
class ReminderWorkScheduler @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val clock: Clock,
    private val notifier: ReminderNotifier
) : ReminderScheduler {
    private val mutex = Mutex()
    override suspend fun reconcile(settings: UserReminderSettings?, force: Boolean) = withContext(Dispatchers.IO) {
        mutex.withLock {
            val work = WorkManager.getInstance(context)
            notifier.createChannels()
            work.cancelUniqueWork("ExpenseReminderPeriodicWork").result.get()
            val userTag = settings?.userId?.let(::userTag)
            work.getWorkInfosByTag(ALL_TAG).get().filter { !it.state.isFinished && userTag !in it.tags }
                .forEach { work.cancelWorkById(it.id).result.get() }
            notifier.cancelOtherUsers(settings?.userId)
            if (settings == null) return@withLock
            ReminderType.entries.forEach { type ->
                val schedule = settings[type]
                val name = workName(settings.userId, type)
                if (!schedule.enabled) {
                    work.cancelUniqueWork(name).result.get()
                    notifier.cancelType(settings.userId, type)
                } else {
                    val tag = revisionTag(schedule.revision)
                    val unchanged = work.getWorkInfosForUniqueWork(name).get().any { !it.state.isFinished && tag in it.tags }
                    if (!force && unchanged) return@forEach
                    val now = clock.instant()
                    val target = ReminderSchedulePolicy.nextAt(now, type, schedule)
                    val request = PeriodicWorkRequestBuilder<ExpenseReminderWorker>(
                        if (type == ReminderType.WEEKLY_SUMMARY) 7 else 1, TimeUnit.DAYS)
                        .setInitialDelay(Duration.between(now, target).toMillis().coerceAtLeast(0), TimeUnit.MILLISECONDS)
                        .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 15, TimeUnit.MINUTES)
                        .setInputData(workDataOf(USER to settings.userId, TYPE to type.key,
                            REVISION to schedule.revision, NOT_BEFORE to target.toEpochMilli()))
                        .addTag(ALL_TAG).addTag(userTag!!).addTag(tag).build()
                    work.enqueueUniquePeriodicWork(name, ExistingPeriodicWorkPolicy.CANCEL_AND_REENQUEUE, request).result.get()
                }
            }
        }
    }
    companion object {
        const val ALL_TAG = "pfms_financial_reminders"
        const val USER = "reminder_user"
        const val TYPE = "reminder_type"
        const val REVISION = "reminder_revision"
        const val NOT_BEFORE = "reminder_not_before"
        fun userTag(uid: String) = "pfms_reminder_user_${ReminderPreferencesStore.identity(uid)}"
        fun workName(uid: String, type: ReminderType) = "${userTag(uid)}_${type.key}"
        fun revisionTag(revision: String) = "reminder_revision_$revision"
    }
}

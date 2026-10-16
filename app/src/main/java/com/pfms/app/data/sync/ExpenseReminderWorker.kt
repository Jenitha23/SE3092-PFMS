package com.pfms.app.data.sync

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.hilt.work.HiltWorker
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.google.firebase.auth.FirebaseAuth
import com.pfms.app.MainActivity
import com.pfms.app.data.local.dao.RecurringExpenseDao
import com.pfms.app.data.local.entity.RecurringExpenseEntity
import com.pfms.app.domain.usecase.RecurringExpenseCalculator
import com.pfms.app.ui.navigation.Routes
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.time.LocalDate
import java.util.Locale
import java.util.concurrent.TimeUnit
import com.pfms.app.domain.repository.AdvancedExpenseRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout
import android.util.Log

@HiltWorker
class ExpenseReminderWorker @AssistedInject constructor(
    @Assisted private val context: Context,
    @Assisted params: WorkerParameters,
    private val recurringDao: RecurringExpenseDao,
    private val auth: FirebaseAuth,
    private val advancedExpenses: AdvancedExpenseRepository
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val uid = auth.currentUser?.uid ?: return Result.success()

        createNotificationChannel()

        val today = LocalDate.now(RecurringExpenseCalculator.COLOMBO_ZONE)

        // Refresh even when no expense screen is open; fall back to Room when offline.
        try {
            withTimeout(10_000) { advancedExpenses.refreshTemplates() }
        } catch (e: CancellationException) {
            if (e !is TimeoutCancellationException) throw e
        } catch (e: Exception) {
            Log.w("ExpenseReminderWorker", "Using cached reminders; server refresh unavailable", e)
        }

        val activeTemplates: List<RecurringExpenseEntity> = try {
            recurringDao.getActiveTemplates(uid)
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            return Result.retry()
        }
        if (auth.currentUser?.uid != uid) return Result.success()

        // Check if permission is granted for Android 13+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(
                    context,
                    android.Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                return Result.success()
            }
        }

        // Scan active templates and notify for due items or review reminders
        activeTemplates.forEach { template ->
            if (template.flag.equals("CANCEL", ignoreCase = true)) return@forEach
            val notificationId = "${uid}:${template.id}".hashCode()
            val dueDate = runCatching { LocalDate.parse(template.nextDueDate) }.getOrNull()
            val isDue = dueDate != null && !dueDate.isAfter(today)

            val isReviewDate = template.reminderDate.isNotBlank() &&
                    runCatching { LocalDate.parse(template.reminderDate) }.getOrNull()?.let { !it.isAfter(today) } == true

            if (template.isSubscription) {
                if (isReviewDate || template.flag.equals("REVIEW", ignoreCase = true)) {
                    showNotification(
                        notificationId = notificationId,
                        title = "Subscription Review: ${template.title}",
                        body = "Your subscription '${template.title}' is scheduled for review (LKR ${String.format(Locale.US, "%.2f", template.amountMinor / 100.0)}).",
                        targetRoute = Routes.Subscriptions
                    )
                } else if (isDue) {
                    showNotification(
                        notificationId = notificationId,
                        title = "Subscription Payment Due: ${template.title}",
                        body = "LKR ${String.format(Locale.US, "%.2f", template.amountMinor / 100.0)} is due for renewal today.",
                        targetRoute = Routes.Subscriptions
                    )
                }
            } else if (isDue) {
                showNotification(
                    notificationId = notificationId,
                    title = "Recurring Expense Due: ${template.title}",
                    body = "LKR ${String.format(Locale.US, "%.2f", template.amountMinor / 100.0)} is due today (${template.categoryName}). Tap to confirm.",
                    targetRoute = Routes.RecurringExpenses
                )
            }
        }

        return Result.success()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Expense & Subscription Reminders",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Reminders for recurring expenses and upcoming subscription reviews."
            }
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            manager?.createNotificationChannel(channel)
        }
    }

    private fun showNotification(
        notificationId: Int,
        title: String,
        body: String,
        targetRoute: String
    ) {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra(EXTRA_DESTINATION_ROUTE, targetRoute)
        }

        val pendingIntent = PendingIntent.getActivity(
            context,
            notificationId,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0)
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(title)
            .setContentText(body)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()

        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
        manager?.notify(notificationId, notification)
    }

    companion object {
        const val CHANNEL_ID = "pfms_expense_reminders"
        const val EXTRA_DESTINATION_ROUTE = "extra_destination_route"
        private const val WORK_NAME = "ExpenseReminderPeriodicWork"

        fun enqueuePeriodic(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.NOT_REQUIRED)
                .build()

            val request = PeriodicWorkRequestBuilder<ExpenseReminderWorker>(
                1, TimeUnit.DAYS
            )
                .setConstraints(constraints)
                .build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                request
            )
        }

        fun runOnce(context: Context) {
            val request = OneTimeWorkRequestBuilder<ExpenseReminderWorker>().build()
            WorkManager.getInstance(context).enqueue(request)
        }
    }
}

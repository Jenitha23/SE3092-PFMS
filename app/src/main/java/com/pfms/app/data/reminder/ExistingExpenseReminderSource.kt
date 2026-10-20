package com.pfms.app.data.reminder

import android.util.Log
import com.pfms.app.data.local.dao.RecurringExpenseDao
import com.pfms.app.domain.model.ReminderType
import com.pfms.app.domain.repository.AdvancedExpenseRepository
import com.pfms.app.domain.usecase.dashboard.AppClock
import com.pfms.app.ui.navigation.Routes
import kotlinx.coroutines.*
import java.time.Clock
import java.time.LocalDate
import java.util.Locale
import javax.inject.Inject

interface ReminderContentSource {
    suspend fun candidates(uid: String, type: ReminderType): List<ReminderNotification>
}

/** The existing two expense rules, extracted from ExpenseReminderWorker; no new financial rules. */
class ExistingExpenseReminderSource @Inject constructor(
    private val dao: RecurringExpenseDao,
    private val repository: AdvancedExpenseRepository,
    private val preferences: ReminderPreferencesStore,
    private val clock: Clock
) : ReminderContentSource {
    override suspend fun candidates(uid: String, type: ReminderType): List<ReminderNotification> {
        if (type != ReminderType.DUE_RECURRING && type != ReminderType.SUBSCRIPTION_REVIEW) return emptyList()
        try { withTimeout(10_000) { repository.refreshTemplates() } }
        catch (error: CancellationException) { if (error !is TimeoutCancellationException) throw error }
        catch (error: Exception) { Log.w("FinancialReminders", "Using cached expense templates", error) }
        val today = AppClock.today(clock)
        val reviewEnabled = preferences.read(uid)[ReminderType.SUBSCRIPTION_REVIEW].enabled
        return dao.getActiveTemplates(uid).mapNotNull { item ->
            if (item.flag.equals("CANCEL", true)) return@mapNotNull null
            val due = runCatching { LocalDate.parse(item.nextDueDate) }.getOrNull()?.let { !it.isAfter(today) } == true
            val review = item.isSubscription && (item.flag.equals("REVIEW", true) ||
                runCatching { LocalDate.parse(item.reminderDate) }.getOrNull()?.let { !it.isAfter(today) } == true)
            val amount = String.format(Locale.US, "%.2f", item.amountMinor / 100.0)
            when {
                type == ReminderType.SUBSCRIPTION_REVIEW && review -> ReminderNotification(item.id,
                    "Subscription Review: ${item.title}", "Your subscription '${item.title}' is scheduled for review (LKR $amount).", Routes.Subscriptions)
                type == ReminderType.DUE_RECURRING && due && !(review && reviewEnabled) -> ReminderNotification(item.id,
                    if (item.isSubscription) "Subscription Payment Due: ${item.title}" else "Recurring Expense Due: ${item.title}",
                    if (item.isSubscription) "LKR $amount is due for renewal today."
                    else "LKR $amount is due today (${item.categoryName}). Tap to confirm.",
                    if (item.isSubscription) Routes.Subscriptions else Routes.RecurringExpenses)
                else -> null
            }
        }
    }
}

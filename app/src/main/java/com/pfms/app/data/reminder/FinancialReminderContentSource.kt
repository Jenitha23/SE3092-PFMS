package com.pfms.app.data.reminder

import android.util.Log
import com.pfms.app.domain.model.*
import com.pfms.app.domain.repository.AdvancedExpenseRepository
import com.pfms.app.domain.repository.FinancialReminderRepository
import com.pfms.app.domain.usecase.FinancialReminderUseCases
import com.pfms.app.domain.usecase.ReminderSchedulePolicy
import com.pfms.app.domain.usecase.dashboard.AppClock
import com.pfms.app.ui.navigation.Routes
import kotlinx.coroutines.*
import java.time.Clock
import java.time.LocalDate
import javax.inject.Inject

interface ReminderContentSource {
    suspend fun candidates(uid: String, type: ReminderType): List<ReminderNotification>
    suspend fun isCurrent(uid: String, type: ReminderType, candidate: ReminderNotification): Boolean = true
}

/** Adapts repository snapshots and pure domain rules to the existing delivery system. */
class FinancialReminderContentSource @Inject constructor(
    private val repository: FinancialReminderRepository,
    private val advancedExpenses: AdvancedExpenseRepository,
    private val preferences: ReminderPreferencesStore,
    private val clock: Clock,
    private val rules: FinancialReminderUseCases
) : ReminderContentSource {
    override suspend fun candidates(uid: String, type: ReminderType): List<ReminderNotification> {
        // Preserve the previous bounded refresh and Room fallback for recurring/subscription checks.
        if (type == ReminderType.DUE_RECURRING || type == ReminderType.SUBSCRIPTION_REVIEW) {
            try { withTimeout(10_000) { advancedExpenses.refreshTemplates() } }
            catch (error: CancellationException) { if (error !is TimeoutCancellationException) throw error }
            catch (error: Exception) { Log.w("FinancialReminders", "Using cached expense templates", error) }
        }
        return evaluate(uid, type)
    }

    override suspend fun isCurrent(uid: String, type: ReminderType, candidate: ReminderNotification): Boolean =
        evaluate(uid, type).any { it.key == candidate.key && it.occurrence == candidate.occurrence && it.validation == candidate.validation }

    private suspend fun evaluate(uid: String, type: ReminderType): List<ReminderNotification> {
        val records = repository.snapshot(uid, type)
        require(records.userId == uid)
        val today = AppClock.today(clock)
        var episode: Long? = null
        val messages = when (type) {
            ReminderType.DAILY_LOG -> rules.daily()
            ReminderType.SALARY_DAY -> rules.salary(records, today)
            ReminderType.DUE_RECURRING -> rules.recurring(records, today)
            ReminderType.FREELANCE_OVERDUE -> rules.freelance(records, today)
            ReminderType.SUBSCRIPTION_REVIEW -> rules.subscriptions(records, today)
            ReminderType.WEEKLY_SUMMARY -> {
                val schedule = preferences.read(uid)[type]
                val slot = ReminderSchedulePolicy.deliverySlot(clock.instant(), type, schedule) ?: return emptyList()
                rules.weekly(records, LocalDate.parse(slot))
            }
            ReminderType.GOAL_BEHIND -> {
                val assessment = rules.goal(records, today)
                episode = preferences.locked { preferences.goalEpisode(uid, assessment.state, assessment.reminder != null) }
                listOfNotNull(assessment.reminder)
            }
        }
        return messages.map { message -> ReminderNotification(message.key, message.title, message.body,
            when (message.destination) {
                ReminderDestination.DAILY_ENTRY -> Routes.Dashboard
                ReminderDestination.SALARY, ReminderDestination.INVOICES -> Routes.IncomeList
                ReminderDestination.RECURRING -> Routes.RecurringExpenses
                ReminderDestination.SUBSCRIPTIONS -> Routes.Subscriptions
                ReminderDestination.SUMMARY -> Routes.SpendingAnalytics
                ReminderDestination.GOAL -> Routes.SavingsGoal
            }, episode?.let { "episode:$it" } ?: message.occurrence, message.toString()) }
    }
}

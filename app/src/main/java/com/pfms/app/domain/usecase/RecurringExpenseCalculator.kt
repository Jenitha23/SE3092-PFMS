package com.pfms.app.domain.usecase

import com.pfms.app.domain.model.Recurrence
import com.pfms.app.domain.model.RecurringExpense
import com.pfms.app.domain.model.SubscriptionFlag
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate
import java.time.ZoneId

/**
 * Domain calculations for recurring expenses and subscription management (FR-19, FR-20).
 * Handles due-date progression, leap years, month boundaries, and monthly subscription equivalents.
 */
object RecurringExpenseCalculator {

    val COLOMBO_ZONE: ZoneId = ZoneId.of("Asia/Colombo")

    /**
     * Calculates the next due date based on the specified recurrence pattern.
     * Accurately handles month boundaries (e.g. Jan 31 -> Feb 28/29) and leap years.
     */
    fun calculateNextDueDate(currentDueDate: LocalDate, recurrence: Recurrence): LocalDate {
        return when (recurrence) {
            Recurrence.DAILY -> currentDueDate.plusDays(1)
            Recurrence.WEEKLY -> currentDueDate.plusWeeks(1)
            Recurrence.MONTHLY -> currentDueDate.plusMonths(1)
        }
    }

    /**
     * Checks whether a recurring expense template is currently due in Asia/Colombo time zone.
     * A template is due if it is active and its nextDueDate is on or before today.
     */
    fun isDue(template: RecurringExpense, today: LocalDate = LocalDate.now(COLOMBO_ZONE)): Boolean {
        if (!template.active || (template.subscription && template.flag == SubscriptionFlag.CANCEL)) return false
        val dueDate = runCatching { LocalDate.parse(template.nextDueDate) }.getOrNull() ?: return false
        return !dueDate.isAfter(today)
    }

    /**
     * Checks whether a subscription is due for review on its configured date (FR-20).
     * Returns true if the subscription is active, not cancelled, and:
     * - The flag is explicitly set to REVIEW, or
     * - A reminderDate is configured and is on or before today.
     */
    fun isReviewDue(template: RecurringExpense, today: LocalDate = LocalDate.now(COLOMBO_ZONE)): Boolean {
        if (!template.subscription || !template.active || template.flag == SubscriptionFlag.CANCEL) return false
        if (template.flag == SubscriptionFlag.REVIEW) return true
        if (template.reminderDate.isBlank()) return false
        val reminder = runCatching { LocalDate.parse(template.reminderDate) }.getOrNull() ?: return false
        return !reminder.isAfter(today)
    }


    /**
     * Calculates the total monthly equivalent amount for active subscriptions in minor units (LKR cents).
     * Conversion rules according to SRS (FR-20):
     * - Monthly: exact monthly amount.
     * - Weekly: amount * 52 / 12, rounded HALF_UP.
     * - Daily: amount * 365 / 12, rounded HALF_UP.
     * - Cancelled / paused subscriptions are excluded.
     */
    fun calculateMonthlySubscriptionTotal(subscriptions: List<RecurringExpense>): Long {
        return monthlyTotal(subscriptions.filter { it.subscription && it.active && it.flag != SubscriptionFlag.CANCEL })
    }

    /** Monthly equivalent of scheduled recurring expenses; paused templates are excluded. */
    fun calculateMonthlyScheduledTotal(templates: List<RecurringExpense>): Long =
        monthlyTotal(templates.filter { !it.subscription && it.active })

    fun isDueSoon(template: RecurringExpense, today: LocalDate = LocalDate.now(COLOMBO_ZONE)): Boolean {
        if (!template.active || (template.subscription && template.flag == SubscriptionFlag.CANCEL)) return false
        val date = runCatching { LocalDate.parse(template.nextDueDate) }.getOrNull() ?: return false
        return !date.isAfter(today.plusDays(7))
    }

    private fun monthlyTotal(templates: List<RecurringExpense>): Long {
        return templates
            .fold(0L) { total, item ->
                val monthly = when (item.recurrence) {
                    Recurrence.MONTHLY -> item.amountMinor
                    Recurrence.WEEKLY -> {
                        BigDecimal.valueOf(item.amountMinor)
                            .multiply(BigDecimal.valueOf(52))
                            .divide(BigDecimal.valueOf(12), 0, RoundingMode.HALF_UP)
                            .longValueExact()
                    }
                    Recurrence.DAILY -> {
                        BigDecimal.valueOf(item.amountMinor)
                            .multiply(BigDecimal.valueOf(365))
                            .divide(BigDecimal.valueOf(12), 0, RoundingMode.HALF_UP)
                            .longValueExact()
                    }
                }
                Math.addExact(total, monthly)
            }
    }
}

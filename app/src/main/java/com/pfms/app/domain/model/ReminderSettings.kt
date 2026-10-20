package com.pfms.app.domain.model

enum class ReminderType(val key: String, val title: String, val available: Boolean = false) {
    DAILY_LOG("daily_log", "Daily log reminder"),
    SALARY_DAY("salary_day", "Salary day reminder"),
    DUE_RECURRING("due_recurring", "Due recurring items reminder", true),
    FREELANCE_OVERDUE("freelance_overdue", "Freelance invoice overdue reminder"),
    SUBSCRIPTION_REVIEW("subscription_review", "Subscription review reminder", true),
    WEEKLY_SUMMARY("weekly_summary", "Weekly summary reminder"),
    GOAL_BEHIND("goal_behind", "Goal pace Behind reminder");

    companion object { fun fromKey(key: String?) = entries.firstOrNull { it.key == key } }
}

data class ReminderSchedule(
    val enabled: Boolean = false,
    val minuteOfDay: Int = 9 * 60,
    val dayOfWeek: Int = 7,
    val revision: String = "initial-v1"
) {
    init { require(minuteOfDay in 0..1439); require(dayOfWeek in 1..7) }
}

data class UserReminderSettings(val userId: String, val schedules: Map<ReminderType, ReminderSchedule>) {
    operator fun get(type: ReminderType): ReminderSchedule = schedules.getValue(type)
    companion object {
        fun defaults(uid: String) = UserReminderSettings(uid, ReminderType.entries.associateWith { type ->
            ReminderSchedule(
                // Preserve the two notification types that existed before these settings.
                enabled = type == ReminderType.DUE_RECURRING || type == ReminderType.SUBSCRIPTION_REVIEW,
                minuteOfDay = when (type) { ReminderType.DAILY_LOG -> 20 * 60; ReminderType.WEEKLY_SUMMARY -> 18 * 60; else -> 9 * 60 })
        })
    }
}

data class NotificationAccess(val allowed: Boolean = false, val needsRuntimePermission: Boolean = false)

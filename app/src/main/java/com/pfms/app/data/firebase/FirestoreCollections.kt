package com.pfms.app.data.firebase

/**
 * Single source of truth for collection names.
 * Layout: users/{uid}/<subcollection>/{docId}. Keep this in sync with firestore.rules.
 */
object FirestoreCollections {
    const val USERS = "users"

    const val CATEGORIES = "categories"
    const val INCOME_SOURCES = "incomeSources"
    const val INCOME = "income"
    const val EXPENSES = "expenses"
    const val SAVINGS_GOALS = "savingsGoals"
    const val GOAL_CONTRIBUTIONS = "goalContributions"
    const val RECURRING_TEMPLATES = "recurringTemplates"
    const val REMINDERS = "reminders"

    /**
     * Every subcollection under users/{uid}. Account deletion (FR-06) walks this list, so any
     * new subcollection MUST be added here (and to firestore.rules).
     */
    val USER_SUBCOLLECTIONS = listOf(
        CATEGORIES,
        INCOME_SOURCES,
        INCOME,
        EXPENSES,
        SAVINGS_GOALS,
        GOAL_CONTRIBUTIONS,
        RECURRING_TEMPLATES,
        REMINDERS
    )
}

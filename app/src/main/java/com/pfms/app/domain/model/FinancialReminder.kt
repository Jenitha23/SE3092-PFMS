package com.pfms.app.domain.model

/** An authenticated, consistent snapshot of the existing offline cache. */
data class ReminderFinancialRecords(
    val userId: String,
    val incomes: List<Income> = emptyList(),
    val expenses: List<Expense> = emptyList(),
    val salary: SalaryTemplate? = null,
    val recurring: List<RecurringExpense> = emptyList(),
    val goal: SavingsGoal? = null,
    val goalTransactions: List<GoalTransaction> = emptyList()
)

enum class ReminderDestination { DAILY_ENTRY, SALARY, RECURRING, INVOICES, SUBSCRIPTIONS, SUMMARY, GOAL }

data class FinancialReminder(
    val key: String,
    val title: String,
    val body: String,
    val destination: ReminderDestination,
    /** Null uses the scheduled day/week. Non-null suppresses unchanged financial occurrences across days. */
    val occurrence: String? = null
)

data class GoalReminderAssessment(val state: String, val reminder: FinancialReminder? = null)

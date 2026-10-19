package com.pfms.app.ui.navigation

/** Centralized route names for the PFMS navigation graph. */
object Routes {
    fun reminderDestination(route: String?): String? =
        route?.takeIf { it == RecurringExpenses || it == Subscriptions }

    const val Splash = "splash"
    const val Login = "login"
    const val Register = "register"
    const val ForgotPassword = "forgot_password"
    const val Lock = "lock"
    const val Dashboard = "dashboard"
    const val IncomeOnboarding = "income_onboarding"
    const val Settings = "settings"

    // Dashboard, analytics and history
    const val SpendingAnalytics = "spending_analytics"
    const val TransactionHistory = "transaction_history"

    // Income Management
    const val IncomeList = "income_list"
    const val AddIncome = "add_income"
    const val EditIncome = "edit_income/{incomeId}"
    fun editIncome(incomeId: String): String =
        "edit_income/${android.net.Uri.encode(incomeId)}"
    const val SalaryTemplate = "salary_template"
    const val IncomeAnalytics = "income_analytics"

    const val Categories = "expense_categories"
    const val RecurringExpenses = "recurring_expenses"
    const val Subscriptions = "subscriptions"

    // Savings Goal Management — Member 2 (FR-23 through FR-28)
    const val SavingsGoal = "savings_goal"

    // Expense Management — Member 1
    const val ExpenseList = "expense_list"
    const val AddExpense = "add_expense"
    const val EditExpense = "edit_expense/{expenseId}"
    fun editExpense(expenseId: String): String =
        "edit_expense/${android.net.Uri.encode(expenseId)}"

    const val RepeatExpense = "repeat_expense/{expenseId}"
    fun repeatExpense(expenseId: String): String =
        "repeat_expense/${android.net.Uri.encode(expenseId)}"
}

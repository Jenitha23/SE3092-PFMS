package com.pfms.app.ui.navigation

/** Centralized route names for the PFMS navigation graph. */
object Routes {
    const val Splash = "splash"
    const val Login = "login"
    const val Register = "register"
    const val ForgotPassword = "forgot_password"
    const val Lock = "lock"
    const val Dashboard = "dashboard"
    const val Settings = "settings"

    // Income Management
    const val IncomeList = "income_list"
    const val AddIncome = "add_income"
    const val EditIncome = "edit_income/{incomeId}"
    fun editIncome(incomeId: String): String =
        "edit_income/${android.net.Uri.encode(incomeId)}"
    const val SalaryTemplate = "salary_template"

    // Expense Management — Member 1
    const val ExpenseList = "expense_list"
    const val AddExpense = "add_expense"
    const val EditExpense = "edit_expense/{expenseId}"
    fun editExpense(expenseId: String): String =
        "edit_expense/${android.net.Uri.encode(expenseId)}"
}

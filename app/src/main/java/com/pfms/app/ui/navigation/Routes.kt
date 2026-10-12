package com.pfms.app.ui.navigation

/** Type-safe route constants for the PFMS navigation graph. */
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

    fun editIncome(incomeId: String): String {
        return "edit_income/${android.net.Uri.encode(incomeId)}"
    }
}

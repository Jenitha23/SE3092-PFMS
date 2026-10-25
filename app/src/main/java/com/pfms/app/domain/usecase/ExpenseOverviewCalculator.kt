package com.pfms.app.domain.usecase

import com.pfms.app.domain.model.Expense
import com.pfms.app.domain.usecase.analytics.IncomeRealizedCalculator
import com.pfms.app.domain.usecase.dashboard.MonthlyFinanceCalculator
import java.time.YearMonth

object ExpenseOverviewCalculator {
    fun monthly(expenses: List<Expense>, month: YearMonth): List<Expense> = expenses.filter {
        IncomeRealizedCalculator.parseYearMonth(it.date) == month
    }
    fun breakdown(expenses: List<Expense>) = MonthlyFinanceCalculator.categoryBreakdown(expenses, visibleCategories = 3)
    fun split(expenses: List<Expense>) = MonthlyFinanceCalculator.split(expenses)
}

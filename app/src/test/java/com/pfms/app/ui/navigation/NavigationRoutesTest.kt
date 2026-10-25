package com.pfms.app.ui.navigation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class NavigationRoutesTest {

    @Test fun financeFeatureRoutesAreDistinctAndRequireNoArguments() {
        val routes = listOf(Routes.SpendingAnalytics, Routes.SpendingPatterns, Routes.SavingsGoal,
            Routes.SavingsSimulator, Routes.AddIncome, Routes.IncomeList)
        assertEquals(routes.size, routes.toSet().size)
        org.junit.Assert.assertTrue(routes.none { it.contains('{') || it.contains('}') })
        assertEquals("spending_patterns", Routes.SpendingPatterns)
        assertEquals("savings_simulator", Routes.SavingsSimulator)
    }

    @Test
    fun `IncomeAnalytics route constant is defined and matches expected route pattern`() {
        assertEquals("income_analytics", Routes.IncomeAnalytics)
        assertNotNull(Routes.IncomeAnalytics)
    }

    @Test
    fun `IncomeAnalytics route is distinct from other core routes`() {
        assertNotEquals(Routes.IncomeAnalytics, Routes.IncomeList)
        assertNotEquals(Routes.IncomeAnalytics, Routes.Dashboard)
        assertNotEquals(Routes.IncomeAnalytics, Routes.SalaryTemplate)
        assertNotEquals(Routes.IncomeAnalytics, Routes.Settings)
    }

    @Test
    fun `Income related routes are properly registered`() {
        assertEquals("income_list", Routes.IncomeList)
        assertEquals("add_income", Routes.AddIncome)
        assertEquals("salary_template", Routes.SalaryTemplate)
        assertEquals("income_analytics", Routes.IncomeAnalytics)
    }

    @Test
    fun `SavingsGoal route is properly defined and distinct from existing routes`() {
        assertEquals("savings_goal", Routes.SavingsGoal)
        assertNotNull(Routes.SavingsGoal)
        assertNotEquals(Routes.SavingsGoal, Routes.Dashboard)
        assertNotEquals(Routes.SavingsGoal, Routes.IncomeList)
        assertNotEquals(Routes.SavingsGoal, Routes.ExpenseList)
        assertNotEquals(Routes.SavingsGoal, Routes.Settings)
    }
}

package com.pfms.app.ui.navigation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class NavigationRoutesTest {

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
}

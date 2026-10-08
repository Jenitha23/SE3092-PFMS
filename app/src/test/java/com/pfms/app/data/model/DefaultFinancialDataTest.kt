package com.pfms.app.data.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for [DefaultFinancialData] — the seed data written to Firestore
 * when a new user registers (FR-01, FR-16).
 *
 * These tests guard against accidental changes to category IDs, names or
 * classifications that would break idempotent profile initialisation.
 */
class DefaultFinancialDataTest {

    // ------------------------------------------------------------------ expense categories

    @Test
    fun `expense categories list is not empty`() {
        assertTrue(DefaultFinancialData.expenseCategories.isNotEmpty())
    }

    @Test
    fun `expense categories have unique IDs`() {
        val ids = DefaultFinancialData.expenseCategories.map { it.id }
        assertEquals(ids.size, ids.distinct().size)
    }

    @Test
    fun `expense categories have unique names`() {
        val names = DefaultFinancialData.expenseCategories.map { it.name }
        assertEquals(names.size, names.distinct().size)
    }

    @Test
    fun `every classification is committed or discretionary`() {
        DefaultFinancialData.expenseCategories.forEach { category ->
            assertTrue(
                "Category '${category.name}' has invalid classification '${category.classification}'",
                category.classification == Classification.COMMITTED ||
                        category.classification == Classification.DISCRETIONARY
            )
        }
    }

    @Test
    fun `rent is a committed category`() {
        val rent = DefaultFinancialData.expenseCategories.first { it.id == "rent" }
        assertEquals("Rent", rent.name)
        assertEquals(Classification.COMMITTED, rent.classification)
    }

    @Test
    fun `entertainment is a discretionary category`() {
        val entertainment = DefaultFinancialData.expenseCategories.first { it.id == "entertainment" }
        assertEquals("Entertainment", entertainment.name)
        assertEquals(Classification.DISCRETIONARY, entertainment.classification)
    }

    // ------------------------------------------------------------------ income sources

    @Test
    fun `income sources list is not empty`() {
        assertTrue(DefaultFinancialData.incomeSources.isNotEmpty())
    }

    @Test
    fun `income sources have unique IDs`() {
        val ids = DefaultFinancialData.incomeSources.map { it.id }
        assertEquals(ids.size, ids.distinct().size)
    }

    @Test
    fun `income sources have unique names`() {
        val names = DefaultFinancialData.incomeSources.map { it.name }
        assertEquals(names.size, names.distinct().size)
    }

    @Test
    fun `salary is regular income`() {
        val salary = DefaultFinancialData.incomeSources.first { it.id == "salary" }
        assertEquals("Salary", salary.name)
        assertEquals(true, salary.isRegular)
    }

    @Test
    fun `freelance is variable income`() {
        val freelance = DefaultFinancialData.incomeSources.first { it.id == "freelance" }
        assertEquals("Freelance", freelance.name)
        assertEquals(false, freelance.isRegular)
    }

    @Test
    fun `only salary is regular`() {
        val regulars = DefaultFinancialData.incomeSources.filter { it.isRegular }
        assertEquals(1, regulars.size)
        assertEquals("salary", regulars.first().id)
    }

    // ------------------------------------------------------------------ classification constants

    @Test
    fun `committed classification constant is correct`() {
        assertEquals("Committed", Classification.COMMITTED)
    }

    @Test
    fun `discretionary classification constant is correct`() {
        assertEquals("Discretionary", Classification.DISCRETIONARY)
    }
}

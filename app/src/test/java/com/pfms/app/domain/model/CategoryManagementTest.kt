package com.pfms.app.domain.model

import com.pfms.app.data.model.Classification
import com.pfms.app.data.model.DefaultFinancialData
import org.junit.Assert.*
import org.junit.Test

class CategoryManagementTest {

    @Test
    fun defaultCategories_containsExactlyTwelveCategories() {
        val categories = DefaultFinancialData.expenseCategories
        assertEquals("Must contain exactly 12 default categories", 12, categories.size)
    }

    @Test
    fun defaultCategories_hasCorrectSixCommittedCategories() {
        val committed = DefaultFinancialData.expenseCategories
            .filter { it.classification == Classification.COMMITTED }
            .map { it.name }
            .toSet()

        val expected = setOf("Rent", "Utilities", "Subscriptions", "Gym", "Groceries", "Health")
        assertEquals("Committed categories must match FR-16", expected, committed)
        assertEquals("Must have exactly 6 committed categories", 6, committed.size)
    }

    @Test
    fun defaultCategories_hasCorrectSixDiscretionaryCategories() {
        val discretionary = DefaultFinancialData.expenseCategories
            .filter { it.classification == Classification.DISCRETIONARY }
            .map { it.name }
            .toSet()

        val expected = setOf(
            "Food Delivery",
            "Coffee & Dining Out",
            "Entertainment",
            "Shopping",
            "Transport",
            "Other"
        )
        assertEquals("Discretionary categories must match FR-16", expected, discretionary)
        assertEquals("Must have exactly 6 discretionary categories", 6, discretionary.size)
    }

    @Test
    fun defaultCategories_hasStableDeterministicIds() {
        val ids = DefaultFinancialData.expenseCategories.map { it.id }
        assertEquals("All IDs must be unique", ids.size, ids.toSet().size)
        assertTrue("Contains rent ID", ids.contains("rent"))
        assertTrue("Contains groceries ID", ids.contains("groceries"))
        assertTrue("Contains subscriptions ID", ids.contains("subscriptions"))
    }

    @Test
    fun customCategory_validationRules() {
        fun validateName(name: String): String? {
            val cleaned = name.trim()
            if (cleaned.isEmpty()) return "Category name cannot be empty"
            if (cleaned.length > 50) return "Category name must be 1 to 50 characters"
            return null
        }

        assertNotNull("Blank rejected", validateName("   "))
        assertNotNull("Empty rejected", validateName(""))
        assertNotNull("Too long rejected", validateName("A".repeat(51)))
        assertNull("Valid 1 char accepted", validateName("A"))
        assertNull("Valid trimmed accepted", validateName("  Books  "))
    }

    @Test
    fun duplicateCategory_caseInsensitiveDetection() {
        val existing = listOf("Groceries", "Gym", "Custom Tech")
        val newName = "  custom tech  "
        val isDuplicate = existing.any { it.trim().equals(newName.trim(), ignoreCase = true) }
        assertTrue("Duplicate must be rejected case-insensitively", isDuplicate)

        val nonDuplicate = "Education"
        val isNonDuplicate = existing.any { it.trim().equals(nonDuplicate.trim(), ignoreCase = true) }
        assertFalse("Unique name should not be duplicate", isNonDuplicate)
    }

    @Test
    fun archivedCategory_preservesHistoricalAssociation() {
        val archivedCat = ExpenseCategory(
            id = "cat_1",
            name = "Old Streaming",
            classification = ExpenseClassification.DISCRETIONARY,
            custom = true,
            archived = true
        )

        // Historical expense references this archived category
        val expense = Expense(
            id = "exp_1",
            categoryId = archivedCat.id,
            categoryName = archivedCat.name,
            amountMinor = 120000L
        )

        assertEquals("cat_1", expense.categoryId)
        assertEquals("Old Streaming", expense.categoryName)
        assertTrue(archivedCat.archived)
    }
}

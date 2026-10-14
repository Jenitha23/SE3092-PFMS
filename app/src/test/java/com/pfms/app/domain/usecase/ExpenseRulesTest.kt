
package com.pfms.app.domain.usecase

import com.pfms.app.domain.model.Expense
import com.pfms.app.domain.model.ExpenseClassification
import com.pfms.app.domain.model.ExpensePaymentMethod
import org.junit.Assert.*
import org.junit.Test

class ExpenseRulesTest {

    @Test
    fun validAmountConvertsToMinorUnits() {
        assertEquals(
            125050L,
            ExpenseRules.parseAmount("1250.50")
        )
    }

    @Test
    fun zeroAmountIsRejected() {
        assertNull(ExpenseRules.parseAmount("0"))
    }

    @Test
    fun negativeAmountIsRejected() {
        assertNull(ExpenseRules.parseAmount("-100"))
    }

    @Test
    fun moreThanTwoDecimalPlacesIsRejected() {
        assertNull(ExpenseRules.parseAmount("10.123"))
    }

    @Test
    fun missingCategoryIsRejected() {
        val result = ExpenseRules.validateExpense(
            amountMinor = 10000L,
            categoryId = "",
            date = "2026-10-14"
        )

        assertEquals(
            "Select an expense category.",
            result
        )
    }

    @Test
    fun categoryDefaultClassificationIsApplied() {
        val result = ExpenseRules.resolveClassification(
            categoryDefault =
                ExpenseClassification.COMMITTED,
            userOverride = null
        )

        assertEquals(
            ExpenseClassification.COMMITTED,
            result
        )
    }

    @Test
    fun userCanOverrideClassification() {
        val result = ExpenseRules.resolveClassification(
            categoryDefault =
                ExpenseClassification.COMMITTED,
            userOverride =
                ExpenseClassification.DISCRETIONARY
        )

        assertEquals(
            ExpenseClassification.DISCRETIONARY,
            result
        )
    }

    @Test
    fun identicalExpenseWithin60SecondsIsDetected() {

        val now = 1_000_000L

        val existing = Expense(
            id = "expense-1",
            amountMinor = 150000L,
            categoryId = "coffee",
            categoryName = "Coffee & Dining Out",
            classification =
                ExpenseClassification.DISCRETIONARY,
            paymentMethod =
                ExpensePaymentMethod.CARD,
            date = "2026-10-14",
            createdAt = now - 30_000L
        )

        val newExpense = existing.copy(
            id = "expense-2",
            createdAt = now
        )

        assertTrue(
            ExpenseRules.isDuplicateWithin60Seconds(
                newExpense = newExpense,
                recentExpenses = listOf(existing),
                nowMillis = now
            )
        )
    }

    @Test
    fun oldIdenticalExpenseIsNotFlagged() {

        val now = 1_000_000L

        val existing = Expense(
            id = "expense-1",
            amountMinor = 150000L,
            categoryId = "coffee",
            date = "2026-10-14",
            createdAt = now - 61_000L
        )

        val newExpense = existing.copy(
            id = "expense-2",
            createdAt = now
        )

        assertFalse(
            ExpenseRules.isDuplicateWithin60Seconds(
                newExpense = newExpense,
                recentExpenses = listOf(existing),
                nowMillis = now
            )
        )
    }
}

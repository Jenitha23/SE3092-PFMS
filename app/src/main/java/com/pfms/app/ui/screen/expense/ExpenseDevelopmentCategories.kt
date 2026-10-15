
package com.pfms.app.ui.screen.expense

import com.pfms.app.domain.model.ExpenseClassification

/**
 * Temporary categories for Member 1 development
 * and integration testing.
 *
 * Member 2 will later replace these with categories
 * loaded from the category repository.
 */
object ExpenseDevelopmentCategories {

    val categories = listOf(
        ExpenseCategoryOption(
            id = "rent",
            name = "Rent",
            defaultClassification =
                ExpenseClassification.COMMITTED
        ),

        ExpenseCategoryOption(
            id = "groceries",
            name = "Groceries",
            defaultClassification =
                ExpenseClassification.COMMITTED
        ),

        ExpenseCategoryOption(
            id = "utilities",
            name = "Utilities",
            defaultClassification =
                ExpenseClassification.COMMITTED
        ),

        ExpenseCategoryOption(
            id = "transport",
            name = "Transport",
            defaultClassification =
                ExpenseClassification.COMMITTED
        ),

        ExpenseCategoryOption(
            id = "dining",
            name = "Dining Out",
            defaultClassification =
                ExpenseClassification.DISCRETIONARY
        ),

        ExpenseCategoryOption(
            id = "shopping",
            name = "Shopping",
            defaultClassification =
                ExpenseClassification.DISCRETIONARY
        ),

        ExpenseCategoryOption(
            id = "entertainment",
            name = "Entertainment",
            defaultClassification =
                ExpenseClassification.DISCRETIONARY
        )
    )
}

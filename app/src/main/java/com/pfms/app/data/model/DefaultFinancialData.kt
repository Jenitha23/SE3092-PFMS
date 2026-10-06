package com.pfms.app.data.model

/** Spending classification used for the Committed vs Discretionary split (FR-16, FR-17, FR-31). */
object Classification {
    const val COMMITTED = "Committed"
    const val DISCRETIONARY = "Discretionary"
}

data class DefaultCategory(
    /** Stable document ID, so initialisation is idempotent and never creates duplicates. */
    val id: String,
    val name: String,
    val classification: String
)

data class DefaultIncomeSource(
    val id: String,
    val name: String,
    /** Salary is Regular income; every other source is Variable (FR-12). */
    val isRegular: Boolean
)

object DefaultFinancialData {

    /** FR-16 */
    val expenseCategories = listOf(
        DefaultCategory("rent", "Rent", Classification.COMMITTED),
        DefaultCategory("utilities", "Utilities", Classification.COMMITTED),
        DefaultCategory("subscriptions", "Subscriptions", Classification.COMMITTED),
        DefaultCategory("gym", "Gym", Classification.COMMITTED),
        DefaultCategory("groceries", "Groceries", Classification.COMMITTED),
        DefaultCategory("health", "Health", Classification.COMMITTED),
        DefaultCategory("food_delivery", "Food Delivery", Classification.DISCRETIONARY),
        DefaultCategory("coffee_dining", "Coffee & Dining Out", Classification.DISCRETIONARY),
        DefaultCategory("entertainment", "Entertainment", Classification.DISCRETIONARY),
        DefaultCategory("shopping", "Shopping", Classification.DISCRETIONARY),
        DefaultCategory("transport", "Transport", Classification.DISCRETIONARY),
        DefaultCategory("other", "Other", Classification.DISCRETIONARY)
    )

    /** FR-06 (income) */
    val incomeSources = listOf(
        DefaultIncomeSource("salary", "Salary", isRegular = true),
        DefaultIncomeSource("freelance", "Freelance", isRegular = false),
        DefaultIncomeSource("google_adsense", "Google AdSense", isRegular = false),
        DefaultIncomeSource("crypto", "Crypto", isRegular = false),
        DefaultIncomeSource("other", "Other", isRegular = false)
    )
}

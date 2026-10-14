
package com.pfms.app.ui.screen.expense

import com.pfms.app.domain.model.ExpenseClassification

/**
 * A display contract between Member 1's expense UI
 * and Member 2's category management module.
 */
data class ExpenseCategoryOption(
    val id: String,
    val name: String,
    val defaultClassification: ExpenseClassification
)

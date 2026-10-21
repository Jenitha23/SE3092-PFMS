package com.pfms.app.domain.repository

import com.pfms.app.domain.model.ExpenseCategory
import com.pfms.app.domain.model.ExpenseClassification
import com.pfms.app.domain.model.RecurringExpense
import kotlinx.coroutines.flow.Flow

interface AdvancedExpenseRepository {
    fun categories(): Flow<List<ExpenseCategory>>
    fun templates(): Flow<List<RecurringExpense>>
    suspend fun refreshTemplates()
    suspend fun initializeDefaults()
    suspend fun createCategory(name: String, classification: ExpenseClassification)
    suspend fun createCategoryWithId(id: String, name: String, classification: ExpenseClassification) = createCategory(name, classification)
    suspend fun updateCategory(category: ExpenseCategory)
    suspend fun saveTemplate(template: RecurringExpense)
    suspend fun deleteTemplate(templateId: String)
    suspend fun confirmDue(template: RecurringExpense)
    suspend fun repeatExpense(expenseId: String)
    suspend fun repeatExpenseWithId(expenseId: String, newId: String) = repeatExpense(expenseId)
}

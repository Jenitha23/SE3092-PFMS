package com.pfms.app.data

import com.pfms.app.data.model.DefaultFinancialData
import com.pfms.app.domain.model.ExpenseCategory
import com.pfms.app.domain.model.ExpenseClassification
import com.pfms.app.domain.model.RecurringExpense
import com.pfms.app.domain.repository.AdvancedExpenseRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.UUID

class FakeAdvancedExpenseRepository : AdvancedExpenseRepository {

    private val categoriesState = MutableStateFlow<List<ExpenseCategory>>(emptyList())
    private val templatesState = MutableStateFlow<List<RecurringExpense>>(emptyList())

    var confirmedCount = 0
    var repeatedCount = 0

    override fun categories(): Flow<List<ExpenseCategory>> = categoriesState.asStateFlow()

    override fun templates(): Flow<List<RecurringExpense>> = templatesState.asStateFlow()
    override suspend fun refreshTemplates() = Unit

    override suspend fun initializeDefaults() {
        val existingIds = categoriesState.value.map { it.id }.toSet()
        val defaults = DefaultFinancialData.expenseCategories.filter { it.id !in existingIds }.map {
            ExpenseCategory(
                id = it.id,
                name = it.name,
                classification = if (it.classification.equals("Committed", true)) ExpenseClassification.COMMITTED else ExpenseClassification.DISCRETIONARY,
                custom = false,
                archived = false
            )
        }
        categoriesState.value = categoriesState.value + defaults
    }

    override suspend fun createCategory(name: String, classification: ExpenseClassification) {
        val cleaned = name.trim()
        require(cleaned.isNotEmpty()) { "Category name cannot be empty" }
        require(categoriesState.value.none { it.name.equals(cleaned, ignoreCase = true) }) { "Duplicate category" }

        val newCat = ExpenseCategory(
            id = UUID.randomUUID().toString(),
            name = cleaned,
            classification = classification,
            custom = true,
            archived = false
        )
        categoriesState.value = categoriesState.value + newCat
    }

    override suspend fun updateCategory(category: ExpenseCategory) {
        require(category.custom) { "Default categories cannot be updated" }
        categoriesState.value = categoriesState.value.map {
            if (it.id == category.id) category else it
        }
    }

    override suspend fun saveTemplate(template: RecurringExpense) {
        val id = template.id.ifBlank { UUID.randomUUID().toString() }
        val toSave = template.copy(id = id)
        val current = templatesState.value
        if (current.any { it.id == id }) {
            templatesState.value = current.map { if (it.id == id) toSave else it }
        } else {
            templatesState.value = current + toSave
        }
    }

    override suspend fun deleteTemplate(templateId: String) {
        templatesState.value = templatesState.value.filterNot { it.id == templateId }
    }

    val createdExpenses = mutableListOf<Map<String, Any>>()

    override suspend fun confirmDue(template: RecurringExpense) {
        confirmedCount++
        val due = java.time.LocalDate.parse(template.nextDueDate)
        val next = com.pfms.app.domain.usecase.RecurringExpenseCalculator.calculateNextDueDate(due, template.recurrence)
        val expenseId = "recurring_${template.id}_${template.nextDueDate}"
        check(createdExpenses.none { it["id"] == expenseId }) { "Duplicate confirmation detected" }
        createdExpenses.add(mapOf("id" to expenseId, "amountMinor" to template.amountMinor, "date" to template.nextDueDate))
        saveTemplate(template.copy(nextDueDate = next.toString()))
    }

    override suspend fun repeatExpense(expenseId: String) {
        repeatedCount++
    }
}

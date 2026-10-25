package com.pfms.app.viewmodel

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import com.pfms.app.domain.model.*
import com.pfms.app.domain.usecase.ExpenseRules
import com.pfms.app.domain.usecase.IncomeEntryAmount
import com.pfms.app.domain.usecase.dashboard.AppClock
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale

data class ExpenseCategoryChoice(val id: String, val name: String, val defaultClassification: ExpenseClassification)
data class ExpensePastChoice(val id: String, val title: String, val subtitle: String)
data class ExpenseDraftState(val title: String = "Add Expense", val saveLabel: String = "Save Expense",
    val amount: String = "", val formatted: String? = null, val category: ExpenseCategoryChoice? = null,
    val categories: List<ExpenseCategoryChoice> = emptyList(), val method: ExpensePaymentMethod = ExpensePaymentMethod.CASH,
    val paymentLabel: String = "Cash", val date: String = "", val dateLabel: String = "", val dateMillis: Long = 0,
    val classification: ExpenseClassification = ExpenseClassification.DISCRETIONARY, val overridden: Boolean = false,
    val merchant: String = "", val note: String = "", val menu: String? = null, val past: List<ExpensePastChoice> = emptyList(),
    val saving: Boolean = false, val error: String? = null, val quick: Boolean = false,
    val outlook: String = "LKR 0.00", val outlookMonth: String = "")

/** Draft edits, repeat selection, defaults and validation are independent of the rendering layer. */
class ExpenseDraftViewModel(private val saved: SavedStateHandle, private val initial: Expense?,
    defaultPayment: ExpensePaymentMethod, private val repeating: Boolean = false, private val quick: Boolean = false) : ViewModel() {
    private var categories: List<ExpenseCategoryChoice> = emptyList()
    private var past: List<Expense> = emptyList()
    private var monthly: List<Expense> = emptyList()
    private var saving = false; private var backendError: String? = null; private var inputError: String? = null; private var retry = false
    private val _state = MutableStateFlow(ExpenseDraftState())
    val state = _state.asStateFlow()
    init {
        seed("amount", initial?.let { BigDecimal.valueOf(it.amountMinor, 2).toPlainString() }.orEmpty())
        seed("category", initial?.categoryId.orEmpty())
        seed("date", if (repeating) AppClock.today().toString() else initial?.date ?: AppClock.today().toString())
        seed("method", (initial?.paymentMethod ?: defaultPayment).name)
        seed("classification", initial?.classification?.name.orEmpty())
        seed("merchant", initial?.merchant.orEmpty()); seed("note", initial?.note.orEmpty())
        refresh()
    }
    private fun seed(key: String, value: String) { if (!saved.contains(key)) saved[key] = value }
    private fun text(key: String): String = saved[key] ?: ""
    private fun change(key: String, value: String) { if (!saving) { saved[key] = value; inputError = null; refresh() } }
    fun sync(options: List<ExpenseCategoryChoice>, previous: List<Expense>, isSaving: Boolean, error: String?, canRetry: Boolean,
        monthlyExpenses: List<Expense> = emptyList()) {
        categories = options; past = previous; monthly = monthlyExpenses; saving = isSaving; backendError = error; retry = canRetry
        if (quick && text("category").isBlank()) saved["category"] = options.firstOrNull()?.id.orEmpty()
        refresh()
    }
    fun amount(value: String) = change("amount", value)
    fun category(value: String) { if (!saving && categories.any { it.id == value }) { saved["classification"] = ""; saved["menu"] = null; change("category", value) } }
    fun payment(value: ExpensePaymentMethod) { if (!saving) { saved["menu"] = null; change("method", value.name) } }
    fun classification(value: ExpenseClassification?) = change("classification", value?.name.orEmpty())
    fun merchant(value: String) = change("merchant", value)
    fun note(value: String) = change("note", value)
    fun menu(value: String?) { if (!saving) { saved["menu"] = value; refresh() } }
    fun increment(value: Long) {
        if (saving || value !in listOf(500L, 1000L, 2500L, 5000L, 10000L)) return
        val next = IncomeEntryAmount.increment(text("amount"), value)
        if (next == null) { inputError = "Enter a valid amount before adding more."; refresh() } else amount(next)
    }
    fun date(millis: Long?) {
        if (saving || millis == null) return
        saved["menu"] = null
        change("date", Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate().toString())
    }
    fun repeat(id: String) {
        if (saving) return
        val expense = past.firstOrNull { it.id == id } ?: return
        saved["amount"] = BigDecimal.valueOf(expense.amountMinor, 2).toPlainString()
        saved["category"] = expense.categoryId
        saved["classification"] = expense.classification.name
        saved["method"] = expense.paymentMethod.name
        saved["merchant"] = expense.merchant; saved["note"] = expense.note
        saved["date"] = AppClock.today().toString(); saved["menu"] = null; inputError = null; refresh()
    }
    fun submit(save: (ExpenseFormData) -> Unit) {
        if (saving) return
        val category = categories.firstOrNull { it.id == text("category") }
        val amount = ExpenseRules.parseAmount(text("amount"))
        inputError = if (amount == null) "Enter a valid positive amount with up to 2 decimal places."
            else if (category == null) "Select an expense category." else ExpenseRules.validateExpense(amount, category.id, text("date"))
        if (inputError != null || category == null) { refresh(); return }
        save(ExpenseFormData(text("amount").trim(), category.id, category.name, category.defaultClassification,
            text("classification").takeIf { it.isNotEmpty() }?.let(ExpenseClassification::valueOf),
            ExpensePaymentMethod.valueOf(text("method")), text("date"), text("merchant"), text("note")))
    }
    private fun refresh() {
        val category = categories.firstOrNull { it.id == text("category") }
        val type = text("classification").takeIf { it.isNotEmpty() }?.let(ExpenseClassification::valueOf)
        val date = LocalDate.parse(text("date"))
        val editing = initial != null && !repeating
        val method = ExpensePaymentMethod.valueOf(text("method"))
        val month = java.time.YearMonth.from(AppClock.today())
        val total = com.pfms.app.domain.usecase.ExpenseOverviewCalculator.split(com.pfms.app.domain.usecase.ExpenseOverviewCalculator.monthly(monthly, month)).totalMinor
        _state.value = ExpenseDraftState(if (quick) "Quick Add Expense" else if (editing) "Edit Expense" else if (repeating) "Repeat Expense" else "Add Expense",
            if (retry && backendError != null) "Retry" else if (editing) "Update Expense" else "Save Expense", text("amount"),
            ExpenseRules.parseAmount(text("amount"))?.let { "LKR ${financeMoney(it)}" }, category, categories, method, expensePaymentLabel(method), text("date"),
            date.format(DateTimeFormatter.ofPattern("d MMM yyyy", Locale.ENGLISH)), date.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
            ExpenseRules.resolveClassification(category?.defaultClassification ?: ExpenseClassification.DISCRETIONARY, type), type != null,
            text("merchant"), text("note"), saved["menu"],
            past.map { ExpensePastChoice(it.id, it.merchant.ifBlank { it.categoryName }, "LKR ${financeMoney(it.amountMinor)} • ${it.date}") },
            saving, inputError ?: backendError, quick, "LKR ${financeMoney(total)}", month.format(DateTimeFormatter.ofPattern("MMMM yyyy", Locale.ENGLISH)))
    }
}

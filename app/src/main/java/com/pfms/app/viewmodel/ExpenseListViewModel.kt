package com.pfms.app.viewmodel

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import com.pfms.app.domain.model.*
import com.pfms.app.domain.usecase.ExpenseOverviewCalculator
import com.pfms.app.domain.usecase.dashboard.AppClock
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale

data class ExpenseSlice(val category: String, val value: String, val percent: String, val sweep: Float)
data class ExpenseRecordRow(val id: String, val title: String, val category: String, val date: String,
    val payment: String, val classification: String, val amount: String, val note: String, val menu: Boolean,
    val detail: String = "")
data class ExpenseListState(val monthLabel: String = "", val monthTitle: String = "", val months: List<String> = emptyList(),
    val monthMenu: Boolean = false, val toolsMenu: Boolean = false, val filterMenu: Boolean = false,
    val total: String = "LKR 0.00", val slices: List<ExpenseSlice> = emptyList(),
    val committed: String = "LKR 0.00", val discretionary: String = "LKR 0.00", val committedPercent: String = "0%", val discretionaryPercent: String = "0%",
    val query: String = "", val category: String? = null, val categories: List<String> = emptyList(),
    val classification: ExpenseClassification? = null, val rows: List<ExpenseRecordRow> = emptyList(), val count: String = "0 expenses",
    val oldestFirst: Boolean = false, val filtered: Boolean = false, val loading: Boolean = false, val deleting: Boolean = false,
    val error: String? = null, val deleteId: String? = null)

class ExpenseListViewModel(private val saved: SavedStateHandle) : ViewModel() {
    private var records: List<Expense> = emptyList()
    private var loading = false; private var deleting = false; private var error: String? = null
    private val _state = MutableStateFlow(ExpenseListState())
    val state = _state.asStateFlow()
    init { if (!saved.contains("month")) saved["month"] = YearMonth.from(AppClock.today()).toString(); refresh() }
    private fun text(key: String): String? = saved[key]
    fun sync(expenses: List<Expense>, isLoading: Boolean, isDeleting: Boolean, message: String?) {
        records = expenses; loading = isLoading; deleting = isDeleting; error = message; refresh()
    }
    fun query(value: String) { saved["query"] = value; refresh() }
    fun category(value: String?) { saved["category"] = value; refresh() }
    fun classification(value: ExpenseClassification?) { saved["classification"] = value?.name; saved["filterMenu"] = false; refresh() }
    fun month(value: String) {
        if (runCatching { YearMonth.parse(value) }.isFailure) return
        saved["month"] = value; saved["category"] = null; saved["monthMenu"] = false; refresh()
    }
    fun moveMonth(delta: Long) = month(YearMonth.parse(text("month")).plusMonths(delta).toString())
    fun menu(key: String, value: Boolean) { saved[key] = value; refresh() }
    fun recordMenu(id: String?) { saved["recordMenu"] = id; refresh() }
    fun delete(id: String?) { if (!deleting) { saved["delete"] = id; saved["recordMenu"] = null; refresh() } }
    fun confirmDelete(action: (String) -> Unit) { if (!deleting) text("delete")?.let { saved["delete"] = null; refresh(); action(it) } }
    fun sort() { saved["oldest"] = !(saved["oldest"] ?: false); refresh() }
    fun clearFilters() { saved["query"] = ""; saved["category"] = null; saved["classification"] = null; refresh() }
    private fun refresh() {
        val month = YearMonth.parse(text("month"))
        val monthly = ExpenseOverviewCalculator.monthly(records, month)
        val split = ExpenseOverviewCalculator.split(monthly)
        val query = text("query").orEmpty()
        val category = text("category")
        val classification = text("classification")?.let(ExpenseClassification::valueOf)
        val oldest: Boolean = saved["oldest"] ?: false
        val rows = monthly.filter { (category == null || it.categoryName == category) &&
            (classification == null || classification == it.classification) &&
            (query.isBlank() || listOf(it.categoryName, it.merchant, it.note).any { field -> field.contains(query.trim(), true) }) }
            .sortedWith(if (oldest) compareBy<Expense> { it.date }.thenBy { it.createdAt } else compareByDescending<Expense> { it.date }.thenByDescending { it.createdAt })
        _state.value = ExpenseListState(
            month.format(DateTimeFormatter.ofPattern("MMM yyyy", Locale.ENGLISH)), month.format(DateTimeFormatter.ofPattern("MMMM yyyy", Locale.ENGLISH)),
            (records.mapNotNull { runCatching { YearMonth.from(LocalDate.parse(it.date)).toString() }.getOrNull() } + month.toString() + YearMonth.from(AppClock.today()).toString()).distinct().sortedDescending(),
            saved["monthMenu"] ?: false, saved["toolsMenu"] ?: false, saved["filterMenu"] ?: false,
            "LKR ${financeMoney(split.totalMinor)}", ExpenseOverviewCalculator.breakdown(monthly).map {
                ExpenseSlice(it.categoryName, "LKR ${financeMoney(it.amountMinor)}", percent(it.percent), (it.percent * 3.6).toFloat()) },
            "LKR ${financeMoney(split.committedMinor)}", "LKR ${financeMoney(split.discretionaryMinor)}", percent(split.committedPercent), percent(split.discretionaryPercent),
            query, category, monthly.map { it.categoryName }.distinct().sorted(), classification,
            rows.map { ExpenseRecordRow(it.id, it.merchant.ifBlank { it.categoryName }, it.categoryName,
                runCatching { LocalDate.parse(it.date).format(DateTimeFormatter.ofPattern("d MMM yyyy", Locale.ENGLISH)) }.getOrDefault(it.date),
                expensePaymentLabel(it.paymentMethod), if (it.classification == ExpenseClassification.COMMITTED) "Committed" else "Discretionary",
                "−LKR ${financeMoney(it.amountMinor)}", it.note, text("recordMenu") == it.id,
                if (it.merchant.isBlank() || it.merchant.equals(it.categoryName, true)) expensePaymentLabel(it.paymentMethod)
                else "${it.categoryName} • ${expensePaymentLabel(it.paymentMethod)}") },
            "${rows.size} ${if (rows.size == 1) "expense" else "expenses"}", oldest, query.isNotBlank() || category != null || classification != null,
            loading, deleting, error, text("delete"))
    }
    private fun percent(value: Double): String = if (value == 0.0) "0%" else String.format(Locale.US, "%.1f%%", value)
}

internal fun expensePaymentLabel(method: ExpensePaymentMethod): String = when (method) {
    ExpensePaymentMethod.CASH -> "Cash"; ExpensePaymentMethod.CARD -> "Card"
    ExpensePaymentMethod.BANK_TRANSFER_AUTO_DEBIT -> "Bank transfer / Auto-debit"
    ExpensePaymentMethod.DIGITAL_WALLET_PLATFORM -> "Digital wallet / Platform"
}

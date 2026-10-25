package com.pfms.app.viewmodel

import androidx.lifecycle.ViewModel
import com.pfms.app.ui.component.formatPercent
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.abs

data class SpendingCategoryDisplay(val title: String, val amount: String, val percent: String, val fraction: Float, val start: Float, val sweep: Float)
data class SpendingTrendDisplay(val month: String, val income: String, val expense: String, val incomeFraction: Float, val expenseFraction: Float)
data class SpendingDisplay(val month: String, val total: String, val income: String, val net: String, val count: String,
    val hasExpenses: Boolean, val categories: List<SpendingCategoryDisplay>, val committed: String, val discretionary: String,
    val committedPercent: String, val discretionaryPercent: String, val committedFraction: Float,
    val largest: String, val topExpenses: List<FinanceTransactionDisplay>, val trend: List<SpendingTrendDisplay>, val noTrend: Boolean, val negativeTrend: Boolean)

class SpendingScreenViewModel : ViewModel() {
    private val _display = MutableStateFlow<SpendingDisplay?>(null)
    val display = _display.asStateFlow()
    fun sync(state: SpendingAnalyticsUiState) {
        val summary = state.summary
        var angle = -90f
        val categories = summary?.categories.orEmpty().map { category ->
            val sweep = (category.percent / 100 * 360).toFloat()
            SpendingCategoryDisplay(category.categoryName, DashboardPresentationMapper.money(category.amountMinor), formatPercent(category.percent),
                (category.percent / 100).toFloat().coerceIn(0f, 1f), angle, sweep).also { angle += sweep }
        }
        val scale = state.trend.maxOfOrNull { maxOf(abs(it.incomeMinor.toDouble()), abs(it.expenseMinor.toDouble())) }?.coerceAtLeast(1.0) ?: 1.0
        _display.value = SpendingDisplay(state.selectedMonth.format(DateTimeFormatter.ofPattern("MMMM yyyy", Locale.US)),
            DashboardPresentationMapper.money(summary?.totalExpenseMinor ?: 0), DashboardPresentationMapper.money(summary?.totalIncomeMinor ?: 0),
            DashboardPresentationMapper.money(summary?.netSavingsMinor ?: 0), "${summary?.expenseCount ?: 0} recorded expenses",
            (summary?.expenseCount ?: 0) > 0, categories, DashboardPresentationMapper.money(summary?.split?.committedMinor ?: 0),
            DashboardPresentationMapper.money(summary?.split?.discretionaryMinor ?: 0), formatPercent(summary?.split?.committedPercent ?: 0.0),
            formatPercent(summary?.split?.discretionaryPercent ?: 0.0), ((summary?.split?.committedPercent ?: 0.0) / 100).toFloat().coerceIn(0f, 1f),
            state.insights?.largestCategory?.let { "${it.categoryName} is your largest category at ${formatPercent(it.percent)} of recorded spending." }
                ?: "Record expenses to discover your spending mix.",
            state.insights?.largestExpenses.orEmpty().map(FinanceTransactionPresentation::create),
            state.trend.map { SpendingTrendDisplay(it.month.format(DateTimeFormatter.ofPattern("MMM yyyy", Locale.US)),
                DashboardPresentationMapper.money(it.incomeMinor), DashboardPresentationMapper.money(it.expenseMinor),
                (it.incomeMinor / scale).toFloat(), (it.expenseMinor / scale).toFloat()) },
            state.trend.all { it.incomeMinor == 0L && it.expenseMinor == 0L }, state.trend.any { it.incomeMinor < 0 })
    }
}

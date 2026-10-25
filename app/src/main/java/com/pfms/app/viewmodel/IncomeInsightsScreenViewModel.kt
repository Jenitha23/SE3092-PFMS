package com.pfms.app.viewmodel

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pfms.app.data.export.*
import com.pfms.app.domain.usecase.analytics.*
import com.pfms.app.domain.usecase.savings.SavingsGoalEntryRules
import com.pfms.app.ui.screen.savings.SavingsGoalUiFormatter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.math.BigDecimal
import java.text.NumberFormat
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale

data class InsightFlowRow(val key: String, val title: String, val caption: String, val value: String, val negative: Boolean = false)
data class InsightSourceRow(val key: String, val title: String, val value: String, val percentage: String, val fraction: Float, val negative: Boolean)
data class InsightChartPoint(val key: String, val label: String, val month: String, val value: String, val compact: String, val x: Float, val y: Float, val negative: Boolean,
    val empty: Boolean = false)
data class InsightEstimateComparison(val estimated: String, val actual: String, val difference: String, val variance: String,
    val months: List<String>, val status: String, val below: Boolean)
data class IncomeInsightsDisplay(
    val period: String, val periodDetail: String, val month: String, val range: String, val total: String,
    val gross: String, val losses: String, val margin: String, val change: String,
    val changeNegative: Boolean, val netNegative: Boolean, val dailyAverage: String,
    val entries: String, val pendingCount: String, val pending: String?, val flow: List<InsightFlowRow>,
    val sources: List<InsightSourceRow>, val regular: String, val variable: String, val regularShare: String,
    val variableShare: String, val regularFraction: Float, val variableFraction: Float, val concentration: String,
    val trend: List<InsightChartPoint>, val accumulation: List<InsightChartPoint>, val trendZero: Float, val accumulationZero: Float,
    val average: String, val peak: String?, val trendTitle: String, val emptyTrend: Boolean,
    val grossLabel: String, val lossesLabel: String, val comparison: InsightEstimateComparison?,
    val emptyPeriod: Boolean
)
data class IncomeInsightsScreenState(val source: IncomeAnalyticsUiState = IncomeAnalyticsUiState(),
    val display: IncomeInsightsDisplay? = null, val estimate: String = "", val estimateError: String? = null,
    val calendar: Boolean = false, val calendarMillis: Long? = null, val exportMenu: Boolean = false,
    val exporting: Boolean = false, val exportMessage: String? = null, val selectedTrend: String? = null)

/** Owns UI drafts, formatting, calendar selection and report preparation. Domain owns all financial formulas. */
class IncomeInsightsScreenViewModel : ViewModel() {
    private val _state = MutableStateFlow(IncomeInsightsScreenState())
    val state = _state.asStateFlow()
    private var dirty = false
    private var reportToExport: Pair<IncomeReportFormat, IncomeInsightsReport>? = null

    fun sync(source: IncomeAnalyticsUiState) {
        val snapshot = source.insights ?: IncomeInsightsCalculator.fromSummary(source.monthlySummary, source.trendPoints, source.selectedMonth)
        _state.update { old -> old.copy(source = source, display = display(source, snapshot),
            calendarMillis = source.selectedMonth.atDay(1).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
            estimate = if (!dirty || old.source.estimatedMonthlyIncomeMinor != source.estimatedMonthlyIncomeMinor)
                source.estimatedMonthlyIncomeMinor?.let { BigDecimal.valueOf(it, 2).toPlainString() }.orEmpty() else old.estimate) }
    }
    fun estimate(value: String) { if (!_state.value.source.isSaving) { dirty = true; _state.update { it.copy(estimate = value, estimateError = null) } } }
    fun applyEstimate(save: (Long) -> Unit) {
        if (_state.value.source.isSaving) return
        val amount = SavingsGoalEntryRules.parseAmount(_state.value.estimate)
        if (amount == null) _state.update { it.copy(estimateError = "Enter a non-negative amount with no fractional cents.") }
        else save(amount)
    }
    fun clearEstimate(clear: () -> Unit) { dirty = false; _state.update { it.copy(estimate = "", estimateError = null) }; clear() }
    fun calendar(value: Boolean) { _state.update { it.copy(calendar = value) } }
    fun selectDate(millis: Long?, select: (YearMonth) -> Unit) {
        if (millis == null) return
        select(YearMonth.from(Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC)))
        calendar(false)
    }
    fun trend(key: String) { _state.update { it.copy(selectedTrend = key) } }
    fun exportMenu(value: Boolean) { _state.update { it.copy(exportMenu = value) } }
    fun dismissExportMessage() { _state.update { it.copy(exportMessage = null) } }
    fun report(): IncomeInsightsReport {
        val display = requireNotNull(_state.value.display)
        val rows = listOf(listOf("Income Insights", display.period), listOf("Metric", "LKR / value"),
            listOf("Net realized income", display.total), listOf(display.grossLabel, display.gross), listOf(display.lossesLabel, display.losses),
            listOf("Net margin", display.margin), listOf("Net / calendar day", display.dailyAverage), listOf("Received entries", display.entries)) +
            display.sources.map { listOf(it.title, it.value) } + listOf(listOf("Month", "Net realized income (LKR)")) +
            display.trend.map { listOf(it.key, it.value) } + listOf(listOf("Notes", "Expected freelance is excluded. USD entries use saved historical LKR values. Crypto losses reduce net income."))
        return IncomeInsightsReport(display.period, rows)
    }
    fun prepareExport(format: IncomeReportFormat): String {
        reportToExport = format to report()
        exportMenu(false)
        return "TrackMyFinances-insights-${_state.value.source.selectedMonth}-${_state.value.display?.range}.${format.extension}"
    }
    fun export(uri: Uri?, writer: IncomeInsightsReportWriter) {
        val pending = reportToExport ?: return
        reportToExport = null
        if (uri == null) return
        _state.update { it.copy(exporting = true, exportMessage = null) }
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) { writer.write(uri, pending.second, pending.first) }
            _state.update { it.copy(exporting = false, exportMessage = if (result.isSuccess) "${pending.first.name} report saved." else "Couldn't save the report. Choose another location and try again.") }
        }
    }

    private fun display(source: IncomeAnalyticsUiState, snapshot: IncomeInsightsSnapshot): IncomeInsightsDisplay {
        val period = if (snapshot.start == snapshot.end) longMonth(snapshot.end) else "${shortMonth(snapshot.start)} – ${shortMonth(snapshot.end)}"
        val periodDays = java.time.temporal.ChronoUnit.DAYS.between(snapshot.start.atDay(1), snapshot.end.atEndOfMonth()) + 1
        val trend = snapshot.trend.map(::point)
        val sources = snapshot.sources.map { row -> InsightSourceRow(row.key, sourceTitle(row.key), money(row.amountMinor), pct(row.grossShare) + " of gross inflow",
            (row.grossShare / 100).toFloat().coerceIn(0f, 1f), row.amountMinor < 0) }
        val comparison = source.estimationComparison
        val grossLabel = if (snapshot.hasRecordDetail) "Gross received income" else "Positive source totals"
        val lossLabel = if (snapshot.hasRecordDetail) "Realized crypto losses" else "Negative source totals"
        return IncomeInsightsDisplay(period, "$periodDays calendar days · Recorded entries", longMonth(source.selectedMonth), rangeLabel(source.range), money(snapshot.netMinor),
            money(snapshot.grossMinor), if (snapshot.lossesMinor > 0) "− ${money(snapshot.lossesMinor)}" else money(0), if (snapshot.grossMinor > 0) pct(snapshot.margin) else "—",
            snapshot.changePercent?.let { (if (it > 0) "+" else "") + pct(it) + " vs previous period" } ?: "No positive prior-period baseline",
            (snapshot.changePercent ?: 0.0) < 0, snapshot.netMinor < 0, money(snapshot.dailyAverageMinor),
            if (snapshot.hasRecordDetail) snapshot.realizedCount.toString() else "—", if (snapshot.hasRecordDetail) snapshot.pendingCount.toString() else "—",
            if (snapshot.pendingCount > 0) "${snapshot.pendingCount} awaiting payment · ${money(snapshot.pendingMinor)}" else null,
            listOf(InsightFlowRow("gross", grossLabel, "Received earnings before realized losses", money(snapshot.grossMinor)),
                InsightFlowRow("losses", lossLabel, "Deducted from received earnings", if (snapshot.lossesMinor > 0) "− ${money(snapshot.lossesMinor)}" else money(0), snapshot.lossesMinor > 0),
                InsightFlowRow("net", "Net realized income", "Gross received income minus realized losses", money(snapshot.netMinor), snapshot.netMinor < 0)),
            sources, money(snapshot.regularGrossMinor), money(snapshot.variableGrossMinor), pct(snapshot.regularShare) + " of gross inflow", pct(snapshot.variableShare) + " of gross inflow",
            (snapshot.regularShare / 100).toFloat().coerceIn(0f, 1f), (snapshot.variableShare / 100).toFloat().coerceIn(0f, 1f),
            snapshot.biggestSource?.let { "${sourceTitle(it)} accounts for ${pct(snapshot.biggestShare)} of gross received income in this period." } ?: "Record received income to see your source mix.",
            trend, snapshot.accumulation.map(::point), snapshot.trendZero, snapshot.accumulationZero, money(snapshot.averageTrendMinor),
            snapshot.peak?.let { "Peak · ${shortMonth(YearMonth.parse(it.key))} · ${money(it.amountMinor)}" },
            if (trend.isEmpty()) "Net Income Trend" else "${trend.size}-Month Net Income Trend", snapshot.trend.all { it.empty }, grossLabel, lossLabel,
            comparison?.let { result -> InsightEstimateComparison(money(result.estimatedMonthlyIncomeMinor), money(result.trailingThreeMonthAverageMinor),
                money(result.differenceMinor), pct(result.variancePercentage),
                result.monthsEvaluated.map { key -> runCatching { shortMonth(YearMonth.parse(key)) }.getOrDefault(key) },
                when(result.status) { com.pfms.app.domain.model.analytics.EstimationStatus.ABOVE_ESTIMATE -> "Above Estimate"; com.pfms.app.domain.model.analytics.EstimationStatus.BELOW_ESTIMATE -> "Below Estimate"; else -> "On Target" },
                result.status == com.pfms.app.domain.model.analytics.EstimationStatus.BELOW_ESTIMATE) },
            if (snapshot.hasRecordDetail) snapshot.realizedCount == 0 && snapshot.pendingCount == 0 else !source.hasMonthlyIncome)
    }
    private fun point(point: IncomeInsightPoint): InsightChartPoint {
        val date = if (point.key.length == 7) YearMonth.parse(point.key).atDay(1) else LocalDate.parse(point.key)
        return InsightChartPoint(point.key, if (point.key.length == 7) date.format(DateTimeFormatter.ofPattern("MMM", Locale.US)) else date.dayOfMonth.toString(),
            if (point.key.length == 7) shortMonth(YearMonth.from(date)) else date.format(DateTimeFormatter.ofPattern("dd MMM yyyy", Locale.US)),
            money(point.amountMinor), if (point.empty) "—" else compact(point.amountMinor), point.x, point.y, point.amountMinor < 0, point.empty)
    }
    private fun compact(value: Long): String {
        val amount = BigDecimal.valueOf(value, 2)
        val divisor = if (amount.abs() >= BigDecimal(1000000)) BigDecimal(1000000) else if (amount.abs() >= BigDecimal(1000)) BigDecimal(1000) else BigDecimal.ONE
        return NumberFormat.getNumberInstance(Locale.US).apply { maximumFractionDigits = 1 }.format(amount.divide(divisor)) + if (divisor == BigDecimal(1000000)) "m" else if (divisor == BigDecimal(1000)) "k" else ""
    }
    private fun money(value: Long) = SavingsGoalUiFormatter.formatLkr(value)
    private fun pct(value: Double) = String.format(Locale.US, "%.1f%%", if (value.isFinite()) value else 0.0)
    private fun longMonth(month: YearMonth) = month.format(DateTimeFormatter.ofPattern("MMMM yyyy", Locale.US))
    private fun shortMonth(month: YearMonth) = month.format(DateTimeFormatter.ofPattern("MMM yyyy", Locale.US))
    private fun sourceTitle(key: String) = if (key == "Crypto") "Crypto net" else if (key == "Freelance") "Freelance (received)" else key
    private fun rangeLabel(range: IncomeInsightRange) = when(range) { IncomeInsightRange.MONTH -> "1M"; IncomeInsightRange.QUARTER -> "3M"; IncomeInsightRange.HALF_YEAR -> "6M"; IncomeInsightRange.YEAR -> "1Y"; IncomeInsightRange.ALL -> "All" }
}

package com.pfms.app.viewmodel

import androidx.lifecycle.ViewModel
import com.pfms.app.domain.model.SpendingPatternInsight
import com.pfms.app.domain.usecase.savings.SavingsGoalEntryRules
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.math.BigDecimal
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale

data class PatternMonthChoice(val month: YearMonth, val label: String)
data class PatternDisplayRow(val title: String, val increase: String, val current: String, val baseline: String, val difference: String, val comparison: String)

object SecondaryFinancePresentation {
    fun history(date: java.time.LocalDate) = (1L..3L).map { offset ->
        val month = YearMonth.from(date).minusMonths(offset)
        PatternMonthChoice(month, month.format(DateTimeFormatter.ofPattern("MMMM yyyy", Locale.US)))
    }
    fun pattern(row: SpendingPatternInsight) = PatternDisplayRow(row.categoryName, "${row.increasePercent}% above baseline",
        DashboardPresentationMapper.money(row.currentAmountMinor), DashboardPresentationMapper.money(row.baselineAmountMinor),
        DashboardPresentationMapper.money(row.differenceMinor), "${row.comparisonMonths} months · ${if (row.isMonthToDateComparison) "Comparable days" else "Complete months"}")
}

data class SimulatorScreenState(val source: SavingsSimulatorUiState = SavingsSimulatorUiState(), val reduction: String = "0", val planned: String = "0",
    val reductionError: String? = null, val plannedError: String? = null, val metrics: List<Pair<String, String>> = emptyList(),
    val progress: Float = 0f, val progressLabel: String = "0%")

class SavingsSimulatorScreenViewModel : ViewModel() {
    private val _state = MutableStateFlow(SimulatorScreenState())
    val state = _state.asStateFlow()
    private var dirty = false
    fun sync(source: SavingsSimulatorUiState) {
        val result = source.result
        _state.update { old -> old.copy(source = source,
            reduction = if (dirty) old.reduction else BigDecimal.valueOf(source.monthlyReductionMinor, 2).toPlainString(),
            planned = if (dirty) old.planned else BigDecimal.valueOf(source.plannedContributionMinor, 2).toPlainString(),
            progress = ((result?.projectedProgressPercent ?: 0) / 100f).coerceIn(0f, 1f), progressLabel = "${result?.projectedProgressPercent ?: 0}%",
            metrics = if (result == null) emptyList() else listOf("Currently saved" to DashboardPresentationMapper.money(result.currentSavedMinor),
                "Potential extra by deadline" to DashboardPresentationMapper.money(result.potentialExtraByDeadlineMinor),
                "Projected balance" to DashboardPresentationMapper.money(result.projectedBalanceAtDeadlineMinor),
                "Remaining shortfall" to DashboardPresentationMapper.money(result.projectedShortfallMinor),
                "Estimated completion" to (result.estimatedCompletionDate?.format(DateTimeFormatter.ofPattern("dd MMM yyyy", Locale.US)) ?: "Not estimated"))) }
    }
    fun edit(value: String, reduction: Boolean, save: (Long) -> Unit) {
        dirty = true
        val minor = if (value.isBlank()) 0L else SavingsGoalEntryRules.parseAmount(value)
        val error = if (minor == null) "Enter zero or a positive amount with up to 2 decimal places." else null
        _state.update { if (reduction) it.copy(reduction = value, reductionError = error) else it.copy(planned = value, plannedError = error) }
        if (minor != null) save(minor)
    }
}

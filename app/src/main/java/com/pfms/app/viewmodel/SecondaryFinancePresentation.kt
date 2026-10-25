package com.pfms.app.viewmodel

import androidx.lifecycle.ViewModel
import com.pfms.app.domain.model.SpendingPatternInsight
import com.pfms.app.domain.model.SpendingIncreaseLevel
import com.pfms.app.domain.usecase.savings.SavingsGoalEntryRules
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.math.BigDecimal
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale

data class PatternMonthChoice(val month: YearMonth, val label: String)
data class PatternDisplayRow(val title: String, val increase: String, val current: String, val baseline: String, val difference: String, val comparison: String,
    val severity: String, val level: SpendingIncreaseLevel)

object SecondaryFinancePresentation {
    fun history(date: java.time.LocalDate) = (1L..3L).map { offset ->
        val month = YearMonth.from(date).minusMonths(offset)
        PatternMonthChoice(month, month.format(DateTimeFormatter.ofPattern("MMMM yyyy", Locale.US)))
    }
    fun pattern(row: SpendingPatternInsight) = PatternDisplayRow(row.categoryName, "${row.increasePercent}% above baseline",
        DashboardPresentationMapper.money(row.currentAmountMinor), DashboardPresentationMapper.money(row.baselineAmountMinor),
        DashboardPresentationMapper.money(row.differenceMinor), "Average of ${row.comparisonMonths} historical months · ${if (row.isMonthToDateComparison) "Comparable days" else "Complete months"}",
        when (row.level) { SpendingIncreaseLevel.NOTICEABLE -> "Noticeable increase"; SpendingIncreaseLevel.HIGH -> "High increase" }, row.level)
}

data class SimulatorScreenState(val source: SavingsSimulatorUiState = SavingsSimulatorUiState(), val reduction: String = "0", val planned: String = "0",
    val reductionError: String? = null, val plannedError: String? = null, val metrics: List<Pair<String, String>> = emptyList(),
    val progress: Float = 0f, val progressLabel: String = "0%", val canShowResults: Boolean = false)

class SavingsSimulatorScreenViewModel : ViewModel() {
    private val _state = MutableStateFlow(SimulatorScreenState())
    val state = _state.asStateFlow()
    private var reductionDirty = false
    private var plannedDirty = false
    private var goalId: String? = null
    fun sync(source: SavingsSimulatorUiState) {
        val changedGoal = source.goal != null && goalId != null && source.goal.id != goalId
        if (changedGoal) { reductionDirty = false; plannedDirty = false }
        source.goal?.id?.let { goalId = it }
        _state.update { old -> presentation(old.copy(source = source,
            reduction = if (reductionDirty) old.reduction else BigDecimal.valueOf(source.monthlyReductionMinor, 2).toPlainString(),
            planned = if (plannedDirty) old.planned else BigDecimal.valueOf(source.plannedContributionMinor, 2).toPlainString(),
            reductionError = if (changedGoal) null else old.reductionError,
            plannedError = if (changedGoal) null else old.plannedError)) }
    }

    private fun parse(value: String) = if (value.isBlank()) 0L else SavingsGoalEntryRules.parseAmount(value)

    private fun presentation(state: SimulatorScreenState): SimulatorScreenState {
        val result = state.source.result
        val matches = !state.source.isLoading && state.source.error == null && state.reductionError == null && state.plannedError == null &&
            result != null && parse(state.reduction) == result.monthlyReductionMinor && parse(state.planned) == result.monthlyPlannedContributionMinor
        return state.copy(canShowResults = matches,
            progress = if (matches) ((result!!.projectedProgressPercent) / 100f).coerceIn(0f, 1f) else 0f,
            progressLabel = if (matches) "${result!!.projectedProgressPercent}%" else "0%",
            metrics = if (!matches) emptyList() else buildList {
                add("Currently saved" to DashboardPresentationMapper.money(result!!.currentSavedMinor))
                add("Potential extra by deadline" to DashboardPresentationMapper.money(result.potentialExtraByDeadlineMinor))
                add("Projected balance" to DashboardPresentationMapper.money(result.projectedBalanceAtDeadlineMinor))
                add("Remaining shortfall" to DashboardPresentationMapper.money(result.projectedShortfallMinor))
                if (!result.isGoalAlreadyComplete) result.estimatedCompletionDate?.let {
                    add("Estimated completion" to it.format(DateTimeFormatter.ofPattern("dd MMM yyyy", Locale.US)))
                }
            })
    }
    fun edit(value: String, reduction: Boolean, save: (Long) -> Unit) {
        if (reduction) reductionDirty = true else plannedDirty = true
        val minor = parse(value)
        val error = if (minor == null) "Enter zero or a positive amount with up to 2 decimal places." else null
        _state.update { presentation(if (reduction) it.copy(reduction = value, reductionError = error) else it.copy(planned = value, plannedError = error)) }
        if (minor != null) save(minor)
    }
}

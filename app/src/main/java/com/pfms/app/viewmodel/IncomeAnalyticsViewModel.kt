package com.pfms.app.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pfms.app.domain.model.Income
import com.pfms.app.domain.model.analytics.IncomeAnalyticsSummary
import com.pfms.app.domain.model.analytics.IncomeEstimationComparison
import com.pfms.app.domain.model.analytics.IncomeTrendPoint
import com.pfms.app.domain.model.analytics.MonthlyIncomeSummary
import com.pfms.app.domain.model.analytics.RegularVariableIncome
import com.pfms.app.domain.repository.AuthRepository
import com.pfms.app.domain.repository.IncomeRepository
import com.pfms.app.domain.usecase.analytics.GetIncomeAnalyticsUseCase
import com.pfms.app.domain.usecase.analytics.GetIncomeEstimationComparisonUseCase
import com.pfms.app.domain.usecase.analytics.GetIncomeTrendsUseCase
import com.pfms.app.domain.usecase.analytics.GetMonthlyIncomeSummaryUseCase
import com.pfms.app.domain.usecase.analytics.GetRegularVsVariableIncomeUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.YearMonth
import java.time.ZoneId
import javax.inject.Inject

data class IncomeAnalyticsUiState(
    val isLoading: Boolean = true,
    val selectedMonth: YearMonth = YearMonth.now(ZoneId.of("Asia/Colombo")),
    val monthlySummary: MonthlyIncomeSummary? = null,
    val regularVariable: RegularVariableIncome? = null,
    val trendPoints: List<IncomeTrendPoint> = emptyList(),
    val analyticsSummary: IncomeAnalyticsSummary? = null,
    val estimationComparison: IncomeEstimationComparison? = null,
    val estimatedMonthlyIncomeMinor: Long? = null,
    val errorMessage: String? = null
)

/**
 * ViewModel orchestrating income analytics, monthly breakdowns, trends,
 * and user-estimated income comparisons.
 *
 * Architecture Principles:
 * - Pure calculation boundary: All formulas are delegated to Phase 6 analytics use cases.
 * - Reactive: Observes [IncomeRepository.observeIncome] and recomputes analytics on changes.
 * - In-memory estimated income: The project currently does not persist estimated monthly income;
 *   estimates are maintained in UI state for evaluation against actual trailing averages.
 */
@HiltViewModel
class IncomeAnalyticsViewModel @Inject constructor(
    private val incomeRepository: IncomeRepository,
    private val authRepository: AuthRepository,
    private val getMonthlyIncomeSummaryUseCase: GetMonthlyIncomeSummaryUseCase,
    private val getRegularVsVariableIncomeUseCase: GetRegularVsVariableIncomeUseCase,
    private val getIncomeTrendsUseCase: GetIncomeTrendsUseCase,
    private val getIncomeEstimationComparisonUseCase: GetIncomeEstimationComparisonUseCase,
    private val getIncomeAnalyticsUseCase: GetIncomeAnalyticsUseCase
) : ViewModel() {

    private val _uiState = MutableStateFlow(IncomeAnalyticsUiState())
    val uiState: StateFlow<IncomeAnalyticsUiState> = _uiState.asStateFlow()

    private var currentIncomes: List<Income> = emptyList()

    init {
        observeIncomeData()
    }

    private fun observeIncomeData() {
        viewModelScope.launch {
            combine(
                authRepository.currentUser,
                incomeRepository.observeIncome()
            ) { user, records ->
                Pair(user, records)
            }
                .catch {
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            errorMessage = "Unable to load income records."
                        )
                    }
                }
                .collect { (user, records) ->
                    if (user == null) {
                        currentIncomes = emptyList()
                        _uiState.update {
                            it.copy(
                                isLoading = false,
                                monthlySummary = null,
                                regularVariable = null,
                                trendPoints = emptyList(),
                                analyticsSummary = null,
                                estimationComparison = null,
                                errorMessage = null
                            )
                        }
                    } else {
                        currentIncomes = records
                        recalculateAnalytics(
                            records = records,
                            month = _uiState.value.selectedMonth,
                            estimatedIncome = _uiState.value.estimatedMonthlyIncomeMinor
                        )
                    }
                }
        }
    }

    fun selectMonth(yearMonth: YearMonth) {
        _uiState.update { it.copy(selectedMonth = yearMonth) }
        recalculateAnalytics(
            records = currentIncomes,
            month = yearMonth,
            estimatedIncome = _uiState.value.estimatedMonthlyIncomeMinor
        )
    }

    fun previousMonth() {
        selectMonth(_uiState.value.selectedMonth.minusMonths(1))
    }

    fun nextMonth() {
        selectMonth(_uiState.value.selectedMonth.plusMonths(1))
    }

    /**
     * Sets user-entered estimated monthly income.
     * Note: Persistence wiring is deferred; this estimate is maintained in UI state.
     */
    fun setEstimatedMonthlyIncome(amountMinor: Long) {
        if (amountMinor < 0L) {
            _uiState.update { it.copy(errorMessage = "Estimated income cannot be negative.") }
            return
        }

        _uiState.update {
            it.copy(
                estimatedMonthlyIncomeMinor = amountMinor,
                errorMessage = null
            )
        }

        recalculateAnalytics(
            records = currentIncomes,
            month = _uiState.value.selectedMonth,
            estimatedIncome = amountMinor
        )
    }

    fun clearEstimatedMonthlyIncome() {
        _uiState.update {
            it.copy(
                estimatedMonthlyIncomeMinor = null,
                estimationComparison = null
            )
        }
        recalculateAnalytics(
            records = currentIncomes,
            month = _uiState.value.selectedMonth,
            estimatedIncome = null
        )
    }

    fun clearError() {
        _uiState.update { it.copy(errorMessage = null) }
    }

    private fun recalculateAnalytics(
        records: List<Income>,
        month: YearMonth,
        estimatedIncome: Long?
    ) {
        try {
            val monthlySummary = getMonthlyIncomeSummaryUseCase(records, month)
            val regularVsVariable = getRegularVsVariableIncomeUseCase(monthlySummary)
            val trends = getIncomeTrendsUseCase(records, monthCount = 6, referenceMonth = month)
            val analytics = getIncomeAnalyticsUseCase(
                incomes = records,
                targetMonth = month,
                estimatedMonthlyIncomeMinor = estimatedIncome,
                trendMonthCount = 6
            )
            val comparison = estimatedIncome?.let { estimate ->
                getIncomeEstimationComparisonUseCase(
                    estimatedMonthlyIncomeMinor = estimate,
                    incomes = records,
                    referenceMonth = month
                )
            }

            _uiState.update {
                it.copy(
                    isLoading = false,
                    monthlySummary = monthlySummary,
                    regularVariable = regularVsVariable,
                    trendPoints = trends,
                    analyticsSummary = analytics,
                    estimationComparison = comparison,
                    errorMessage = null
                )
            }
        } catch (e: Exception) {
            _uiState.update {
                it.copy(
                    isLoading = false,
                    errorMessage = "Error calculating income analytics."
                )
            }
        }
    }
}

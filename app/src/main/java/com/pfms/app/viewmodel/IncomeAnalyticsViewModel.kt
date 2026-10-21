package com.pfms.app.viewmodel

import com.pfms.app.data.error.OperationErrors
import com.pfms.app.data.error.ErrorAction
import kotlinx.coroutines.Job
import kotlinx.coroutines.withTimeout
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
import com.pfms.app.domain.repository.UserRepository
import com.pfms.app.domain.usecase.analytics.GetIncomeAnalyticsUseCase
import com.pfms.app.domain.usecase.analytics.GetIncomeEstimationComparisonUseCase
import com.pfms.app.domain.usecase.analytics.GetIncomeTrendsUseCase
import com.pfms.app.domain.usecase.analytics.IncomeRealizedCalculator
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
    val hasMonthlyIncome: Boolean = false,
    val errorMessage: String? = null,
    val isSaving: Boolean = false,
    val canRetry: Boolean = false
)

/**
 * ViewModel orchestrating income analytics, monthly breakdowns, trends,
 * and user-estimated income comparisons.
 *
 * Architecture Principles:
 * - Pure calculation boundary: All formulas are delegated to Phase 6 analytics use cases.
 * - Reactive: Observes [IncomeRepository.observeIncome] and recomputes analytics on changes.
 * - Persistent estimate: Stored per authenticated user via [UserRepository] profile persistence.
 */
@HiltViewModel
class IncomeAnalyticsViewModel @Inject constructor(
    private val incomeRepository: IncomeRepository,
    private val authRepository: AuthRepository,
    private val userRepository: UserRepository,
    private val getMonthlyIncomeSummaryUseCase: GetMonthlyIncomeSummaryUseCase,
    private val getRegularVsVariableIncomeUseCase: GetRegularVsVariableIncomeUseCase,
    private val getIncomeTrendsUseCase: GetIncomeTrendsUseCase,
    private val getIncomeEstimationComparisonUseCase: GetIncomeEstimationComparisonUseCase,
    private val getIncomeAnalyticsUseCase: GetIncomeAnalyticsUseCase
) : ViewModel() {

    private val _uiState = MutableStateFlow(IncomeAnalyticsUiState())
    val uiState: StateFlow<IncomeAnalyticsUiState> = _uiState.asStateFlow()

    private var observation: Job? = null
    private var failedAction: (() -> Unit)? = null
    private var estimateDirty = false
    private var currentUid: String? = null
    fun retry() { if (!_uiState.value.isSaving && _uiState.value.canRetry) failedAction?.invoke() }

    private var currentIncomes: List<Income> = emptyList()

    init {
        observeIncomeData()
    }

    private fun observeIncomeData() {
        observation?.cancel()
        observation = viewModelScope.launch {
            combine(
                authRepository.currentUser,
                incomeRepository.observeIncome(),
                userRepository.observeProfile()
            ) { user, records, profile ->
                Triple(user, records, profile)
            }
                .catch { error ->
                    val problem = OperationErrors.present(error, ErrorAction.LOAD)
                    failedAction = ::observeIncomeData
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            errorMessage = problem.message, canRetry = problem.canRetry
                        )
                    }
                }
                .collect { (user, records, profile) ->
                    if (currentUid != user?.uid) {
                        currentUid = user?.uid
                        estimateDirty = false
                        failedAction = null
                        _uiState.update { it.copy(isSaving = false, errorMessage = null, canRetry = false) }
                    }
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
                                estimatedMonthlyIncomeMinor = null,
                                hasMonthlyIncome = false,
                                errorMessage = null
                            )
                        }
                    } else {
                        currentIncomes = records
                        val estimate = if (estimateDirty) _uiState.value.estimatedMonthlyIncomeMinor
                            else profile?.takeIf { it.uid == user.uid }?.estimatedMonthlyIncomeMinor
                        _uiState.update {
                            it.copy(estimatedMonthlyIncomeMinor = estimate)
                        }
                        recalculateAnalytics(
                            records = records,
                            month = _uiState.value.selectedMonth,
                            estimatedIncome = estimate
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
     * Sets user-entered estimated monthly income and persists it for the current user.
     */
    fun setEstimatedMonthlyIncome(amountMinor: Long) {
        if (amountMinor < 0L) {
            _uiState.update { it.copy(errorMessage = "Estimated income cannot be negative.", canRetry = false) }
            return
        }
        saveEstimate(amountMinor)
    }

    fun clearEstimatedMonthlyIncome() = saveEstimate(null)

    private fun saveEstimate(amount: Long?) {
        if (_uiState.value.isSaving) return
        val savingUid = currentUid
        estimateDirty = true
        _uiState.update { it.copy(isSaving = true, estimatedMonthlyIncomeMinor = amount, errorMessage = null, canRetry = false) }
        recalculateAnalytics(currentIncomes, _uiState.value.selectedMonth, amount)
        viewModelScope.launch {
            try {
                withTimeout(20_000) { userRepository.updateEstimatedMonthlyIncome(amount).getOrThrow() }
                if (savingUid != currentUid) return@launch
                estimateDirty = false
                _uiState.update { it.copy(isSaving = false, errorMessage = null, canRetry = false) }
            } catch (error: Exception) {
                val problem = OperationErrors.present(error)
                if (savingUid != currentUid) return@launch
                failedAction = { saveEstimate(amount) }
                _uiState.update { it.copy(isSaving = false, errorMessage = problem.message, canRetry = problem.canRetry) }
            }
        }
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
                    hasMonthlyIncome = records.any { IncomeRealizedCalculator.matchesMonth(it, month) },
                    regularVariable = regularVsVariable,
                    trendPoints = trends,
                    analyticsSummary = analytics,
                    estimationComparison = comparison,
                    errorMessage = if (estimateDirty) it.errorMessage else null
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

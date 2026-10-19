package com.pfms.app.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pfms.app.domain.model.dashboard.MonthlySummary
import com.pfms.app.domain.model.dashboard.MonthlyTrendPoint
import com.pfms.app.domain.model.dashboard.SpendingInsights
import com.pfms.app.domain.repository.DashboardRepository
import com.pfms.app.domain.repository.IncomeRepository
import com.pfms.app.domain.usecase.analytics.IncomeRealizedCalculator
import com.pfms.app.domain.usecase.dashboard.AppClock
import com.pfms.app.domain.usecase.dashboard.MonthlyFinanceCalculator
import com.pfms.app.domain.usecase.dashboard.SpendingInsightsCalculator
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import java.time.YearMonth
import javax.inject.Inject

/** FR-30, FR-31, FR-32: category chart, split bar, insight card and six-month trend. */
data class SpendingAnalyticsUiState(
    val isLoading: Boolean = true,
    val selectedMonth: YearMonth = AppClock.currentMonth(),
    val summary: MonthlySummary? = null,
    val insights: SpendingInsights? = null,
    val trend: List<MonthlyTrendPoint> = emptyList(),
    val errorMessage: String? = null
) {
    val isCurrentMonth: Boolean get() = selectedMonth == AppClock.currentMonth()
}

@HiltViewModel
@OptIn(ExperimentalCoroutinesApi::class)
class SpendingAnalyticsViewModel @Inject constructor(
    private val incomeRepository: IncomeRepository,
    private val dashboardRepository: DashboardRepository
) : ViewModel() {

    private val selectedMonth = MutableStateFlow(AppClock.currentMonth())
    private val retryKey = MutableStateFlow(0)

    val uiState: StateFlow<SpendingAnalyticsUiState> = retryKey.flatMapLatest {
        selectedMonth.flatMapLatest { month ->
            // One range query covers the selected month plus the five before it (trend).
            val trendStart = month.minusMonths(TREND_MONTHS - 1L).atDay(1)
            combine(
                incomeRepository.observeIncome(),
                dashboardRepository.observeExpensesBetween(trendStart, month.atEndOfMonth())
            ) { incomes, expenses ->
                val monthExpenses = expenses.filter {
                    IncomeRealizedCalculator.parseYearMonth(it.date) == month
                }
                SpendingAnalyticsUiState(
                    isLoading = false,
                    selectedMonth = month,
                    summary = MonthlyFinanceCalculator.summarize(incomes, monthExpenses, month),
                    insights = SpendingInsightsCalculator.build(monthExpenses),
                    trend = MonthlyFinanceCalculator.trend(incomes, expenses, month, TREND_MONTHS)
                )
            }.flowOn(Dispatchers.Default)
        }.catch {
            emit(
                SpendingAnalyticsUiState(
                    isLoading = false,
                    selectedMonth = selectedMonth.value,
                    errorMessage = "We couldn't load your spending analytics. Please try again."
                )
            )
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SpendingAnalyticsUiState())

    fun previousMonth() {
        selectedMonth.value = selectedMonth.value.minusMonths(1)
    }

    fun nextMonth() {
        val next = selectedMonth.value.plusMonths(1)
        if (!next.isAfter(AppClock.currentMonth())) selectedMonth.value = next
    }

    fun retry() {
        retryKey.value = retryKey.value + 1
    }

    private companion object {
        const val TREND_MONTHS = 6
    }
}

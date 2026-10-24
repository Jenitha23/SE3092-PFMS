package com.pfms.app.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pfms.app.domain.model.dashboard.MonthlySummary
import com.pfms.app.domain.model.dashboard.Transaction
import com.pfms.app.domain.repository.DashboardRepository
import com.pfms.app.domain.repository.IncomeRepository
import com.pfms.app.domain.usecase.dashboard.AppClock
import com.pfms.app.domain.usecase.dashboard.TransactionHistoryCalculator
import com.pfms.app.domain.usecase.dashboard.DashboardOverviewCalculator
import com.pfms.app.domain.model.SessionState
import com.pfms.app.domain.model.UserProfile
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
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/** FR-29: everything the home dashboard shows for the selected month. */
data class DashboardUiState(
    val isLoading: Boolean = true,
    val selectedMonth: YearMonth = AppClock.currentMonth(),
    val summary: MonthlySummary? = null,
    /** Latest 10 transactions of the selected month, newest first. */
    val latestTransactions: List<Transaction> = emptyList(),
    val errorMessage: String? = null,
    val canRetry: Boolean = true,
    val content: DashboardContentState? = null
) {
    val isCurrentMonth: Boolean get() = selectedMonth == AppClock.currentMonth()
    val monthLabel: String get() = selectedMonth.format(java.time.format.DateTimeFormatter.ofPattern("MMM yyyy", java.util.Locale.ENGLISH))
}

/**
 * Real-time dashboard. Income streams from Room (offline-first), expenses stream from Room which is
 * mirrored from the Firestore listener, and the goal streams from Firestore. Month switching only
 * re-queries the selected month (NFR-18). All calculation is delegated to [DashboardOverviewCalculator]
 * so the dashboard and analytics never disagree (NFR-17).
 */
@HiltViewModel
@OptIn(ExperimentalCoroutinesApi::class)
class DashboardViewModel @Inject constructor(
    private val incomeRepository: IncomeRepository,
    private val dashboardRepository: DashboardRepository
) : ViewModel() {

    private val selectedMonth = MutableStateFlow(AppClock.currentMonth())
    private val retryKey = MutableStateFlow(0)
    private val _chrome = MutableStateFlow(DashboardChromeState())
    val chrome: StateFlow<DashboardChromeState> = _chrome.asStateFlow()
    val monthChoices: List<Pair<YearMonth, String>> get() = (0L..23L).map {
        val month = AppClock.currentMonth().minusMonths(it)
        month to month.format(java.time.format.DateTimeFormatter.ofPattern("MMMM yyyy", java.util.Locale.ENGLISH))
    }

    fun onIdentityChanged(profile: UserProfile?, session: SessionState) {
        _chrome.update { it.copy(identity = DashboardPresentationMapper.identity(profile, session)) }
    }
    fun onGoalChanged(state: SavingsGoalUiState) {
        _chrome.update { it.copy(goal = DashboardPresentationMapper.goal(state)) }
    }
    fun showMonthMenu() { _chrome.update { it.copy(monthMenuExpanded = true) } }
    fun dismissMonthMenu() { _chrome.update { it.copy(monthMenuExpanded = false) } }
    fun selectMonth(month: YearMonth) {
        if (month <= AppClock.currentMonth()) selectedMonth.value = month
        dismissMonthMenu()
    }

    private data class MonthBundle(
        val month: YearMonth,
        val summary: MonthlySummary,
        val latest: List<Transaction>,
        val content: DashboardContentState
    )

    private val monthBundle = selectedMonth.flatMapLatest { month ->
        combine(
            incomeRepository.observeIncome(),
            dashboardRepository.observeExpensesBetween(
                month.minusMonths(DashboardOverviewCalculator.TREND_MONTHS - 1L).atDay(1), month.atEndOfMonth())
        ) { incomes, expenses ->
            val overview = DashboardOverviewCalculator.calculate(incomes, expenses, month)
            val summary = overview.summary
            val monthIncomes = incomes.filter {
                com.pfms.app.domain.usecase.analytics.IncomeRealizedCalculator.matchesMonth(it, month)
            }
            val monthExpenses = expenses.filter {
                com.pfms.app.domain.usecase.analytics.IncomeRealizedCalculator.parseYearMonth(it.date) == month
            }
            val latest = TransactionHistoryCalculator.merge(monthIncomes, monthExpenses)
                .take(LATEST_COUNT)
            MonthBundle(month, summary, latest, DashboardPresentationMapper.content(overview, latest))
        }.flowOn(Dispatchers.Default)
    }

    val uiState: StateFlow<DashboardUiState> = retryKey.flatMapLatest {
        monthBundle.map { bundle ->
            DashboardUiState(
                isLoading = false,
                selectedMonth = bundle.month,
                summary = bundle.summary,
                latestTransactions = bundle.latest,
                content = bundle.content
            )
        }.catch { error ->
            val problem = com.pfms.app.data.error.OperationErrors.present(error, com.pfms.app.data.error.ErrorAction.LOAD)
            emit(
                DashboardUiState(
                    isLoading = false,
                    selectedMonth = selectedMonth.value,
                    errorMessage = problem.message, canRetry = problem.canRetry
                )
            )
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DashboardUiState())

    fun previousMonth() {
        selectedMonth.value = selectedMonth.value.minusMonths(1)
    }

    /** The dashboard never moves past the current month. */
    fun nextMonth() {
        val next = selectedMonth.value.plusMonths(1)
        if (!next.isAfter(AppClock.currentMonth())) selectedMonth.value = next
    }

    fun retry() {
        retryKey.value = retryKey.value + 1
    }

    private companion object {
        const val LATEST_COUNT = 10
    }
}

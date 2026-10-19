package com.pfms.app.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pfms.app.domain.model.dashboard.MonthlySummary
import com.pfms.app.domain.model.dashboard.Transaction
import com.pfms.app.domain.repository.DashboardRepository
import com.pfms.app.domain.repository.IncomeRepository
import com.pfms.app.domain.usecase.dashboard.AppClock
import com.pfms.app.domain.usecase.dashboard.TransactionHistoryCalculator
import com.pfms.app.domain.usecase.dashboard.MonthlyFinanceCalculator
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

/** FR-29: everything the home dashboard shows for the selected month. */
data class DashboardUiState(
    val isLoading: Boolean = true,
    val selectedMonth: YearMonth = AppClock.currentMonth(),
    val summary: MonthlySummary? = null,
    /** Latest 10 transactions of the selected month, newest first. */
    val latestTransactions: List<Transaction> = emptyList(),
    val errorMessage: String? = null
) {
    val isCurrentMonth: Boolean get() = selectedMonth == AppClock.currentMonth()
}

/**
 * Real-time dashboard. Income streams from Room (offline-first), expenses stream from Room which is
 * mirrored from the Firestore listener, and the goal streams from Firestore. Month switching only
 * re-queries the selected month (NFR-18). All calculation is delegated to [MonthlyFinanceCalculator]
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

    private data class MonthBundle(
        val month: YearMonth,
        val summary: MonthlySummary,
        val latest: List<Transaction>
    )

    private val monthBundle = selectedMonth.flatMapLatest { month ->
        combine(
            incomeRepository.observeIncome(),
            dashboardRepository.observeExpensesBetween(month.atDay(1), month.atEndOfMonth())
        ) { incomes, expenses ->
            val summary = MonthlyFinanceCalculator.summarize(incomes, expenses, month)
            val monthIncomes = incomes.filter {
                com.pfms.app.domain.usecase.analytics.IncomeRealizedCalculator.matchesMonth(it, month)
            }
            val latest = `TransactionHistoryCalculator`.merge(monthIncomes, expenses)
                .take(LATEST_COUNT)
            MonthBundle(month, summary, latest)
        }.flowOn(Dispatchers.Default)
    }

    val uiState: StateFlow<DashboardUiState> = retryKey.flatMapLatest {
        monthBundle.map { bundle ->
            DashboardUiState(
                isLoading = false,
                selectedMonth = bundle.month,
                summary = bundle.summary,
                latestTransactions = bundle.latest
            )
        }.catch {
            emit(
                DashboardUiState(
                    isLoading = false,
                    selectedMonth = selectedMonth.value,
                    errorMessage = "We couldn't load your dashboard. Please try again."
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

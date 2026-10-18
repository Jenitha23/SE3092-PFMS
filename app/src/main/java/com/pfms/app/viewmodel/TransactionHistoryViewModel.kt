package com.pfms.app.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pfms.app.domain.model.ExpenseClassification
import com.pfms.app.domain.model.ExpensePaymentMethod
import com.pfms.app.domain.model.dashboard.HistoryFilter
import com.pfms.app.domain.model.dashboard.HistorySummary
import com.pfms.app.domain.model.dashboard.Transaction
import com.pfms.app.domain.model.dashboard.TransactionTypeFilter
import com.pfms.app.domain.usecase.dashboard.TransactionHistoryCalculator
import com.pfms.app.domain.repository.DashboardRepository
import com.pfms.app.domain.repository.IncomeRepository
import com.pfms.app.domain.usecase.analytics.IncomeRealizedCalculator
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
import java.time.LocalDate
import javax.inject.Inject

/** FR-33: filtered, incrementally loaded history with a match count and total. */
data class TransactionHistoryUiState(
    val isLoading: Boolean = true,
    val filter: HistoryFilter = HistoryFilter(),
    /** The rows currently loaded (grows by one page at a time). */
    val visible: List<Transaction> = emptyList(),
    val summary: HistorySummary = HistorySummary(0, 0L, 0L),
    val canLoadMore: Boolean = false,
    val hasAnyTransactions: Boolean = false,
    val incomeSources: List<String> = INCOME_SOURCES,
    val expenseCategories: List<String> = emptyList(),
    val errorMessage: String? = null
) {
    companion object {
        val INCOME_SOURCES = listOf(
            IncomeRealizedCalculator.SOURCE_SALARY,
            IncomeRealizedCalculator.SOURCE_FREELANCE,
            IncomeRealizedCalculator.SOURCE_GOOGLE_ADSENSE,
            IncomeRealizedCalculator.SOURCE_CRYPTO,
            IncomeRealizedCalculator.SOURCE_OTHER
        )
    }
}

@HiltViewModel
@OptIn(ExperimentalCoroutinesApi::class)
class TransactionHistoryViewModel @Inject constructor(
    incomeRepository: IncomeRepository,
    dashboardRepository: DashboardRepository
) : ViewModel() {

    private val filter = MutableStateFlow(HistoryFilter())
    private val visibleCount = MutableStateFlow(`TransactionHistoryCalculator`.PAGE_SIZE)
    private val retryKey = MutableStateFlow(0)

    private data class Merged(val rows: List<Transaction>, val categories: List<String>)
    private data class Filtered(val rows: List<Transaction>, val summary: HistorySummary)

    // Stage 1: merge + sort once per data change (off the main thread, NFR-07).
    private val merged = retryKey.flatMapLatest {
        combine(
            incomeRepository.observeIncome(),
            dashboardRepository.observeExpenses()
        ) { incomes, expenses ->
            Merged(
                rows = `TransactionHistoryCalculator`.merge(incomes, expenses),
                categories = expenses.map { it.categoryName.trim() }
                    .filter { it.isNotBlank() }
                    .distinct()
                    .sorted()
            )
        }.flowOn(Dispatchers.Default)
    }

    // Stage 2: filter + total whenever the data or the filter changes.
    private val filtered = combine(merged, filter) { data, f ->
        val rows = `TransactionHistoryCalculator`.filter(data.rows, f)
        Filtered(rows, `TransactionHistoryCalculator`.summarize(rows)) to data
    }.flowOn(Dispatchers.Default)

    // Stage 3: slice the visible page. Scrolling only changes this cheap step.
    val uiState: StateFlow<TransactionHistoryUiState> = combine(filtered, filter, visibleCount) { pair, f, count ->
        val (result, data) = pair
        TransactionHistoryUiState(
            isLoading = false,
            filter = f,
            visible = result.rows.take(count),
            summary = result.summary,
            canLoadMore = result.rows.size > count,
            hasAnyTransactions = data.rows.isNotEmpty(),
            expenseCategories = data.categories
        )
    }.catch {
        emit(
            TransactionHistoryUiState(
                isLoading = false,
                filter = filter.value,
                errorMessage = "We couldn't load your transactions. Please try again."
            )
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TransactionHistoryUiState())

    fun loadMore() {
        visibleCount.value = visibleCount.value + `TransactionHistoryCalculator`.PAGE_SIZE
    }

    fun retry() {
        retryKey.value = retryKey.value + 1
    }

    fun setType(type: TransactionTypeFilter) = updateFilter {
        // Drop filters that cannot apply to the newly selected type.
        when (type) {
            TransactionTypeFilter.INCOME -> it.copy(
                type = type, expenseCategory = null, paymentMethod = null, classification = null
            )
            TransactionTypeFilter.EXPENSE -> it.copy(type = type, incomeSource = null)
            TransactionTypeFilter.ALL -> it.copy(type = type)
        }
    }

    fun setDateRange(start: LocalDate?, end: LocalDate?) = updateFilter {
        // Keep the range valid if the user picks them in either order.
        if (start != null && end != null && end.isBefore(start)) it.copy(startDate = end, endDate = start)
        else it.copy(startDate = start, endDate = end)
    }

    fun setIncomeSource(source: String?) = updateFilter {
        it.copy(incomeSource = source, type = if (source != null) TransactionTypeFilter.INCOME else it.type)
    }

    fun setExpenseCategory(category: String?) = updateFilter {
        it.copy(expenseCategory = category, type = if (category != null) TransactionTypeFilter.EXPENSE else it.type)
    }

    fun setPaymentMethod(method: ExpensePaymentMethod?) = updateFilter {
        it.copy(paymentMethod = method, type = if (method != null) TransactionTypeFilter.EXPENSE else it.type)
    }

    fun setClassification(classification: ExpenseClassification?) = updateFilter {
        it.copy(classification = classification, type = if (classification != null) TransactionTypeFilter.EXPENSE else it.type)
    }

    fun clearFilters() = updateFilter { HistoryFilter() }

    private fun updateFilter(change: (HistoryFilter) -> HistoryFilter) {
        filter.value = change(filter.value)
        visibleCount.value = `TransactionHistoryCalculator`.PAGE_SIZE
    }
}

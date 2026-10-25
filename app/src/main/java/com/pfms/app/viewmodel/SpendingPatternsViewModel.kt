package com.pfms.app.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pfms.app.domain.model.SpendingPatternResult
import com.pfms.app.data.error.ErrorAction
import com.pfms.app.data.error.OperationErrors
import com.pfms.app.domain.usecase.ObserveSpendingPatternsUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn

/** User explicitly confirms which past months are sufficiently recorded. */
@HiltViewModel
class SpendingPatternsViewModel @Inject constructor(
    private val observePatterns: ObserveSpendingPatternsUseCase
) : ViewModel() {
    private val selectedHistory = MutableStateFlow<Set<YearMonth>>(emptySet())
    private val today = MutableStateFlow(LocalDate.now(ZoneId.of("Asia/Colombo")))
    private val reload = MutableStateFlow(0L)
    internal var dateProvider: () -> LocalDate = { LocalDate.now(ZoneId.of("Asia/Colombo")) }

    val confirmedMonths: StateFlow<Set<YearMonth>> = selectedHistory

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val uiState: StateFlow<SpendingPatternsUiState> = combine(today, selectedHistory, reload) { date, months, _ -> date to months }
        .flatMapLatest { (date, months) ->
            observePatterns.observe(date, months)
                .map { result -> SpendingPatternsUiState(date = date, result = result, isLoading = false) }
                .onStart { emit(SpendingPatternsUiState(date = date)) }
                .catch {
                    val problem = OperationErrors.present(it, ErrorAction.LOAD)
                    emit(SpendingPatternsUiState(date = date, isLoading = false, error = problem.message, canRetry = problem.canRetry))
                }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000, replayExpirationMillis = 0), SpendingPatternsUiState())

    fun setMonthConfirmed(month: YearMonth, confirmed: Boolean) {
        val current = YearMonth.from(today.value)
        if (month >= current || month < current.minusMonths(3)) return
        selectedHistory.value = if (confirmed) selectedHistory.value + month else selectedHistory.value - month
    }

    fun refreshDate() {
        val date = dateProvider()
        val month = YearMonth.from(date)
        selectedHistory.value = selectedHistory.value.filter { it < month && it >= month.minusMonths(3) }.toSet()
        today.value = date
    }

    fun retry() { refreshDate(); reload.value++ }
}

data class SpendingPatternsUiState(
    val date: LocalDate = LocalDate.now(ZoneId.of("Asia/Colombo")),
    val isLoading: Boolean = true,
    val result: SpendingPatternResult? = null,
    val error: String? = null,
    val canRetry: Boolean = false
)

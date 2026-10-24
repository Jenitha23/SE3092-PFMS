package com.pfms.app.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pfms.app.domain.model.SpendingPatternResult
import com.pfms.app.domain.model.SpendingPatternStatus
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
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn

/** User explicitly confirms which past months are sufficiently recorded. */
@HiltViewModel
class SpendingPatternsViewModel @Inject constructor(
    private val observePatterns: ObserveSpendingPatternsUseCase
) : ViewModel() {
    private val selectedHistory = MutableStateFlow<Set<YearMonth>>(emptySet())
    private val today = MutableStateFlow(LocalDate.now(ZoneId.of("Asia/Colombo")))

    val confirmedMonths: StateFlow<Set<YearMonth>> = selectedHistory

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val uiState: StateFlow<SpendingPatternsUiState> = combine(today, selectedHistory) { date, months -> date to months }
        .flatMapLatest { (date, months) ->
            observePatterns.observe(date, months)
                .combine(flowOf(date)) { result, day -> SpendingPatternsUiState(date = day, result = result, isLoading = false) }
                .catch { emit(SpendingPatternsUiState(date = date, isLoading = false, error = it.message ?: "Unable to calculate insights")) }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SpendingPatternsUiState())

    fun setMonthConfirmed(month: YearMonth, confirmed: Boolean) {
        val current = YearMonth.from(today.value)
        if (month >= current || month < current.minusMonths(3)) return
        selectedHistory.value = if (confirmed) selectedHistory.value + month else selectedHistory.value - month
    }

    fun refreshDate() { today.value = LocalDate.now(ZoneId.of("Asia/Colombo")) }
}

data class SpendingPatternsUiState(
    val date: LocalDate = LocalDate.now(ZoneId.of("Asia/Colombo")),
    val isLoading: Boolean = true,
    val result: SpendingPatternResult? = null,
    val error: String? = null
)

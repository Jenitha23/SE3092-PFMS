package com.pfms.app.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pfms.app.data.error.ErrorAction
import com.pfms.app.data.error.OperationErrors
import com.pfms.app.domain.model.MonthlyDataStatus
import com.pfms.app.domain.model.MonthlyFinancialRecords
import com.pfms.app.domain.repository.FinancialContextRepository
import com.pfms.app.domain.usecase.MonthlyDataPolicy
import com.pfms.app.domain.usecase.WelcomeBackEligibility
import com.pfms.app.domain.usecase.dashboard.AppClock
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.time.Clock
import java.time.YearMonth
import javax.inject.Inject

data class MonthlyDataUiState(
    val userId: String? = null,
    val status: MonthlyDataStatus? = null,
    val isLoading: Boolean = true,
    val isReviewing: Boolean = false,
    val errorMessage: String? = null
)

@HiltViewModel
@OptIn(ExperimentalCoroutinesApi::class)
class FinancialContextViewModel @Inject constructor(
    private val repository: FinancialContextRepository,
    private val clock: Clock
) : ViewModel() {
    private val month = MutableStateFlow(AppClock.currentMonth(clock))
    private val reload = MutableStateFlow(0)
    private val reviewing = MutableStateFlow(false)
    private val reviewError = MutableStateFlow<String?>(null)
    private var latestRecords: MonthlyFinancialRecords? = null
    private val today = flow {
        while (currentCoroutineContext().isActive) {
            emit(AppClock.today(clock))
            delay(60_000)
        }
    }.distinctUntilChanged().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AppClock.today(clock))

    private val data = combine(month, reload) { selected, _ -> selected }.flatMapLatest { selected ->
        combine(repository.observeMonth(selected), today) { records, date ->
            if (latestRecords?.userId != records.userId) reviewError.value = null
            latestRecords = records
            if (!records.isReady) MonthlyDataUiState() else MonthlyDataUiState(
                userId = records.userId, status = MonthlyDataPolicy.assess(records, date), isLoading = false)
        }.onStart { latestRecords = null; emit(MonthlyDataUiState()) }
            .catch { error -> emit(MonthlyDataUiState(isLoading = false,
                errorMessage = OperationErrors.present(error, ErrorAction.LOAD).message)) }
    }

    val monthlyState: StateFlow<MonthlyDataUiState> = combine(data, reviewing, reviewError) { data, busy, error ->
        data.copy(isReviewing = busy, errorMessage = error ?: data.errorMessage)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), MonthlyDataUiState())

    val showWelcomeBack: StateFlow<Boolean> = combine(
        repository.observeWelcomeActivity(), today
    ) { activity, date -> !activity.dismissed && WelcomeBackEligibility.isEligible(activity.lastRecordedAt, date) }
        .catch { error ->
            if (error is CancellationException) throw error
            emit(false) // An unavailable activity timestamp is not evidence of inactivity.
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    fun selectMonth(selected: YearMonth) {
        if (month.value != selected) { reviewError.value = null; month.value = selected }
    }

    fun retry() { reviewError.value = null; reload.value++ }

    fun reviewMonth(signature: String) {
        if (reviewing.value) return
        val records = latestRecords?.takeIf { it.isReady && it.month == month.value } ?: return
        reviewing.value = true
        reviewError.value = null
        viewModelScope.launch {
            try {
                withTimeout(10_000) { repository.reviewMonth(records.userId, records.month, signature).getOrThrow() }
            } catch (error: Exception) {
                val problem = OperationErrors.present(error)
                if (latestRecords?.userId == records.userId) reviewError.value = if (error is IllegalArgumentException)
                    "Your records changed. Please review this month again." else problem.message
            } finally { reviewing.value = false }
        }
    }

    fun dismissWelcome() { viewModelScope.launch { repository.dismissWelcome() } }
}

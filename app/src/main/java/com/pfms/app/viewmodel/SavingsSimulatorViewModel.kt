package com.pfms.app.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pfms.app.domain.model.GoalTransaction
import com.pfms.app.domain.model.SavingsGoal
import com.pfms.app.domain.model.SavingsSimulation
import com.pfms.app.domain.repository.SavingsGoalRepository
import com.pfms.app.domain.usecase.CalculateSavingsSimulationUseCase
import com.pfms.app.data.error.ErrorAction
import com.pfms.app.data.error.OperationErrors
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.map

@HiltViewModel
class SavingsSimulatorViewModel @Inject constructor(
    private val goals: SavingsGoalRepository,
    private val simulate: CalculateSavingsSimulationUseCase
) : ViewModel() {
    private val monthlyReductionMinor = MutableStateFlow(0L)
    private val plannedContributionMinor = MutableStateFlow(0L)
    private val reload = MutableStateFlow(0L)
    internal var dateProvider: () -> LocalDate = { LocalDate.now(ZoneId.of("Asia/Colombo")) }

    @OptIn(ExperimentalCoroutinesApi::class)
    private val goalAndLedger = goals.observeActiveGoal().flatMapLatest { goal ->
        if (goal == null) flowOf(null to emptyList<GoalTransaction>())
        else goals.observeTransactions(goal.id).map { ledger -> goal to ledger }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    val uiState: StateFlow<SavingsSimulatorUiState> = reload.flatMapLatest {
        combine(goalAndLedger, monthlyReductionMinor, plannedContributionMinor) { (goal, ledger), reduction, planned ->
            if (goal == null) SavingsSimulatorUiState(isLoading = false)
            else {
                val result = runCatching {
                    simulate.simulate(goal, ledger, reduction, planned, dateProvider())
                }
                SavingsSimulatorUiState(
                    isLoading = false, goal = goal, monthlyReductionMinor = reduction,
                    plannedContributionMinor = planned, result = result.getOrNull(),
                    error = if (result.isFailure) "This scenario couldn't be calculated. Try smaller amounts or check your goal's target and date." else null
                )
            }
        }.catch { error ->
            val problem = OperationErrors.present(error, ErrorAction.LOAD)
            emit(SavingsSimulatorUiState(isLoading = false, error = problem.message, canRetry = problem.canRetry))
        }.onStart { emit(SavingsSimulatorUiState()) }
    }.combine(goals.observeRestoration()) { state, restoration ->
        if (state.goal != null) state
        else if (restoration.isRefreshing) state.copy(isLoading = true)
        else if (restoration.error != null && state.error == null) state.copy(isLoading = false,
            error = OperationErrors.present(restoration.error, ErrorAction.LOAD).message)
        else state
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000, replayExpirationMillis = 0), SavingsSimulatorUiState())

    /** Called with LKR minor units, never floats. */
    fun setMonthlyReductionMinor(amount: Long) { if (amount >= 0L) monthlyReductionMinor.value = amount }
    fun setPlannedContributionMinor(amount: Long) { if (amount >= 0L) plannedContributionMinor.value = amount }

    /** Restart read observation and recalculate the date; never persist a simulated amount. */
    fun retry() { reload.value++ }
}

data class SavingsSimulatorUiState(
    val isLoading: Boolean = true,
    val goal: SavingsGoal? = null,
    val monthlyReductionMinor: Long = 0L,
    val plannedContributionMinor: Long = 0L,
    val result: SavingsSimulation? = null,
    val error: String? = null,
    val canRetry: Boolean = false
)

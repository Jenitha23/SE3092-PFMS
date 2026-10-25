package com.pfms.app.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pfms.app.domain.model.GoalTransaction
import com.pfms.app.domain.model.SavingsGoal
import com.pfms.app.domain.model.SavingsSimulation
import com.pfms.app.domain.repository.SavingsGoalRepository
import com.pfms.app.domain.usecase.CalculateSavingsSimulationUseCase
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

@HiltViewModel
class SavingsSimulatorViewModel @Inject constructor(
    private val goals: SavingsGoalRepository,
    private val simulate: CalculateSavingsSimulationUseCase
) : ViewModel() {
    private val monthlyReductionMinor = MutableStateFlow(0L)
    private val plannedContributionMinor = MutableStateFlow(0L)

    @OptIn(ExperimentalCoroutinesApi::class)
    private val goalAndLedger = goals.observeActiveGoal().flatMapLatest { goal ->
        if (goal == null) flowOf(null to emptyList<GoalTransaction>())
        else goals.observeTransactions(goal.id).flatMapLatest { ledger -> flowOf(goal to ledger) }
    }

    val uiState: StateFlow<SavingsSimulatorUiState> = combine(
        goalAndLedger, monthlyReductionMinor, plannedContributionMinor
    ) { (goal, ledger), reduction, planned ->
        if (goal == null) SavingsSimulatorUiState(isLoading = false)
        else {
            val result = runCatching {
                simulate.simulate(goal, ledger, reduction, planned, LocalDate.now(ZoneId.of("Asia/Colombo")))
            }
            SavingsSimulatorUiState(
                isLoading = false, goal = goal, monthlyReductionMinor = reduction,
                plannedContributionMinor = planned, result = result.getOrNull(),
                error = result.exceptionOrNull()?.message
            )
        }
    }.catch { error ->
        val problem = com.pfms.app.data.error.OperationErrors.present(error, com.pfms.app.data.error.ErrorAction.LOAD)
        emit(SavingsSimulatorUiState(isLoading = false, error = problem.message))
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SavingsSimulatorUiState())

    /** Called with LKR minor units, never floats. */
    fun setMonthlyReductionMinor(amount: Long) { if (amount >= 0L) monthlyReductionMinor.value = amount }
    fun setPlannedContributionMinor(amount: Long) { if (amount >= 0L) plannedContributionMinor.value = amount }
}

data class SavingsSimulatorUiState(
    val isLoading: Boolean = true,
    val goal: SavingsGoal? = null,
    val monthlyReductionMinor: Long = 0L,
    val plannedContributionMinor: Long = 0L,
    val result: SavingsSimulation? = null,
    val error: String? = null
)

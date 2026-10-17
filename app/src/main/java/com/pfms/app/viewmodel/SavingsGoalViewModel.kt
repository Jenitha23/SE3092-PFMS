package com.pfms.app.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pfms.app.domain.model.GoalTransaction
import com.pfms.app.domain.model.GoalTransactionType
import com.pfms.app.domain.model.SavingsGoal
import com.pfms.app.domain.repository.AuthRepository
import com.pfms.app.domain.repository.ExpenseRepository
import com.pfms.app.domain.repository.IncomeRepository
import com.pfms.app.domain.repository.SavingsGoalRepository
import com.pfms.app.domain.usecase.savings.CalculateCurrentMonthGoalContributionUseCase
import com.pfms.app.domain.usecase.savings.CalculateDiscretionaryImpactUseCase
import com.pfms.app.domain.usecase.savings.CalculateGoalPaceUseCase
import com.pfms.app.domain.usecase.savings.CalculateGoalProgressUseCase
import com.pfms.app.domain.usecase.savings.CalculateMonthlyNetSavingsUseCase
import com.pfms.app.domain.usecase.savings.CalculateSavedAmountUseCase
import com.pfms.app.domain.usecase.savings.CalculateTrailingNetSavingsAverageUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.util.UUID
import javax.inject.Inject

/**
 * ViewModel responsible for reactive savings goal management (FR-23 through FR-28).
 *
 * Coordinates:
 * - Reactive single active goal observation and switching
 * - Active goal transaction ledger observation
 * - Progress calculation, pace metrics, and discretionary spending impact
 * - CRUD mutations backed by [SavingsGoalRepository] with optimistic local persistence
 */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class SavingsGoalViewModel @Inject constructor(
    private val savingsGoalRepository: SavingsGoalRepository,
    private val expenseRepository: ExpenseRepository,
    private val incomeRepository: IncomeRepository,
    private val authRepository: AuthRepository,
    private val calculateSavedAmountUseCase: CalculateSavedAmountUseCase,
    private val calculateGoalProgressUseCase: CalculateGoalProgressUseCase,
    private val calculateCurrentMonthGoalContributionUseCase: CalculateCurrentMonthGoalContributionUseCase,
    private val calculateMonthlyNetSavingsUseCase: CalculateMonthlyNetSavingsUseCase,
    private val calculateTrailingNetSavingsAverageUseCase: CalculateTrailingNetSavingsAverageUseCase,
    private val calculateGoalPaceUseCase: CalculateGoalPaceUseCase,
    private val calculateDiscretionaryImpactUseCase: CalculateDiscretionaryImpactUseCase
) : ViewModel() {

    internal var dateProvider: () -> LocalDate = { LocalDate.now(ZoneId.of("Asia/Colombo")) }

    private val _operationInProgress = MutableStateFlow(false)
    private val _errorMessage = MutableStateFlow<String?>(null)

    private val activeGoalAndTransactionsFlow = savingsGoalRepository.observeActiveGoal()
        .flatMapLatest { goal ->
            if (goal == null) {
                flowOf(null to emptyList<GoalTransaction>())
            } else {
                savingsGoalRepository.observeTransactions(goal.id).map { txList ->
                    goal to txList
                }
            }
        }

    val uiState: StateFlow<SavingsGoalUiState> = combine(
        activeGoalAndTransactionsFlow,
        incomeRepository.observeIncome(),
        expenseRepository.observeExpenses(),
        _operationInProgress,
        _errorMessage
    ) { (goal, transactions), incomes, expenses, inProgress, error ->
        if (goal == null) {
            SavingsGoalUiState(
                isLoading = false,
                activeGoal = null,
                transactions = emptyList(),
                currentSavedMinor = 0L,
                remainingAmountMinor = 0L,
                progressPercent = 0.0,
                remainingMonths = 0L,
                requiredMonthlySavingMinor = 0L,
                currentMonthContributionMinor = 0L,
                currentMonthNetSavingsMinor = 0L,
                trailingThreeMonthNetSavingsMinor = 0L,
                pace = null,
                discretionaryImpact = null,
                isCompleted = false,
                isOverdue = false,
                errorMessage = error,
                operationInProgress = inProgress
            )
        } else {
            val today = dateProvider()
            val currentMonth = YearMonth.from(today)

            val currentSaved = calculateSavedAmountUseCase(goal, transactions)
            val progress = calculateGoalProgressUseCase(goal, transactions, today)
            val monthlyContribution = calculateCurrentMonthGoalContributionUseCase(goal.id, transactions, currentMonth)
            val monthlyNetSavings = calculateMonthlyNetSavingsUseCase(incomes, expenses, currentMonth)
            val trailingAvgResult = calculateTrailingNetSavingsAverageUseCase(incomes, expenses, currentMonth)
            val pace = calculateGoalPaceUseCase(goal, transactions, incomes, expenses, today)
            val discretionaryImpact = calculateDiscretionaryImpactUseCase(
                expenses = expenses,
                selectedMonth = currentMonth,
                requiredMonthlySavingMinor = progress.requiredMonthlySavingMinor
            )

            SavingsGoalUiState(
                isLoading = false,
                activeGoal = goal,
                transactions = transactions,
                currentSavedMinor = currentSaved,
                remainingAmountMinor = progress.remainingAmountMinor,
                progressPercent = progress.progressPercentage,
                remainingMonths = progress.remainingMonths,
                requiredMonthlySavingMinor = progress.requiredMonthlySavingMinor,
                currentMonthContributionMinor = monthlyContribution,
                currentMonthNetSavingsMinor = monthlyNetSavings,
                trailingThreeMonthNetSavingsMinor = trailingAvgResult.averageMinor,
                pace = pace,
                discretionaryImpact = discretionaryImpact,
                isCompleted = progress.isCompleted,
                isOverdue = progress.isOverdue,
                errorMessage = error,
                operationInProgress = inProgress
            )
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
        initialValue = SavingsGoalUiState(isLoading = true)
    )

    fun clearError() {
        _errorMessage.value = null
    }

    fun createGoal(goal: SavingsGoal) {
        viewModelScope.launch {
            _operationInProgress.value = true
            _errorMessage.value = null
            try {
                val user = authRepository.currentUser.first()
                if (user == null || user.uid.isBlank()) {
                    _errorMessage.value = "Authentication required: no active user session"
                    return@launch
                }
                val goalWithUser = if (goal.userId.isBlank()) goal.copy(userId = user.uid) else goal
                val result = savingsGoalRepository.createGoal(goalWithUser)
                if (result.isFailure) {
                    _errorMessage.value = result.exceptionOrNull()?.message ?: "Failed to create savings goal"
                }
            } catch (e: Exception) {
                _errorMessage.value = e.message ?: "An unexpected error occurred while creating savings goal"
            } finally {
                _operationInProgress.value = false
            }
        }
    }

    fun updateGoal(goal: SavingsGoal) {
        viewModelScope.launch {
            _operationInProgress.value = true
            _errorMessage.value = null
            try {
                val user = authRepository.currentUser.first()
                if (user == null || user.uid.isBlank()) {
                    _errorMessage.value = "Authentication required: no active user session"
                    return@launch
                }
                val goalWithUser = if (goal.userId.isBlank()) goal.copy(userId = user.uid) else goal
                val result = savingsGoalRepository.updateGoal(goalWithUser)
                if (result.isFailure) {
                    _errorMessage.value = result.exceptionOrNull()?.message ?: "Failed to update savings goal"
                }
            } catch (e: Exception) {
                _errorMessage.value = e.message ?: "An unexpected error occurred while updating savings goal"
            } finally {
                _operationInProgress.value = false
            }
        }
    }

    fun archiveGoal(goalId: String) {
        viewModelScope.launch {
            _operationInProgress.value = true
            _errorMessage.value = null
            try {
                val user = authRepository.currentUser.first()
                if (user == null || user.uid.isBlank()) {
                    _errorMessage.value = "Authentication required: no active user session"
                    return@launch
                }
                val result = savingsGoalRepository.archiveGoal(goalId)
                if (result.isFailure) {
                    _errorMessage.value = result.exceptionOrNull()?.message ?: "Failed to archive savings goal"
                }
            } catch (e: Exception) {
                _errorMessage.value = e.message ?: "An unexpected error occurred while archiving savings goal"
            } finally {
                _operationInProgress.value = false
            }
        }
    }

    fun deleteGoal(goalId: String) {
        viewModelScope.launch {
            _operationInProgress.value = true
            _errorMessage.value = null
            try {
                val user = authRepository.currentUser.first()
                if (user == null || user.uid.isBlank()) {
                    _errorMessage.value = "Authentication required: no active user session"
                    return@launch
                }
                val result = savingsGoalRepository.deleteGoal(goalId)
                if (result.isFailure) {
                    _errorMessage.value = result.exceptionOrNull()?.message ?: "Failed to delete savings goal"
                }
            } catch (e: Exception) {
                _errorMessage.value = e.message ?: "An unexpected error occurred while deleting savings goal"
            } finally {
                _operationInProgress.value = false
            }
        }
    }

    fun addContribution(
        amountMinor: Long,
        note: String? = null,
        date: String? = null
    ) {
        val active = uiState.value.activeGoal
        if (active == null) {
            _errorMessage.value = "No active savings goal found"
            return
        }
        addContribution(
            goalId = active.id,
            amountMinor = amountMinor,
            note = note,
            date = date
        )
    }

    fun addContribution(
        goalId: String,
        amountMinor: Long,
        note: String? = null,
        date: String? = null
    ) {
        if (amountMinor <= 0L) {
            _errorMessage.value = "Contribution amount must be greater than zero"
            return
        }
        viewModelScope.launch {
            _operationInProgress.value = true
            _errorMessage.value = null
            try {
                val user = authRepository.currentUser.first()
                if (user == null || user.uid.isBlank()) {
                    _errorMessage.value = "Authentication required: no active user session"
                    return@launch
                }
                val txDate = if (!date.isNullOrBlank()) date else dateProvider().toString()
                val tx = GoalTransaction(
                    id = UUID.randomUUID().toString(),
                    goalId = goalId,
                    userId = user.uid,
                    amountMinor = amountMinor,
                    transactionType = GoalTransactionType.CONTRIBUTION,
                    date = txDate,
                    note = note?.trim()?.ifBlank { null },
                    createdAt = System.currentTimeMillis(),
                    updatedAt = System.currentTimeMillis()
                )
                val result = savingsGoalRepository.addTransaction(tx)
                if (result.isFailure) {
                    _errorMessage.value = result.exceptionOrNull()?.message ?: "Failed to record contribution"
                }
            } catch (e: Exception) {
                _errorMessage.value = e.message ?: "An unexpected error occurred while adding contribution"
            } finally {
                _operationInProgress.value = false
            }
        }
    }

    fun addWithdrawal(
        amountMinor: Long,
        note: String? = null,
        date: String? = null
    ) {
        val active = uiState.value.activeGoal
        if (active == null) {
            _errorMessage.value = "No active savings goal found"
            return
        }
        addWithdrawal(
            goalId = active.id,
            amountMinor = amountMinor,
            note = note,
            date = date
        )
    }

    fun addWithdrawal(
        goalId: String,
        amountMinor: Long,
        note: String? = null,
        date: String? = null
    ) {
        if (amountMinor <= 0L) {
            _errorMessage.value = "Withdrawal amount must be greater than zero"
            return
        }
        viewModelScope.launch {
            _operationInProgress.value = true
            _errorMessage.value = null
            try {
                val user = authRepository.currentUser.first()
                if (user == null || user.uid.isBlank()) {
                    _errorMessage.value = "Authentication required: no active user session"
                    return@launch
                }
                val txDate = if (!date.isNullOrBlank()) date else dateProvider().toString()
                val tx = GoalTransaction(
                    id = UUID.randomUUID().toString(),
                    goalId = goalId,
                    userId = user.uid,
                    amountMinor = amountMinor,
                    transactionType = GoalTransactionType.WITHDRAWAL,
                    date = txDate,
                    note = note?.trim()?.ifBlank { null },
                    createdAt = System.currentTimeMillis(),
                    updatedAt = System.currentTimeMillis()
                )
                val result = savingsGoalRepository.addTransaction(tx)
                if (result.isFailure) {
                    _errorMessage.value = result.exceptionOrNull()?.message ?: "Failed to record withdrawal"
                }
            } catch (e: Exception) {
                _errorMessage.value = e.message ?: "An unexpected error occurred while recording withdrawal"
            } finally {
                _operationInProgress.value = false
            }
        }
    }

    fun updateTransaction(transaction: GoalTransaction) {
        if (transaction.amountMinor < 0L) {
            _errorMessage.value = "Transaction amount must be non-negative"
            return
        }
        viewModelScope.launch {
            _operationInProgress.value = true
            _errorMessage.value = null
            try {
                val user = authRepository.currentUser.first()
                if (user == null || user.uid.isBlank()) {
                    _errorMessage.value = "Authentication required: no active user session"
                    return@launch
                }
                val txWithUser = if (transaction.userId.isBlank()) transaction.copy(userId = user.uid) else transaction
                val result = savingsGoalRepository.updateTransaction(txWithUser)
                if (result.isFailure) {
                    _errorMessage.value = result.exceptionOrNull()?.message ?: "Failed to update transaction"
                }
            } catch (e: Exception) {
                _errorMessage.value = e.message ?: "An unexpected error occurred while updating transaction"
            } finally {
                _operationInProgress.value = false
            }
        }
    }

    fun deleteTransaction(transactionId: String) {
        viewModelScope.launch {
            _operationInProgress.value = true
            _errorMessage.value = null
            try {
                val user = authRepository.currentUser.first()
                if (user == null || user.uid.isBlank()) {
                    _errorMessage.value = "Authentication required: no active user session"
                    return@launch
                }
                val result = savingsGoalRepository.deleteTransaction(transactionId)
                if (result.isFailure) {
                    _errorMessage.value = result.exceptionOrNull()?.message ?: "Failed to delete transaction"
                }
            } catch (e: Exception) {
                _errorMessage.value = e.message ?: "An unexpected error occurred while deleting transaction"
            } finally {
                _operationInProgress.value = false
            }
        }
    }

    fun refreshRemoteData() {
        viewModelScope.launch {
            _operationInProgress.value = true
            try {
                val result = savingsGoalRepository.refreshRemoteSavingsData()
                if (result.isFailure) {
                    _errorMessage.value = result.exceptionOrNull()?.message ?: "Failed to refresh savings data"
                }
            } catch (e: Exception) {
                _errorMessage.value = e.message ?: "Failed to refresh savings data"
            } finally {
                _operationInProgress.value = false
            }
        }
    }
}

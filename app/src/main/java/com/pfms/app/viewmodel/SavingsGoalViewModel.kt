package com.pfms.app.viewmodel

import com.pfms.app.data.error.ErrorAction
import com.pfms.app.data.error.OperationErrors
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.onEach
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

    private val reload = MutableStateFlow(0)
    private val successVersion = MutableStateFlow(0L)
    private val failureVersion = MutableStateFlow(0L)
    private val canRetry = MutableStateFlow(false)
    private var failedAction: (() -> Unit)? = null
    private var createGoalId: String? = null
    private var transactionId: String? = null
    private var lastState = SavingsGoalUiState()

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

    val uiState: StateFlow<SavingsGoalUiState> = reload.flatMapLatest { combine(
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
    }.catch { error ->
        val problem = OperationErrors.present(error, ErrorAction.LOAD)
        failedAction = ::retryLoading
        emit(lastState.copy(isLoading = false, errorMessage = problem.message, canRetry = problem.canRetry))
    } }.combine(authRepository.currentUser) { state, user ->
        // Do not retain the previous account's lastState while its Room stream is switching.
        if (user == null || (state.activeGoal != null && state.activeGoal.userId != user.uid)) {
            SavingsGoalUiState(isLoading = false, errorMessage = state.errorMessage,
                canRetry = state.canRetry, operationInProgress = state.operationInProgress)
        } else state
    }.combine(savingsGoalRepository.observeRestoration()) { state, restoration ->
        val problem = restoration.error?.let { OperationErrors.present(it, ErrorAction.LOAD) }
        if (problem != null && state.errorMessage == null) failedAction = ::refreshRemoteData
        state.copy(
            isLoading = state.isLoading || (state.activeGoal == null && restoration.isRefreshing),
            errorMessage = state.errorMessage ?: problem?.message,
            canRetry = state.canRetry || (problem != null && problem.canRetry)
        )
    }.combine(successVersion) { state, version -> state.copy(successVersion = version) }
        .combine(failureVersion) { state, version -> state.copy(failureVersion = version) }
        .combine(canRetry) { state, retry -> state.copy(canRetry = state.canRetry || retry) }
        .onEach { lastState = it }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
        initialValue = SavingsGoalUiState(isLoading = true)
    )

    fun clearError() {
        savingsGoalRepository.clearRestorationError()
        _errorMessage.value = null
        canRetry.value = false
        failedAction = null
    }

    fun beginForm() {
        createGoalId = null
        transactionId = null
        clearError()
    }

    fun retry() {
        if (!_operationInProgress.value && uiState.value.canRetry) failedAction?.invoke()
    }

    private fun retryLoading() { reload.value++ }

    private fun perform(action: ErrorAction = ErrorAction.SAVE, block: suspend (String) -> Result<Unit>) {
        if (_operationInProgress.value) return
        _operationInProgress.value = true
        _errorMessage.value = null
        canRetry.value = false
        viewModelScope.launch {
            try {
                val user = authRepository.currentUser.first()?.uid?.takeIf { it.isNotBlank() }
                    ?: error("Authentication required: no active user session")
                withTimeout(20_000) { block(user).getOrThrow() }
                failedAction = null
                createGoalId = null
                transactionId = null
                successVersion.value++
            } catch (error: Exception) {
                val problem = OperationErrors.present(error, action)
                _errorMessage.value = problem.message
                canRetry.value = problem.canRetry
                failedAction = { perform(action, block) }
                failureVersion.value++
            } finally {
                _operationInProgress.value = false
            }
        }
    }

    fun createGoal(goal: SavingsGoal) {
        if (_operationInProgress.value) return
        // Generate missing IDs here, retaining the same ID when a failed save is retried.
        val id = goal.id.ifBlank { createGoalId ?: UUID.randomUUID().toString().also { createGoalId = it } }
        perform { uid -> savingsGoalRepository.createGoal(goal.copy(id = id, userId = goal.userId.ifBlank { uid })) }
    }

    fun createGoal(name: String, targetAmountMinor: Long, targetDate: String, openingSavedMinor: Long) =
        createGoal(SavingsGoal(name = name, targetAmountMinor = targetAmountMinor, targetDate = targetDate,
            openingSavedAmountMinor = openingSavedMinor))

    fun updateGoal(goal: SavingsGoal) = perform { uid ->
        savingsGoalRepository.updateGoal(goal.copy(userId = goal.userId.ifBlank { uid }))
    }

    fun archiveGoal(goalId: String) = perform { savingsGoalRepository.archiveGoal(goalId) }

    fun deleteGoal(goalId: String) = perform(ErrorAction.DELETE) { savingsGoalRepository.deleteGoal(goalId) }

    fun addContribution(amountMinor: Long, note: String? = null, date: String? = null) {
        val goal = uiState.value.activeGoal
        if (goal == null) { validationFailure("No active savings goal found"); return }
        addContribution(goal.id, amountMinor, note, date)
    }

    fun addContribution(goalId: String, amountMinor: Long, note: String? = null, date: String? = null) =
        addTransaction(goalId, amountMinor, note, date, GoalTransactionType.CONTRIBUTION)

    fun addWithdrawal(amountMinor: Long, note: String? = null, date: String? = null) {
        val goal = uiState.value.activeGoal
        if (goal == null) { validationFailure("No active savings goal found"); return }
        addWithdrawal(goal.id, amountMinor, note, date)
    }

    fun addWithdrawal(goalId: String, amountMinor: Long, note: String? = null, date: String? = null) =
        addTransaction(goalId, amountMinor, note, date, GoalTransactionType.WITHDRAWAL)

    private fun addTransaction(goalId: String, amount: Long, note: String?, date: String?, type: GoalTransactionType) {
        if (_operationInProgress.value) return
        if (amount <= 0L || (date != null && runCatching { LocalDate.parse(date) }.isFailure)) {
            validationFailure(if (amount <= 0L) {
                if (type == GoalTransactionType.CONTRIBUTION) "Contribution amount must be greater than zero"
                else "Withdrawal amount must be greater than zero"
            } else "Enter a valid transaction date.")
            return
        }
        val id = transactionId ?: UUID.randomUUID().toString().also { transactionId = it }
        val txDate = date ?: dateProvider().toString()
        val now = System.currentTimeMillis()
        perform { uid -> savingsGoalRepository.addTransaction(GoalTransaction(id = id, goalId = goalId, userId = uid,
            amountMinor = amount, transactionType = type, date = txDate,
            note = note?.trim()?.ifBlank { null }, createdAt = now, updatedAt = now)) }
    }

    fun updateTransaction(transaction: GoalTransaction) {
        if (transaction.amountMinor <= 0L) {
            validationFailure("Transaction amount must be greater than zero")
            return
        }
        perform { uid -> savingsGoalRepository.updateTransaction(transaction.copy(userId = transaction.userId.ifBlank { uid })) }
    }

    fun deleteTransaction(transactionId: String) = perform(ErrorAction.DELETE) {
        savingsGoalRepository.deleteTransaction(transactionId)
    }

    private fun validationFailure(message: String) {
        _errorMessage.value = message
        canRetry.value = false
        failedAction = null
        failureVersion.value++
    }

    fun refreshRemoteData() = perform(ErrorAction.LOAD) { savingsGoalRepository.refreshRemoteSavingsData().map { Unit } }
}

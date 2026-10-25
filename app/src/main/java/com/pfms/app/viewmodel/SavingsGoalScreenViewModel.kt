package com.pfms.app.viewmodel

import androidx.lifecycle.ViewModel
import com.pfms.app.domain.model.*
import com.pfms.app.domain.model.savings.GoalPaceStatus
import com.pfms.app.domain.usecase.SafeCalculations
import com.pfms.app.domain.usecase.savings.*
import com.pfms.app.ui.screen.savings.SavingsGoalUiFormatter
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import java.util.Locale

data class GoalIdea(val key: String, val name: String, val caption: String, val group: String)
val savingsGoalIdeas = listOf(
    GoalIdea("safety", "Emergency Fund", "Prepare for unexpected costs", "SAFETY"),
    GoalIdea("tech", "Gadget / Tech", "Save for your next upgrade", "ASSET"),
    GoalIdea("travel", "Dream Getaway", "Plan a memorable trip", "EXPERIENCE"),
    GoalIdea("home", "Home & Living", "Make room for what matters", "MILESTONE"),
    GoalIdea("education", "Education", "Invest in your next chapter", "LEARNING"),
    GoalIdea("vehicle", "Vehicle Fund", "Plan your next set of wheels", "ASSET"))

data class GoalEditorDraft(val original: SavingsGoal?, val name: String, val target: String, val date: String,
    val opening: String, val errors: SavingsGoalEntryRules.GoalResult? = null, val picker: Boolean = false,
    val dateMillis: Long? = null, val monthlyPreview: String? = null, val monthsLabel: String = "")
data class GoalTransactionDraft(val original: GoalTransaction?, val type: GoalTransactionType, val amount: String,
    val date: String, val note: String, val errors: SavingsGoalEntryRules.TransactionResult? = null, val picker: Boolean = false, val dateMillis: Long? = null,
    val balanceProvided: Boolean = false, val availableMinor: Long? = null, val available: String? = null,
    val currentSavings: String? = null, val balanceError: String? = null, val submissionPending: Boolean = false)
data class GoalLedgerRow(val original: GoalTransaction, val title: String, val amount: String, val date: String, val note: String?)
data class SavingsPresentation(
    val saved: String = "LKR 0.00", val target: String = "LKR 0.00", val remaining: String = "LKR 0.00", val monthly: String = "LKR 0.00",
    val targetDate: String = "", val progress: Float = 0f, val progressLabel: String = "0%", val months: String = "",
    val contribution: String = "LKR 0.00", val net: String = "LKR 0.00", val average: String = "LKR 0.00",
    val paceLabel: String = "Gathering data", val projection: String = "Add transactions to see your pace", val shortfall: String? = null,
    val impact: String = "", val discretionary: String = "LKR 0.00", val committed: String = "LKR 0.00", val ledger: List<GoalLedgerRow> = emptyList())
data class SavingsScreenState(val source: SavingsGoalUiState = SavingsGoalUiState(), val display: SavingsPresentation = SavingsPresentation(),
    val goalDraft: GoalEditorDraft? = null, val transactionDraft: GoalTransactionDraft? = null,
    val confirmation: String? = null, val transactionToDelete: String? = null, val help: Boolean = false, val ideasExpanded: Boolean = false)

/** Owns presentation and draft actions; persistence remains in SavingsGoalViewModel. */
class SavingsGoalScreenViewModel : ViewModel() {
    internal var today: () -> LocalDate = { LocalDate.now(ZoneId.of("Asia/Colombo")) }
    private val progress = CalculateGoalProgressUseCase(CalculateSavedAmountUseCase())
    private val _state = MutableStateFlow(SavingsScreenState())
    val state = _state.asStateFlow()

    fun sync(source: SavingsGoalUiState) {
        _state.update { current ->
            val success = source.successVersion != current.source.successVersion
            current.copy(source = source, display = presentation(source), goalDraft = if (success) null else current.goalDraft,
                transactionDraft = if (success) null else current.transactionDraft?.let { draft ->
                    withdrawalPreview(draft.copy(submissionPending = draft.submissionPending &&
                        (source.operationInProgress || source.errorMessage == null)), source).let { refreshed ->
                        if (draft.errors == null) refreshed else refreshed.copy(errors = transactionValidation(refreshed))
                    }
                }, confirmation = if (success) null else current.confirmation,
                transactionToDelete = if (success) null else current.transactionToDelete)
        }
    }
    fun openGoal(editing: Boolean = false, idea: String? = null) {
        val goal = if (editing) _state.value.source.activeGoal ?: return else SavingsGoalDefaults.createPrefilledExample(today())
        val choice = savingsGoalIdeas.firstOrNull { it.key == idea }
        val draft = GoalEditorDraft(if (editing) goal else null, choice?.name ?: goal.name,
            if (choice != null) "" else SavingsGoalUiFormatter.formatMinorToDecimalString(goal.targetAmountMinor), goal.targetDate,
            if (choice != null) "0.00" else SavingsGoalUiFormatter.formatMinorToDecimalString(goal.openingSavedAmountMinor))
        _state.update { it.copy(goalDraft = preview(draft), confirmation = null) }
    }
    fun dismissGoal() { if (!_state.value.source.operationInProgress) _state.update { it.copy(goalDraft = null) } }
    fun goalName(value: String) = changeGoal { it.copy(name = value, errors = null) }
    fun goalTarget(value: String) = changeGoal { it.copy(target = value, errors = null) }
    fun goalOpening(value: String) = changeGoal { it.copy(opening = value, errors = null) }
    fun goalPicker(value: Boolean) = changeGoal { it.copy(picker = value) }
    fun goalDate(millis: Long?) { iso(millis)?.let { date -> changeGoal { it.copy(date = date, picker = false, errors = null) } } }
    private fun changeGoal(block: (GoalEditorDraft) -> GoalEditorDraft) {
        if (!_state.value.source.operationInProgress) _state.update { it.copy(goalDraft = it.goalDraft?.let { draft -> preview(block(draft)) }) }
    }
    private fun preview(draft: GoalEditorDraft): GoalEditorDraft {
        val input = SavingsGoalEntryRules.validateGoal(draft.name, draft.target, draft.date, draft.opening)
        if (!input.isValid) return draft.copy(dateMillis = millis(draft.date), monthlyPreview = null, monthsLabel = "Enter your target and savings to see the monthly plan.")
        val goal = (draft.original ?: SavingsGoal()).copy(name = draft.name, targetAmountMinor = input.targetAmountMinor,
            targetDate = draft.date, openingSavedAmountMinor = input.openingSavedAmountMinor)
        val result = progress(goal, if (draft.original != null) _state.value.source.transactions else emptyList(), today())
        return draft.copy(dateMillis = millis(draft.date), monthlyPreview = if (result.isCompleted) "Target already funded" else if (result.isOverdue) "Target date has passed" else "${SavingsGoalUiFormatter.formatLkr(result.requiredMonthlySavingMinor)} / mo",
            monthsLabel = if (result.isCompleted) "Your saved amount meets the target." else if (result.isOverdue) "Choose a new date to plan future contributions." else "${result.remainingMonths} monthly contributions to target")
    }
    fun submitGoal(onCreate: (String, Long, String, Long) -> Unit, onUpdate: (SavingsGoal) -> Unit) {
        val draft = _state.value.goalDraft ?: return
        if (_state.value.source.operationInProgress) return
        val validation = SavingsGoalEntryRules.validateGoal(draft.name, draft.target, draft.date, draft.opening)
        if (!validation.isValid) { _state.update { it.copy(goalDraft = draft.copy(errors = validation)) }; return }
        if (draft.original == null) onCreate(draft.name.trim(), validation.targetAmountMinor, draft.date.trim(), validation.openingSavedAmountMinor)
        else onUpdate(draft.original.copy(name = draft.name.trim(), targetAmountMinor = validation.targetAmountMinor,
            targetDate = draft.date.trim(), openingSavedAmountMinor = validation.openingSavedAmountMinor))
    }
    fun openTransaction(type: GoalTransactionType, original: GoalTransaction? = null, balanceProvided: Boolean = false) {
        val date = original?.date ?: today().toString()
        _state.update { it.copy(transactionDraft = withdrawalPreview(GoalTransactionDraft(original, type,
            original?.let { tx -> SavingsGoalUiFormatter.formatMinorToDecimalString(tx.amountMinor) }.orEmpty(), date, original?.note.orEmpty(),
            dateMillis = millis(date), balanceProvided = balanceProvided), it.source)) }
    }
    fun dismissTransaction() { if (!_state.value.source.operationInProgress && _state.value.transactionDraft?.submissionPending != true) _state.update { it.copy(transactionDraft = null) } }
    private fun changeTransaction(block: (GoalTransactionDraft) -> GoalTransactionDraft) { if (!_state.value.source.operationInProgress && _state.value.transactionDraft?.submissionPending != true) _state.update { state ->
        state.copy(transactionDraft = state.transactionDraft?.let { withdrawalPreview(block(it), state.source) })
    } }
    fun transactionAmount(value: String) = changeTransaction { it.copy(amount = value, errors = null) }
    fun transactionNote(value: String) = changeTransaction { it.copy(note = value) }
    fun transactionPicker(value: Boolean) = changeTransaction { it.copy(picker = value) }
    fun transactionDate(value: Long?) { iso(value)?.let { date -> changeTransaction { it.copy(date = date, dateMillis = value, picker = false, errors = null) } } }
    fun submitTransaction(contribution: (Long, String?, String?) -> Unit, withdrawal: (Long, String?, String?) -> Unit, update: (GoalTransaction) -> Unit) {
        val draft = _state.value.transactionDraft ?: return
        if (_state.value.source.isLoading || _state.value.source.operationInProgress || draft.submissionPending) return
        val refreshed = withdrawalPreview(draft, _state.value.source)
        val result = transactionValidation(refreshed)
        if (!result.isValid) { changeTransaction { it.copy(errors = result) }; return }
        val note = draft.note.trim().ifBlank { null }
        // Guard immediately, before persistence/loading state can make a round trip through Compose.
        // Rejection releases this guard in sync(); confirmed success closes the draft there.
        _state.update { it.copy(transactionDraft = refreshed.copy(submissionPending = true)) }
        if (draft.original != null) update(draft.original.copy(amountMinor = result.amountMinor, note = note, date = draft.date, updatedAt = System.currentTimeMillis()))
        else if (draft.type == GoalTransactionType.CONTRIBUTION) contribution(result.amountMinor, note, draft.date)
        else withdrawal(result.amountMinor, note, draft.date)
    }

    private fun transactionValidation(draft: GoalTransactionDraft): SavingsGoalEntryRules.TransactionResult {
        val input = SavingsGoalEntryRules.validateTransaction(draft.amount, draft.date)
        if (!input.isValid || draft.type != GoalTransactionType.WITHDRAWAL) return input
        val error = draft.balanceError ?: draft.availableMinor?.takeIf { input.amountMinor > it }?.let {
            "Insufficient savings. You can withdraw up to ${SavingsGoalUiFormatter.formatLkr(it)}."
        }
        return if (error == null) input else input.copy(isValid = false, amountError = error)
    }

    private fun withdrawalPreview(draft: GoalTransactionDraft, source: SavingsGoalUiState): GoalTransactionDraft {
        if (draft.type != GoalTransactionType.WITHDRAWAL) return draft
        val goal = source.activeGoal
        // Individual compatibility dialogs may only know a supplied aggregate balance. Do not
        // invent a zero balance for an edit whose caller has not supplied the goal/ledger.
        if (goal == null && !draft.balanceProvided) return draft.copy(availableMinor = null, available = null, currentSavings = null, balanceError = null)
        if (millis(draft.date) == null) return draft.copy(availableMinor = null, available = null, balanceError = null)
        return try {
            require(draft.original == null || goal == null || draft.original.goalId == goal.id) { "Goal changed" }
            val ledger = if (goal == null) emptyList() else source.transactions.filter {
                it.goalId == goal.id && (goal.userId.isBlank() || it.userId.isBlank() || it.userId == goal.userId)
            }
            val opening = goal?.openingSavedAmountMinor ?: source.currentSavedMinor
            val current = ValidateGoalLedgerUseCase.validate(opening, ledger)
            val candidate = draft.original?.copy(date = draft.date) ?: GoalTransaction(
                id = "withdrawal-preview", goalId = goal?.id.orEmpty(), transactionType = GoalTransactionType.WITHDRAWAL,
                date = draft.date, createdAt = System.currentTimeMillis())
            val available = ValidateGoalLedgerUseCase.availableForWithdrawal(opening, ledger, candidate)
            draft.copy(availableMinor = available, available = SavingsGoalUiFormatter.formatLkr(available),
                currentSavings = SavingsGoalUiFormatter.formatLkr(current), balanceError = null)
        } catch (_: IllegalArgumentException) {
            draft.copy(availableMinor = null, available = null, currentSavings = null, balanceError = "Unable to verify available savings. Refresh the goal before withdrawing.")
        } catch (_: ArithmeticException) {
            draft.copy(availableMinor = null, available = null, currentSavings = null, balanceError = "Unable to verify available savings. Refresh the goal before withdrawing.")
        }
    }
    fun confirmation(value: String?, transactionId: String? = null) { _state.update { it.copy(confirmation = value, transactionToDelete = transactionId) } }
    fun help(value: Boolean) { _state.update { it.copy(help = value) } }
    fun browseIdeas() { _state.update { it.copy(ideasExpanded = !it.ideasExpanded) } }
    private fun millis(value: String): Long? = runCatching { LocalDate.parse(value).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli() }.getOrNull()
    private fun iso(value: Long?): String? = value?.let { Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate().toString() }

    private fun presentation(source: SavingsGoalUiState): SavingsPresentation {
        val pct = SafeCalculations.progressFraction(source.progressPercent)
        val pace = source.pace
        return SavingsPresentation(saved = SavingsGoalUiFormatter.formatLkr(source.currentSavedMinor), target = SavingsGoalUiFormatter.formatLkr(source.activeGoal?.targetAmountMinor ?: 0),
            remaining = SavingsGoalUiFormatter.formatLkr(source.remainingAmountMinor), monthly = SavingsGoalUiFormatter.formatLkr(source.requiredMonthlySavingMinor),
            targetDate = SavingsGoalUiFormatter.formatDisplayDate(source.activeGoal?.targetDate), progress = pct, progressLabel = String.format(Locale.US, "%.1f%%", pct * 100),
            months = "${source.remainingMonths} months remaining", contribution = SavingsGoalUiFormatter.formatLkr(source.currentMonthContributionMinor),
            net = SavingsGoalUiFormatter.formatLkr(source.currentMonthNetSavingsMinor), average = SavingsGoalUiFormatter.formatLkr(source.trailingThreeMonthNetSavingsMinor),
            paceLabel = when { source.isCompleted -> "Target reached"; source.isOverdue -> "Update your target date"; pace?.paceStatus == GoalPaceStatus.BEHIND -> "Behind target"; pace?.paceStatus == GoalPaceStatus.ON_TRACK -> "On track"; else -> "Gathering data" },
            projection = pace?.projectedCompletionDate?.let { "Projected · ${SavingsGoalUiFormatter.formatDisplayDate(it.toString())}" } ?: "Add contributions to build your savings pace",
            shortfall = pace?.shortfallMinor?.takeIf { it > 0 }?.let { SavingsGoalUiFormatter.formatLkr(it) },
            impact = SavingsGoalUiFormatter.formatDiscretionaryImpactSentence(source.discretionaryImpact, source.isCompleted),
            discretionary = SavingsGoalUiFormatter.formatLkr(source.discretionaryImpact?.discretionaryExpensesMinor ?: 0),
            committed = SavingsGoalUiFormatter.formatLkr(source.discretionaryImpact?.committedExpensesMinor ?: 0),
            ledger = source.transactions.sortedWith(compareByDescending<GoalTransaction> { it.date }.thenByDescending { it.createdAt }).map { tx ->
                GoalLedgerRow(tx, if (tx.transactionType == GoalTransactionType.CONTRIBUTION) "Contribution" else "Withdrawal",
                    (if (tx.transactionType == GoalTransactionType.CONTRIBUTION) "+ " else "− ") + SavingsGoalUiFormatter.formatLkr(tx.amountMinor), SavingsGoalUiFormatter.formatDisplayDate(tx.date), tx.note)
            })
    }
}

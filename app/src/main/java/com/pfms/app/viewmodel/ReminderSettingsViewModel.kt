package com.pfms.app.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pfms.app.data.error.*
import com.pfms.app.data.reminder.ReminderSchedulingException
import com.pfms.app.domain.model.*
import com.pfms.app.domain.repository.ReminderSettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import javax.inject.Inject

data class ReminderSettingsUiState(
    val settings: UserReminderSettings? = null,
    val access: NotificationAccess = NotificationAccess(),
    val isLoading: Boolean = true,
    val isSaving: Boolean = false,
    val errorMessage: String? = null
)

@HiltViewModel
class ReminderSettingsViewModel @Inject constructor(private val repository: ReminderSettingsRepository) : ViewModel() {
    private val state = MutableStateFlow(ReminderSettingsUiState(access = repository.notificationAccess()))
    val uiState: StateFlow<ReminderSettingsUiState> = state.asStateFlow()
    private var observation: Job? = null
    private var retryAction: (() -> Unit)? = null
    init { observe() }
    private fun observe() {
        observation?.cancel()
        state.update { it.copy(isLoading = true, errorMessage = null) }
        observation = viewModelScope.launch {
            repository.observeSettings().catch { error ->
                val message = OperationErrors.present(error, ErrorAction.LOAD).message
                state.update { it.copy(settings = null, isLoading = false, errorMessage = message) }
                retryAction = ::observe
            }.collect { settings ->
                if (state.value.settings?.userId != settings?.userId) retryAction = null
                state.update { it.copy(settings = settings, isLoading = false,
                    errorMessage = if (it.settings?.userId != settings?.userId) null else it.errorMessage) }
            }
        }
    }
    fun setEnabled(type: ReminderType, value: Boolean) = update(type, enabled = value)
    fun setTime(type: ReminderType, hour: Int, minute: Int) {
        if (hour !in 0..23 || minute !in 0..59) return
        update(type, time = hour * 60 + minute)
    }
    fun setWeekday(day: Int) { if (day in 1..7) update(ReminderType.WEEKLY_SUMMARY, day = day) }
    private fun update(type: ReminderType, enabled: Boolean? = null, time: Int? = null, day: Int? = null) {
        val uid = state.value.settings?.userId ?: return
        if (state.value.isSaving) return
        perform({ repository.update(uid, type, enabled, time, day) }, uid) { update(type, enabled, time, day) }
    }
    private fun perform(action: suspend () -> Result<Unit>, uid: String?, retry: () -> Unit) {
        if (state.value.isSaving) return
        state.update { it.copy(isSaving = true, errorMessage = null) }
        viewModelScope.launch {
            try {
                withTimeout(20_000) { action().getOrThrow() }
                retryAction = null
            } catch (error: Exception) {
                val message = if (error is ReminderSchedulingException)
                    "Your reminder preferences are saved. Tap Retry to update the schedule."
                    else OperationErrors.present(error).message
                if (state.value.settings?.userId == uid) {
                    state.update { it.copy(errorMessage = message) }
                    retryAction = if (error is ReminderSchedulingException) ::reconcile else retry
                }
            } finally { state.update { it.copy(isSaving = false) } }
        }
    }
    private fun reconcile() = perform(repository::reconcile, state.value.settings?.userId, ::reconcile)
    fun retry() { if (!state.value.isSaving) retryAction?.invoke() }
    fun refreshPermission() { state.update { it.copy(access = repository.notificationAccess()) } }
}

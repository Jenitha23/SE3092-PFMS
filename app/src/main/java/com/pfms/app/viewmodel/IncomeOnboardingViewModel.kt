package com.pfms.app.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pfms.app.domain.repository.UserRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.math.BigDecimal
import javax.inject.Inject

data class IncomeOnboardingState(val required: Boolean = false, val saving: Boolean = false, val error: String? = null)

@HiltViewModel
class IncomeOnboardingViewModel @Inject constructor(private val users: UserRepository) : ViewModel() {
    private val state = MutableStateFlow(IncomeOnboardingState())
    val uiState = state.asStateFlow()
    private var userId: String? = null
    init {
        viewModelScope.launch {
            users.observeProfile().collect { profile ->
                if (profile?.uid != userId) { userId = profile?.uid; state.value = IncomeOnboardingState() }
                if (!state.value.saving) state.value = state.value.copy(required = profile != null &&
                    !profile.incomeOnboardingComplete && profile.estimatedMonthlyIncomeMinor == null)
            }
        }
    }
    fun save(text: String) {
        if (state.value.saving) return
        val minor = parseEstimate(text)
        if (minor == null) { state.value = state.value.copy(error = "Enter zero or a positive amount with up to 2 decimal places."); return }
        val savingUser = userId
        state.value = state.value.copy(saving = true, error = null)
        viewModelScope.launch {
            try {
                withTimeout(15_000) { users.updateEstimatedMonthlyIncome(minor).getOrThrow() }
                if (savingUser == userId) state.value = IncomeOnboardingState()
            } catch (e: Exception) {
                if (e is CancellationException && e !is TimeoutCancellationException) throw e
                if (savingUser == userId) state.value = IncomeOnboardingState(required = true,
                    error = "Unable to save. Check your connection and try again.")
            }
        }
    }
    companion object {
        fun parseEstimate(text: String): Long? = runCatching {
            require(text.trim().matches(Regex("\\d+(\\.\\d{1,2})?")))
            BigDecimal(text.trim()).movePointRight(2).longValueExact()
        }.getOrNull()
    }
}

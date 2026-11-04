package com.pfms.app.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pfms.app.data.local.WelcomeOnboardingPreferences
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/** null = preferences not loaded yet. Never show welcome for a split-second on startup. */
@HiltViewModel
class WelcomeOnboardingViewModel @Inject constructor(
    private val preferences: WelcomeOnboardingPreferences
) : ViewModel() {
    val welcomeSeen: StateFlow<Boolean?> = preferences.welcomeSeen
        .map { it as Boolean? }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    private val _error = MutableStateFlow<String?>(null)
    val error = _error.asStateFlow()

    fun completeWelcome() {
        viewModelScope.launch {
            _error.value = null
            runCatching { preferences.completeWelcome() }
                .onFailure { _error.value = "Unable to save your preference. Please try again." }
        }
    }
}

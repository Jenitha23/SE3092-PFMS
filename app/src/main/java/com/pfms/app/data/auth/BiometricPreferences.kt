package com.pfms.app.data.auth

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

private val Context.biometricDataStore by preferencesDataStore(
    name = "biometric_preferences"
)

class BiometricPreferences @Inject constructor(
    // Hilt can only inject a Context when it is qualified.
    @ApplicationContext private val context: Context
) {

    private val biometricEnabledKey = booleanPreferencesKey("biometric_enabled")
    private val promptShownKey = booleanPreferencesKey("biometric_prompt_shown")

    val biometricEnabled: Flow<Boolean> =
        context.biometricDataStore.data.map { preferences ->
            preferences[biometricEnabledKey] ?: false
        }

    /** True once the one-time "Unlock faster?" question has been answered. */
    val biometricPromptShown: Flow<Boolean> =
        context.biometricDataStore.data.map { preferences ->
            preferences[promptShownKey] ?: false
        }

    suspend fun setBiometricPromptShown(shown: Boolean) {
        context.biometricDataStore.edit { preferences ->
            preferences[promptShownKey] = shown
        }
    }

    /** Called on logout / account deletion so the next user starts clean. */
    suspend fun clearAll() {
        context.biometricDataStore.edit { preferences -> preferences.clear() }
    }

    suspend fun setBiometricEnabled(enabled: Boolean) {
        context.biometricDataStore.edit { preferences ->
            preferences[biometricEnabledKey] = enabled
        }
    }
}

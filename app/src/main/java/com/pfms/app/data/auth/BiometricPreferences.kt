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

    val biometricEnabled: Flow<Boolean> =
        context.biometricDataStore.data.map { preferences ->
            preferences[biometricEnabledKey] ?: false
        }

    suspend fun setBiometricEnabled(enabled: Boolean) {
        context.biometricDataStore.edit { preferences ->
            preferences[biometricEnabledKey] = enabled
        }
    }
}

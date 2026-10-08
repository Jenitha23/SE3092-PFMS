package com.pfms.app.ui.theme

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.themeDataStore by preferencesDataStore(
    name = "theme_preferences"
)

/**
 * Lightweight local preference persistence for PFMS application theme mode.
 * Completely independent from Firebase and user authentication data.
 */
@Singleton
class ThemePreferences @Inject constructor(
    @param:ApplicationContext private val context: Context
) {
    private val themeModeKey = stringPreferencesKey("app_theme_mode")

    val themeMode: Flow<AppThemeMode> =
        context.themeDataStore.data.map { preferences ->
            val savedValue = preferences[themeModeKey]
            try {
                if (savedValue != null) AppThemeMode.valueOf(savedValue) else AppThemeMode.SYSTEM
            } catch (e: IllegalArgumentException) {
                AppThemeMode.SYSTEM
            }
        }

    suspend fun setThemeMode(mode: AppThemeMode) {
        context.themeDataStore.edit { preferences ->
            preferences[themeModeKey] = mode.name
        }
    }
}

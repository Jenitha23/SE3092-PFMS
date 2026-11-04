package com.pfms.app.data.local

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

// Separate preference file: do not redefine an existing Context.dataStore delegate.
private val Context.welcomeOnboardingStore by preferencesDataStore(name = "welcome_onboarding")

/** This flag is device-local, not specific to a Firebase user. */
@Singleton
class WelcomeOnboardingPreferences @Inject constructor(
    @param:ApplicationContext private val context: Context
) {
    private companion object {
        val WELCOME_SEEN = booleanPreferencesKey("welcome_seen")
    }

    val welcomeSeen: Flow<Boolean> = context.welcomeOnboardingStore.data.map { prefs ->
        prefs[WELCOME_SEEN] ?: false
    }

    suspend fun completeWelcome() {
        context.welcomeOnboardingStore.edit { prefs -> prefs[WELCOME_SEEN] = true }
    }
}

package com.pfms.app.data.repository

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.pfms.app.domain.repository.RememberedLoginRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import java.io.IOException
import javax.inject.Inject

private val Context.loginPreferences by preferencesDataStore(name = "remembered_login")

/** Remembers only the email; credentials remain managed by Firebase Authentication. */
class LocalRememberedLoginRepository @Inject constructor(
    @param:ApplicationContext private val context: Context
) : RememberedLoginRepository {
    private val emailKey = stringPreferencesKey("email")

    override suspend fun getEmail(): String? = try {
        context.loginPreferences.data.first()[emailKey]
    } catch (_: IOException) {
        null
    }

    override suspend fun setEmail(email: String?) {
        context.loginPreferences.edit {
            if (email == null) it.remove(emailKey) else it[emailKey] = email
        }
    }
}

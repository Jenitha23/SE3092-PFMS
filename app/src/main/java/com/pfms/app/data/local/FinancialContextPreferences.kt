package com.pfms.app.data.local

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.map
import java.time.YearMonth
import javax.inject.Inject
import javax.inject.Singleton

private val Context.financialContextStore by preferencesDataStore(name = "financial_context")

/** Device-local review confirmations and last known logging time, always keyed by Firebase UID. */
@Singleton
class FinancialContextPreferences @Inject constructor(@param:ApplicationContext private val context: Context) {
    private fun reviewKey(uid: String, month: YearMonth) = stringPreferencesKey("review_${uid}_$month")
    private fun activityKey(uid: String) = longPreferencesKey("last_recorded_$uid")
    fun review(uid: String, month: YearMonth) = context.financialContextStore.data.map { it[reviewKey(uid, month)] }
    fun lastRecordedAt(uid: String) = context.financialContextStore.data.map { it[activityKey(uid)] }
    suspend fun review(uid: String, month: YearMonth, signature: String) {
        context.financialContextStore.edit { it[reviewKey(uid, month)] = signature }
    }
    suspend fun rememberRecordedAt(uid: String, timestamp: Long) {
        if (timestamp <= 0) return
        context.financialContextStore.edit { values ->
            if (timestamp > (values[activityKey(uid)] ?: 0L)) values[activityKey(uid)] = timestamp
        }
    }
}

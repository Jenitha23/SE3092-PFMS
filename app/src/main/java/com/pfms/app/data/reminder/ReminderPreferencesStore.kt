package com.pfms.app.data.reminder

import android.content.Context
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import com.pfms.app.domain.model.*
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.security.MessageDigest
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

private val Context.reminderStore by preferencesDataStore(name = "financial_reminders")

/** No financial data or cross-device settings are written to Firestore by this foundation. */
@Singleton
class ReminderPreferencesStore @Inject constructor(@param:ApplicationContext private val context: Context) {
    private val mutex = Mutex()
    // All settings mutations and final notification delivery share this lock.
    suspend fun <T> locked(action: suspend () -> T): T = mutex.withLock { action() }
    private fun prefix(uid: String, type: ReminderType) = "${identity(uid)}_${type.key}"
    fun observe(uid: String): Flow<UserReminderSettings> = context.reminderStore.data.map { values ->
        val defaults = UserReminderSettings.defaults(uid)
        UserReminderSettings(uid, ReminderType.entries.associateWith { type ->
            val key = prefix(uid, type); val fallback = defaults[type]
            ReminderSchedule(values[booleanPreferencesKey("${key}_enabled")] ?: fallback.enabled,
                (values[intPreferencesKey("${key}_time")] ?: fallback.minuteOfDay).coerceIn(0, 1439),
                (values[intPreferencesKey("${key}_day")] ?: fallback.dayOfWeek).coerceIn(1, 7),
                values[stringPreferencesKey("${key}_revision")] ?: fallback.revision)
        })
    }.distinctUntilChanged()
    suspend fun read(uid: String) = observe(uid).first()

    /** Caller holds locked(), including its authentication check. */
    suspend fun update(uid: String, type: ReminderType, enabled: Boolean?, minute: Int?, day: Int?) {
        require(minute == null || minute in 0..1439); require(day == null || day in 1..7)
        val current = read(uid)[type]
        val next = current.copy(enabled = enabled ?: current.enabled,
            minuteOfDay = minute ?: current.minuteOfDay, dayOfWeek = day ?: current.dayOfWeek)
        if (next == current) return
        val key = prefix(uid, type)
        context.reminderStore.edit {
            it[booleanPreferencesKey("${key}_enabled")] = next.enabled
            it[intPreferencesKey("${key}_time")] = next.minuteOfDay
            it[intPreferencesKey("${key}_day")] = next.dayOfWeek
            it[stringPreferencesKey("${key}_revision")] = UUID.randomUUID().toString()
        }
    }

    /** Durable at-most-once claim, made before posting to close the process-death duplicate window. */
    suspend fun claim(uid: String, type: ReminderType, item: String, slot: String): Boolean {
        val key = stringPreferencesKey("sent_${prefix(uid, type)}_${identity(item)}")
        var claimed = false
        context.reminderStore.edit { values ->
            // ISO date ordering also avoids duplicates after moving the device clock backwards.
            if ((values[key] ?: "") < slot) { values[key] = slot; claimed = true }
        }
        return claimed
    }
    suspend fun release(uid: String, type: ReminderType, item: String, slot: String) {
        val key = stringPreferencesKey("sent_${prefix(uid, type)}_${identity(item)}")
        context.reminderStore.edit { if (it[key] == slot) it.remove(key) }
    }
    companion object {
        fun identity(value: String): String = MessageDigest.getInstance("SHA-256").digest(value.toByteArray())
            .joinToString("") { "%02x".format(it.toInt() and 255) }
    }
}

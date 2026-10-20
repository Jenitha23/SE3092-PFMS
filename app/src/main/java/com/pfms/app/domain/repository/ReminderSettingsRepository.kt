package com.pfms.app.domain.repository

import com.pfms.app.domain.model.*
import kotlinx.coroutines.flow.Flow

interface ReminderSettingsRepository {
    fun observeSettings(): Flow<UserReminderSettings?>
    fun notificationAccess(): NotificationAccess
    suspend fun update(userId: String, type: ReminderType, enabled: Boolean? = null,
        minuteOfDay: Int? = null, dayOfWeek: Int? = null): Result<Unit>
    suspend fun reconcile(): Result<Unit>
}

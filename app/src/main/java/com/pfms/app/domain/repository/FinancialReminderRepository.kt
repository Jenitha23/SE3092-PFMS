package com.pfms.app.domain.repository

import com.pfms.app.domain.model.*

interface FinancialReminderRepository {
    suspend fun snapshot(userId: String, type: ReminderType): ReminderFinancialRecords
}

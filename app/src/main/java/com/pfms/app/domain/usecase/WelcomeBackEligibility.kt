package com.pfms.app.domain.usecase

import com.pfms.app.domain.usecase.dashboard.AppClock
import java.time.Instant
import java.time.LocalDate
import java.time.temporal.ChronoUnit

object WelcomeBackEligibility {
    fun isEligible(lastRecordedAt: Long?, today: LocalDate): Boolean {
        if (lastRecordedAt == null || lastRecordedAt <= 0L) return false
        val lastDay = runCatching { Instant.ofEpochMilli(lastRecordedAt).atZone(AppClock.zone).toLocalDate() }.getOrNull()
            ?: return false
        return !lastDay.isAfter(today) && ChronoUnit.DAYS.between(lastDay, today) >= 7
    }
}

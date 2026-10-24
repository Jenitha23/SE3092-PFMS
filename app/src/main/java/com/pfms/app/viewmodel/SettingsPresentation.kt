package com.pfms.app.viewmodel

import com.pfms.app.domain.model.AuthUser
import com.pfms.app.domain.model.ReminderType
import com.pfms.app.domain.model.UserProfile
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale

enum class SettingsDialog { NAME, PAYMENT, THEME, PASSWORD, DELETE, REMINDERS }
enum class SettingsField { NAME, CURRENT_PASSWORD, NEW_PASSWORD, CONFIRM_PASSWORD, DELETE_PASSWORD }

/** Transient input stays in memory and is cleared when its dialog closes. */
data class SettingsEditorState(
    val dialog: SettingsDialog? = null,
    val name: String = "",
    val originalName: String = "",
    val currentPassword: String = "",
    val newPassword: String = "",
    val confirmPassword: String = "",
    val deletePassword: String = "",
    val error: String? = null
) {
    val canSaveName: Boolean get() = name.isNotBlank() && name.trim() != originalName.trim()
    val canSavePassword: Boolean get() = currentPassword.isNotBlank() && newPassword.isNotBlank()
}

data class SettingsProfileState(
    val name: String = "Your profile",
    val email: String = "",
    val initials: String = "TM",
    val verified: Boolean = false,
    val paymentMethod: String = "Choose a payment method",
    val currency: String = "LKR · Sri Lankan rupee",
    val canChangePassword: Boolean = false,
    val googleOnly: Boolean = false
)

object SettingsPresentation {
    fun profile(user: AuthUser?, profile: UserProfile?): SettingsProfileState {
        val name = profile?.displayName?.takeIf { it.isNotBlank() }
            ?: user?.displayName?.takeIf { it.isNotBlank() } ?: "Your profile"
        val initials = name.trim().split(Regex("\\s+")).take(2)
            .mapNotNull { it.firstOrNull()?.toString() }.joinToString("").uppercase(Locale.ROOT)
        val currency = profile?.baseCurrency ?: "LKR"
        return SettingsProfileState(name, user?.email ?: profile?.email.orEmpty(), initials,
            user?.isEmailVerified == true,
            profile?.defaultPaymentMethod ?: "Choose a payment method",
            if (currency == "LKR") "LKR · Sri Lankan rupee" else currency,
            user?.canChangePassword == true,
            user?.isGoogleAccount == true && !user.canChangePassword)
    }

    fun dailyReminder(state: ReminderSettingsUiState): String {
        if (state.isLoading) return "Loading your schedule…"
        val schedule = state.settings?.schedules?.get(ReminderType.DAILY_LOG)
            ?: return "Schedule unavailable · open reminders to retry"
        val time = LocalTime.of(schedule.minuteOfDay / 60, schedule.minuteOfDay % 60)
            .format(DateTimeFormatter.ofPattern("h:mm a", Locale.ENGLISH))
        val summary = "Every day at $time · Asia/Colombo"
        return if (schedule.enabled && !state.access.allowed) "$summary · notifications off" else summary
    }
}


package com.pfms.app.domain.usecase

import com.pfms.app.domain.model.SalaryTemplate
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId

class SalaryReminderUseCase {

    private val colomboZone =
        ZoneId.of("Asia/Colombo")

    fun shouldShowReminder(
        template: SalaryTemplate,
        today: LocalDate = LocalDate.now(colomboZone)
    ): Boolean {

        if (!template.isEnabled) {
            return false
        }

        if (template.amountMinor <= 0L) {
            return false
        }

        if (template.paymentDay !in 1..31) {
            return false
        }

        val currentMonth = YearMonth.from(today)

        val dueDay = minOf(
            template.paymentDay,
            currentMonth.lengthOfMonth()
        )

        if (today.dayOfMonth < dueDay) {
            return false
        }

        val currentMonthKey = currentMonth.toString()

        if (template.lastConfirmedMonth ==
            currentMonthKey
        ) {
            return false
        }

        return true
    }

    fun getCurrentMonthKey(
        today: LocalDate = LocalDate.now(colomboZone)
    ): String {
        return YearMonth.from(today).toString()
    }
}

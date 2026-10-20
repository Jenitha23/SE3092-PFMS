package com.pfms.app.domain.usecase

import java.time.LocalDate

/** Transaction dates describe when money moved, not when the user recorded the entry. */
object FinancialEntryDate {
    fun parse(value: String?): LocalDate? = value?.takeIf { it.matches(Regex("\\d{4}-\\d{2}-\\d{2}")) }
        ?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
}

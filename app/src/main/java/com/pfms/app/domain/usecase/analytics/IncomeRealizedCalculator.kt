package com.pfms.app.domain.usecase.analytics

import com.pfms.app.domain.model.Income
import com.pfms.app.domain.usecase.IncomeSummaryCalculator
import java.time.YearMonth
import java.time.LocalDate

/**
 * Shared calculation rules for realized income evaluation.
 *
 * Enforces Member 2 rules:
 * - Freelance is only realized when paymentStatus == "Received" (excludes Expected/pending).
 * - Crypto Gain adds to realized income; Crypto Loss subtracts from realized income.
 * - USD income uses stored amountLkrMinor (historical converted amount) without live FX calls.
 * - Non-realized records (e.g. expected freelance) contribute 0.
 */
object IncomeRealizedCalculator {

    const val SOURCE_SALARY = "Salary"
    const val SOURCE_FREELANCE = "Freelance"
    const val SOURCE_GOOGLE_ADSENSE = "Google AdSense"
    const val SOURCE_CRYPTO = "Crypto"
    const val SOURCE_OTHER = "Other"

    const val STATUS_RECEIVED = "Received"
    const val STATUS_EXPECTED = "Expected"

    const val CRYPTO_GAIN = "Gain"
    const val CRYPTO_LOSS = "Loss"

    /**
     * Resolves the effective amount in minor LKR units.
     * Uses historical [Income.amountLkrMinor] when available (for USD entries),
     * Unconverted foreign amounts contribute zero until their historical LKR value is available.
     */
    fun getEffectiveLkrMinor(income: Income): Long {
        return IncomeSummaryCalculator.lkrAmount(income)
    }

    /**
     * Returns true if this record counts as realized/actual income.
     * Expected freelance invoices are not realized until received.
     */
    fun isRealized(income: Income): Boolean {
        if (income.source.equals(SOURCE_FREELANCE, ignoreCase = true)) {
            return income.paymentStatus.equals(STATUS_RECEIVED, ignoreCase = true)
        }
        return true
    }

    /**
     * Returns the signed realized amount in minor units:
     * - Returns 0 for unrealized items (e.g. Expected freelance).
     * - Returns positive amount for standard gains/received payments.
     * - Returns negative amount for Crypto Loss.
     */
    fun getRealizedSignedAmountMinor(income: Income): Long {
        if (!isRealized(income)) return 0L

        val amount = getEffectiveLkrMinor(income)
        if (income.source.equals(SOURCE_CRYPTO, ignoreCase = true) &&
            income.cryptoType.equals(CRYPTO_LOSS, ignoreCase = true)
        ) {
            return -amount
        }
        return amount
    }

    /**
     * Defensively parses [YearMonth] from a date string (e.g. "2026-10-15" or "2026-10").
     */
    fun parseYearMonth(dateString: String?): YearMonth? {
        if (dateString.isNullOrBlank()) return null
        return try {
            val trimmed = dateString.trim()
            if (trimmed.length == 7) YearMonth.parse(trimmed)
            else YearMonth.from(LocalDate.parse(trimmed.take(10)))
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Returns true if the income receivedDate matches the specified [targetMonth].
     */
    fun matchesMonth(income: Income, targetMonth: YearMonth): Boolean {
        val ym = parseYearMonth(income.receivedDate) ?: return false
        return ym == targetMonth
    }
}

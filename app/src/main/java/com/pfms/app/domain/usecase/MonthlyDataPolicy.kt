package com.pfms.app.domain.usecase

import com.pfms.app.domain.model.*
import com.pfms.app.domain.usecase.analytics.IncomeRealizedCalculator
import java.nio.ByteBuffer
import java.security.MessageDigest
import java.time.LocalDate
import java.time.YearMonth

/** Completeness is explicitly reviewed, never inferred from the number of no-entry days. */
object MonthlyDataPolicy {
    fun assess(records: MonthlyFinancialRecords, today: LocalDate): MonthlyDataStatus {
        val incomes = records.incomes.filter { IncomeRealizedCalculator.matchesMonth(it, records.month) }
        val expenses = records.expenses.filter { IncomeRealizedCalculator.parseYearMonth(it.date) == records.month }
        val count = incomes.size + expenses.size
        val signature = signature(incomes, expenses)
        val unassignedDates = records.incomes.any { IncomeRealizedCalculator.parseYearMonth(it.receivedDate) == null } ||
            records.expenses.any { IncomeRealizedCalculator.parseYearMonth(it.date) == null }
        val invalid = unassignedDates || incomes.any { FinancialEntryDate.parse(it.receivedDate) == null || it.amountMinor <= 0 } ||
            expenses.any { FinancialEntryDate.parse(it.date) == null || it.amountMinor <= 0 }
        val missingConversion = incomes.any {
            IncomeRealizedCalculator.isRealized(it) && it.currency != "LKR" &&
                (it.amountLkrMinor == null || it.amountLkrMinor <= 0)
        } || expenses.any { it.currency != "LKR" }
        val closed = records.month < YearMonth.from(today)
        val reason = when {
            invalid -> MonthlyDataReason.INVALID_RECORD
            missingConversion -> MonthlyDataReason.MISSING_CONVERSION
            !closed -> MonthlyDataReason.MONTH_IN_PROGRESS
            records.reviewedSignature == signature -> MonthlyDataReason.REVIEWED
            count == 0 -> MonthlyDataReason.NO_ENTRIES
            else -> MonthlyDataReason.UNREVIEWED
        }
        return MonthlyDataStatus(records.month, reason, count, signature,
            closed && !invalid && !missingConversion && reason != MonthlyDataReason.REVIEWED)
    }

    /** Stable across sync acknowledgements; financial edits, additions and deletions invalidate review. */
    private fun signature(incomes: List<Income>, expenses: List<Expense>): String {
        val digest = MessageDigest.getInstance("SHA-256")
        fun add(value: Any?) {
            if (value == null) { digest.update(0.toByte()); return }
            digest.update(1.toByte())
            val bytes = value.toString().toByteArray(Charsets.UTF_8)
            digest.update(ByteBuffer.allocate(4).putInt(bytes.size).array())
            digest.update(bytes)
        }
        add("fr37-v1")
        incomes.sortedBy { it.id }.forEach { row ->
            listOf("income", row.id, row.amountMinor, row.currency, row.source, row.receivedDate,
                row.amountLkrMinor, row.exchangeRate, row.exchangeRateDate, row.paymentStatus,
                row.cryptoType, row.cryptoAsset, row.projectName, row.note).forEach(::add)
        }
        expenses.sortedBy { it.id }.forEach { row ->
            listOf("expense", row.id, row.amountMinor, row.currency, row.date, row.categoryId,
                row.categoryName, row.classification, row.paymentMethod, row.merchant, row.note).forEach(::add)
        }
        return digest.digest().joinToString("") { "%02x".format(it.toInt() and 0xff) }
    }
}

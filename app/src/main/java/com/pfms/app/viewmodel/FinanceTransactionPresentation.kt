package com.pfms.app.viewmodel

import com.pfms.app.domain.model.dashboard.*
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

data class FinanceTransactionDisplay(val transaction: Transaction, val title: String, val date: String, val detail: String,
    val badge: String, val amount: String, val positive: Boolean, val pending: Boolean, val category: String, val income: Boolean)

object FinanceTransactionPresentation {
    fun create(row: Transaction): FinanceTransactionDisplay {
        val income = row.type == TransactionType.INCOME
        val positive = income && !row.isLoss
        val payment = row.paymentMethod?.displayLabel().orEmpty()
        val details = row.subtitle.split(" • ", " · ").map(String::trim)
        val merchant = details.firstOrNull().orEmpty()
        val title = if (income || merchant.isBlank() || merchant == payment) row.title else merchant
        val detail = if (income) row.subtitle else listOf(row.title.takeIf { it != title }, payment.takeIf { it.isNotBlank() }, details.drop(1).joinToString(" · ").takeIf { it.isNotBlank() })
            .filterNotNull().joinToString(" · ")
        val date = runCatching { LocalDate.parse(row.date.take(10)).format(DateTimeFormatter.ofPattern("dd MMM yyyy", Locale.US)) }.getOrDefault(row.date)
        val badge = when { row.isPending -> "Expected"; row.isLoss -> "Crypto loss"; income -> "Received"; else -> row.classification?.displayLabel() ?: "Expense" }
        return FinanceTransactionDisplay(row, title, date, detail, badge,
            (if (row.isPending) "" else if (positive) "+" else "−") + DashboardPresentationMapper.money(row.amountMinor),
            positive, row.isPending, row.expenseCategory.orEmpty(), income)
    }
}

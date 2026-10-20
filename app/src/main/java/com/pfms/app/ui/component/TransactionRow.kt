package com.pfms.app.ui.component

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.pfms.app.domain.model.dashboard.Transaction
import com.pfms.app.domain.model.dashboard.TransactionType
import com.pfms.app.ui.screen.income.formatAnalyticsLkrAmount
import com.pfms.app.ui.theme.EmeraldPrimary
import com.pfms.app.ui.theme.ErrorExpense
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

private val rowDateFormat: DateTimeFormatter = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.US)

/** "22 Sep 2025"; falls back to the raw text if the stored date cannot be parsed. */
fun formatTransactionDate(isoDate: String): String =
    runCatching { LocalDate.parse(isoDate.trim().take(10)).format(rowDateFormat) }.getOrDefault(isoDate.ifBlank { "Date unavailable" })

/** Text for the signed amount, e.g. "+ LKR 130,000.00" or "- LKR 2,500.00". Never colour-only (NFR-21). */
fun transactionAmountText(tx: Transaction): String {
    val money = formatAnalyticsLkrAmount(tx.amountMinor)
    return when {
        tx.type == TransactionType.EXPENSE -> "- $money"
        tx.isLoss -> "- $money"
        else -> "+ $money"
    }
}

/**
 * One transaction line, shared by the dashboard, the history and the top-5 list so they look and read
 * the same. The whole row is one accessible element with a spoken summary and a 56dp minimum height.
 */
@Composable
fun TransactionRow(
    transaction: Transaction,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null
) {
    val isIncome = transaction.type == TransactionType.INCOME && !transaction.isLoss
    val accent = if (isIncome) EmeraldPrimary else ErrorExpense
    val kind = if (transaction.type == TransactionType.INCOME) "Income" else "Expense"
    val amountText = transactionAmountText(transaction)
    val dateText = formatTransactionDate(transaction.date)
    val spoken = buildString {
        append("$kind, ${transaction.title}, $amountText, $dateText")
        if (transaction.subtitle.isNotBlank()) append(", ${transaction.subtitle}")
        if (transaction.isPending) append(", not counted until received")
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(vertical = 6.dp)
            .semantics(mergeDescendants = true) { contentDescription = spoken },
        verticalAlignment = Alignment.CenterVertically
    ) {
        Surface(
            modifier = Modifier.size(38.dp),
            shape = CircleShape,
            color = accent.copy(alpha = 0.12f)
        ) {
            Box(contentAlignment = Alignment.Center) {
                if (isIncome) ArrowUpVector(modifier = Modifier.size(16.dp), tint = accent)
                else ArrowDownVector(modifier = Modifier.size(16.dp), tint = accent)
            }
        }
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = transaction.title,
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            val line = listOf(dateText, transaction.subtitle).filter { it.isNotBlank() }.joinToString(" \u2022 ")
            Text(
                text = line,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Spacer(Modifier.width(8.dp))
        Text(
            text = amountText,
            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
            color = if (transaction.isPending) MaterialTheme.colorScheme.onSurfaceVariant else accent
        )
    }
}

package com.pfms.app.domain.usecase.dashboard

import com.pfms.app.domain.model.Expense
import com.pfms.app.domain.model.Income
import com.pfms.app.domain.model.dashboard.Transaction
import com.pfms.app.domain.model.dashboard.TransactionType
import com.pfms.app.domain.model.dashboard.displayLabel
import com.pfms.app.domain.usecase.analytics.IncomeRealizedCalculator

private const val UNCATEGORISED = "Uncategorised"

/** Uses the income team's realised-income rules so every screen agrees (NFR-17). */
fun Income.toTransaction(): Transaction {
    val realized = IncomeRealizedCalculator.isRealized(this)
    val isLoss = source.equals(IncomeRealizedCalculator.SOURCE_CRYPTO, ignoreCase = true) &&
            cryptoType.equals(IncomeRealizedCalculator.CRYPTO_LOSS, ignoreCase = true)
    val detail = listOfNotNull(projectName, cryptoAsset, note.takeIf { it.isNotBlank() })
        .filter { it.isNotBlank() }
        .joinToString(" \u2022 ")
    val subtitle = when {
        !realized -> listOf("Expected", detail).filter { it.isNotBlank() }.joinToString(" \u2022 ")
        else -> detail
    }
    return Transaction(
        id = id,
        type = TransactionType.INCOME,
        date = receivedDate,
        createdAt = createdAt,
        title = source,
        subtitle = subtitle,
        amountMinor = IncomeRealizedCalculator.getEffectiveLkrMinor(this),
        netEffectMinor = IncomeRealizedCalculator.getRealizedSignedAmountMinor(this),
        incomeSource = source,
        isPending = !realized,
        isLoss = isLoss
    )
}

fun Expense.toTransaction(): Transaction {
    val name = categoryName.trim().ifBlank { UNCATEGORISED }
    val detail = listOf(merchant.trim(), note.trim()).filter { it.isNotBlank() }
        .joinToString(" \u2022 ")
        .ifBlank { paymentMethod.displayLabel() }
    return Transaction(
        id = id,
        type = TransactionType.EXPENSE,
        date = date,
        createdAt = createdAt,
        title = name,
        subtitle = detail,
        amountMinor = amountMinor,
        netEffectMinor = -amountMinor,
        expenseCategory = name,
        paymentMethod = paymentMethod,
        classification = classification
    )
}

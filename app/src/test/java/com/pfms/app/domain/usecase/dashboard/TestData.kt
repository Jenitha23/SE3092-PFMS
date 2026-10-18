package com.pfms.app.domain.usecase.dashboard

import com.pfms.app.domain.model.Expense
import com.pfms.app.domain.model.ExpenseClassification
import com.pfms.app.domain.model.ExpensePaymentMethod
import com.pfms.app.domain.model.Income

internal fun income(
    id: String,
    date: String,
    amountMinor: Long,
    source: String = "Salary",
    status: String = "Received",
    cryptoType: String? = null,
    currency: String = "LKR",
    lkrMinor: Long? = null
) = Income(
    id = id,
    userId = "u1",
    amountMinor = amountMinor,
    currency = currency,
    source = source,
    receivedDate = date,
    paymentStatus = status,
    cryptoType = cryptoType,
    amountLkrMinor = lkrMinor
)

internal fun expense(
    id: String,
    date: String,
    amountMinor: Long,
    category: String = "Coffee & Dining Out",
    classification: ExpenseClassification = ExpenseClassification.DISCRETIONARY,
    method: ExpensePaymentMethod = ExpensePaymentMethod.CASH,
    createdAt: Long = 0L
) = Expense(
    id = id,
    userId = "u1",
    amountMinor = amountMinor,
    categoryId = category,
    categoryName = category,
    classification = classification,
    paymentMethod = method,
    date = date,
    createdAt = createdAt
)

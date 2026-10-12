
package com.pfms.app.domain.model

data class SalaryTemplate(
    val id: String = "",
    val userId: String = "",
    val amountMinor: Long = 0L,
    val currency: String = "LKR",
    val paymentDay: Int = 25,
    val isEnabled: Boolean = true,

    // YYYY-MM, e.g. 2026-10
    val lastConfirmedMonth: String? = null,

    val createdAt: Long = 0L,
    val updatedAt: Long = 0L
)

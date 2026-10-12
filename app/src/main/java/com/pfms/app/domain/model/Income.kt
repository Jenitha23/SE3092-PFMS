
package com.pfms.app.domain.model

data class Income(
    val id: String = "",
    val userId: String = "",
    val amountMinor: Long = 0L,
    val currency: String = "LKR",
    val source: String = "Salary",
    val receivedDate: String = "",
    val note: String = "",

    // Member 2 will handle these advanced fields.
    val amountLkrMinor: Long? = null,
    val exchangeRate: String? = null,
    val exchangeRateDate: String? = null,
    val paymentStatus: String = "Received",
    val projectName: String? = null,
    val cryptoType: String? = null,
    val cryptoAsset: String? = null,

    // Member 3 will coordinate persistence and sync.
    val createdAt: Long = 0L,
    val updatedAt: Long = 0L
)

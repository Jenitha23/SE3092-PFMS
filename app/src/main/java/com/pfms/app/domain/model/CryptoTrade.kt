package com.pfms.app.domain.model

/** LKR money is integer cents; quantity is a decimal STRING (never Double). */
enum class CryptoSide { BUY, SELL }

data class CryptoTrade(
    val id: String,
    val userId: String,
    val asset: String,
    val side: CryptoSide,
    val quantity: String,
    val grossLkrMinor: Long,
    val feeLkrMinor: Long,
    val date: String, // ISO-8601 yyyy-MM-dd
    val createdAt: Long,
    val originalCurrency: String = "LKR",
    val originalGrossMinor: Long = grossLkrMinor,
    val originalFeeMinor: Long = feeLkrMinor,
    val exchangeRate: String? = null,
    val note: String = "",
    val realizedLkrMinor: Long? = null
)

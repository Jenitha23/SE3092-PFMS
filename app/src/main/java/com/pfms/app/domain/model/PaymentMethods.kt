package com.pfms.app.domain.model

/** Payment methods from FR-15. The exact strings are also enforced in firestore.rules. */
object PaymentMethods {
    const val CASH = "Cash"
    const val CARD = "Card"
    const val BANK_TRANSFER = "Bank Transfer / Auto-debit"
    const val DIGITAL_WALLET = "Digital Wallet / Platform"

    val all: List<String> = listOf(CASH, CARD, BANK_TRANSFER, DIGITAL_WALLET)
}

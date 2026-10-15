package com.pfms.app.data.remote.firestore.mapper

import com.google.firebase.firestore.DocumentSnapshot
import com.pfms.app.domain.model.SalaryTemplate

/**
 * Mapping between [SalaryTemplate] domain model and Firestore document maps/snapshots.
 * Excludes local-only metadata (syncStatus, isDeleted).
 */
fun SalaryTemplate.toFirestoreMap(): Map<String, Any?> {
    return mapOf(
        "id" to id,
        "userId" to userId,
        "amountMinor" to amountMinor,
        "currency" to currency,
        "paymentDay" to paymentDay,
        "isEnabled" to isEnabled,
        "lastConfirmedMonth" to lastConfirmedMonth,
        "createdAt" to createdAt,
        "updatedAt" to updatedAt
    )
}

fun DocumentSnapshot.toSalaryTemplate(): SalaryTemplate? {
    if (!exists()) return null
    return try {
        val docId = id
        val modelId = getString("id")?.takeIf { it.isNotBlank() } ?: docId
        val userId = getString("userId").orEmpty()
        val amountMinor = (get("amountMinor") as? Number)?.toLong() ?: 0L
        val currency = getString("currency") ?: "LKR"
        val paymentDay = (get("paymentDay") as? Number)?.toInt() ?: 25
        val isEnabled = getBoolean("isEnabled") ?: true
        val lastConfirmedMonth = getString("lastConfirmedMonth")

        val createdAt = parseTimestamp(get("createdAt"))
        val updatedAt = parseTimestamp(get("updatedAt"))

        SalaryTemplate(
            id = modelId,
            userId = userId,
            amountMinor = amountMinor,
            currency = currency,
            paymentDay = paymentDay,
            isEnabled = isEnabled,
            lastConfirmedMonth = lastConfirmedMonth,
            createdAt = createdAt,
            updatedAt = updatedAt
        )
    } catch (e: Exception) {
        null
    }
}

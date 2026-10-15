package com.pfms.app.data.remote.firestore.mapper

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentSnapshot
import com.pfms.app.domain.model.Income

/**
 * Mapping between [Income] domain model and Firestore document maps/snapshots.
 * Preserves all basic fields and Member 2 advanced fields.
 * Excludes local-only metadata (syncStatus, isDeleted).
 */
fun Income.toFirestoreMap(): Map<String, Any?> {
    return mapOf(
        "id" to id,
        "userId" to userId,
        "amountMinor" to amountMinor,
        "currency" to currency,
        "source" to source,
        "receivedDate" to receivedDate,
        "note" to note,
        // Member 2 fields
        "amountLkrMinor" to amountLkrMinor,
        "exchangeRate" to exchangeRate,
        "exchangeRateDate" to exchangeRateDate,
        "paymentStatus" to paymentStatus,
        "projectName" to projectName,
        "cryptoType" to cryptoType,
        "cryptoAsset" to cryptoAsset,
        // Metadata / timestamp fields
        "createdAt" to createdAt,
        "updatedAt" to updatedAt
    )
}

fun DocumentSnapshot.toIncome(): Income? {
    if (!exists()) return null
    return try {
        val docId = id
        val modelId = getString("id")?.takeIf { it.isNotBlank() } ?: docId
        val userId = getString("userId").orEmpty()
        val amountMinor = (get("amountMinor") as? Number)?.toLong() ?: 0L
        val currency = getString("currency") ?: "LKR"
        val source = getString("source") ?: "Salary"
        val receivedDate = getString("receivedDate").orEmpty()
        val note = getString("note").orEmpty()

        val amountLkrMinor = (get("amountLkrMinor") as? Number)?.toLong()
        val exchangeRate = getString("exchangeRate")
        val exchangeRateDate = getString("exchangeRateDate")
        val paymentStatus = getString("paymentStatus") ?: "Received"
        val projectName = getString("projectName")
        val cryptoType = getString("cryptoType")
        val cryptoAsset = getString("cryptoAsset")

        val createdAt = parseTimestamp(get("createdAt"))
        val updatedAt = parseTimestamp(get("updatedAt"))

        Income(
            id = modelId,
            userId = userId,
            amountMinor = amountMinor,
            currency = currency,
            source = source,
            receivedDate = receivedDate,
            note = note,
            amountLkrMinor = amountLkrMinor,
            exchangeRate = exchangeRate,
            exchangeRateDate = exchangeRateDate,
            paymentStatus = paymentStatus,
            projectName = projectName,
            cryptoType = cryptoType,
            cryptoAsset = cryptoAsset,
            createdAt = createdAt,
            updatedAt = updatedAt
        )
    } catch (e: Exception) {
        null
    }
}

internal fun parseTimestamp(raw: Any?): Long {
    return when (raw) {
        is Timestamp -> raw.toDate().time
        is Number -> raw.toLong()
        else -> 0L
    }
}

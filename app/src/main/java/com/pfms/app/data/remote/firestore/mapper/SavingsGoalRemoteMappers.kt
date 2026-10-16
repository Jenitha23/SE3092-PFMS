package com.pfms.app.data.remote.firestore.mapper

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentSnapshot
import com.pfms.app.domain.model.GoalStatus
import com.pfms.app.domain.model.GoalTransaction
import com.pfms.app.domain.model.GoalTransactionType
import com.pfms.app.domain.model.SavingsGoal

/**
 * Mappers between Savings Goal domain models and Firestore document maps/snapshots.
 * Local-only metadata (syncStatus, isDeleted) is strictly excluded from remote persistence.
 */

fun SavingsGoal.toFirestoreMap(): Map<String, Any?> {
    return mapOf(
        "id" to id,
        "userId" to userId,
        "name" to name,
        "targetAmountMinor" to targetAmountMinor,
        "targetDate" to targetDate,
        "openingSavedAmountMinor" to openingSavedAmountMinor,
        "status" to status.name,
        "createdAt" to createdAt,
        "updatedAt" to updatedAt
    )
}

fun GoalTransaction.toFirestoreMap(): Map<String, Any?> {
    return mapOf(
        "id" to id,
        "goalId" to goalId,
        "userId" to userId,
        "amountMinor" to amountMinor,
        "transactionType" to transactionType.name,
        "date" to date,
        "note" to note,
        "createdAt" to createdAt,
        "updatedAt" to updatedAt
    )
}

fun Map<String, Any?>.toSavingsGoal(documentId: String = ""): SavingsGoal? {
    return try {
        val idVal = (get("id") as? String)?.takeIf { it.isNotBlank() } ?: documentId
        val userIdVal = (get("userId") as? String).orEmpty()
        val nameVal = (get("name") as? String).orEmpty()
        val targetAmountMinorVal = parseLong(get("targetAmountMinor"))
        val targetDateVal = (get("targetDate") as? String).orEmpty()
        val openingSavedAmountMinorVal = parseLong(get("openingSavedAmountMinor"))
        val statusVal = parseGoalStatus(get("status"))
        val createdAtVal = parseTimestamp(get("createdAt"))
        val updatedAtVal = parseTimestamp(get("updatedAt"))

        SavingsGoal(
            id = idVal,
            userId = userIdVal,
            name = nameVal,
            targetAmountMinor = targetAmountMinorVal,
            targetDate = targetDateVal,
            openingSavedAmountMinor = openingSavedAmountMinorVal,
            status = statusVal,
            createdAt = createdAtVal,
            updatedAt = updatedAtVal
        )
    } catch (_: Exception) {
        null
    }
}

fun DocumentSnapshot.toSavingsGoal(): SavingsGoal? {
    if (!exists()) return null
    val dataMap = data ?: return null
    return dataMap.toSavingsGoal(id)
}

fun Map<String, Any?>.toGoalTransaction(documentId: String = ""): GoalTransaction? {
    return try {
        val idVal = (get("id") as? String)?.takeIf { it.isNotBlank() } ?: documentId
        val goalIdVal = (get("goalId") as? String).orEmpty()
        val userIdVal = (get("userId") as? String).orEmpty()
        val amountMinorRaw = parseLong(get("amountMinor"))
        val amountMinorVal = maxOf(0L, amountMinorRaw)
        val transactionTypeVal = parseGoalTransactionType(get("transactionType"))
        val dateVal = (get("date") as? String).orEmpty()
        val noteVal = get("note") as? String
        val createdAtVal = parseTimestamp(get("createdAt"))
        val updatedAtVal = parseTimestamp(get("updatedAt"))

        GoalTransaction(
            id = idVal,
            goalId = goalIdVal,
            userId = userIdVal,
            amountMinor = amountMinorVal,
            transactionType = transactionTypeVal,
            date = dateVal,
            note = noteVal,
            createdAt = createdAtVal,
            updatedAt = updatedAtVal
        )
    } catch (_: Exception) {
        null
    }
}

fun DocumentSnapshot.toGoalTransaction(): GoalTransaction? {
    if (!exists()) return null
    val dataMap = data ?: return null
    return dataMap.toGoalTransaction(id)
}

internal fun parseGoalStatus(raw: Any?): GoalStatus {
    val str = (raw as? String)?.trim() ?: return GoalStatus.ACTIVE
    return try {
        GoalStatus.valueOf(str.uppercase())
    } catch (_: Exception) {
        GoalStatus.ACTIVE
    }
}

internal fun parseGoalTransactionType(raw: Any?): GoalTransactionType {
    val str = (raw as? String)?.trim() ?: return GoalTransactionType.CONTRIBUTION
    return try {
        GoalTransactionType.valueOf(str.uppercase())
    } catch (_: Exception) {
        GoalTransactionType.CONTRIBUTION
    }
}

internal fun parseLong(raw: Any?): Long {
    return when (raw) {
        is Number -> raw.toLong()
        is String -> raw.toLongOrNull() ?: 0L
        else -> 0L
    }
}

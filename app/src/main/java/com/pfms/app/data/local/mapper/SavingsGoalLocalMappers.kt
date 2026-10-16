package com.pfms.app.data.local.mapper

import com.pfms.app.data.local.entity.GoalTransactionEntity
import com.pfms.app.data.local.entity.SavingsGoalEntity
import com.pfms.app.data.local.entity.SyncStatus
import com.pfms.app.domain.model.GoalStatus
import com.pfms.app.domain.model.GoalTransaction
import com.pfms.app.domain.model.GoalTransactionType
import com.pfms.app.domain.model.SavingsGoal

/**
 * Clean mapping extensions between Savings Goal Domain models and Room Local Entities.
 * Contains purely structural mappings; preserves sync metadata solely within the data layer.
 */

fun SavingsGoal.toEntity(
    syncStatus: SyncStatus = SyncStatus.SYNCED,
    isDeleted: Boolean = false
): SavingsGoalEntity {
    return SavingsGoalEntity(
        id = id,
        userId = userId,
        name = name,
        targetAmountMinor = targetAmountMinor,
        targetDate = targetDate,
        openingSavedAmountMinor = openingSavedAmountMinor,
        status = status.name,
        createdAt = createdAt,
        updatedAt = updatedAt,
        syncStatus = syncStatus,
        isDeleted = isDeleted
    )
}

fun SavingsGoalEntity.toDomain(): SavingsGoal {
    val domainStatus = try {
        GoalStatus.valueOf(status.trim().uppercase())
    } catch (_: Exception) {
        GoalStatus.ACTIVE
    }

    return SavingsGoal(
        id = id,
        userId = userId,
        name = name,
        targetAmountMinor = targetAmountMinor,
        targetDate = targetDate,
        openingSavedAmountMinor = openingSavedAmountMinor,
        status = domainStatus,
        createdAt = createdAt,
        updatedAt = updatedAt
    )
}

fun List<SavingsGoalEntity>.toDomain(): List<SavingsGoal> = map { it.toDomain() }

fun GoalTransaction.toEntity(
    syncStatus: SyncStatus = SyncStatus.SYNCED,
    isDeleted: Boolean = false
): GoalTransactionEntity {
    return GoalTransactionEntity(
        id = id,
        goalId = goalId,
        userId = userId,
        amountMinor = amountMinor,
        transactionType = transactionType.name,
        date = date,
        note = note,
        createdAt = createdAt,
        updatedAt = updatedAt,
        syncStatus = syncStatus,
        isDeleted = isDeleted
    )
}

fun GoalTransactionEntity.toDomain(): GoalTransaction {
    val domainType = try {
        GoalTransactionType.valueOf(transactionType.trim().uppercase())
    } catch (_: Exception) {
        GoalTransactionType.CONTRIBUTION
    }

    return GoalTransaction(
        id = id,
        goalId = goalId,
        userId = userId,
        amountMinor = amountMinor,
        transactionType = domainType,
        date = date,
        note = note,
        createdAt = createdAt,
        updatedAt = updatedAt
    )
}

@JvmName("goalTransactionListToDomain")
fun List<GoalTransactionEntity>.toDomain(): List<GoalTransaction> = map { it.toDomain() }

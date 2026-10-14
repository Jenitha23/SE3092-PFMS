package com.pfms.app.data.local.mapper

import com.pfms.app.data.local.entity.IncomeEntity
import com.pfms.app.data.local.entity.SalaryTemplateEntity
import com.pfms.app.data.local.entity.SyncStatus
import com.pfms.app.domain.model.Income
import com.pfms.app.domain.model.SalaryTemplate

/**
 * Clean mapping extensions between Domain models and Room Local Entities.
 * Contains purely structural mappings; no repository or Firebase logic.
 */

fun Income.toEntity(
    syncStatus: SyncStatus = SyncStatus.SYNCED,
    isDeleted: Boolean = false
): IncomeEntity {
    return IncomeEntity(
        id = id,
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
        updatedAt = updatedAt,
        syncStatus = syncStatus,
        isDeleted = isDeleted
    )
}

fun IncomeEntity.toDomain(): Income {
    return Income(
        id = id,
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
}

fun List<IncomeEntity>.toDomain(): List<Income> = map { it.toDomain() }

fun SalaryTemplate.toEntity(
    syncStatus: SyncStatus = SyncStatus.SYNCED,
    isDeleted: Boolean = false
): SalaryTemplateEntity {
    return SalaryTemplateEntity(
        id = id,
        userId = userId,
        amountMinor = amountMinor,
        currency = currency,
        paymentDay = paymentDay,
        isEnabled = isEnabled,
        lastConfirmedMonth = lastConfirmedMonth,
        createdAt = createdAt,
        updatedAt = updatedAt,
        syncStatus = syncStatus,
        isDeleted = isDeleted
    )
}

fun SalaryTemplateEntity.toDomain(): SalaryTemplate {
    return SalaryTemplate(
        id = id,
        userId = userId,
        amountMinor = amountMinor,
        currency = currency,
        paymentDay = paymentDay,
        isEnabled = isEnabled,
        lastConfirmedMonth = lastConfirmedMonth,
        createdAt = createdAt,
        updatedAt = updatedAt
    )
}

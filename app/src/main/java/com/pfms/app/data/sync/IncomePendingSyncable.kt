package com.pfms.app.data.sync

/**
 * Narrowly scoped sync interface for income background synchronization.
 * Prevents polluting the Member 1 [com.pfms.app.domain.repository.IncomeRepository] interface.
 */
interface IncomePendingSyncable {
    suspend fun syncPendingIncome(): Result<Int>
}

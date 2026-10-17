package com.pfms.app.data.sync

/**
 * Narrowly scoped sync interface for savings goals and transactions background synchronization.
 * Prevents polluting domain repository interfaces and exposes only pending sync operations.
 */
interface SavingsGoalPendingSyncable {
    suspend fun syncPendingGoals(): Result<Int>
    suspend fun syncPendingTransactions(): Result<Int>
}

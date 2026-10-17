package com.pfms.app.data.sync

import com.pfms.app.domain.repository.AuthRepository
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PendingSyncCoordinatorImpl @Inject constructor(
    private val authRepository: AuthRepository,
    private val incomeSyncable: IncomePendingSyncable,
    private val templateSyncable: SalaryTemplatePendingSyncable,
    private val savingsGoalSyncable: SavingsGoalPendingSyncable
) : PendingSyncCoordinator {

    override suspend fun syncAllPending(): Result<Unit> {
        val user = authRepository.currentUser.first()
        if (user == null || user.uid.isBlank()) {
            // No authenticated user -> safely exit without syncing
            return Result.success(Unit)
        }

        val incomeResult = try {
            val res = incomeSyncable.syncPendingIncome()
            if (res.isFailure && isAuthSessionLost(res.exceptionOrNull())) {
                return Result.success(Unit)
            }
            res
        } catch (e: Exception) {
            if (isAuthSessionLost(e)) {
                return Result.success(Unit)
            }
            Result.failure(e)
        }

        val templateResult = try {
            val res = templateSyncable.syncPendingTemplate()
            if (res.isFailure && isAuthSessionLost(res.exceptionOrNull())) {
                return Result.success(Unit)
            }
            res
        } catch (e: Exception) {
            if (isAuthSessionLost(e)) {
                return Result.success(Unit)
            }
            Result.failure(e)
        }

        val savingsGoalResult = try {
            val res = savingsGoalSyncable.syncPendingGoals()
            if (res.isFailure && isAuthSessionLost(res.exceptionOrNull())) {
                return Result.success(Unit)
            }
            res
        } catch (e: Exception) {
            if (isAuthSessionLost(e)) {
                return Result.success(Unit)
            }
            Result.failure(e)
        }

        val savingsTxResult = try {
            val res = savingsGoalSyncable.syncPendingTransactions()
            if (res.isFailure && isAuthSessionLost(res.exceptionOrNull())) {
                return Result.success(Unit)
            }
            res
        } catch (e: Exception) {
            if (isAuthSessionLost(e)) {
                return Result.success(Unit)
            }
            Result.failure(e)
        }

        return if (incomeResult.isSuccess && templateResult.isSuccess && savingsGoalResult.isSuccess && savingsTxResult.isSuccess) {
            Result.success(Unit)
        } else {
            val failure = incomeResult.exceptionOrNull()
                ?: templateResult.exceptionOrNull()
                ?: savingsGoalResult.exceptionOrNull()
                ?: savingsTxResult.exceptionOrNull()
                ?: RuntimeException("Failed to synchronize pending changes")
            Result.failure(failure)
        }
    }

    private suspend fun isAuthSessionLost(throwable: Throwable?): Boolean {
        if (throwable == null) return false
        if (throwable is IllegalStateException && throwable.message?.contains("Authentication required", ignoreCase = true) == true) {
            return true
        }
        val current = authRepository.currentUser.first()
        return (current == null || current.uid.isBlank()) && throwable is IllegalStateException
    }
}

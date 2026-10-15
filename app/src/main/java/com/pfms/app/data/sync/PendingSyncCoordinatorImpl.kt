package com.pfms.app.data.sync

import com.pfms.app.domain.repository.AuthRepository
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PendingSyncCoordinatorImpl @Inject constructor(
    private val authRepository: AuthRepository,
    private val incomeSyncable: IncomePendingSyncable,
    private val templateSyncable: SalaryTemplatePendingSyncable
) : PendingSyncCoordinator {

    override suspend fun syncAllPending(): Result<Unit> {
        val user = authRepository.currentUser.first()
        if (user == null || user.uid.isBlank()) {
            // No authenticated user -> safely exit without syncing
            return Result.success(Unit)
        }

        val incomeResult = try {
            incomeSyncable.syncPendingIncome()
        } catch (e: IllegalStateException) {
            // Auth session may have ended mid-sync
            return Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }

        val templateResult = try {
            templateSyncable.syncPendingTemplate()
        } catch (e: IllegalStateException) {
            return Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }

        return if (incomeResult.isSuccess && templateResult.isSuccess) {
            Result.success(Unit)
        } else {
            val failure = incomeResult.exceptionOrNull()
                ?: templateResult.exceptionOrNull()
                ?: RuntimeException("Failed to synchronize pending changes")
            Result.failure(failure)
        }
    }
}

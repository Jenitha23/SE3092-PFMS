package com.pfms.app.data.sync

/**
 * Narrowly scoped sync interface for salary template background synchronization.
 * Prevents polluting the Member 1 [com.pfms.app.domain.repository.SalaryTemplateRepository] interface.
 */
interface SalaryTemplatePendingSyncable {
    suspend fun syncPendingTemplate(): Result<Unit>
}

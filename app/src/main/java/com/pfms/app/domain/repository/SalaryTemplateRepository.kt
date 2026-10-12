
package com.pfms.app.domain.repository

import com.pfms.app.domain.model.SalaryTemplate
import kotlinx.coroutines.flow.Flow

interface SalaryTemplateRepository {

    // Observe the current authenticated user's
    // recurring salary template.
    fun observeSalaryTemplate(): Flow<SalaryTemplate?>

    // Create or update the salary template.
    suspend fun saveSalaryTemplate(
        template: SalaryTemplate
    )

    // Confirm the salary for one particular month.
    //
    // Implementation requirements for Member 3:
    //
    // 1. Use the authenticated user's UID.
    // 2. Use a deterministic salary income record ID
    //    derived from the template and month.
    // 3. Save the confirmed salary income record.
    // 4. Update the template amount and
    //    lastConfirmedMonth consistently.
    // 5. Prevent duplicate confirmations,
    //    including during offline synchronization.
    // 6. Throw an exception on failure.
    //
    // This must not overwrite an existing confirmed
    // salary if the confirmation is submitted again.
    suspend fun confirmMonthlySalary(
        templateId: String,
        monthKey: String,
        amountMinor: Long
    )
}

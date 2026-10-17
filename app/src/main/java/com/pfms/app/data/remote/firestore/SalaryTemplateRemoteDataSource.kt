package com.pfms.app.data.remote.firestore

import com.pfms.app.domain.model.SalaryTemplate
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

interface SalaryTemplateRemoteDataSource {
    fun observeSnapshots(userId: String): Flow<RemoteRecords<SalaryTemplate>> = observeSalaryTemplates(userId).map { RemoteRecords(it, false) }
    suspend fun getAllSalaryTemplates(userId: String): List<SalaryTemplate>
    suspend fun getSalaryTemplateById(userId: String, templateId: String): SalaryTemplate?
    suspend fun upsertSalaryTemplate(userId: String, template: SalaryTemplate)
    suspend fun deleteSalaryTemplate(userId: String, templateId: String)
    fun observeSalaryTemplates(userId: String): Flow<List<SalaryTemplate>>
}

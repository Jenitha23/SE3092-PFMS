package com.pfms.app.data.remote.firestore

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.pfms.app.data.firebase.FirestoreCollections
import com.pfms.app.data.remote.firestore.mapper.toFirestoreMap
import com.pfms.app.data.remote.firestore.mapper.toSalaryTemplate
import com.pfms.app.domain.model.SalaryTemplate
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FirestoreSalaryTemplateRemoteDataSource @Inject constructor(
    private val firestore: FirebaseFirestore,
    private val auth: FirebaseAuth
) : SalaryTemplateRemoteDataSource {

    private fun validateAuthenticatedAccess(userId: String) {
        require(userId.isNotBlank()) { "User ID must not be blank" }
        val currentUid = auth.currentUser?.uid
        require(!currentUid.isNullOrBlank()) { "Authentication required: no active user session" }
        require(currentUid == userId) {
            "Access denied: authenticated user '$currentUid' cannot access resources for user '$userId'"
        }
    }

    private fun userTemplatesCollection(userId: String) =
        firestore.collection(FirestoreCollections.USERS)
            .document(userId)
            .collection(FirestoreCollections.RECURRING_TEMPLATES)

    override suspend fun getAllSalaryTemplates(userId: String): List<SalaryTemplate> {
        validateAuthenticatedAccess(userId)

        val snapshot = userTemplatesCollection(userId).get().await()
        return snapshot.documents.mapNotNull { it.toSalaryTemplate() }
    }

    override suspend fun getSalaryTemplateById(userId: String, templateId: String): SalaryTemplate? {
        validateAuthenticatedAccess(userId)
        require(templateId.isNotBlank()) { "Template ID must not be blank" }

        val snapshot = userTemplatesCollection(userId).document(templateId).get().await()
        return snapshot.toSalaryTemplate()
    }

    override suspend fun upsertSalaryTemplate(userId: String, template: SalaryTemplate) {
        validateAuthenticatedAccess(userId)
        require(template.id.isNotBlank()) { "Template ID must not be blank" }

        require(template.userId == userId) {
            "Cross-user write prevented: path userId '$userId' does not match template.userId '${template.userId}'"
        }

        userTemplatesCollection(userId)
            .document(template.id)
            .set(template.toFirestoreMap(), SetOptions.merge())
            .await()
    }

    override suspend fun deleteSalaryTemplate(userId: String, templateId: String) {
        validateAuthenticatedAccess(userId)
        require(templateId.isNotBlank()) { "Template ID must not be blank" }

        userTemplatesCollection(userId)
            .document(templateId)
            .delete()
            .await()
    }

    override fun observeSalaryTemplates(userId: String): Flow<List<SalaryTemplate>> = observeSnapshots(userId).map { it.rows }

    override fun observeSnapshots(userId: String): Flow<RemoteRecords<SalaryTemplate>> = callbackFlow {
        validateAuthenticatedAccess(userId)

        val registration = userTemplatesCollection(userId)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    close(error)
                    return@addSnapshotListener
                }
                val list = snapshot?.documents?.mapNotNull { it.toSalaryTemplate() }.orEmpty()
                trySend(RemoteRecords(list, snapshot != null && !snapshot.metadata.isFromCache && !snapshot.metadata.hasPendingWrites()))
            }

        awaitClose { registration.remove() }
    }
}

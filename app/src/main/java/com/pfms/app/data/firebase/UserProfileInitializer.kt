package com.pfms.app.data.firebase

import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Source
import com.pfms.app.data.model.DefaultFinancialData
import kotlinx.coroutines.tasks.await
import javax.inject.Inject

/**
 * Creates users/{uid} plus the default categories and income sources (FR-01).
 *
 * - One atomic batch: either the profile AND its defaults exist, or nothing does. So
 *   "profile exists" is a safe test for "initialised".
 * - Idempotent: deterministic document IDs and an existence check make it safe to call from
 *   register, email login and Google sign-in (this also repairs accounts whose first setup
 *   failed half-way, e.g. the app was killed right after account creation).
 * - Reads from the SERVER on purpose: a stale offline cache must not decide that a profile is missing.
 */
class UserProfileInitializer @Inject constructor(
    private val firestore: FirebaseFirestore
) {

    suspend fun ensureInitialised(uid: String, displayName: String, email: String) {
        val userDoc = firestore.collection(FirestoreCollections.USERS).document(uid)

        if (userDoc.get(Source.SERVER).await().exists()) return

        val batch = firestore.batch()

        batch.set(
            userDoc,
            mapOf(
                "uid" to uid,
                "displayName" to displayName.trim().take(MAX_NAME_LENGTH),
                "email" to email,
                "baseCurrency" to BASE_CURRENCY,
                "defaultPaymentMethod" to null,
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            )
        )

        DefaultFinancialData.expenseCategories.forEach { category ->
            batch.set(
                userDoc.collection(FirestoreCollections.CATEGORIES).document(category.id),
                mapOf(
                    "name" to category.name,
                    "defaultClassification" to category.classification,
                    "isDefault" to true,
                    "isArchived" to false,
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            )
        }

        DefaultFinancialData.incomeSources.forEach { source ->
            batch.set(
                userDoc.collection(FirestoreCollections.INCOME_SOURCES).document(source.id),
                mapOf(
                    "name" to source.name,
                    "isRegular" to source.isRegular,
                    "isDefault" to true,
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            )
        }

        batch.commit().awaitOnline(BULK_WRITE_TIMEOUT_MS)
    }

    private companion object {
        const val BASE_CURRENCY = "LKR"
        const val MAX_NAME_LENGTH = 100
    }
}

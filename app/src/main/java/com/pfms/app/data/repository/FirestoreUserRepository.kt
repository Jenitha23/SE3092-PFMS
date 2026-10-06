package com.pfms.app.data.repository

import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.pfms.app.data.firebase.FirestoreCollections
import com.pfms.app.domain.model.UserProfile
import com.pfms.app.domain.repository.AuthRepository
import com.pfms.app.domain.repository.UserRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import javax.inject.Inject

class FirestoreUserRepository @Inject constructor(
    private val firestore: FirebaseFirestore,
    private val authRepository: AuthRepository
) : UserRepository {

    /** Real-time listener; switches automatically when the signed-in user changes or signs out. */
    @OptIn(ExperimentalCoroutinesApi::class)
    override fun observeProfile(): Flow<UserProfile?> =
        authRepository.currentUser.flatMapLatest { user ->
            if (user == null) flowOf(null) else profileFlow(user.uid)
        }

    private fun profileFlow(uid: String): Flow<UserProfile?> = callbackFlow {
        val registration = firestore.collection(FirestoreCollections.USERS)
            .document(uid)
            .addSnapshotListener { snapshot, error ->
                // On error (e.g. permission denied right after logout) emit null, never crash.
                trySend(if (error != null) null else snapshot?.toUserProfile())
            }
        awaitClose { registration.remove() }
    }

    private fun DocumentSnapshot.toUserProfile(): UserProfile? {
        if (!exists()) return null
        return UserProfile(
            uid = id,
            displayName = getString("displayName").orEmpty(),
            email = getString("email").orEmpty(),
            baseCurrency = getString("baseCurrency") ?: "LKR",
            defaultPaymentMethod = getString("defaultPaymentMethod")
        )
    }
}

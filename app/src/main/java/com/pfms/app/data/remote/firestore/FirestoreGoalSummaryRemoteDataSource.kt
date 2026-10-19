package com.pfms.app.data.remote.firestore

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.pfms.app.data.firebase.FirestoreCollections
import com.pfms.app.data.firebase.userIds
import com.pfms.app.domain.model.dashboard.ActiveGoalSnapshot
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Real-time listener on users/{uid}/savingsGoals and users/{uid}/goalContributions (the collection
 * names come from [FirestoreCollections], shared with firestore.rules).
 *
 * Document contract read here (the savings-goal feature writes these fields):
 *  savingsGoals/{id}:        name:String, targetAmountMinor:Long, openingAmountMinor:Long,
 *                            targetDate:String(yyyy-MM-dd), status:String ("ACTIVE" | "COMPLETED" | ...)
 *                            (a boolean `active` or `archived` is also honoured)
 *  goalContributions/{id}:   goalId:String, amountMinor:Long, type:String ("CONTRIBUTION" | "WITHDRAWAL")
 *
 * saved = opening + contributions - withdrawals (FR-24). Missing fields fall back to safe defaults
 * and a failing listener degrades to "no goal" so the dashboard never crashes (NFR-08).
 * If the goal feature uses different field names, this is the only file to adjust.
 */
@Singleton
@OptIn(ExperimentalCoroutinesApi::class)
class FirestoreGoalSummaryRemoteDataSource @Inject constructor(
    private val auth: FirebaseAuth,
    private val firestore: FirebaseFirestore
) : GoalSummaryRemoteDataSource {

    override fun observeActiveGoal(): Flow<ActiveGoalSnapshot?> =
        auth.userIds().flatMapLatest { uid ->
            if (uid == null) flowOf(null) else observeForUser(uid)
        }

    private fun observeForUser(uid: String): Flow<ActiveGoalSnapshot?> {
        val goals = listen(uid, FirestoreCollections.SAVINGS_GOALS)
        val contributions = listen(uid, FirestoreCollections.GOAL_CONTRIBUTIONS)
        return combine(goals, contributions) { goalDocs, contributionDocs ->
            val goalDoc = goalDocs.firstOrNull { it.isActiveGoal() } ?: return@combine null
            val target = goalDoc.getLong("targetAmountMinor") ?: 0L
            val opening = goalDoc.getLong("openingAmountMinor") ?: 0L
            val net = contributionDocs
                .filter { doc ->
                    val goalId = doc.getString("goalId")
                    goalId.isNullOrBlank() || goalId == goalDoc.id
                }
                .sumOf { doc ->
                    val amount = doc.getLong("amountMinor") ?: 0L
                    if (doc.getString("type").equals("WITHDRAWAL", ignoreCase = true)) -amount else amount
                }
            ActiveGoalSnapshot(
                id = goalDoc.id,
                name = goalDoc.getString("name").orEmpty().ifBlank { "Savings goal" },
                targetAmountMinor = target,
                savedAmountMinor = opening + net,
                targetDate = goalDoc.getString("targetDate")
            )
        }.catch { emit(null) }
    }

    private fun DocumentSnapshot.isActiveGoal(): Boolean {
        if (getBoolean("archived") == true) return false
        getBoolean("active")?.let { return it }
        val status = getString("status")
        return status == null || status.equals("ACTIVE", ignoreCase = true)
    }

    private fun listen(uid: String, subcollection: String): Flow<List<DocumentSnapshot>> = callbackFlow {
        val registration = firestore
            .collection(FirestoreCollections.USERS)
            .document(uid)
            .collection(subcollection)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    // Degrade to "nothing" instead of failing the whole dashboard.
                    trySend(emptyList())
                    return@addSnapshotListener
                }
                trySend(snapshot?.documents.orEmpty())
            }
        awaitClose { registration.remove() }
    }
}

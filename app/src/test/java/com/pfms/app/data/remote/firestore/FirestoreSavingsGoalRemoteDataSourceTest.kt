package com.pfms.app.data.remote.firestore

import com.google.android.gms.tasks.Tasks
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.firestore.CollectionReference
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.QuerySnapshot
import com.google.firebase.firestore.SetOptions
import com.pfms.app.data.firebase.FirestoreCollections
import com.pfms.app.data.remote.firestore.mapper.toFirestoreMap
import com.pfms.app.data.remote.firestore.mapper.toGoalTransaction
import com.pfms.app.data.remote.firestore.mapper.toSavingsGoal
import com.pfms.app.domain.model.GoalStatus
import com.pfms.app.domain.model.GoalTransaction
import com.pfms.app.domain.model.GoalTransactionType
import com.pfms.app.domain.model.SavingsGoal
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class FirestoreSavingsGoalRemoteDataSourceTest {

    private lateinit var firestore: FirebaseFirestore
    private lateinit var auth: FirebaseAuth
    private lateinit var mockUser: FirebaseUser
    private lateinit var usersCollection: CollectionReference
    private lateinit var userDoc: DocumentReference
    private lateinit var goalsCollection: CollectionReference
    private lateinit var transactionsCollection: CollectionReference

    private lateinit var dataSource: FirestoreSavingsGoalRemoteDataSource

    @Before
    fun setUp() {
        firestore = mock()
        auth = mock()
        mockUser = mock()
        usersCollection = mock()
        userDoc = mock()
        goalsCollection = mock()
        transactionsCollection = mock()

        whenever(auth.currentUser).thenReturn(mockUser)
        whenever(mockUser.uid).thenReturn("user-123")

        whenever(firestore.collection(FirestoreCollections.USERS)).thenReturn(usersCollection)
        whenever(usersCollection.document("user-123")).thenReturn(userDoc)
        whenever(userDoc.collection(FirestoreCollections.SAVINGS_GOALS)).thenReturn(goalsCollection)
        whenever(userDoc.collection(FirestoreCollections.GOAL_CONTRIBUTIONS)).thenReturn(transactionsCollection)

        dataSource = FirestoreSavingsGoalRemoteDataSource(firestore, auth)
    }

    // 1. authenticated user can write own goal
    @Test
    fun upsertGoal_authenticatedUserCanWriteOwnGoal() = runTest {
        val goal = SavingsGoal(
            id = "goal-1",
            userId = "user-123",
            name = "MacBook Pro",
            targetAmountMinor = 49_000_000L,
            targetDate = "2027-10-15",
            status = GoalStatus.ACTIVE
        )

        val goalDoc: DocumentReference = mock()
        whenever(goalsCollection.document("goal-1")).thenReturn(goalDoc)
        whenever(goalDoc.set(any(), any<SetOptions>())).thenReturn(Tasks.forResult(null))

        val result = dataSource.upsertGoal("user-123", goal)
        assertTrue("Expected success for valid user and goal", result.isSuccess)

        verify(goalsCollection).document("goal-1")
        verify(goalDoc).set(eq(goal.toFirestoreMap()), eq(SetOptions.merge()))
    }

    // 2. mismatched requested userId is rejected
    @Test
    fun upsertGoal_mismatchedRequestedUserIdIsRejected() = runTest {
        val goal = SavingsGoal(
            id = "goal-1",
            userId = "victim-user",
            name = "Secret Goal"
        )

        val result = dataSource.upsertGoal(userId = "victim-user", goal = goal)
        assertTrue("Must fail when requested userId does not match auth.currentUser.uid", result.isFailure)
        assertTrue(result.exceptionOrNull() is SecurityException)

        verify(firestore, never()).collection(any())
    }

    // 3. goal.userId mismatch is rejected
    @Test
    fun upsertGoal_modelUserIdMismatchIsRejected() = runTest {
        // Authenticated user path, but spoofed goal.userId
        val goal = SavingsGoal(
            id = "goal-1",
            userId = "other-user",
            name = "Spoofed Goal"
        )

        val result = dataSource.upsertGoal(userId = "user-123", goal = goal)
        assertTrue("Must fail when goal.userId does not match path userId", result.isFailure)
        assertTrue(result.exceptionOrNull() is IllegalArgumentException)

        verify(firestore, never()).collection(any())
    }

    // 4. authenticated user can write own transaction
    @Test
    fun upsertTransaction_authenticatedUserCanWriteOwnTransaction() = runTest {
        val tx = GoalTransaction(
            id = "tx-1",
            goalId = "goal-1",
            userId = "user-123",
            amountMinor = 500_000L,
            transactionType = GoalTransactionType.CONTRIBUTION,
            date = "2026-10-16"
        )

        val txDoc: DocumentReference = mock()
        whenever(transactionsCollection.document("tx-1")).thenReturn(txDoc)
        whenever(txDoc.set(any(), any<SetOptions>())).thenReturn(Tasks.forResult(null))

        val result = dataSource.upsertTransaction("user-123", tx)
        assertTrue("Expected success for valid user and transaction", result.isSuccess)

        verify(transactionsCollection).document("tx-1")
        verify(txDoc).set(eq(tx.toFirestoreMap()), eq(SetOptions.merge()))
    }

    // 5. transaction.userId mismatch is rejected
    @Test
    fun upsertTransaction_modelUserIdMismatchIsRejected() = runTest {
        val tx = GoalTransaction(
            id = "tx-1",
            goalId = "goal-1",
            userId = "other-user",
            amountMinor = 100_000L
        )

        val result = dataSource.upsertTransaction(userId = "user-123", transaction = tx)
        assertTrue("Must fail when transaction.userId does not match path userId", result.isFailure)
        assertTrue(result.exceptionOrNull() is IllegalArgumentException)

        verify(firestore, never()).collection(any())
    }

    // 6. deterministic goal document ID
    @Test
    fun upsertGoal_usesDeterministicDocumentId() = runTest {
        val goal = SavingsGoal(
            id = "deterministic-goal-id-999",
            userId = "user-123",
            name = "Car Fund"
        )

        val goalDoc: DocumentReference = mock()
        whenever(goalsCollection.document("deterministic-goal-id-999")).thenReturn(goalDoc)
        whenever(goalDoc.set(any(), any<SetOptions>())).thenReturn(Tasks.forResult(null))

        dataSource.upsertGoal("user-123", goal)

        verify(goalsCollection).document("deterministic-goal-id-999")
    }

    // 7. deterministic transaction document ID
    @Test
    fun upsertTransaction_usesDeterministicDocumentId() = runTest {
        val tx = GoalTransaction(
            id = "deterministic-tx-id-888",
            goalId = "goal-1",
            userId = "user-123",
            amountMinor = 20_000L
        )

        val txDoc: DocumentReference = mock()
        whenever(transactionsCollection.document("deterministic-tx-id-888")).thenReturn(txDoc)
        whenever(txDoc.set(any(), any<SetOptions>())).thenReturn(Tasks.forResult(null))

        dataSource.upsertTransaction("user-123", tx)

        verify(transactionsCollection).document("deterministic-tx-id-888")
    }

    // 8. goal mapper round-trip
    @Test
    fun goalMapper_roundTripPreservesAllFields() {
        val goal = SavingsGoal(
            id = "g-1",
            userId = "user-123",
            name = "Emergency Buffer",
            targetAmountMinor = 100_000_000L,
            targetDate = "2028-01-01",
            openingSavedAmountMinor = 10_000_000L,
            status = GoalStatus.COMPLETED,
            createdAt = 123456L,
            updatedAt = 654321L
        )

        val firestoreMap = goal.toFirestoreMap()
        val mappedBack = firestoreMap.toSavingsGoal("g-1")

        assertNotNull(mappedBack)
        assertEquals(goal, mappedBack)
    }

    // 9. transaction mapper round-trip
    @Test
    fun transactionMapper_roundTripPreservesAllFields() {
        val tx = GoalTransaction(
            id = "tx-1",
            goalId = "goal-1",
            userId = "user-123",
            amountMinor = 750_000L,
            transactionType = GoalTransactionType.WITHDRAWAL,
            date = "2026-10-10",
            note = "Bought accessories",
            createdAt = 11111L,
            updatedAt = 22222L
        )

        val firestoreMap = tx.toFirestoreMap()
        val mappedBack = firestoreMap.toGoalTransaction("tx-1")

        assertNotNull(mappedBack)
        assertEquals(tx, mappedBack)
    }

    // 10. malformed goal status safely handled
    @Test
    fun goalMapper_malformedStatus_safelyHandledToActive() {
        val map = mapOf(
            "id" to "g-bad-status",
            "userId" to "user-123",
            "name" to "Goal",
            "status" to "CORRUPTED_OR_UNKNOWN_STATUS"
        )

        val goal = map.toSavingsGoal()
        assertNotNull(goal)
        assertEquals(GoalStatus.ACTIVE, goal?.status)
    }

    // 11. malformed transaction type safely handled
    @Test
    fun transactionMapper_malformedType_safelyHandledToContribution() {
        val map = mapOf(
            "id" to "tx-bad-type",
            "goalId" to "goal-1",
            "userId" to "user-123",
            "amountMinor" to 50_000L,
            "transactionType" to "UNKNOWN_TX_TYPE"
        )

        val tx = map.toGoalTransaction()
        assertNotNull(tx)
        assertEquals(GoalTransactionType.CONTRIBUTION, tx?.transactionType)
    }

    // 12. missing goal document returns null
    @Test
    fun fetchGoalById_missingDocumentReturnsNull() = runTest {
        val goalDoc: DocumentReference = mock()
        val snapshot: DocumentSnapshot = mock()

        whenever(goalsCollection.document("missing-goal")).thenReturn(goalDoc)
        whenever(snapshot.exists()).thenReturn(false)
        whenever(goalDoc.get()).thenReturn(Tasks.forResult(snapshot))

        val result = dataSource.fetchGoalById("user-123", "missing-goal")
        assertTrue("Result must be success for missing document", result.isSuccess)
        assertNull("Missing document must return null without exception", result.getOrNull())
    }

    // 13. transactions filtered by goalId
    @Test
    fun fetchTransactionsForGoal_filtersByGoalIdAndSortsInMemory() = runTest {
        val query: Query = mock()
        val querySnapshot: QuerySnapshot = mock()
        val doc1: DocumentSnapshot = mock()
        val doc2: DocumentSnapshot = mock()

        whenever(transactionsCollection.whereEqualTo("goalId", "goal-target")).thenReturn(query)
        whenever(query.get()).thenReturn(Tasks.forResult(querySnapshot))

        whenever(doc1.exists()).thenReturn(true)
        whenever(doc1.id).thenReturn("tx-day2")
        whenever(doc1.data).thenReturn(
            mapOf(
                "id" to "tx-day2",
                "goalId" to "goal-target",
                "userId" to "user-123",
                "amountMinor" to 1000L,
                "date" to "2026-10-10",
                "createdAt" to 200L
            )
        )

        whenever(doc2.exists()).thenReturn(true)
        whenever(doc2.id).thenReturn("tx-day1")
        whenever(doc2.data).thenReturn(
            mapOf(
                "id" to "tx-day1",
                "goalId" to "goal-target",
                "userId" to "user-123",
                "amountMinor" to 2000L,
                "date" to "2026-10-01",
                "createdAt" to 100L
            )
        )

        // Mock returning unsorted documents
        whenever(querySnapshot.documents).thenReturn(listOf(doc1, doc2))

        val result = dataSource.fetchTransactionsForGoal("user-123", "goal-target")
        assertTrue(result.isSuccess)

        val list = result.getOrNull()
        assertNotNull(list)
        assertEquals(2, list?.size)
        // Verified sorted by date ASC
        assertEquals("tx-day1", list?.get(0)?.id)
        assertEquals("tx-day2", list?.get(1)?.id)

        verify(transactionsCollection).whereEqualTo("goalId", "goal-target")
    }

    // 14. syncStatus and isDeleted never serialized remotely
    @Test
    fun serialization_neverIncludesSyncStatusOrIsDeleted() {
        val goal = SavingsGoal(
            id = "g-1",
            userId = "user-123",
            name = "Test Goal"
        )
        val goalMap = goal.toFirestoreMap()
        assertFalse("syncStatus must not be serialized remotely", goalMap.containsKey("syncStatus"))
        assertFalse("isDeleted must not be serialized remotely", goalMap.containsKey("isDeleted"))

        val tx = GoalTransaction(
            id = "tx-1",
            goalId = "g-1",
            userId = "user-123",
            amountMinor = 10_000L
        )
        val txMap = tx.toFirestoreMap()
        assertFalse("syncStatus must not be serialized remotely", txMap.containsKey("syncStatus"))
        assertFalse("isDeleted must not be serialized remotely", txMap.containsKey("isDeleted"))
    }

    // Additional: deleteGoal calls delete on document
    @Test
    fun deleteGoal_deletesExactDocument() = runTest {
        val goalDoc: DocumentReference = mock()
        whenever(goalsCollection.document("goal-to-delete")).thenReturn(goalDoc)
        whenever(goalDoc.delete()).thenReturn(Tasks.forResult(null))

        val result = dataSource.deleteGoal("user-123", "goal-to-delete")
        assertTrue(result.isSuccess)
        verify(goalsCollection).document("goal-to-delete")
        verify(goalDoc).delete()
    }

    // Additional: deleteTransaction calls delete on document
    @Test
    fun deleteTransaction_deletesExactDocument() = runTest {
        val txDoc: DocumentReference = mock()
        whenever(transactionsCollection.document("tx-to-delete")).thenReturn(txDoc)
        whenever(txDoc.delete()).thenReturn(Tasks.forResult(null))

        val result = dataSource.deleteTransaction("user-123", "tx-to-delete")
        assertTrue(result.isSuccess)
        verify(transactionsCollection).document("tx-to-delete")
        verify(txDoc).delete()
    }

    // Additional: unauthenticated session returns failure
    @Test
    fun unauthenticatedSession_returnsFailure() = runTest {
        whenever(auth.currentUser).thenReturn(null)

        val result = dataSource.fetchGoals("user-123")
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is IllegalStateException)
    }
}

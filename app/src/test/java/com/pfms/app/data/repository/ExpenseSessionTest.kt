package com.pfms.app.data.repository

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.firestore.*
import com.pfms.app.domain.model.Expense
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test
import org.mockito.kotlin.*

@OptIn(ExperimentalCoroutinesApi::class)
class ExpenseSessionTest {
    @Test fun malformedExpenseIsReportedThroughFlowInsteadOfCrashingFirebaseCallback() = runTest {
        val auth = mock<FirebaseAuth>()
        val firestore = mock<FirebaseFirestore>()
        val users = mock<CollectionReference>()
        val userDocument = mock<DocumentReference>()
        val expenses = mock<CollectionReference>()
        val ordered = mock<Query>()
        val user = mock<FirebaseUser>()
        whenever(user.uid).thenReturn("owner")
        whenever(auth.currentUser).thenReturn(user)
        whenever(firestore.collection("users")).thenReturn(users)
        whenever(users.document("owner")).thenReturn(userDocument)
        whenever(userDocument.collection("expenses")).thenReturn(expenses)
        whenever(expenses.orderBy("date", Query.Direction.DESCENDING)).thenReturn(ordered)
        whenever(ordered.addSnapshotListener(any<EventListener<QuerySnapshot>>())).thenReturn(mock())
        doAnswer { it.getArgument<FirebaseAuth.AuthStateListener>(0).onAuthStateChanged(auth); null }
            .whenever(auth).addAuthStateListener(any())
        var reported: Throwable? = null
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            FirestoreExpenseRepository(auth, firestore).observeExpenses().catch { reported = it }.collect {}
        }
        runCurrent()
        val callback = argumentCaptor<EventListener<QuerySnapshot>>()
        verify(ordered).addSnapshotListener(callback.capture())
        val snapshot = mock<QuerySnapshot>()
        val badDocument = mock<DocumentSnapshot>()
        whenever(badDocument.id).thenReturn("bad")
        whenever(badDocument.getLong("amountMinor")).thenThrow(IllegalStateException("Invalid stored amount"))
        whenever(snapshot.documents).thenReturn(listOf(badDocument))
        callback.firstValue.onEvent(snapshot, null)
        runCurrent()
        assertEquals("Invalid stored amount", reported?.message)
    }

    @Test fun lateLoginAttachesListenerAndLogoutDetachesIt() = runTest {
        val auth = mock<FirebaseAuth>()
        val firestore = mock<FirebaseFirestore>()
        val users = mock<CollectionReference>()
        val userDocument = mock<DocumentReference>()
        val expenses = mock<CollectionReference>()
        val ordered = mock<Query>()
        val registration = mock<ListenerRegistration>()
        val user = mock<FirebaseUser>()
        whenever(user.uid).thenReturn("user-a")
        whenever(firestore.collection("users")).thenReturn(users)
        whenever(users.document("user-a")).thenReturn(userDocument)
        whenever(userDocument.collection("expenses")).thenReturn(expenses)
        whenever(expenses.orderBy("date", Query.Direction.DESCENDING)).thenReturn(ordered)
        whenever(ordered.addSnapshotListener(any<EventListener<QuerySnapshot>>())).thenReturn(registration)
        var signedIn: FirebaseUser? = null
        whenever(auth.currentUser).thenAnswer { signedIn }
        lateinit var authListener: FirebaseAuth.AuthStateListener
        doAnswer {
            authListener = it.getArgument(0)
            authListener.onAuthStateChanged(auth)
            null
        }.whenever(auth).addAuthStateListener(any())
        val repository = FirestoreExpenseRepository(auth, firestore)
        val received = mutableListOf<List<Expense>>()
        val observation = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            repository.observeExpenses().collect { received += it }
        }
        runCurrent()
        assertEquals(emptyList<Expense>(), received.last())
        verify(ordered, never()).addSnapshotListener(any<EventListener<QuerySnapshot>>())
        signedIn = user
        authListener.onAuthStateChanged(auth)
        runCurrent()
        verify(ordered).addSnapshotListener(any<EventListener<QuerySnapshot>>())
        signedIn = null
        authListener.onAuthStateChanged(auth)
        runCurrent()
        verify(registration).remove()
        assertTrue(received.last().isEmpty())
        observation.cancel()
        runCurrent()
        verify(auth).removeAuthStateListener(authListener)
    }
}

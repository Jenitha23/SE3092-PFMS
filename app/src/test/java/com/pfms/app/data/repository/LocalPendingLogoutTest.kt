package com.pfms.app.data.repository

import android.content.Context
import com.google.android.gms.tasks.Tasks
import com.google.firebase.firestore.FirebaseFirestore
import com.pfms.app.data.auth.BiometricPreferences
import com.pfms.app.data.local.PfmsDatabase
import com.pfms.app.data.local.dao.PendingChangesDao
import com.pfms.app.data.local.dao.ExpenseDao
import com.pfms.app.domain.model.AuthUser
import com.pfms.app.domain.repository.AuthRepository
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test
import org.mockito.kotlin.*

class LocalPendingLogoutTest {
    @Test fun localRoomQueueTriggersLogoutWarningBeforeAnyFirestoreWait() = runTest {
        val db = mock<PfmsDatabase>()
        val pending = mock<PendingChangesDao>()
        val firestore = mock<FirebaseFirestore>()
        val auth = mock<AuthRepository>()
        whenever(db.pendingChangesDao()).thenReturn(pending)
        whenever(auth.currentUser).thenReturn(flowOf(AuthUser("a", "A", "a@example.test", true)))
        whenever(pending.pendingCount("a")).thenReturn(20)
        val repository = FirestoreLocalDataRepository(mock<Context>(), firestore, mock<BiometricPreferences>(), db, auth)
        assertTrue(repository.hasUnsynchronizedData())
        verify(firestore, never()).waitForPendingWrites()
    }

    @Test fun emptyRoomQueueStillChecksSdkManagedWrites() = runTest {
        val db = mock<PfmsDatabase>()
        val pending = mock<PendingChangesDao>()
        val expenses = mock<ExpenseDao>()
        val firestore = mock<FirebaseFirestore>()
        val auth = mock<AuthRepository>()
        whenever(db.pendingChangesDao()).thenReturn(pending)
        whenever(auth.currentUser).thenReturn(flowOf(AuthUser("a", "A", "a@example.test", true)))
        whenever(pending.pendingCount("a")).thenReturn(0)
        whenever(db.expenseDao()).thenReturn(expenses)
        whenever(expenses.hasPendingOrConflicting("a")).thenReturn(false)
        whenever(firestore.waitForPendingWrites()).thenReturn(Tasks.forResult(null))
        val repository = FirestoreLocalDataRepository(mock<Context>(), firestore, mock<BiometricPreferences>(), db, auth)
        assertFalse(repository.hasUnsynchronizedData())
        verify(firestore).waitForPendingWrites()
    }

    @Test fun expenseAwaitingDuplicateConfirmationStillTriggersLogoutWarning() = runTest {
        val db = mock<PfmsDatabase>()
        val pending = mock<PendingChangesDao>()
        val expenses = mock<ExpenseDao>()
        val firestore = mock<FirebaseFirestore>()
        val auth = mock<AuthRepository>()
        whenever(db.pendingChangesDao()).thenReturn(pending)
        whenever(db.expenseDao()).thenReturn(expenses)
        whenever(auth.currentUser).thenReturn(flowOf(AuthUser("a", "A", "a@example.test", true)))
        whenever(pending.pendingCount("a")).thenReturn(0)
        whenever(expenses.hasPendingOrConflicting("a")).thenReturn(true)
        val repository = FirestoreLocalDataRepository(mock<Context>(), firestore, mock<BiometricPreferences>(), db, auth)
        assertTrue(repository.hasUnsynchronizedData())
        verify(firestore, never()).waitForPendingWrites()
    }
}

package com.pfms.app.data.repository

import com.google.android.gms.tasks.Tasks
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.firestore.*
import com.pfms.app.data.local.dao.ExpenseCategoryDao
import com.pfms.app.data.local.dao.RecurringExpenseDao
import com.pfms.app.domain.model.*
import com.pfms.app.data.firebase.FirestoreCollections
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import org.junit.Assert.*
import org.mockito.kotlin.*

class AdvancedExpenseRepositoryRegressionTest {
    private val auth = mock<FirebaseAuth>()
    private val firestore = mock<FirebaseFirestore>()
    private val users = mock<CollectionReference>()
    private val userDocument = mock<DocumentReference>()
    private val templates = mock<CollectionReference>()
    private val expenses = mock<CollectionReference>()
    private val categories = mock<ExpenseCategoryDao>()
    private val recurring = mock<RecurringExpenseDao>()
    private lateinit var repository: FirestoreAdvancedExpenseRepository

    @Before fun setup() {
        val user = mock<FirebaseUser>()
        whenever(user.uid).thenReturn("owner")
        whenever(auth.currentUser).thenReturn(user)
        whenever(firestore.collection("users")).thenReturn(users)
        whenever(users.document("owner")).thenReturn(userDocument)
        whenever(userDocument.collection(FirestoreCollections.RECURRING_TEMPLATES)).thenReturn(templates)
        whenever(userDocument.collection("expenses")).thenReturn(expenses)
        repository = FirestoreAdvancedExpenseRepository(auth, firestore, categories, recurring)
    }

    @Test fun emptyServerSnapshotClearsDeletedCachedTemplates() = runTest {
        val snapshot = mock<QuerySnapshot>()
        whenever(snapshot.documents).thenReturn(emptyList())
        whenever(templates.get(Source.SERVER)).thenReturn(Tasks.forResult(snapshot))
        repository.refreshTemplates()
        verify(recurring).replaceForUser("owner", emptyList())
    }

    @Test fun cancelFlagPersistsInactiveTemplateAndClearsReminder() = runTest {
        val document = mock<DocumentReference>()
        whenever(templates.document("sub")).thenReturn(document)
        whenever(document.set(any<Map<String, Any>>())).thenReturn(Tasks.forResult(null))
        repository.saveTemplate(RecurringExpense(id = "sub", title = "Streaming", categoryId = "rent",
            amountMinor = 10000, nextDueDate = "2020-01-01", subscription = true,
            active = true, flag = SubscriptionFlag.CANCEL, reminderDate = "2020-01-01"))
        val saved = argumentCaptor<Map<String, Any>>()
        verify(document).set(saved.capture())
        assertEquals(false, saved.firstValue["active"])
        assertEquals("", saved.firstValue["reminderDate"])
        val cached = argumentCaptor<com.pfms.app.data.local.entity.RecurringExpenseEntity>()
        verify(recurring).upsertTemplate(cached.capture())
        assertFalse(cached.firstValue.isActive)
    }

    @Test fun staleDueConfirmationCannotRecordPausedTemplate() = runTest {
        val templateDocument = mock<DocumentReference>()
        val expenseDocument = mock<DocumentReference>()
        val transaction = mock<Transaction>()
        val latest = mock<DocumentSnapshot>()
        val absent = mock<DocumentSnapshot>()
        whenever(templates.document("sub")).thenReturn(templateDocument)
        whenever(expenses.document("recurring_sub_2020-01-01")).thenReturn(expenseDocument)
        whenever(transaction.get(templateDocument)).thenReturn(latest)
        whenever(transaction.get(expenseDocument)).thenReturn(absent)
        whenever(latest.exists()).thenReturn(true)
        whenever(latest.id).thenReturn("sub")
        whenever(latest.getString("nextDueDate")).thenReturn("2020-01-01")
        whenever(latest.getBoolean("active")).thenReturn(false)
        whenever(firestore.runTransaction(any<Transaction.Function<RecurringExpense>>())).thenAnswer {
            try { Tasks.forResult(it.getArgument<Transaction.Function<RecurringExpense>>(0).apply(transaction)) }
            catch (e: Exception) { Tasks.forException<RecurringExpense>(e) }
        }
        val result = runCatching {
            repository.confirmDue(RecurringExpense(id = "sub", active = true,
                amountMinor = 10000, nextDueDate = "2020-01-01"))
        }
        assertTrue("Expected paused-template rejection, got ${result.exceptionOrNull()}",
            result.exceptionOrNull()?.message.orEmpty().contains("paused or cancelled"))
        verify(transaction, never()).set(eq(expenseDocument), any<Any>())
    }
}

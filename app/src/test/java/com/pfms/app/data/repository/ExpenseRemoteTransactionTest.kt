package com.pfms.app.data.repository

import com.google.android.gms.tasks.Tasks
import com.google.firebase.firestore.*
import com.pfms.app.data.remote.firestore.FirestoreExpenseRemoteDataSource
import com.pfms.app.domain.model.*
import kotlinx.coroutines.test.runTest
import org.junit.*
import org.junit.Assert.*
import org.mockito.kotlin.*

class ExpenseRemoteTransactionTest {
    private val firestore = mock<FirebaseFirestore>()
    private val users = mock<CollectionReference>()
    private val user = mock<DocumentReference>()
    private val expenses = mock<CollectionReference>()
    private val guards = mock<CollectionReference>()
    private val transaction = mock<Transaction>()
    private val refs = mutableMapOf<String, DocumentReference>()
    private val data = mutableMapOf<DocumentReference, Map<String, Any>>()
    private val remote = FirestoreExpenseRemoteDataSource(firestore)
    private val first = Expense(id = "one", userId = "a", amountMinor = 10000, categoryId = "rent",
        date = "2026-10-17", createdAt = 100000, updatedAt = 100000)

    @Before fun setup() {
        whenever(firestore.collection("users")).thenReturn(users)
        whenever(users.document("a")).thenReturn(user)
        whenever(user.collection("expenses")).thenReturn(expenses)
        whenever(user.collection("expenseDuplicateGuards")).thenReturn(guards)
        whenever(expenses.document(any())).thenAnswer { call -> refs.getOrPut("expense:${call.getArgument<String>(0)}") { mock() } }
        whenever(guards.document(any())).thenAnswer { call -> refs.getOrPut("guard:${call.getArgument<String>(0)}") { mock() } }
        whenever(transaction.get(any())).thenAnswer { call ->
            val fields = data[call.getArgument<DocumentReference>(0)]
            mock<DocumentSnapshot>().also { snapshot ->
                whenever(snapshot.exists()).thenReturn(fields != null)
                whenever(snapshot.getLong(any())).thenAnswer { fields?.get(it.getArgument<String>(0)) as? Long }
            }
        }
        whenever(transaction.set(any(), any<Map<String, Any>>())).thenAnswer { call ->
            data[call.getArgument(0)] = call.getArgument(1)
            transaction
        }
        whenever(firestore.runTransaction(any<Transaction.Function<Any?>>())).thenAnswer { call ->
            try { Tasks.forResult(call.getArgument<Transaction.Function<Any?>>(0).apply(transaction)) }
            catch (e: Exception) { Tasks.forException<Any?>(e) }
        }
    }

    @Test fun secondClientIsRejectedByTheSharedTransactionalGuard() = runTest {
        remote.insert("a", first, false)
        val failure = runCatching { remote.insert("a", first.copy(id = "two", createdAt = 100001), false) }.exceptionOrNull()
        assertTrue(failure is DuplicateExpenseException)
        assertEquals(1, data.keys.count { it == refs["expense:one"] || it == refs["expense:two"] })
    }

    @Test fun explicitConfirmationAndIdempotentRetryDoNotCreateExtraRecords() = runTest {
        remote.insert("a", first, false)
        remote.insert("a", first, false)
        remote.insert("a", first.copy(id = "two"), true)
        assertEquals(3, data.size) // Two transactions and one shared guard.
    }

    @Test fun expiredGuardAllowsANewTransaction() = runTest {
        remote.insert("a", first, false)
        remote.insert("a", first.copy(id = "later", createdAt = 160001), false)
        assertNotNull(data[refs["expense:later"]])
    }

    @Test fun insertAcknowledgementDoesNotLoseAnEditMadeWhilePending() = runTest {
        remote.insert("a", first, false)
        remote.insert("a", first.copy(amountMinor = 20000, updatedAt = 100001), false)
        assertEquals(20000L, data[refs["expense:one"]]?.get("amountMinor"))
    }

    @Test fun concurrentInserts_oneSucceedsAndOneThrowsDuplicate() = runTest {
        val expense1 = first.copy(id = "c1", createdAt = 200000)
        val expense2 = first.copy(id = "c2", createdAt = 200005)

        // First insert succeeds and writes the transaction guard
        remote.insert("a", expense1, allowDuplicate = false)

        // Second concurrent insert for the identical expense within 60s is blocked by the transactional guard
        val failure = runCatching {
            remote.insert("a", expense2, allowDuplicate = false)
        }.exceptionOrNull()

        assertTrue("Concurrent identical insert within 60s must throw DuplicateExpenseException", failure is DuplicateExpenseException)

        // When confirmed with allowDuplicate = true, the second record is saved successfully
        remote.insert("a", expense2, allowDuplicate = true)
        assertNotNull(data[refs["expense:c2"]])
    }
}


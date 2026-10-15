package com.pfms.app.data.remote.firestore

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.firestore.CollectionReference
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.pfms.app.data.firebase.FirestoreCollections
import com.pfms.app.data.remote.firestore.mapper.toFirestoreMap
import com.pfms.app.data.remote.firestore.mapper.toIncome
import com.pfms.app.data.remote.firestore.mapper.toSalaryTemplate
import com.pfms.app.domain.model.Income
import com.pfms.app.domain.model.SalaryTemplate
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class FirestoreMappersTest {

    @Test
    fun `Income toFirestoreMap maps all basic and Member 2 fields and preserves timestamps`() {
        val income = Income(
            id = "inc-101",
            userId = "user-abc",
            amountMinor = 15000000L,
            currency = "USD",
            source = "Freelance",
            receivedDate = "2026-10-15",
            note = "Payment for Project Apollo",
            amountLkrMinor = 450000000L,
            exchangeRate = "300.00",
            exchangeRateDate = "2026-10-15",
            paymentStatus = "Received",
            projectName = "Project Apollo",
            cryptoType = null,
            cryptoAsset = null,
            createdAt = 1773000000000L,
            updatedAt = 1773005000000L
        )

        val map = income.toFirestoreMap()

        assertEquals("inc-101", map["id"])
        assertEquals("user-abc", map["userId"])
        assertEquals(15000000L, map["amountMinor"])
        assertEquals("USD", map["currency"])
        assertEquals("Freelance", map["source"])
        assertEquals("2026-10-15", map["receivedDate"])
        assertEquals("Payment for Project Apollo", map["note"])
        assertEquals(450000000L, map["amountLkrMinor"])
        assertEquals("300.00", map["exchangeRate"])
        assertEquals("2026-10-15", map["exchangeRateDate"])
        assertEquals("Received", map["paymentStatus"])
        assertEquals("Project Apollo", map["projectName"])
        assertNull(map["cryptoType"])
        assertNull(map["cryptoAsset"])
        assertEquals(1773000000000L, map["createdAt"])
        assertEquals(1773005000000L, map["updatedAt"])

        // Ensure local-only fields are NOT in the remote map
        assertFalse(map.containsKey("syncStatus"))
        assertFalse(map.containsKey("isDeleted"))
    }

    @Test
    fun `DocumentSnapshot toIncome parses all fields defensively`() {
        val snapshot: DocumentSnapshot = mock()
        whenever(snapshot.exists()).thenReturn(true)
        whenever(snapshot.id).thenReturn("inc-202")
        whenever(snapshot.getString("id")).thenReturn("inc-202")
        whenever(snapshot.getString("userId")).thenReturn("user-abc")
        whenever(snapshot.get("amountMinor")).thenReturn(20000000L)
        whenever(snapshot.getString("currency")).thenReturn("USD")
        whenever(snapshot.getString("source")).thenReturn("Crypto")
        whenever(snapshot.getString("receivedDate")).thenReturn("2026-10-14")
        whenever(snapshot.getString("note")).thenReturn("BTC Sale")
        whenever(snapshot.get("amountLkrMinor")).thenReturn(600000000L)
        whenever(snapshot.getString("exchangeRate")).thenReturn("300.00")
        whenever(snapshot.getString("exchangeRateDate")).thenReturn("2026-10-14")
        whenever(snapshot.getString("paymentStatus")).thenReturn("Received")
        whenever(snapshot.getString("projectName")).thenReturn(null)
        whenever(snapshot.getString("cryptoType")).thenReturn("Gain")
        whenever(snapshot.getString("cryptoAsset")).thenReturn("BTC")
        whenever(snapshot.get("createdAt")).thenReturn(1773000000000L)
        whenever(snapshot.get("updatedAt")).thenReturn(1773005000000L)

        val parsed = snapshot.toIncome()

        assertEquals("inc-202", parsed?.id)
        assertEquals("user-abc", parsed?.userId)
        assertEquals(20000000L, parsed?.amountMinor)
        assertEquals("USD", parsed?.currency)
        assertEquals("Crypto", parsed?.source)
        assertEquals("Gain", parsed?.cryptoType)
        assertEquals("BTC", parsed?.cryptoAsset)
        assertEquals(1773000000000L, parsed?.createdAt)
        assertEquals(1773005000000L, parsed?.updatedAt)
    }

    @Test
    fun `SalaryTemplate toFirestoreMap maps all fields and preserves timestamps`() {
        val template = SalaryTemplate(
            id = "template-1",
            userId = "user-123",
            amountMinor = 25000000L,
            currency = "LKR",
            paymentDay = 25,
            isEnabled = true,
            lastConfirmedMonth = "2026-09",
            createdAt = 1772000000000L,
            updatedAt = 1772005000000L
        )

        val map = template.toFirestoreMap()

        assertEquals("template-1", map["id"])
        assertEquals("user-123", map["userId"])
        assertEquals(25000000L, map["amountMinor"])
        assertEquals("LKR", map["currency"])
        assertEquals(25, map["paymentDay"])
        assertEquals(true, map["isEnabled"])
        assertEquals("2026-09", map["lastConfirmedMonth"])
        assertEquals(1772000000000L, map["createdAt"])
        assertEquals(1772005000000L, map["updatedAt"])

        // Ensure local-only fields are NOT in the remote map
        assertFalse(map.containsKey("syncStatus"))
        assertFalse(map.containsKey("isDeleted"))
    }

    @Test
    fun `DocumentSnapshot toSalaryTemplate parses all fields defensively`() {
        val snapshot: DocumentSnapshot = mock()
        whenever(snapshot.exists()).thenReturn(true)
        whenever(snapshot.id).thenReturn("template-1")
        whenever(snapshot.getString("id")).thenReturn("template-1")
        whenever(snapshot.getString("userId")).thenReturn("user-123")
        whenever(snapshot.get("amountMinor")).thenReturn(25000000L)
        whenever(snapshot.getString("currency")).thenReturn("LKR")
        whenever(snapshot.get("paymentDay")).thenReturn(25)
        whenever(snapshot.getBoolean("isEnabled")).thenReturn(true)
        whenever(snapshot.getString("lastConfirmedMonth")).thenReturn("2026-09")
        whenever(snapshot.get("createdAt")).thenReturn(1772000000000L)
        whenever(snapshot.get("updatedAt")).thenReturn(1772005000000L)

        val parsed = snapshot.toSalaryTemplate()

        assertEquals("template-1", parsed?.id)
        assertEquals("user-123", parsed?.userId)
        assertEquals(25000000L, parsed?.amountMinor)
        assertEquals("LKR", parsed?.currency)
        assertEquals(25, parsed?.paymentDay)
        assertTrue(parsed?.isEnabled == true)
        assertEquals("2026-09", parsed?.lastConfirmedMonth)
        assertEquals(1772000000000L, parsed?.createdAt)
        assertEquals(1772005000000L, parsed?.updatedAt)
    }

    // ─────────────────────────────────────────────────────────────
    // AUTHENTICATED USER ISOLATION TESTS — INCOME
    // ─────────────────────────────────────────────────────────────

    @Test
    fun `IncomeRemoteDataSource throws when no user is authenticated`() = runTest {
        val firestore: FirebaseFirestore = mock()
        val auth: FirebaseAuth = mock()
        whenever(auth.currentUser).thenReturn(null)

        val dataSource = FirestoreIncomeRemoteDataSource(firestore, auth)

        val income = Income(id = "inc-1", userId = "user-123", amountMinor = 5000L)

        try {
            dataSource.upsertIncome(userId = "user-123", income = income)
            fail("Expected IllegalArgumentException when unauthenticated")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message?.contains("Authentication required") == true)
        }
    }

    @Test
    fun `IncomeRemoteDataSource throws when authenticated UID does not match requested userId`() = runTest {
        val firestore: FirebaseFirestore = mock()
        val auth: FirebaseAuth = mock()
        val mockUser: FirebaseUser = mock()
        whenever(mockUser.uid).thenReturn("authenticated-user-1")
        whenever(auth.currentUser).thenReturn(mockUser)

        val dataSource = FirestoreIncomeRemoteDataSource(firestore, auth)

        val income = Income(id = "inc-1", userId = "victim-user-2", amountMinor = 5000L)

        try {
            dataSource.upsertIncome(userId = "victim-user-2", income = income)
            fail("Expected IllegalArgumentException when authenticated UID does not match requested userId")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message?.contains("Access denied") == true)
        }
    }

    @Test
    fun `IncomeRemoteDataSource throws when model userId does not match path userId`() = runTest {
        val firestore: FirebaseFirestore = mock()
        val auth: FirebaseAuth = mock()
        val mockUser: FirebaseUser = mock()
        whenever(mockUser.uid).thenReturn("authenticated-user-1")
        whenever(auth.currentUser).thenReturn(mockUser)

        val dataSource = FirestoreIncomeRemoteDataSource(firestore, auth)

        // Path matches authenticated user, but income.userId is spoofed
        val income = Income(id = "inc-1", userId = "other-user-3", amountMinor = 5000L)

        try {
            dataSource.upsertIncome(userId = "authenticated-user-1", income = income)
            fail("Expected IllegalArgumentException when model.userId does not match path userId")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message?.contains("Cross-user write prevented") == true)
        }
    }

    // ─────────────────────────────────────────────────────────────
    // AUTHENTICATED USER ISOLATION TESTS — SALARY TEMPLATE
    // ─────────────────────────────────────────────────────────────

    @Test
    fun `SalaryTemplateRemoteDataSource throws when no user is authenticated`() = runTest {
        val firestore: FirebaseFirestore = mock()
        val auth: FirebaseAuth = mock()
        whenever(auth.currentUser).thenReturn(null)

        val dataSource = FirestoreSalaryTemplateRemoteDataSource(firestore, auth)

        val template = SalaryTemplate(id = "tpl-1", userId = "user-123", amountMinor = 5000L)

        try {
            dataSource.upsertSalaryTemplate(userId = "user-123", template = template)
            fail("Expected IllegalArgumentException when unauthenticated")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message?.contains("Authentication required") == true)
        }
    }

    @Test
    fun `SalaryTemplateRemoteDataSource throws when authenticated UID does not match requested userId`() = runTest {
        val firestore: FirebaseFirestore = mock()
        val auth: FirebaseAuth = mock()
        val mockUser: FirebaseUser = mock()
        whenever(mockUser.uid).thenReturn("authenticated-user-1")
        whenever(auth.currentUser).thenReturn(mockUser)

        val dataSource = FirestoreSalaryTemplateRemoteDataSource(firestore, auth)

        val template = SalaryTemplate(id = "tpl-1", userId = "victim-user-2", amountMinor = 5000L)

        try {
            dataSource.upsertSalaryTemplate(userId = "victim-user-2", template = template)
            fail("Expected IllegalArgumentException when authenticated UID does not match requested userId")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message?.contains("Access denied") == true)
        }
    }

    @Test
    fun `SalaryTemplateRemoteDataSource throws when model userId does not match path userId`() = runTest {
        val firestore: FirebaseFirestore = mock()
        val auth: FirebaseAuth = mock()
        val mockUser: FirebaseUser = mock()
        whenever(mockUser.uid).thenReturn("authenticated-user-1")
        whenever(auth.currentUser).thenReturn(mockUser)

        val dataSource = FirestoreSalaryTemplateRemoteDataSource(firestore, auth)

        // Path matches authenticated user, but template.userId is spoofed
        val template = SalaryTemplate(id = "tpl-1", userId = "other-user-3", amountMinor = 5000L)

        try {
            dataSource.upsertSalaryTemplate(userId = "authenticated-user-1", template = template)
            fail("Expected IllegalArgumentException when model.userId does not match path userId")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message?.contains("Cross-user write prevented") == true)
        }
    }
}

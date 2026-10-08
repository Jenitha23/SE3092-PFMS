package com.pfms.app.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Unit tests for [UserProfile] data class.
 *
 * UserProfile is what Firestore returns for the signed-in user's document.
 * Default values (baseCurrency, defaultPaymentMethod) must match what
 * UserProfileInitializer writes at registration.
 */
class UserProfileTest {

    @Test
    fun `default baseCurrency is LKR`() {
        val profile = UserProfile(uid = "uid-1", displayName = "Test", email = "t@t.com")
        assertEquals("LKR", profile.baseCurrency)
    }

    @Test
    fun `default payment method is null`() {
        val profile = UserProfile(uid = "uid-1", displayName = "Test", email = "t@t.com")
        assertNull(profile.defaultPaymentMethod)
    }

    @Test
    fun `custom payment method is preserved`() {
        val profile = UserProfile(
            uid = "uid-1",
            displayName = "Test",
            email = "t@t.com",
            defaultPaymentMethod = PaymentMethods.CARD
        )
        assertEquals(PaymentMethods.CARD, profile.defaultPaymentMethod)
    }

    @Test
    fun `all fields are stored correctly`() {
        val profile = UserProfile(
            uid = "uid-abc",
            displayName = "Kasun Silva",
            email = "kasun@mail.com",
            baseCurrency = "USD",
            defaultPaymentMethod = PaymentMethods.CASH
        )
        assertEquals("uid-abc", profile.uid)
        assertEquals("Kasun Silva", profile.displayName)
        assertEquals("kasun@mail.com", profile.email)
        assertEquals("USD", profile.baseCurrency)
        assertEquals(PaymentMethods.CASH, profile.defaultPaymentMethod)
    }

    @Test
    fun `two profiles with same data are equal`() {
        val a = UserProfile("uid-1", "A", "a@a.com")
        val b = UserProfile("uid-1", "A", "a@a.com")
        assertEquals(a, b)
    }
}

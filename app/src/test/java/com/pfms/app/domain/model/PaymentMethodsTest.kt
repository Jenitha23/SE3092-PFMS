package com.pfms.app.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for [PaymentMethods].
 *
 * Verifies constants and the "all" list that drives the Settings picker
 * and is enforced in Firestore security rules (FR-15).
 */
class PaymentMethodsTest {

    @Test
    fun `cash constant is correct`() {
        assertEquals("Cash", PaymentMethods.CASH)
    }

    @Test
    fun `card constant is correct`() {
        assertEquals("Card", PaymentMethods.CARD)
    }

    @Test
    fun `bank transfer constant is correct`() {
        assertEquals("Bank Transfer / Auto-debit", PaymentMethods.BANK_TRANSFER)
    }

    @Test
    fun `digital wallet constant is correct`() {
        assertEquals("Digital Wallet / Platform", PaymentMethods.DIGITAL_WALLET)
    }

    @Test
    fun `all list contains exactly four methods`() {
        assertEquals(4, PaymentMethods.all.size)
    }

    @Test
    fun `all list contains every defined method`() {
        assertTrue(PaymentMethods.all.contains(PaymentMethods.CASH))
        assertTrue(PaymentMethods.all.contains(PaymentMethods.CARD))
        assertTrue(PaymentMethods.all.contains(PaymentMethods.BANK_TRANSFER))
        assertTrue(PaymentMethods.all.contains(PaymentMethods.DIGITAL_WALLET))
    }

    @Test
    fun `all list has no duplicates`() {
        assertEquals(PaymentMethods.all.size, PaymentMethods.all.distinct().size)
    }
}

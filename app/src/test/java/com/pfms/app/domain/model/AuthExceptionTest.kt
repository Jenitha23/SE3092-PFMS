package com.pfms.app.domain.model

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Unit tests for [AuthException].
 *
 * AuthException is the domain error type shown to the user — it must carry
 * a plain-language message (FR-02, FR-36) and a machine-readable type.
 */
class AuthExceptionTest {

    @Test
    fun `message is preserved`() {
        val ex = AuthException("Something went wrong.")
        assertEquals("Something went wrong.", ex.message)
    }

    @Test
    fun `default type is UNKNOWN`() {
        val ex = AuthException("error")
        assertEquals(AuthErrorType.UNKNOWN, ex.type)
    }

    @Test
    fun `explicit type is preserved`() {
        val ex = AuthException("offline", AuthErrorType.NETWORK)
        assertEquals(AuthErrorType.NETWORK, ex.type)
    }

    @Test
    fun `is a subclass of Exception`() {
        val ex: Exception = AuthException("test")
        assertEquals("test", ex.message)
    }
}

package com.pfms.app.domain.validation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Unit tests for [AuthFieldErrors] computed properties.
 */
class AuthFieldErrorsTest {

    @Test
    fun `no errors reports hasErrors false`() {
        val errors = AuthFieldErrors()
        assertEquals(false, errors.hasErrors)
    }

    @Test
    fun `displayName error alone reports hasErrors true`() {
        val errors = AuthFieldErrors(displayName = "Name required")
        assertEquals(true, errors.hasErrors)
    }

    @Test
    fun `email error alone reports hasErrors true`() {
        val errors = AuthFieldErrors(email = "Email required")
        assertEquals(true, errors.hasErrors)
    }

    @Test
    fun `password error alone reports hasErrors true`() {
        val errors = AuthFieldErrors(password = "Password required")
        assertEquals(true, errors.hasErrors)
    }

    @Test
    fun `first returns displayName when all three are set`() {
        val errors = AuthFieldErrors(
            displayName = "Name error",
            email = "Email error",
            password = "Password error"
        )
        assertEquals("Name error", errors.first)
    }

    @Test
    fun `first returns email when displayName is null`() {
        val errors = AuthFieldErrors(email = "Email error", password = "Password error")
        assertEquals("Email error", errors.first)
    }

    @Test
    fun `first returns password when only password is set`() {
        val errors = AuthFieldErrors(password = "Password error")
        assertEquals("Password error", errors.first)
    }

    @Test
    fun `first returns null when no errors`() {
        assertNull(AuthFieldErrors().first)
    }
}

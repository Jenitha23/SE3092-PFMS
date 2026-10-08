package com.pfms.app.viewmodel

import com.pfms.app.domain.validation.AuthFieldErrors
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Unit tests for [AuthUiState].
 *
 * AuthUiState drives the login/register/forgot-password screens. Its defaults
 * must be safe (not loading, no messages) so screens start in a clean state.
 */
class AuthUiStateTest {

    @Test
    fun `default state is not loading`() {
        val state = AuthUiState()
        assertEquals(false, state.isLoading)
    }

    @Test
    fun `default state has no error message`() {
        assertNull(AuthUiState().errorMessage)
    }

    @Test
    fun `default state has no info message`() {
        assertNull(AuthUiState().infoMessage)
    }

    @Test
    fun `default state has no field errors`() {
        val state = AuthUiState()
        assertEquals(false, state.fieldErrors.hasErrors)
    }

    @Test
    fun `loading state is set correctly`() {
        val state = AuthUiState(isLoading = true)
        assertEquals(true, state.isLoading)
    }

    @Test
    fun `error message is preserved`() {
        val state = AuthUiState(errorMessage = "Invalid email or password.")
        assertEquals("Invalid email or password.", state.errorMessage)
    }

    @Test
    fun `info message is preserved`() {
        val state = AuthUiState(infoMessage = "Reset link sent.")
        assertEquals("Reset link sent.", state.infoMessage)
    }

    @Test
    fun `field errors are preserved`() {
        val errors = AuthFieldErrors(email = "Required")
        val state = AuthUiState(fieldErrors = errors)
        assertEquals("Required", state.fieldErrors.email)
    }
}

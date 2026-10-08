package com.pfms.app.domain.model

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Unit tests for [AuthUser] computed properties.
 *
 * AuthUser is a plain data class with no Android dependencies, so it runs
 * as a fast local JUnit test — exactly the pattern the lab advocates.
 */
class AuthUserTest {

    // ------------------------------------------------------------------ canChangePassword

    @Test
    fun `password-only account can change password`() {
        val user = makeUser(providerIds = listOf(AuthUser.PASSWORD_PROVIDER))
        assertEquals(true, user.canChangePassword)
    }

    @Test
    fun `google-only account cannot change password`() {
        val user = makeUser(providerIds = listOf(AuthUser.GOOGLE_PROVIDER))
        assertEquals(false, user.canChangePassword)
    }

    @Test
    fun `linked account with password and google can change password`() {
        val user = makeUser(providerIds = listOf(AuthUser.PASSWORD_PROVIDER, AuthUser.GOOGLE_PROVIDER))
        assertEquals(true, user.canChangePassword)
    }

    @Test
    fun `account with empty providers cannot change password`() {
        val user = makeUser(providerIds = emptyList())
        assertEquals(false, user.canChangePassword)
    }

    // ------------------------------------------------------------------ isGoogleAccount

    @Test
    fun `google-only account is google account`() {
        val user = makeUser(providerIds = listOf(AuthUser.GOOGLE_PROVIDER))
        assertEquals(true, user.isGoogleAccount)
    }

    @Test
    fun `password-only account is not google account`() {
        val user = makeUser(providerIds = listOf(AuthUser.PASSWORD_PROVIDER))
        assertEquals(false, user.isGoogleAccount)
    }

    @Test
    fun `linked account is also a google account`() {
        val user = makeUser(providerIds = listOf(AuthUser.PASSWORD_PROVIDER, AuthUser.GOOGLE_PROVIDER))
        assertEquals(true, user.isGoogleAccount)
    }

    // ------------------------------------------------------------------ companion constants

    @Test
    fun `password provider constant is correct`() {
        assertEquals("password", AuthUser.PASSWORD_PROVIDER)
    }

    @Test
    fun `google provider constant is correct`() {
        assertEquals("google.com", AuthUser.GOOGLE_PROVIDER)
    }

    // ------------------------------------------------------------------ helper

    private fun makeUser(providerIds: List<String>) = AuthUser(
        uid = "test-uid",
        displayName = "Test User",
        email = "test@example.com",
        isEmailVerified = true,
        providerIds = providerIds
    )
}

package com.pfms.app.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for [ReauthCredential] sealed interface.
 *
 * ReauthCredential is used for sensitive actions (change password, delete account,
 * biometric unlock fallback). It must correctly hold the credential value.
 */
class ReauthCredentialTest {

    @Test
    fun `Password credential holds the password`() {
        val cred = ReauthCredential.Password("mySecret123")
        assertEquals("mySecret123", cred.value)
    }

    @Test
    fun `Google credential holds the id token`() {
        val cred = ReauthCredential.Google("google-id-token-xyz")
        assertEquals("google-id-token-xyz", cred.idToken)
    }

    @Test
    fun `Password credential is a ReauthCredential`() {
        val cred: ReauthCredential = ReauthCredential.Password("pass")
        assertTrue(cred is ReauthCredential.Password)
    }

    @Test
    fun `Google credential is a ReauthCredential`() {
        val cred: ReauthCredential = ReauthCredential.Google("token")
        assertTrue(cred is ReauthCredential.Google)
    }

    @Test
    fun `two Password credentials with same value are equal`() {
        val a = ReauthCredential.Password("same")
        val b = ReauthCredential.Password("same")
        assertEquals(a, b)
    }

    @Test
    fun `two Google credentials with same token are equal`() {
        val a = ReauthCredential.Google("token")
        val b = ReauthCredential.Google("token")
        assertEquals(a, b)
    }
}

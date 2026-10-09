package com.pfms.app.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for [SessionState] sealed interface.
 *
 * Ensures the sealed subtypes hold the correct data and can be matched correctly.
 * SessionState drives the navigation graph's start destination (FR-02).
 */
class SessionStateTest {

    @Test
    fun `Loading is a SessionState`() {
        val state: SessionState = SessionState.Loading
        assertTrue(state is SessionState.Loading)
    }

    @Test
    fun `Unauthenticated is a SessionState`() {
        val state: SessionState = SessionState.Unauthenticated
        assertTrue(state is SessionState.Unauthenticated)
    }

    @Test
    fun `Authenticated wraps the AuthUser`() {
        val user = AuthUser(
            uid = "uid-1",
            displayName = "Test",
            email = "test@mail.com",
            isEmailVerified = true,
            providerIds = listOf("password")
        )
        val state = SessionState.Authenticated(user)

        assertTrue(state is SessionState.Authenticated)
        assertEquals("uid-1", state.user.uid)
        assertEquals("Test", state.user.displayName)
    }

    @Test
    fun `two Authenticated states with different users are not equal`() {
        val user1 = AuthUser("uid-1", "A", "a@test.com", true)
        val user2 = AuthUser("uid-2", "B", "b@test.com", false)

        assertTrue(SessionState.Authenticated(user1) != SessionState.Authenticated(user2))
    }
}

package com.pfms.app.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for [LogoutResult] sealed class.
 *
 * LogoutResult drives the UI after a logout attempt (FR-05):
 * Success → navigate to login, UnsynchronizedDataWarning → show dialog, Failure → show error.
 */
class LogoutResultTest {

    @Test
    fun `Success is a LogoutResult`() {
        val result: LogoutResult = LogoutResult.Success
        assertTrue(result is LogoutResult.Success)
    }

    @Test
    fun `UnsynchronizedDataWarning is a LogoutResult`() {
        val result: LogoutResult = LogoutResult.UnsynchronizedDataWarning
        assertTrue(result is LogoutResult.UnsynchronizedDataWarning)
    }

    @Test
    fun `Failure carries error message`() {
        val result = LogoutResult.Failure("Network unavailable")
        assertEquals("Network unavailable", result.message)
    }

    @Test
    fun `two Failures with different messages are not equal`() {
        val a = LogoutResult.Failure("Error A")
        val b = LogoutResult.Failure("Error B")
        assertTrue(a != b)
    }

    @Test
    fun `two Failures with same message are equal`() {
        val a = LogoutResult.Failure("Same error")
        val b = LogoutResult.Failure("Same error")
        assertEquals(a, b)
    }
}

package com.pfms.app.domain.validation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Unit tests for [AuthValidator].
 *
 * Each test exercises exactly one validation rule so that a failure name-points
 * to the exact branch that broke — following the lab methodology of "keep your
 * tests small and focused: each test should exercise a single rule".
 */
class AuthValidatorTest {

    // ------------------------------------------------------------------ validateDisplayName

    @Test
    fun `blank display name is rejected`() {
        val error = AuthValidator.validateDisplayName("   ")
        assertEquals("Please enter your name.", error)
    }

    @Test
    fun `empty display name is rejected`() {
        val error = AuthValidator.validateDisplayName("")
        assertEquals("Please enter your name.", error)
    }

    @Test
    fun `display name at max length passes`() {
        val name = "A".repeat(AuthValidator.MAX_DISPLAY_NAME_LENGTH)
        assertNull(AuthValidator.validateDisplayName(name))
    }

    @Test
    fun `display name exceeding max length is rejected`() {
        val name = "A".repeat(AuthValidator.MAX_DISPLAY_NAME_LENGTH + 1)
        assertEquals(
            "Name must be ${AuthValidator.MAX_DISPLAY_NAME_LENGTH} characters or fewer.",
            AuthValidator.validateDisplayName(name)
        )
    }

    @Test
    fun `valid display name passes`() {
        assertNull(AuthValidator.validateDisplayName("Kasun Silva"))
    }

    // ------------------------------------------------------------------ validateEmail

    @Test
    fun `blank email is rejected`() {
        val error = AuthValidator.validateEmail("   ")
        assertEquals("Please enter your email address.", error)
    }

    @Test
    fun `empty email is rejected`() {
        val error = AuthValidator.validateEmail("")
        assertEquals("Please enter your email address.", error)
    }

    @Test
    fun `email without at symbol is rejected`() {
        val error = AuthValidator.validateEmail("kasungmail.com")
        assertEquals("Please enter a valid email address.", error)
    }

    @Test
    fun `email without domain extension is rejected`() {
        val error = AuthValidator.validateEmail("kasun@gmail")
        assertEquals("Please enter a valid email address.", error)
    }

    @Test
    fun `email with single char domain extension is rejected`() {
        val error = AuthValidator.validateEmail("kasun@gmail.c")
        assertEquals("Please enter a valid email address.", error)
    }

    @Test
    fun `valid email passes`() {
        assertNull(AuthValidator.validateEmail("kasun@gmail.com"))
    }

    @Test
    fun `valid email with plus alias passes`() {
        assertNull(AuthValidator.validateEmail("kasun+test@gmail.com"))
    }

    // ------------------------------------------------------------------ validateNewPassword

    @Test
    fun `password shorter than minimum is rejected`() {
        val error = AuthValidator.validateNewPassword("Ab1234")
        assertEquals(
            "Password must be at least ${AuthValidator.MIN_PASSWORD_LENGTH} characters.",
            error
        )
    }

    @Test
    fun `empty password is rejected`() {
        val error = AuthValidator.validateNewPassword("")
        assertEquals(
            "Password must be at least ${AuthValidator.MIN_PASSWORD_LENGTH} characters.",
            error
        )
    }

    @Test
    fun `password at exactly minimum length passes`() {
        val password = "A".repeat(AuthValidator.MIN_PASSWORD_LENGTH)
        assertNull(AuthValidator.validateNewPassword(password))
    }

    @Test
    fun `password longer than minimum passes`() {
        assertNull(AuthValidator.validateNewPassword("VerySecurePassword123"))
    }

    // ------------------------------------------------------------------ validateExistingPassword

    @Test
    fun `empty existing password is rejected`() {
        val error = AuthValidator.validateExistingPassword("")
        assertEquals("Please enter your password.", error)
    }

    @Test
    fun `non-empty existing password passes`() {
        assertNull(AuthValidator.validateExistingPassword("x"))
    }

    // ------------------------------------------------------------------ validateRegistration

    @Test
    fun `registration with all blank fields has errors`() {
        val errors = AuthValidator.validateRegistration("", "", "")
        assertEquals(true, errors.hasErrors)
    }

    @Test
    fun `registration with blank name only reports name error`() {
        val errors = AuthValidator.validateRegistration("", "kasun@gmail.com", "password123")
        assertEquals("Please enter your name.", errors.displayName)
    }

    @Test
    fun `registration with invalid email only reports email error`() {
        val errors = AuthValidator.validateRegistration("Kasun", "bad-email", "password123")
        assertNull(errors.displayName)
        assertEquals("Please enter a valid email address.", errors.email)
    }

    @Test
    fun `registration with short password only reports password error`() {
        val errors = AuthValidator.validateRegistration("Kasun", "kasun@gmail.com", "abc")
        assertNull(errors.displayName)
        assertNull(errors.email)
        assertEquals(
            "Password must be at least ${AuthValidator.MIN_PASSWORD_LENGTH} characters.",
            errors.password
        )
    }

    @Test
    fun `valid registration has no errors`() {
        val errors = AuthValidator.validateRegistration("Kasun", "kasun@gmail.com", "password123")
        assertEquals(false, errors.hasErrors)
    }

    // ------------------------------------------------------------------ validateLogin

    @Test
    fun `login with blank email reports email error`() {
        val errors = AuthValidator.validateLogin("", "password123")
        assertEquals("Please enter your email address.", errors.email)
    }

    @Test
    fun `login with empty password reports password error`() {
        val errors = AuthValidator.validateLogin("kasun@gmail.com", "")
        assertNull(errors.email)
        assertEquals("Please enter your password.", errors.password)
    }

    @Test
    fun `valid login has no errors`() {
        val errors = AuthValidator.validateLogin("kasun@gmail.com", "password123")
        assertEquals(false, errors.hasErrors)
    }
}

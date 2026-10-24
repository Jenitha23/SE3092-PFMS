package com.pfms.app.domain.validation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PasswordStrengthTest {
    @Test
    fun `short complex passwords are still too short`() {
        assertEquals(PasswordStrength.WEAK, PasswordStrength.evaluate("Aa1!"))
    }

    @Test
    fun `strength guidance does not change registration requirements`() {
        assertEquals(PasswordStrength.FAIR, PasswordStrength.evaluate("abcdefgh"))
        assertNull(AuthValidator.validateNewPassword("abcdefgh"))
        assertEquals(PasswordStrength.STRONG, PasswordStrength.evaluate("Abcdef1!"))
        assertEquals(PasswordStrength.STRONG, PasswordStrength.evaluate("Abcdefghijk1!"))
    }

    @Test
    fun `whitespace is not counted as a symbol`() {
        assertEquals(PasswordStrength.GOOD, PasswordStrength.evaluate("Abcdef1 "))
        assertEquals(PasswordStrength.EMPTY, PasswordStrength.evaluate(""))
    }

    @Test
    fun `strong passwords fill all four bars starting at eight characters`() {
        listOf("Abcdef1!", "Abcdef12!", "Abcdefghijk1!").forEach { password ->
            assertEquals(4, PasswordStrength.evaluate(password).segments)
        }
    }

    @Test
    fun `length alone cannot make a password strong`() {
        assertEquals(PasswordStrength.FAIR, PasswordStrength.evaluate("abcdefghijklmnop"))
        assertEquals(PasswordStrength.GOOD, PasswordStrength.evaluate("Abcdef12"))
        assertEquals(PasswordStrength.GOOD, PasswordStrength.evaluate("Abcdefg!"))
        assertEquals(PasswordStrength.WEAK, PasswordStrength.evaluate("Abcde1!"))
    }
}

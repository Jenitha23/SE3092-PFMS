package com.pfms.app.domain.validation

/** Guidance only; registration still follows AuthValidator's existing password rules. */
enum class PasswordStrength(val segments: Int) {
    EMPTY(0), WEAK(1), FAIR(2), GOOD(3), STRONG(4);

    companion object {
        fun evaluate(password: String): PasswordStrength {
            if (password.isEmpty()) return EMPTY
            if (password.length < AuthValidator.MIN_PASSWORD_LENGTH) return WEAK
            val hasLetters = password.any(Char::isLetter)
            val hasNumbers = password.any(Char::isDigit)
            val hasSymbols = password.any { !it.isLetterOrDigit() && !it.isWhitespace() }
            return when {
                hasLetters && hasNumbers && hasSymbols -> STRONG
                listOf(hasLetters, hasNumbers, hasSymbols).count { it } >= 2 -> GOOD
                else -> FAIR
            }
        }
    }
}

package com.pfms.app.ui.theme

/**
 * Supported application theme modes for PFMS.
 *
 * - [SYSTEM]: Follows the Android operating system dark / light mode.
 * - [LIGHT]: Forces the PFMS light fintech theme.
 * - [DARK]: Forces the PFMS dark fintech theme.
 */
enum class AppThemeMode {
    SYSTEM,
    LIGHT,
    DARK;

    fun getDisplayName(): String = when (this) {
        SYSTEM -> "System Default"
        LIGHT -> "Light"
        DARK -> "Dark"
    }
}

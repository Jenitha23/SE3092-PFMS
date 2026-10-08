package com.pfms.app.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

private val DarkColorScheme = darkColorScheme(
    primary = EmeraldPrimaryAccentDark,
    onPrimary = Color(0xFF00382B),
    primaryContainer = EmeraldPrimaryDark,
    onPrimaryContainer = EmeraldPrimaryLight,
    secondary = DarkTextSecondary,
    onSecondary = DarkNavyText,
    secondaryContainer = DarkCharcoalSurface,
    onSecondaryContainer = DarkTextPrimary,
    tertiary = Color(0xFFBCA1F8),
    onTertiary = Color(0xFF381478),
    background = DeepNavyBackground,
    onBackground = DarkTextPrimary,
    surface = DarkCharcoalSurface,
    onSurface = DarkTextPrimary,
    surfaceVariant = DarkMutedSurface,
    onSurfaceVariant = DarkTextSecondary,
    outline = Color(0xFF8692A3),
    outlineVariant = DarkBorderOutline,
    error = Color(0xFFFFB2BF),
    onError = Color(0xFF680017),
    errorContainer = Color(0xFF8E1734),
    onErrorContainer = Color(0xFFFFD9DF)
)

private val LightColorScheme = lightColorScheme(
    primary = EmeraldPrimary,
    onPrimary = Color.White,
    primaryContainer = EmeraldPrimaryLight,
    onPrimaryContainer = EmeraldPrimaryDark,
    secondary = DarkNavyText,
    onSecondary = Color.White,
    secondaryContainer = MutedSurface,
    onSecondaryContainer = DarkNavyText,
    tertiary = PurpleAccent,
    onTertiary = Color.White,
    background = AppBackground,
    onBackground = DarkNavyText,
    surface = SurfaceCard,
    onSurface = DarkNavyText,
    surfaceVariant = MutedSurface,
    onSurfaceVariant = SecondaryText,
    outline = SecondaryText,
    outlineVariant = BorderOutline,
    error = ErrorExpense,
    onError = Color.White,
    errorContainer = Color(0xFFFFE8EC),
    onErrorContainer = Color(0xFF680017)
)

/**
 * PFMS Theme wrapper supporting [AppThemeMode] (System Default, Light, Dark).
 */
@Composable
fun PFMSTheme(
    themeMode: AppThemeMode = AppThemeMode.SYSTEM,
    darkTheme: Boolean = when (themeMode) {
        AppThemeMode.SYSTEM -> isSystemInDarkTheme()
        AppThemeMode.LIGHT -> false
        AppThemeMode.DARK -> true
    },
    // Keep dynamicColor disabled to preserve the fintech brand emerald & navy styling
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }

        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
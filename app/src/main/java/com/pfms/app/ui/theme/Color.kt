package com.pfms.app.ui.theme

import androidx.compose.ui.graphics.Color

// ============================================================================
// PFMS Modern Fintech Color Palette
// ============================================================================

// Primary Brand Colors (Emerald & Teal)
val EmeraldPrimary = Color(0xFF169C7B)
val EmeraldPrimaryDark = Color(0xFF0E7C67)
val EmeraldPrimaryLight = Color(0xFFDDF5EE)
val EmeraldPrimaryAccentDark = Color(0xFF2ED1A2)

// Neutrals & Surfaces (Light Theme)
val DarkNavyText = Color(0xFF10213A)
val SecondaryText = Color(0xFF667085)
val AppBackground = Color(0xFFF7F9FC)
val SurfaceCard = Color(0xFFFFFFFF)
val BorderOutline = Color(0xFFE3E8EF)
val MutedSurface = Color(0xFFF1F4F8)

// Semantic Accents
val SuccessEmerald = Color(0xFF169C7B)
val ErrorExpense = Color(0xFFE84C6A)
val PurpleAccent = Color(0xFF7A4DD8)
val OrangeAccent = Color(0xFFF5A340)
val BlueAccent = Color(0xFF3C91E6)

// Dark Theme Surfaces & Accents
val DeepNavyBackground = Color(0xFF0B1320)
val DarkCharcoalSurface = Color(0xFF121D2C)
val DarkMutedSurface = Color(0xFF1A2638)
val DarkBorderOutline = Color(0xFF243247)
val DarkTextPrimary = Color(0xFFF0F4F8)
val DarkTextSecondary = Color(0xFF9EAAB9)

// ============================================================================
// Backward-Compatibility Aliases
// ============================================================================
val NavyPrimary = EmeraldPrimary
val NavySecondary = DarkNavyText
val NavyTertiary = PurpleAccent
val NavyContainer = EmeraldPrimaryLight
val OnNavyContainer = EmeraldPrimaryDark

val NavyPrimaryDark = EmeraldPrimaryAccentDark
val NavySecondaryDark = DarkTextSecondary
val NavyTertiaryDark = Color(0xFFBCA1F8)
val NavyContainerDark = EmeraldPrimaryDark
val OnNavyContainerDark = EmeraldPrimaryLight

val Purple80 = EmeraldPrimaryAccentDark
val PurpleGrey80 = DarkTextSecondary
val Pink80 = ErrorExpense
val Purple40 = EmeraldPrimary
val PurpleGrey40 = SecondaryText
val Pink40 = ErrorExpense
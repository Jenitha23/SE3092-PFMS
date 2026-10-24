package com.pfms.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

val VaultGreen = Color(0xFF0A3025)
val VaultDeep = Color(0xFF102A22)
val VaultMint = Color(0xFF59DFB1)
val VaultMuted = Color(0xFF9ABDB0)
val CashflowRed = Color(0xFFFF6E88)

private val FinanceTypography = Typography(
    headlineLarge = TextStyle(fontFamily = PlusJakartaSans, fontWeight = FontWeight.Bold, fontSize = 30.sp, lineHeight = 38.sp, letterSpacing = (-1).sp),
    headlineSmall = TextStyle(fontFamily = PlusJakartaSans, fontWeight = FontWeight.Bold, fontSize = 22.sp, lineHeight = 30.sp),
    titleLarge = TextStyle(fontFamily = PlusJakartaSans, fontWeight = FontWeight.Bold, fontSize = 19.sp, lineHeight = 26.sp),
    titleMedium = TextStyle(fontFamily = PlusJakartaSans, fontWeight = FontWeight.Bold, fontSize = 15.sp, lineHeight = 22.sp),
    titleSmall = TextStyle(fontFamily = PlusJakartaSans, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, lineHeight = 19.sp),
    bodyLarge = TextStyle(fontFamily = PlusJakartaSans, fontSize = 15.sp, lineHeight = 22.sp),
    bodyMedium = TextStyle(fontFamily = PlusJakartaSans, fontSize = 13.sp, lineHeight = 20.sp),
    bodySmall = TextStyle(fontFamily = PlusJakartaSans, fontSize = 11.sp, lineHeight = 17.sp),
    labelLarge = TextStyle(fontFamily = PlusJakartaSans, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, lineHeight = 20.sp),
    labelMedium = TextStyle(fontFamily = PlusJakartaSans, fontWeight = FontWeight.SemiBold, fontSize = 11.sp, lineHeight = 16.sp),
    labelSmall = TextStyle(fontFamily = PlusJakartaSans, fontWeight = FontWeight.Medium, fontSize = 10.sp, lineHeight = 15.sp)
)

@Composable
fun FinanceTheme(content: @Composable () -> Unit) {
    val current = MaterialTheme.colorScheme
    val colors = if (current.background.luminance() > 0.5f) current.copy(
        primary = Color(0xFF008660), onPrimary = Color.White,
        primaryContainer = Color(0xFFECFDF5), onPrimaryContainer = VaultGreen,
        background = Color(0xFFF3F6F7), surface = Color.White,
        onSurface = Color(0xFF152821), onBackground = Color(0xFF152821),
        surfaceVariant = Color(0xFFF3F7F8), onSurfaceVariant = Color(0xFF647F75),
        outlineVariant = Color(0xFFE4ECE9), error = Color(0xFFE34361)
    ) else current.copy(primary = VaultMint)
    MaterialTheme(colorScheme = colors, typography = FinanceTypography, content = content)
}

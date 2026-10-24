package com.pfms.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.pfms.app.R

@OptIn(androidx.compose.ui.text.ExperimentalTextApi::class)
val PlusJakartaSans = FontFamily(
    listOf(FontWeight.Normal, FontWeight.Medium, FontWeight.SemiBold, FontWeight.Bold).map { weight ->
        Font(R.font.plus_jakarta_sans, weight = weight,
            variationSettings = FontVariation.Settings(FontVariation.weight(weight.weight)))
    }
)

private val AuthTypography = Typography(
    headlineLarge = TextStyle(fontFamily = PlusJakartaSans, fontWeight = FontWeight.Bold,
        fontSize = 28.sp, lineHeight = 36.sp, letterSpacing = (-1).sp),
    titleLarge = TextStyle(fontFamily = PlusJakartaSans, fontWeight = FontWeight.Bold,
        fontSize = 20.sp, lineHeight = 28.sp, letterSpacing = (-0.7).sp),
    bodyLarge = TextStyle(fontFamily = PlusJakartaSans, fontSize = 15.sp, lineHeight = 21.sp),
    bodyMedium = TextStyle(fontFamily = PlusJakartaSans, fontSize = 13.sp, lineHeight = 19.sp),
    bodySmall = TextStyle(fontFamily = PlusJakartaSans, fontSize = 11.sp, lineHeight = 17.sp),
    labelLarge = TextStyle(fontFamily = PlusJakartaSans, fontWeight = FontWeight.SemiBold,
        fontSize = 15.sp, lineHeight = 22.sp),
    labelMedium = TextStyle(fontFamily = PlusJakartaSans, fontWeight = FontWeight.SemiBold,
        fontSize = 12.sp, lineHeight = 18.sp)
)

/** Scoped to authentication while the rest of the app is redesigned screen by screen. */
@Composable
fun AuthTheme(content: @Composable () -> Unit) {
    val current = MaterialTheme.colorScheme
    val isLight = current.background.luminance() > 0.5f
    val colors = if (isLight) current.copy(
        primary = Color(0xFF006B50), onPrimary = Color.White,
        primaryContainer = Color(0xFFD1F2E6), onPrimaryContainer = Color(0xFF0D3B36),
        background = Color(0xFFF8F9FF), onBackground = Color(0xFF142031),
        surface = Color.White, onSurface = Color(0xFF142031),
        surfaceVariant = Color(0xFFF0F3FF), onSurfaceVariant = Color(0xFF424F48),
        secondaryContainer = Color(0xFFE7EEFF), outline = Color(0xFF738178),
        outlineVariant = Color(0xFFDDE5F9)
    ) else current
    MaterialTheme(colorScheme = colors, typography = AuthTypography, content = content)
}

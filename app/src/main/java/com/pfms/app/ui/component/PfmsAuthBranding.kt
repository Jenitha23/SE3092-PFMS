package com.pfms.app.ui.component

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pfms.app.ui.theme.EmeraldPrimary

/**
 * 3-bar rising financial graph logo from the reference UI.
 */
@Composable
fun PfmsLogo(
    modifier: Modifier = Modifier,
    barColor: Color = EmeraldPrimary
) {
    Canvas(modifier = modifier.size(width = 46.dp, height = 36.dp)) {
        val totalWidth = size.width
        val totalHeight = size.height
        val barWidth = totalWidth * 0.22f
        val cornerRadius = CornerRadius(barWidth / 2, barWidth / 2)

        // Bar 1 (Shortest - left): Medium Emerald
        drawRoundRect(
            color = if (barColor == EmeraldPrimary) Color(0xFF169C7B) else barColor,
            topLeft = Offset(x = totalWidth * 0.10f, y = totalHeight * 0.48f),
            size = Size(width = barWidth, height = totalHeight * 0.52f),
            cornerRadius = cornerRadius
        )

        // Bar 2 (Medium - center): Light Emerald / Mint
        drawRoundRect(
            color = if (barColor == EmeraldPrimary) Color(0xFF2ED1A2) else barColor.copy(alpha = 0.8f),
            topLeft = Offset(x = totalWidth * 0.39f, y = totalHeight * 0.24f),
            size = Size(width = barWidth, height = totalHeight * 0.76f),
            cornerRadius = cornerRadius
        )

        // Bar 3 (Tallest - right): Deep Emerald
        drawRoundRect(
            color = if (barColor == EmeraldPrimary) Color(0xFF0E7C67) else barColor,
            topLeft = Offset(x = totalWidth * 0.68f, y = 0f),
            size = Size(width = barWidth, height = totalHeight),
            cornerRadius = cornerRadius
        )
    }
}

/**
 * Authentic 4-color Google "G" logo vector.
 */
@Composable
fun GoogleLogo(
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier.size(20.dp)) {
        val strokeW = size.width * 0.22f
        val radius = (size.width - strokeW) / 2
        val center = Offset(size.width / 2, size.height / 2)

        // Red arc (top)
        drawArc(
            color = Color(0xFFEA4335),
            startAngle = 180f,
            sweepAngle = 140f,
            useCenter = false,
            topLeft = Offset(strokeW / 2, strokeW / 2),
            size = Size(size.width - strokeW, size.height - strokeW),
            style = Stroke(width = strokeW)
        )

        // Yellow arc (top left to bottom left)
        drawArc(
            color = Color(0xFFFBBC05),
            startAngle = 120f,
            sweepAngle = 90f,
            useCenter = false,
            topLeft = Offset(strokeW / 2, strokeW / 2),
            size = Size(size.width - strokeW, size.height - strokeW),
            style = Stroke(width = strokeW)
        )

        // Green arc (bottom)
        drawArc(
            color = Color(0xFF34A853),
            startAngle = 40f,
            sweepAngle = 100f,
            useCenter = false,
            topLeft = Offset(strokeW / 2, strokeW / 2),
            size = Size(size.width - strokeW, size.height - strokeW),
            style = Stroke(width = strokeW)
        )

        // Blue arc & center horizontal bar (right)
        drawArc(
            color = Color(0xFF4285F4),
            startAngle = -45f,
            sweepAngle = 85f,
            useCenter = false,
            topLeft = Offset(strokeW / 2, strokeW / 2),
            size = Size(size.width - strokeW, size.height - strokeW),
            style = Stroke(width = strokeW)
        )

        // Horizontal crossbar
        drawLine(
            color = Color(0xFF4285F4),
            start = Offset(center.x, center.y),
            end = Offset(size.width - strokeW / 2, center.y),
            strokeWidth = strokeW
        )
    }
}

/**
 * Clean vector illustration matching the reference UI illustration:
 * Person working at a desk with laptop, green backdrop, and speech bubble "Small steps. Big goals."
 */
@Composable
fun FinanceIllustration(
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(148.dp),
        contentAlignment = Alignment.Center
    ) {
        // Soft mint/teal circular backdrop
        Surface(
            modifier = Modifier.size(136.dp),
            shape = CircleShape,
            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
        ) {}

        // Canvas artwork for desk, laptop, plant, and character silhouette
        Canvas(
            modifier = Modifier.size(width = 170.dp, height = 125.dp)
        ) {
            val w = size.width
            val h = size.height

            // Background plant leaves (left)
            drawCircle(
                color = Color(0xFF2ED1A2).copy(alpha = 0.5f),
                radius = 16f,
                center = Offset(w * 0.18f, h * 0.45f)
            )
            drawCircle(
                color = Color(0xFF169C7B).copy(alpha = 0.6f),
                radius = 14f,
                center = Offset(w * 0.14f, h * 0.58f)
            )

            // Desk surface line
            drawLine(
                color = Color(0xFF10213A).copy(alpha = 0.2f),
                start = Offset(w * 0.08f, h * 0.88f),
                end = Offset(w * 0.92f, h * 0.88f),
                strokeWidth = 3f
            )

            // Character body (dark sweater)
            val bodyPath = Path().apply {
                moveTo(w * 0.28f, h * 0.88f)
                cubicTo(w * 0.30f, h * 0.52f, w * 0.52f, h * 0.52f, w * 0.56f, h * 0.88f)
                close()
            }
            drawPath(path = bodyPath, color = Color(0xFF133E35))

            // Character head
            drawCircle(
                color = Color(0xFFF6C8A4),
                radius = 15f,
                center = Offset(w * 0.42f, h * 0.42f)
            )

            // Character hair
            drawArc(
                color = Color(0xFF10213A),
                startAngle = 170f,
                sweepAngle = 190f,
                useCenter = true,
                topLeft = Offset(w * 0.34f, h * 0.28f),
                size = Size(30f, 26f)
            )

            // Coffee cup on desk
            drawRoundRect(
                color = Color.White,
                topLeft = Offset(w * 0.38f, h * 0.76f),
                size = Size(10f, 13f),
                cornerRadius = CornerRadius(2f, 2f)
            )

            // Laptop base & screen (open at desk)
            val laptopScreen = Path().apply {
                moveTo(w * 0.52f, h * 0.62f)
                lineTo(w * 0.72f, h * 0.60f)
                lineTo(w * 0.70f, h * 0.85f)
                lineTo(w * 0.50f, h * 0.85f)
                close()
            }
            drawPath(path = laptopScreen, color = Color(0xFFCBD5E1))
            drawCircle(
                color = Color.White,
                radius = 3f,
                center = Offset(w * 0.61f, h * 0.72f)
            )
        }

        // Speech bubble: "Small steps. Big goals."
        Surface(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(end = 12.dp, top = 8.dp),
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surface,
            shadowElevation = 2.dp
        ) {
            Text(
                text = "Small\nsteps. Big\ngoals.",
                style = MaterialTheme.typography.labelSmall.copy(
                    fontWeight = FontWeight.Bold,
                    lineHeight = 13.sp
                ),
                color = EmeraldPrimary,
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
            )
        }
    }
}

enum class AuthTab {
    LOGIN,
    SIGN_UP
}

/**
 * Segmented Tab Selector matching Reference UI 1:
 * Full-width rounded card with active white segment and emerald underline indicator.
 */
@Composable
fun PfmsSegmentedAuthTab(
    selectedTab: AuthTab,
    onTabSelected: (AuthTab) -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .height(50.dp),
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Log In Tab
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(11.dp))
                    .then(
                        if (selectedTab == AuthTab.LOGIN) {
                            Modifier.background(MaterialTheme.colorScheme.surface)
                        } else {
                            Modifier.clickable { onTabSelected(AuthTab.LOGIN) }
                        }
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Log In",
                    style = MaterialTheme.typography.titleSmall.copy(
                        fontWeight = if (selectedTab == AuthTab.LOGIN) FontWeight.Bold else FontWeight.Medium
                    ),
                    color = if (selectedTab == AuthTab.LOGIN) {
                        EmeraldPrimary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    }
                )

                // Active green bottom underline indicator
                if (selectedTab == AuthTab.LOGIN) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .fillMaxWidth(0.55f)
                            .height(3.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(EmeraldPrimary)
                    )
                }
            }

            // Sign Up Tab
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(11.dp))
                    .then(
                        if (selectedTab == AuthTab.SIGN_UP) {
                            Modifier.background(MaterialTheme.colorScheme.surface)
                        } else {
                            Modifier.clickable { onTabSelected(AuthTab.SIGN_UP) }
                        }
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Sign Up",
                    style = MaterialTheme.typography.titleSmall.copy(
                        fontWeight = if (selectedTab == AuthTab.SIGN_UP) FontWeight.Bold else FontWeight.Medium
                    ),
                    color = if (selectedTab == AuthTab.SIGN_UP) {
                        EmeraldPrimary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    }
                )

                // Active green bottom underline indicator
                if (selectedTab == AuthTab.SIGN_UP) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .fillMaxWidth(0.55f)
                            .height(3.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(EmeraldPrimary)
                    )
                }
            }
        }
    }
}

/**
 * Vector Mail/Envelope icon for input fields.
 */
@Composable
fun MailVectorIcon(
    modifier: Modifier = Modifier,
    tint: Color = MaterialTheme.colorScheme.onSurfaceVariant
) {
    Canvas(modifier = modifier.size(20.dp)) {
        val w = size.width
        val h = size.height
        val inset = 1.5f

        // Envelope rectangle
        drawRoundRect(
            color = tint,
            topLeft = Offset(inset, h * 0.2f),
            size = Size(w - 2 * inset, h * 0.6f),
            cornerRadius = CornerRadius(4f, 4f),
            style = Stroke(width = 1.8f)
        )

        // Envelope fold line
        val foldPath = Path().apply {
            moveTo(inset + 1f, h * 0.25f)
            lineTo(w / 2f, h * 0.55f)
            lineTo(w - inset - 1f, h * 0.25f)
        }
        drawPath(
            path = foldPath,
            color = tint,
            style = Stroke(width = 1.8f, cap = StrokeCap.Round, join = StrokeJoin.Round)
        )
    }
}

/**
 * Vector Lock icon for password input fields.
 */
@Composable
fun LockVectorIcon(
    modifier: Modifier = Modifier,
    tint: Color = MaterialTheme.colorScheme.onSurfaceVariant
) {
    Canvas(modifier = modifier.size(20.dp)) {
        val w = size.width
        val h = size.height

        // Shackle
        val shacklePath = Path().apply {
            moveTo(w * 0.32f, h * 0.44f)
            lineTo(w * 0.32f, h * 0.24f)
            cubicTo(w * 0.32f, h * 0.08f, w * 0.68f, h * 0.08f, w * 0.68f, h * 0.24f)
            lineTo(w * 0.68f, h * 0.44f)
        }
        drawPath(
            path = shacklePath,
            color = tint,
            style = Stroke(width = 1.8f, cap = StrokeCap.Round)
        )

        // Body
        drawRoundRect(
            color = tint,
            topLeft = Offset(w * 0.20f, h * 0.42f),
            size = Size(w * 0.60f, h * 0.48f),
            cornerRadius = CornerRadius(4f, 4f),
            style = Stroke(width = 1.8f)
        )

        // Keyhole
        drawCircle(
            color = tint,
            radius = 2.2f,
            center = Offset(w * 0.5f, h * 0.62f)
        )
    }
}

/**
 * Vector User icon for display name field.
 */
@Composable
fun UserVectorIcon(
    modifier: Modifier = Modifier,
    tint: Color = MaterialTheme.colorScheme.onSurfaceVariant
) {
    Canvas(modifier = modifier.size(20.dp)) {
        val w = size.width
        val h = size.height

        // Head
        drawCircle(
            color = tint,
            radius = w * 0.22f,
            center = Offset(w * 0.5f, h * 0.32f),
            style = Stroke(width = 1.8f)
        )

        // Shoulders
        val shoulders = Path().apply {
            moveTo(w * 0.16f, h * 0.86f)
            cubicTo(w * 0.18f, h * 0.62f, w * 0.82f, h * 0.62f, w * 0.84f, h * 0.86f)
        }
        drawPath(
            path = shoulders,
            color = tint,
            style = Stroke(width = 1.8f, cap = StrokeCap.Round)
        )
    }
}

/**
 * Vector Eye icon for password visibility toggle.
 */
@Composable
fun EyeVectorIcon(
    visible: Boolean,
    modifier: Modifier = Modifier,
    tint: Color = MaterialTheme.colorScheme.onSurfaceVariant
) {
    Canvas(modifier = modifier.size(20.dp)) {
        val w = size.width
        val h = size.height

        // Eye shape
        val eyePath = Path().apply {
            moveTo(w * 0.10f, h * 0.5f)
            cubicTo(w * 0.30f, h * 0.22f, w * 0.70f, h * 0.22f, w * 0.90f, h * 0.5f)
            cubicTo(w * 0.70f, h * 0.78f, w * 0.30f, h * 0.78f, w * 0.10f, h * 0.5f)
            close()
        }
        drawPath(
            path = eyePath,
            color = tint,
            style = Stroke(width = 1.8f)
        )

        // Pupil
        drawCircle(
            color = tint,
            radius = 3.5f,
            center = Offset(w * 0.5f, h * 0.5f)
        )

        // Slash if not visible
        if (!visible) {
            drawLine(
                color = tint,
                start = Offset(w * 0.15f, h * 0.85f),
                end = Offset(w * 0.85f, h * 0.15f),
                strokeWidth = 1.8f,
                cap = StrokeCap.Round
            )
        }
    }
}

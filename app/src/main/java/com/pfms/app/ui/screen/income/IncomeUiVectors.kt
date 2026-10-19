package com.pfms.app.ui.screen.income

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp

// ════════════════════════════════════════════════════════════════════════════
// Custom Canvas Vector Icons matching the modern Income UI design
// ════════════════════════════════════════════════════════════════════════════

/**
 * Briefcase vector icon for "Salary" source.
 */
@Composable
fun IncomeBriefcaseIcon(
    modifier: Modifier = Modifier,
    tint: Color = Color(0xFF007A5E)
) {
    Canvas(modifier = modifier.size(22.dp)) {
        val w = size.width
        val h = size.height

        // Top handle
        val handlePath = Path().apply {
            moveTo(w * 0.35f, h * 0.28f)
            lineTo(w * 0.35f, h * 0.16f)
            cubicTo(w * 0.35f, h * 0.10f, w * 0.42f, h * 0.08f, w * 0.50f, h * 0.08f)
            cubicTo(w * 0.58f, h * 0.08f, w * 0.65f, h * 0.10f, w * 0.65f, h * 0.16f)
            lineTo(w * 0.65f, h * 0.28f)
        }
        drawPath(path = handlePath, color = tint, style = Stroke(width = 2.2f, cap = StrokeCap.Round))

        // Main briefcase body
        drawRoundRect(
            color = tint,
            topLeft = Offset(w * 0.12f, h * 0.26f),
            size = Size(w * 0.76f, h * 0.62f),
            cornerRadius = CornerRadius(5f, 5f),
            style = Stroke(width = 2.2f)
        )

        // Middle horizontal seam / strap
        drawLine(
            color = tint,
            start = Offset(w * 0.12f, h * 0.56f),
            end = Offset(w * 0.88f, h * 0.56f),
            strokeWidth = 2f
        )

        // Center lock / buckle
        drawRoundRect(
            color = tint,
            topLeft = Offset(w * 0.42f, h * 0.50f),
            size = Size(w * 0.16f, h * 0.14f),
            cornerRadius = CornerRadius(2f, 2f),
            style = Fill
        )
    }
}

/**
 * Laptop vector icon for "Freelance" source.
 */
@Composable
fun IncomeLaptopIcon(
    modifier: Modifier = Modifier,
    tint: Color = Color(0xFF0284C7)
) {
    Canvas(modifier = modifier.size(22.dp)) {
        val w = size.width
        val h = size.height

        // Laptop display
        drawRoundRect(
            color = tint,
            topLeft = Offset(w * 0.18f, h * 0.18f),
            size = Size(w * 0.64f, h * 0.48f),
            cornerRadius = CornerRadius(4f, 4f),
            style = Stroke(width = 2.2f)
        )

        // Keyboard base
        drawLine(
            color = tint,
            start = Offset(w * 0.08f, h * 0.72f),
            end = Offset(w * 0.92f, h * 0.72f),
            strokeWidth = 2.4f,
            cap = StrokeCap.Round
        )

        // Inner screen code / terminal prompt symbol "< >"
        val codePath = Path().apply {
            moveTo(w * 0.40f, h * 0.36f)
            lineTo(w * 0.33f, h * 0.42f)
            lineTo(w * 0.40f, h * 0.48f)

            moveTo(w * 0.60f, h * 0.36f)
            lineTo(w * 0.67f, h * 0.42f)
            lineTo(w * 0.60f, h * 0.48f)
        }
        drawPath(path = codePath, color = tint, style = Stroke(width = 1.8f, cap = StrokeCap.Round, join = StrokeJoin.Round))
    }
}

/**
 * AdSense / Media icon for "Google AdSense" source.
 */
@Composable
fun IncomeAdSenseIcon(
    modifier: Modifier = Modifier,
    tint: Color = Color(0xFFD97706)
) {
    Canvas(modifier = modifier.size(22.dp)) {
        val w = size.width
        val h = size.height

        // AdSense yellow angled pill
        val yellowPill = Path().apply {
            moveTo(w * 0.22f, h * 0.78f)
            lineTo(w * 0.66f, h * 0.18f)
        }
        drawLine(
            color = Color(0xFFFBBF24),
            start = Offset(w * 0.22f, h * 0.78f),
            end = Offset(w * 0.66f, h * 0.18f),
            strokeWidth = 5f,
            cap = StrokeCap.Round
        )

        // Blue vertical pill
        drawLine(
            color = Color(0xFF3B82F6),
            start = Offset(w * 0.22f, h * 0.78f),
            end = Offset(w * 0.22f, h * 0.34f),
            strokeWidth = 5f,
            cap = StrokeCap.Round
        )

        // Green circle/pill head
        drawCircle(
            color = Color(0xFF10B981),
            radius = 3.5f,
            center = Offset(w * 0.66f, h * 0.18f)
        )
    }
}

/**
 * Crypto 'B' currency icon for "Crypto" source.
 */
@Composable
fun IncomeCryptoIcon(
    modifier: Modifier = Modifier,
    tint: Color = Color(0xFF7E22CE)
) {
    Canvas(modifier = modifier.size(22.dp)) {
        val w = size.width
        val h = size.height

        // Circle outline
        drawCircle(
            color = tint,
            radius = w * 0.44f,
            center = Offset(w * 0.5f, h * 0.5f),
            style = Stroke(width = 2f)
        )

        // Vertical spine
        drawLine(
            color = tint,
            start = Offset(w * 0.40f, h * 0.22f),
            end = Offset(w * 0.40f, h * 0.78f),
            strokeWidth = 2f,
            cap = StrokeCap.Round
        )

        // Top loop of B
        val topLoop = Path().apply {
            moveTo(w * 0.40f, h * 0.28f)
            lineTo(w * 0.54f, h * 0.28f)
            cubicTo(w * 0.65f, h * 0.28f, w * 0.65f, h * 0.48f, w * 0.54f, h * 0.48f)
            lineTo(w * 0.40f, h * 0.48f)
        }
        drawPath(path = topLoop, color = tint, style = Stroke(width = 1.8f))

        // Bottom loop of B
        val bottomLoop = Path().apply {
            moveTo(w * 0.40f, h * 0.48f)
            lineTo(w * 0.57f, h * 0.48f)
            cubicTo(w * 0.68f, h * 0.48f, w * 0.68f, h * 0.72f, w * 0.57f, h * 0.72f)
            lineTo(w * 0.40f, h * 0.72f)
        }
        drawPath(path = bottomLoop, color = tint, style = Stroke(width = 1.8f))
    }
}

/**
 * Other / General income vector icon.
 */
@Composable
fun IncomeOtherIcon(
    modifier: Modifier = Modifier,
    tint: Color = Color(0xFF475569)
) {
    Canvas(modifier = modifier.size(22.dp)) {
        val w = size.width
        val h = size.height

        // Wallet / pouch body
        drawRoundRect(
            color = tint,
            topLeft = Offset(w * 0.15f, h * 0.24f),
            size = Size(w * 0.70f, h * 0.56f),
            cornerRadius = CornerRadius(4f, 4f),
            style = Stroke(width = 2f)
        )

        // Coin or star inside
        drawCircle(
            color = tint,
            radius = w * 0.10f,
            center = Offset(w * 0.50f, h * 0.52f)
        )
    }
}

/**
 * Trend Up Vector Icon ("↑ +12%").
 */
@Composable
fun IncomeTrendUpIcon(
    modifier: Modifier = Modifier,
    tint: Color = Color(0xFF007A5E)
) {
    Canvas(modifier = modifier.size(14.dp)) {
        val w = size.width
        val h = size.height

        // Diagonal arrow line
        drawLine(
            color = tint,
            start = Offset(w * 0.20f, h * 0.80f),
            end = Offset(w * 0.75f, h * 0.25f),
            strokeWidth = 2.2f,
            cap = StrokeCap.Round
        )

        // Arrow head
        val head = Path().apply {
            moveTo(w * 0.45f, h * 0.25f)
            lineTo(w * 0.75f, h * 0.25f)
            lineTo(w * 0.75f, h * 0.55f)
        }
        drawPath(path = head, color = tint, style = Stroke(width = 2.2f, cap = StrokeCap.Round, join = StrokeJoin.Round))
    }
}

/**
 * Calendar Vector Icon.
 */
@Composable
fun IncomeCalendarIcon(
    modifier: Modifier = Modifier,
    tint: Color = Color(0xFF64748B)
) {
    Canvas(modifier = modifier.size(18.dp)) {
        val w = size.width
        val h = size.height

        // Body
        drawRoundRect(
            color = tint,
            topLeft = Offset(w * 0.10f, h * 0.20f),
            size = Size(w * 0.80f, h * 0.70f),
            cornerRadius = CornerRadius(4f, 4f),
            style = Stroke(width = 1.8f)
        )

        // Top binder hooks
        drawLine(
            color = tint,
            start = Offset(w * 0.30f, h * 0.08f),
            end = Offset(w * 0.30f, h * 0.24f),
            strokeWidth = 1.8f,
            cap = StrokeCap.Round
        )
        drawLine(
            color = tint,
            start = Offset(w * 0.70f, h * 0.08f),
            end = Offset(w * 0.70f, h * 0.24f),
            strokeWidth = 1.8f,
            cap = StrokeCap.Round
        )

        // Header separator line
        drawLine(
            color = tint,
            start = Offset(w * 0.10f, h * 0.42f),
            end = Offset(w * 0.90f, h * 0.42f),
            strokeWidth = 1.4f
        )

        // Inner calendar dots
        drawCircle(color = tint, radius = 1.5f, center = Offset(w * 0.32f, h * 0.60f))
        drawCircle(color = tint, radius = 1.5f, center = Offset(w * 0.50f, h * 0.60f))
        drawCircle(color = tint, radius = 1.5f, center = Offset(w * 0.68f, h * 0.60f))
        drawCircle(color = tint, radius = 1.5f, center = Offset(w * 0.32f, h * 0.76f))
        drawCircle(color = tint, radius = 1.5f, center = Offset(w * 0.50f, h * 0.76f))
    }
}

/**
 * Chevron Down Icon for Dropdowns.
 */
@Composable
fun IncomeChevronDownIcon(
    modifier: Modifier = Modifier,
    tint: Color = Color(0xFF64748B)
) {
    Canvas(modifier = modifier.size(16.dp)) {
        val w = size.width
        val h = size.height

        val path = Path().apply {
            moveTo(w * 0.25f, h * 0.38f)
            lineTo(w * 0.50f, h * 0.62f)
            lineTo(w * 0.75f, h * 0.38f)
        }
        drawPath(path = path, color = tint, style = Stroke(width = 2f, cap = StrokeCap.Round, join = StrokeJoin.Round))
    }
}

/**
 * Chevron Up Icon for Accordion collapse.
 */
@Composable
fun IncomeChevronUpIcon(
    modifier: Modifier = Modifier,
    tint: Color = Color(0xFF64748B)
) {
    Canvas(modifier = modifier.size(16.dp)) {
        val w = size.width
        val h = size.height

        val path = Path().apply {
            moveTo(w * 0.25f, h * 0.62f)
            lineTo(w * 0.50f, h * 0.38f)
            lineTo(w * 0.75f, h * 0.62f)
        }
        drawPath(path = path, color = tint, style = Stroke(width = 2f, cap = StrokeCap.Round, join = StrokeJoin.Round))
    }
}

/**
 * Checkmark icon inside circle for "Confirmed" status.
 */
@Composable
fun IncomeCheckmarkIcon(
    modifier: Modifier = Modifier,
    tint: Color = Color.White
) {
    Canvas(modifier = modifier.size(16.dp)) {
        val w = size.width
        val h = size.height

        drawCircle(
            color = tint,
            radius = w * 0.44f,
            center = Offset(w * 0.5f, h * 0.5f),
            style = Stroke(width = 1.6f)
        )

        val checkPath = Path().apply {
            moveTo(w * 0.30f, h * 0.50f)
            lineTo(w * 0.45f, h * 0.65f)
            lineTo(w * 0.70f, h * 0.35f)
        }
        drawPath(path = checkPath, color = tint, style = Stroke(width = 1.8f, cap = StrokeCap.Round, join = StrokeJoin.Round))
    }
}

/**
 * Clock icon for "Estimated" status.
 */
@Composable
fun IncomeClockIcon(
    modifier: Modifier = Modifier,
    tint: Color = Color(0xFF64748B)
) {
    Canvas(modifier = modifier.size(16.dp)) {
        val w = size.width
        val h = size.height

        drawCircle(
            color = tint,
            radius = w * 0.44f,
            center = Offset(w * 0.5f, h * 0.5f),
            style = Stroke(width = 1.6f)
        )

        val hands = Path().apply {
            moveTo(w * 0.50f, h * 0.26f)
            lineTo(w * 0.50f, h * 0.50f)
            lineTo(w * 0.70f, h * 0.50f)
        }
        drawPath(path = hands, color = tint, style = Stroke(width = 1.6f, cap = StrokeCap.Round, join = StrokeJoin.Round))
    }
}

/**
 * 3 vertical dots icon for options menu.
 */
@Composable
fun IncomeMoreVertIcon(
    modifier: Modifier = Modifier,
    tint: Color = Color(0xFF94A3B8)
) {
    Canvas(modifier = modifier.size(18.dp)) {
        val w = size.width
        val h = size.height
        val r = 1.8f

        drawCircle(color = tint, radius = r, center = Offset(w * 0.5f, h * 0.22f))
        drawCircle(color = tint, radius = r, center = Offset(w * 0.5f, h * 0.50f))
        drawCircle(color = tint, radius = r, center = Offset(w * 0.5f, h * 0.78f))
    }
}

/**
 * Info "i" circle vector icon.
 */
@Composable
fun IncomeInfoCircleIcon(
    modifier: Modifier = Modifier,
    tint: Color = Color(0xFF64748B)
) {
    Canvas(modifier = modifier.size(16.dp)) {
        val w = size.width
        val h = size.height

        drawCircle(
            color = tint,
            radius = w * 0.44f,
            center = Offset(w * 0.5f, h * 0.5f),
            style = Stroke(width = 1.5f)
        )

        // Dot of "i"
        drawCircle(
            color = tint,
            radius = 1.2f,
            center = Offset(w * 0.5f, h * 0.32f)
        )

        // Stem of "i"
        drawLine(
            color = tint,
            start = Offset(w * 0.5f, h * 0.46f),
            end = Offset(w * 0.5f, h * 0.70f),
            strokeWidth = 1.6f,
            cap = StrokeCap.Round
        )
    }
}

/**
 * Plus "+" vector icon.
 */
@Composable
fun IncomePlusIcon(
    modifier: Modifier = Modifier,
    tint: Color = Color.White
) {
    Canvas(modifier = modifier.size(14.dp)) {
        val w = size.width
        val h = size.height

        drawLine(
            color = tint,
            start = Offset(w * 0.5f, h * 0.15f),
            end = Offset(w * 0.5f, h * 0.85f),
            strokeWidth = 2.4f,
            cap = StrokeCap.Round
        )
        drawLine(
            color = tint,
            start = Offset(w * 0.15f, h * 0.5f),
            end = Offset(w * 0.85f, h * 0.5f),
            strokeWidth = 2.4f,
            cap = StrokeCap.Round
        )
    }
}

/**
 * Back arrow vector icon.
 */
@Composable
fun IncomeArrowBackIcon(
    modifier: Modifier = Modifier,
    tint: Color = Color(0xFF0F172A)
) {
    Canvas(modifier = modifier.size(20.dp)) {
        val w = size.width
        val h = size.height

        // Stem
        drawLine(
            color = tint,
            start = Offset(w * 0.85f, h * 0.5f),
            end = Offset(w * 0.18f, h * 0.5f),
            strokeWidth = 2.4f,
            cap = StrokeCap.Round
        )

        // Head
        val head = Path().apply {
            moveTo(w * 0.45f, h * 0.22f)
            lineTo(w * 0.18f, h * 0.50f)
            lineTo(w * 0.45f, h * 0.78f)
        }
        drawPath(path = head, color = tint, style = Stroke(width = 2.4f, cap = StrokeCap.Round, join = StrokeJoin.Round))
    }
}

/**
 * Helper to get the vector icon corresponding to any income source.
 */
@Composable
fun IncomeSourceVector(
    source: String,
    modifier: Modifier = Modifier,
    customTint: Color? = null
) {
    when {
        source.equals("Salary", ignoreCase = true) -> {
            IncomeBriefcaseIcon(modifier = modifier, tint = customTint ?: Color(0xFF007A5E))
        }
        source.equals("Freelance", ignoreCase = true) -> {
            IncomeLaptopIcon(modifier = modifier, tint = customTint ?: Color(0xFF0284C7))
        }
        source.contains("AdSense", ignoreCase = true) -> {
            IncomeAdSenseIcon(modifier = modifier, tint = customTint ?: Color(0xFFD97706))
        }
        source.equals("Crypto", ignoreCase = true) -> {
            IncomeCryptoIcon(modifier = modifier, tint = customTint ?: Color(0xFF7E22CE))
        }
        else -> {
            IncomeOtherIcon(modifier = modifier, tint = customTint ?: Color(0xFF475569))
        }
    }
}

/**
 * Refresh / Sync circular arrow icon.
 */
@Composable
fun IncomeRefreshIcon(
    modifier: Modifier = Modifier,
    tint: Color = Color(0xFF007A5E)
) {
    Canvas(modifier = modifier.size(16.dp)) {
        val w = size.width
        val h = size.height

        drawArc(
            color = tint,
            startAngle = 45f,
            sweepAngle = 270f,
            useCenter = false,
            topLeft = Offset(w * 0.15f, h * 0.15f),
            size = Size(w * 0.70f, h * 0.70f),
            style = Stroke(width = 1.8f, cap = StrokeCap.Round)
        )

        val arrow = Path().apply {
            moveTo(w * 0.62f, h * 0.12f)
            lineTo(w * 0.86f, h * 0.28f)
            lineTo(w * 0.62f, h * 0.44f)
        }
        drawPath(path = arrow, color = tint, style = Stroke(width = 1.8f, cap = StrokeCap.Round, join = StrokeJoin.Round))
    }
}


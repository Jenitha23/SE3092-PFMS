package com.pfms.app.ui.component

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.pfms.app.ui.theme.EmeraldPrimary

/**
 * Donut Chart matching Reference UI 2:
 * 45% Emerald (Committed), 55% Purple (Discretionary).
 */
@Composable
fun SpendingDonutChart(
    modifier: Modifier = Modifier,
    committedColor: Color = Color(0xFF169C7B),
    discretionaryColor: Color = Color(0xFF7A4DD8),
    centerContent: @Composable () -> Unit = {}
) {
    Box(
        modifier = modifier.size(108.dp),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val strokeW = 24f
            val arcSize = Size(size.width - strokeW, size.height - strokeW)
            val topLeft = Offset(strokeW / 2, strokeW / 2)

            // Committed: 45% (162 degrees)
            drawArc(
                color = committedColor,
                startAngle = -85f,
                sweepAngle = 158f,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(width = strokeW, cap = StrokeCap.Round)
            )

            // Discretionary: 55% (198 degrees)
            drawArc(
                color = discretionaryColor,
                startAngle = 78f,
                sweepAngle = 192f,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(width = strokeW, cap = StrokeCap.Round)
            )
        }
        centerContent()
    }
}

/**
 * Arrow Up Vector Icon (Income).
 */
@Composable
fun ArrowUpVector(
    modifier: Modifier = Modifier,
    tint: Color = Color.White
) {
    Canvas(modifier = modifier.size(16.dp)) {
        val w = size.width
        val h = size.height

        // Vertical stem
        drawLine(
            color = tint,
            start = Offset(w * 0.5f, h * 0.85f),
            end = Offset(w * 0.5f, h * 0.18f),
            strokeWidth = 2.4f,
            cap = StrokeCap.Round
        )

        // Arrowhead
        val head = Path().apply {
            moveTo(w * 0.22f, h * 0.45f)
            lineTo(w * 0.5f, h * 0.18f)
            lineTo(w * 0.78f, h * 0.45f)
        }
        drawPath(
            path = head,
            color = tint,
            style = Stroke(width = 2.4f, cap = StrokeCap.Round)
        )
    }
}

/**
 * Arrow Down Vector Icon (Expense).
 */
@Composable
fun ArrowDownVector(
    modifier: Modifier = Modifier,
    tint: Color = Color(0xFFE84C6A)
) {
    Canvas(modifier = modifier.size(16.dp)) {
        val w = size.width
        val h = size.height

        // Vertical stem
        drawLine(
            color = tint,
            start = Offset(w * 0.5f, h * 0.15f),
            end = Offset(w * 0.5f, h * 0.82f),
            strokeWidth = 2.4f,
            cap = StrokeCap.Round
        )

        // Arrowhead
        val head = Path().apply {
            moveTo(w * 0.22f, h * 0.55f)
            lineTo(w * 0.5f, h * 0.82f)
            lineTo(w * 0.78f, h * 0.55f)
        }
        drawPath(
            path = head,
            color = tint,
            style = Stroke(width = 2.4f, cap = StrokeCap.Round)
        )
    }
}

/**
 * Savings Piggy/Target Vector Icon.
 */
@Composable
fun SavingsVector(
    modifier: Modifier = Modifier,
    tint: Color = Color(0xFF7A4DD8)
) {
    Canvas(modifier = modifier.size(18.dp)) {
        val w = size.width
        val h = size.height

        // Piggy / Safe body
        drawRoundRect(
            color = tint,
            topLeft = Offset(w * 0.15f, h * 0.25f),
            size = Size(w * 0.70f, h * 0.55f),
            cornerRadius = CornerRadius(8f, 8f),
            style = Stroke(width = 2f)
        )

        // Coin slot on top
        drawLine(
            color = tint,
            start = Offset(w * 0.40f, h * 0.18f),
            end = Offset(w * 0.60f, h * 0.18f),
            strokeWidth = 2f,
            cap = StrokeCap.Round
        )

        // Center dot
        drawCircle(
            color = tint,
            radius = 2.5f,
            center = Offset(w * 0.5f, h * 0.52f)
        )
    }
}

/**
 * Vector Bell Notification Icon for top header.
 */
@Composable
fun BellVectorIcon(
    modifier: Modifier = Modifier,
    tint: Color = Color.White
) {
    Canvas(modifier = modifier.size(20.dp)) {
        val w = size.width
        val h = size.height

        // Bell dome
        val bellPath = Path().apply {
            moveTo(w * 0.5f, h * 0.15f)
            cubicTo(w * 0.35f, h * 0.15f, w * 0.25f, h * 0.35f, w * 0.25f, h * 0.60f)
            lineTo(w * 0.18f, h * 0.75f)
            lineTo(w * 0.82f, h * 0.75f)
            lineTo(w * 0.75f, h * 0.60f)
            cubicTo(w * 0.75f, h * 0.35f, w * 0.65f, h * 0.15f, w * 0.5f, h * 0.15f)
            close()
        }
        drawPath(path = bellPath, color = tint, style = Stroke(width = 1.8f))

        // Clapper at bottom
        drawArc(
            color = tint,
            startAngle = 0f,
            sweepAngle = 180f,
            useCenter = true,
            topLeft = Offset(w * 0.42f, h * 0.78f),
            size = Size(w * 0.16f, h * 0.12f)
        )
    }
}

/**
 * Target / Goal Vector Icon.
 */
@Composable
fun GoalVectorIcon(
    modifier: Modifier = Modifier,
    tint: Color = Color(0xFF169C7B)
) {
    Canvas(modifier = modifier.size(20.dp)) {
        val center = Offset(size.width / 2, size.height / 2)
        drawCircle(
            color = tint,
            radius = size.width * 0.45f,
            center = center,
            style = Stroke(width = 1.8f)
        )
        drawCircle(
            color = tint,
            radius = size.width * 0.25f,
            center = center,
            style = Stroke(width = 1.8f)
        )
        drawCircle(
            color = tint,
            radius = size.width * 0.10f,
            center = center
        )
    }
}

/**
 * Laptop Vector Icon for the Savings Goal item.
 */
@Composable
fun LaptopVectorIcon(
    modifier: Modifier = Modifier,
    tint: Color = Color(0xFF475569)
) {
    Canvas(modifier = modifier.size(24.dp)) {
        val w = size.width
        val h = size.height

        // Screen
        drawRoundRect(
            color = tint,
            topLeft = Offset(w * 0.20f, h * 0.20f),
            size = Size(w * 0.60f, h * 0.48f),
            cornerRadius = CornerRadius(3f, 3f),
            style = Stroke(width = 1.8f)
        )

        // Base
        drawLine(
            color = tint,
            start = Offset(w * 0.10f, h * 0.72f),
            end = Offset(w * 0.90f, h * 0.72f),
            strokeWidth = 2.4f,
            cap = StrokeCap.Round
        )
    }
}

/**
 * Fork & Spoon / Dining Vector Icon.
 */
@Composable
fun DiningVectorIcon(
    modifier: Modifier = Modifier,
    tint: Color = Color(0xFFE84C6A)
) {
    Canvas(modifier = modifier.size(16.dp)) {
        val w = size.width
        val h = size.height

        // Fork stem
        drawLine(
            color = tint,
            start = Offset(w * 0.35f, h * 0.2f),
            end = Offset(w * 0.35f, h * 0.85f),
            strokeWidth = 1.8f,
            cap = StrokeCap.Round
        )

        // Spoon stem
        drawLine(
            color = tint,
            start = Offset(w * 0.65f, h * 0.2f),
            end = Offset(w * 0.65f, h * 0.85f),
            strokeWidth = 1.8f,
            cap = StrokeCap.Round
        )
    }
}

/**
 * Calendar Vector Icon for Monthly Overview card date tag.
 */
@Composable
fun CalendarVectorIcon(
    modifier: Modifier = Modifier,
    tint: Color = Color(0xFF64748B)
) {
    Canvas(modifier = modifier.size(15.dp)) {
        val w = size.width
        val h = size.height
        val inset = 1f

        // Calendar body
        drawRoundRect(
            color = tint,
            topLeft = Offset(inset, h * 0.22f),
            size = Size(w - 2 * inset, h * 0.72f),
            cornerRadius = CornerRadius(3f, 3f),
            style = Stroke(width = 1.5f)
        )

        // Top binder pins
        drawLine(
            color = tint,
            start = Offset(w * 0.3f, h * 0.08f),
            end = Offset(w * 0.3f, h * 0.25f),
            strokeWidth = 1.6f,
            cap = StrokeCap.Round
        )
        drawLine(
            color = tint,
            start = Offset(w * 0.7f, h * 0.08f),
            end = Offset(w * 0.7f, h * 0.25f),
            strokeWidth = 1.6f,
            cap = StrokeCap.Round
        )

        // Header separator
        drawLine(
            color = tint,
            start = Offset(inset, h * 0.44f),
            end = Offset(w - inset, h * 0.44f),
            strokeWidth = 1.2f
        )

        // Mini calendar dots
        val dotRadius = 1f
        drawCircle(color = tint, radius = dotRadius, center = Offset(w * 0.35f, h * 0.62f))
        drawCircle(color = tint, radius = dotRadius, center = Offset(w * 0.65f, h * 0.62f))
        drawCircle(color = tint, radius = dotRadius, center = Offset(w * 0.35f, h * 0.80f))
        drawCircle(color = tint, radius = dotRadius, center = Offset(w * 0.65f, h * 0.80f))
    }
}

/**
 * Modern House Vector Icon for Home Bottom Navigation Tab.
 */
@Composable
fun HomeNavIcon(
    modifier: Modifier = Modifier,
    tint: Color = Color(0xFF169C7B),
    filled: Boolean = true
) {
    Canvas(modifier = modifier.size(22.dp)) {
        val w = size.width
        val h = size.height

        val housePath = Path().apply {
            moveTo(w * 0.5f, h * 0.12f)
            lineTo(w * 0.88f, h * 0.45f)
            lineTo(w * 0.80f, h * 0.45f)
            lineTo(w * 0.80f, h * 0.88f)
            lineTo(w * 0.58f, h * 0.88f)
            lineTo(w * 0.58f, h * 0.62f)
            lineTo(w * 0.42f, h * 0.62f)
            lineTo(w * 0.42f, h * 0.88f)
            lineTo(w * 0.20f, h * 0.88f)
            lineTo(w * 0.20f, h * 0.45f)
            lineTo(w * 0.12f, h * 0.45f)
            close()
        }

        if (filled) {
            drawPath(path = housePath, color = tint)
        } else {
            drawPath(path = housePath, color = tint, style = Stroke(width = 1.8f, join = StrokeJoin.Round))
        }
    }
}

/**
 * Wallet Vector Icon for Income Bottom Navigation Tab.
 */
@Composable
fun IncomeNavIcon(
    modifier: Modifier = Modifier,
    tint: Color = Color(0xFF94A3B8)
) {
    Canvas(modifier = modifier.size(22.dp)) {
        val w = size.width
        val h = size.height

        // Wallet body
        drawRoundRect(
            color = tint,
            topLeft = Offset(w * 0.12f, h * 0.22f),
            size = Size(w * 0.76f, h * 0.60f),
            cornerRadius = CornerRadius(4f, 4f),
            style = Stroke(width = 1.8f)
        )

        // Wallet flap
        drawRoundRect(
            color = tint,
            topLeft = Offset(w * 0.52f, h * 0.38f),
            size = Size(w * 0.36f, h * 0.28f),
            cornerRadius = CornerRadius(3f, 3f),
            style = Stroke(width = 1.6f)
        )

        // Flap button
        drawCircle(
            color = tint,
            radius = 1.8f,
            center = Offset(w * 0.64f, h * 0.52f)
        )
    }
}

/**
 * Receipt/Expense Vector Icon for Expenses Bottom Navigation Tab.
 */
@Composable
fun ExpensesNavIcon(
    modifier: Modifier = Modifier,
    tint: Color = Color(0xFF94A3B8)
) {
    Canvas(modifier = modifier.size(22.dp)) {
        val w = size.width
        val h = size.height

        // Receipt body with serrated bottom
        val receiptPath = Path().apply {
            moveTo(w * 0.22f, h * 0.15f)
            lineTo(w * 0.78f, h * 0.15f)
            lineTo(w * 0.78f, h * 0.85f)
            lineTo(w * 0.67f, h * 0.77f)
            lineTo(w * 0.56f, h * 0.85f)
            lineTo(w * 0.44f, h * 0.77f)
            lineTo(w * 0.33f, h * 0.85f)
            lineTo(w * 0.22f, h * 0.77f)
            close()
        }
        drawPath(path = receiptPath, color = tint, style = Stroke(width = 1.8f, join = StrokeJoin.Round))

        // Receipt item lines
        drawLine(
            color = tint,
            start = Offset(w * 0.34f, h * 0.32f),
            end = Offset(w * 0.66f, h * 0.32f),
            strokeWidth = 1.6f,
            cap = StrokeCap.Round
        )
        drawLine(
            color = tint,
            start = Offset(w * 0.34f, h * 0.48f),
            end = Offset(w * 0.66f, h * 0.48f),
            strokeWidth = 1.6f,
            cap = StrokeCap.Round
        )
        drawLine(
            color = tint,
            start = Offset(w * 0.34f, h * 0.64f),
            end = Offset(w * 0.52f, h * 0.64f),
            strokeWidth = 1.6f,
            cap = StrokeCap.Round
        )
    }
}

/**
 * Bullseye / Target Vector Icon for Goals Bottom Navigation Tab.
 */
@Composable
fun GoalsNavIcon(
    modifier: Modifier = Modifier,
    tint: Color = Color(0xFF94A3B8)
) {
    Canvas(modifier = modifier.size(22.dp)) {
        val center = Offset(size.width / 2, size.height / 2)
        drawCircle(
            color = tint,
            radius = size.width * 0.44f,
            center = center,
            style = Stroke(width = 1.8f)
        )
        drawCircle(
            color = tint,
            radius = size.width * 0.25f,
            center = center,
            style = Stroke(width = 1.6f)
        )
        drawCircle(
            color = tint,
            radius = size.width * 0.10f,
            center = center
        )
    }
}

/**
 * Bar Chart / Analytics Vector Icon for Insights Bottom Navigation Tab.
 */
@Composable
fun InsightsNavIcon(
    modifier: Modifier = Modifier,
    tint: Color = Color(0xFF94A3B8)
) {
    Canvas(modifier = modifier.size(22.dp)) {
        val w = size.width
        val h = size.height
        val barW = w * 0.15f
        val radius = CornerRadius(barW / 2, barW / 2)

        // Bar 1 (Short)
        drawRoundRect(
            color = tint,
            topLeft = Offset(w * 0.15f, h * 0.55f),
            size = Size(barW, h * 0.35f),
            cornerRadius = radius
        )

        // Bar 2 (Medium)
        drawRoundRect(
            color = tint,
            topLeft = Offset(w * 0.42f, h * 0.30f),
            size = Size(barW, h * 0.60f),
            cornerRadius = radius
        )

        // Bar 3 (Tall)
        drawRoundRect(
            color = tint,
            topLeft = Offset(w * 0.70f, h * 0.15f),
            size = Size(barW, h * 0.75f),
            cornerRadius = radius
        )
    }
}

/**
 * Modern Credit Card Vector Icon for Payment methods.
 */
@Composable
fun CreditCardVectorIcon(
    modifier: Modifier = Modifier,
    tint: Color = EmeraldPrimary
) {
    Canvas(modifier = modifier.size(20.dp)) {
        val w = size.width
        val h = size.height
        val inset = 1f

        // Card body
        drawRoundRect(
            color = tint,
            topLeft = Offset(inset, h * 0.20f),
            size = Size(w - 2 * inset, h * 0.60f),
            cornerRadius = CornerRadius(3f, 3f),
            style = Stroke(width = 1.6f)
        )

        // Magnetic stripe line
        drawLine(
            color = tint,
            start = Offset(inset, h * 0.38f),
            end = Offset(w - inset, h * 0.38f),
            strokeWidth = 1.8f
        )

        // Chip / account indicator
        drawRoundRect(
            color = tint,
            topLeft = Offset(w * 0.20f, h * 0.54f),
            size = Size(w * 0.22f, h * 0.16f),
            cornerRadius = CornerRadius(1.5f, 1.5f)
        )
    }
}

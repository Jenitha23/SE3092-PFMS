package com.pfms.app.ui.component

import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathBuilder
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

private fun financeVector(name: String, block: PathBuilder.() -> Unit) =
    ImageVector.Builder(name, 24.dp, 24.dp, 24f, 24f).apply {
        path(fill = null, stroke = SolidColor(Color.Black), strokeLineWidth = 1.8f,
            strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round, pathBuilder = block)
    }.build()

enum class FinanceSymbol(val vector: ImageVector) {
    PERSON(AuthPersonIcon),
    MAIL(AuthMailIcon),
    LOCK(AuthLockIcon),
    EDIT(financeVector("Edit profile") {
        moveTo(4f, 16f); lineTo(16f, 4f); lineTo(20f, 8f); lineTo(8f, 20f); lineTo(4f, 20f); close()
        moveTo(14f, 6f); lineTo(18f, 10f)
    }),
    SUN(financeVector("Theme") {
        moveTo(12f, 8f); curveTo(17.3f, 8f, 17.3f, 16f, 12f, 16f)
        curveTo(6.7f, 16f, 6.7f, 8f, 12f, 8f); close()
        moveTo(12f, 2f); lineTo(12f, 4f); moveTo(12f, 20f); lineTo(12f, 22f)
        moveTo(2f, 12f); lineTo(4f, 12f); moveTo(20f, 12f); lineTo(22f, 12f)
        moveTo(5f, 5f); lineTo(6.5f, 6.5f); moveTo(17.5f, 17.5f); lineTo(19f, 19f)
        moveTo(5f, 19f); lineTo(6.5f, 17.5f); moveTo(17.5f, 6.5f); lineTo(19f, 5f)
    }),
    SHIELD(financeVector("Privacy") {
        moveTo(12f, 2f); lineTo(21f, 6f); lineTo(20f, 14f)
        curveTo(19f, 18f, 15f, 21f, 12f, 22f)
        curveTo(9f, 21f, 5f, 18f, 4f, 14f); lineTo(3f, 6f); close()
        moveTo(8f, 12f); lineTo(11f, 15f); lineTo(16f, 9f)
    }),
    LOGOUT(financeVector("Log out") {
        moveTo(9f, 3f); lineTo(3f, 3f); lineTo(3f, 21f); lineTo(9f, 21f)
        moveTo(9f, 12f); lineTo(22f, 12f); moveTo(17f, 7f); lineTo(22f, 12f); lineTo(17f, 17f)
    }),
    DELETE(financeVector("Delete account") {
        moveTo(3f, 6f); lineTo(21f, 6f); moveTo(9f, 6f); lineTo(9f, 3f); lineTo(15f, 3f); lineTo(15f, 6f)
        moveTo(5f, 6f); lineTo(6f, 21f); lineTo(18f, 21f); lineTo(19f, 6f)
        moveTo(10f, 10f); lineTo(10f, 17f); moveTo(14f, 10f); lineTo(14f, 17f)
    }),
    HOME(financeVector("Home") {
        moveTo(3f, 11f); lineTo(12f, 3f); lineTo(21f, 11f)
        moveTo(5f, 10f); lineTo(5f, 21f); lineTo(10f, 21f); lineTo(10f, 15f)
        lineTo(14f, 15f); lineTo(14f, 21f); lineTo(19f, 21f); lineTo(19f, 10f)
    }),
    INCOME(financeVector("Income") {
        moveTo(3f, 5f); lineTo(21f, 5f); lineTo(21f, 19f); lineTo(3f, 19f); close()
        moveTo(3f, 10f); lineTo(21f, 10f); moveTo(6f, 15f); lineTo(9f, 15f)
    }),
    EXPENSE(financeVector("Expenses") {
        moveTo(5f, 3f); lineTo(9f, 5f); lineTo(12f, 3f); lineTo(15f, 5f)
        lineTo(19f, 3f); lineTo(19f, 21f); lineTo(15f, 19f); lineTo(12f, 21f)
        lineTo(9f, 19f); lineTo(5f, 21f); close()
        moveTo(8f, 8f); lineTo(16f, 8f); moveTo(8f, 12f); lineTo(14f, 12f)
    }),
    GOAL(financeVector("Goals") {
        moveTo(12f, 2f); curveTo(25.3f, 2f, 25.3f, 22f, 12f, 22f)
        curveTo(-1.3f, 22f, -1.3f, 2f, 12f, 2f); close()
        moveTo(12f, 6f); curveTo(20f, 6f, 20f, 18f, 12f, 18f)
        curveTo(4f, 18f, 4f, 6f, 12f, 6f); close()
        moveTo(12f, 10f); curveTo(14.7f, 10f, 14.7f, 14f, 12f, 14f)
        curveTo(9.3f, 14f, 9.3f, 10f, 12f, 10f); close()
    }),
    CHART(financeVector("Insights") {
        moveTo(3f, 3f); lineTo(3f, 21f); lineTo(21f, 21f)
        moveTo(7f, 15f); lineTo(11f, 10f); lineTo(15f, 13f); lineTo(21f, 6f)
    }),
    WALLET(financeVector("Wallet") {
        moveTo(20f, 7f); lineTo(20f, 3f); lineTo(3f, 3f); lineTo(3f, 21f); lineTo(20f, 21f)
        lineTo(20f, 16f); moveTo(3f, 7f); lineTo(22f, 7f); lineTo(22f, 16f)
        lineTo(16f, 16f); lineTo(16f, 11f); lineTo(22f, 11f)
        moveTo(18.5f, 13.5f); lineTo(19f, 13.5f)
    }),
    BELL(financeVector("Reminders") {
        moveTo(5f, 16f); lineTo(7f, 13f); lineTo(7f, 8f)
        curveTo(7f, 1f, 17f, 1f, 17f, 8f); lineTo(17f, 13f); lineTo(19f, 16f); close()
        moveTo(10f, 20f); curveTo(11f, 22f, 13f, 22f, 14f, 20f)
    }),
    BOLT(financeVector("Quick add") {
        moveTo(13f, 2f); lineTo(4f, 14f); lineTo(11f, 14f); lineTo(10f, 22f)
        lineTo(21f, 9f); lineTo(13f, 9f); close()
    }),
    PLUS(financeVector("Add") {
        moveTo(12f, 5f); lineTo(12f, 19f); moveTo(5f, 12f); lineTo(19f, 12f)
    }),
    MINUS(financeVector("Expense") { moveTo(5f, 12f); lineTo(19f, 12f) }),
    CHEVRON(financeVector("Open") { moveTo(9f, 5f); lineTo(16f, 12f); lineTo(9f, 19f) }),
    DOWN(financeVector("Choose month") { moveTo(5f, 9f); lineTo(12f, 16f); lineTo(19f, 9f) }),
    BACK(financeVector("Previous month") { moveTo(15f, 5f); lineTo(8f, 12f); lineTo(15f, 19f) }),
    MONITOR(financeVector("Savings goal") {
        moveTo(3f, 3f); lineTo(21f, 3f); lineTo(21f, 17f); lineTo(3f, 17f); close()
        moveTo(12f, 17f); lineTo(12f, 21f); moveTo(8f, 21f); lineTo(16f, 21f)
    }),
    EXPORT(financeVector("Export report") {
        moveTo(12f, 3f); lineTo(12f, 15f); moveTo(7f, 10f); lineTo(12f, 15f); lineTo(17f, 10f)
        moveTo(4f, 16f); lineTo(4f, 21f); lineTo(20f, 21f); lineTo(20f, 16f)
    }),
    CALENDAR(financeVector("Calendar") {
        moveTo(3f, 5f); lineTo(21f, 5f); lineTo(21f, 21f); lineTo(3f, 21f); close()
        moveTo(3f, 10f); lineTo(21f, 10f); moveTo(8f, 3f); lineTo(8f, 7f); moveTo(16f, 3f); lineTo(16f, 7f)
        moveTo(8f, 14f); lineTo(10f, 14f); moveTo(14f, 14f); lineTo(16f, 14f)
    }),
    CLOSE(financeVector("Close") { moveTo(6f, 6f); lineTo(18f, 18f); moveTo(18f, 6f); lineTo(6f, 18f) }),
    INFO(financeVector("Information") {
        moveTo(12f, 3f); curveTo(24f, 3f, 24f, 21f, 12f, 21f); curveTo(0f, 21f, 0f, 3f, 12f, 3f); close()
        moveTo(12f, 11f); lineTo(12f, 17f); moveTo(12f, 7f); lineTo(12f, 7.2f)
    }),
    PLANE(financeVector("Travel") {
        moveTo(3f, 12f); lineTo(10f, 10f); lineTo(10f, 3f); lineTo(13f, 3f); lineTo(16f, 9f); lineTo(21f, 9f)
        lineTo(21f, 12f); lineTo(16f, 14f); lineTo(13f, 21f); lineTo(10f, 21f); lineTo(10f, 15f); lineTo(3f, 15f); close()
    }),
    BOOK(financeVector("Education") {
        moveTo(12f, 5f); lineTo(12f, 21f); moveTo(12f, 6f); curveTo(9f, 3f, 5f, 3f, 2f, 4f); lineTo(2f, 19f)
        curveTo(6f, 18f, 9f, 18f, 12f, 21f); curveTo(15f, 18f, 18f, 18f, 22f, 19f); lineTo(22f, 4f); curveTo(18f, 3f, 15f, 3f, 12f, 6f)
    }),
    BULB(financeVector("Tip") {
        moveTo(8f, 17f); lineTo(8f, 15f); curveTo(0f, 7f, 7f, 2f, 12f, 2f); curveTo(17f, 2f, 24f, 7f, 16f, 15f); lineTo(16f, 17f); close()
        moveTo(9f, 21f); lineTo(15f, 21f); moveTo(12f, 17f); lineTo(12f, 11f)
    }),
    CAR(financeVector("Vehicle") {
        moveTo(3f, 10f); lineTo(7f, 4f); lineTo(17f, 4f); lineTo(21f, 10f); lineTo(21f, 19f); lineTo(3f, 19f); close()
        moveTo(3f, 10f); lineTo(21f, 10f); moveTo(6f, 14f); lineTo(8f, 14f); moveTo(16f, 14f); lineTo(18f, 14f)
        moveTo(6f, 19f); lineTo(6f, 22f); moveTo(18f, 19f); lineTo(18f, 22f)
    }),
    PIE(financeVector("Spending split") {
        moveTo(11f, 3f); curveTo(-1f, 3f, -1f, 22f, 12f, 22f)
        curveTo(18f, 22f, 22f, 18f, 22f, 12f); lineTo(11f, 12f); close()
        moveTo(15f, 2f); lineTo(15f, 8f); lineTo(21f, 8f); curveTo(21f, 5f, 18f, 2f, 15f, 2f)
    })
}

@Composable
fun FinanceIcon(symbol: FinanceSymbol, modifier: Modifier = Modifier,
    tint: Color = MaterialTheme.colorScheme.primary, description: String? = null) {
    Icon(symbol.vector, contentDescription = description, tint = tint, modifier = modifier.size(22.dp))
}

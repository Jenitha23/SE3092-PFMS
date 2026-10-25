package com.pfms.app.ui.screen.expense

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.dp
import java.math.BigDecimal
import java.text.NumberFormat
import java.util.Locale

internal val ExpenseTeal = Color(0xFF0D8072)
internal val ExpensePurple = Color(0xFF9A68D9)
internal val ExpenseOrange = Color(0xFFF3A04C)

@Composable
internal fun ExpenseUiTheme(content: @Composable () -> Unit) {
    com.pfms.app.ui.theme.FinanceTheme(content)
}

internal enum class ExpenseSymbol { BACK, ADD, FLASH, SEARCH, FILTER, MORE, CHEVRON, CALENDAR, CARD, BAG, HOME, DINING, CAR, REPEAT, GRID, NOTE, CHECK, EDIT, ARCHIVE, INFO, CLOSE, COFFEE, GYM, HEALTH, CART, TICKET }

/** Small native vectors keep the expense screens sharp at every display density. */
@Composable
internal fun ExpenseIcon(symbol: ExpenseSymbol, modifier: Modifier = Modifier, tint: Color = MaterialTheme.colorScheme.onSurfaceVariant) {
    Canvas(modifier.size(22.dp)) {
        val scale = size.minDimension / 24f
        val stroke = 1.8f * scale
        fun point(x: Float, y: Float) = Offset(x * scale, y * scale)
        fun line(x: Float, y: Float, x2: Float, y2: Float) = drawLine(tint, point(x,y), point(x2,y2), stroke, StrokeCap.Round)
        fun path(vararg points: Pair<Float, Float>) {
            drawPath(Path().apply {
                points.forEachIndexed { index, p -> if (index == 0) moveTo(p.first * scale, p.second * scale) else lineTo(p.first * scale, p.second * scale) }
            }, tint, style = Stroke(stroke, cap = StrokeCap.Round))
        }
        fun box(x: Float, y: Float, w: Float, h: Float, r: Float = 2f) = drawRoundRect(tint, point(x,y), Size(w*scale,h*scale), CornerRadius(r*scale), style = Stroke(stroke))
        when (symbol) {
            ExpenseSymbol.BACK -> { line(20f,12f,4f,12f); path(10f to 5f,3f to 12f,10f to 19f) }
            ExpenseSymbol.ADD -> { line(12f,5f,12f,19f); line(5f,12f,19f,12f) }
            ExpenseSymbol.FLASH -> path(13f to 2f, 6f to 12f, 12f to 12f, 10f to 22f, 18f to 11f, 12f to 11f, 14f to 2f)
            ExpenseSymbol.SEARCH -> { drawCircle(tint,6.5f*scale,point(10f,10f),style=Stroke(stroke)); line(15f,15f,21f,21f) }
            ExpenseSymbol.FILTER -> { line(4f,6f,20f,6f); line(7f,12f,17f,12f); line(10f,18f,14f,18f) }
            ExpenseSymbol.MORE -> listOf(5f,12f,19f).forEach { drawCircle(tint,1.6f*scale,point(12f,it)) }
            ExpenseSymbol.CHEVRON -> path(7f to 10f,12f to 15f,17f to 10f)
            ExpenseSymbol.CALENDAR -> { box(3f,5f,18f,16f); line(3f,10f,21f,10f); line(8f,3f,8f,7f); line(16f,3f,16f,7f); line(8f,14f,9f,14f); line(14f,14f,15f,14f); line(8f,17f,9f,17f) }
            ExpenseSymbol.CARD -> { box(3f,5f,18f,14f); line(3f,10f,21f,10f); line(7f,15f,10f,15f) }
            ExpenseSymbol.BAG -> { box(4f,7f,16f,14f); path(8f to 8f,8f to 4f,16f to 4f,16f to 8f) }
            ExpenseSymbol.HOME -> { path(3f to 11f,12f to 3f,21f to 11f); path(6f to 10f,6f to 21f,18f to 21f,18f to 10f); path(10f to 21f,10f to 15f,14f to 15f,14f to 21f) }
            ExpenseSymbol.DINING -> { line(7f,3f,7f,21f); path(3f to 3f,3f to 9f,11f to 9f,11f to 3f); path(19f to 21f,19f to 3f,16f to 5f,16f to 12f,19f to 12f) }
            ExpenseSymbol.CAR -> { box(3f,10f,18f,9f); path(5f to 10f,7f to 5f,17f to 5f,19f to 10f); line(6f,19f,6f,21f); line(18f,19f,18f,21f); line(6f,14f,8f,14f); line(16f,14f,18f,14f) }
            ExpenseSymbol.REPEAT -> { path(4f to 9f,4f to 6f,19f to 6f,19f to 10f); path(16f to 3f,20f to 6f,16f to 9f); path(20f to 15f,20f to 18f,5f to 18f,5f to 14f); path(8f to 15f,4f to 18f,8f to 21f) }
            ExpenseSymbol.GRID -> { box(3f,3f,7f,7f,1f); box(14f,3f,7f,7f,1f); box(3f,14f,7f,7f,1f); box(14f,14f,7f,7f,1f) }
            ExpenseSymbol.NOTE -> { path(5f to 3f,15f to 3f,20f to 8f,20f to 21f,5f to 21f,5f to 3f); path(15f to 3f,15f to 8f,20f to 8f); line(9f,12f,16f,12f); line(9f,16f,16f,16f) }
            ExpenseSymbol.CHECK -> path(5f to 12f,10f to 17f,20f to 6f)
            ExpenseSymbol.EDIT -> { path(4f to 16f,16f to 4f,20f to 8f,8f to 20f,3f to 21f,4f to 16f); line(13f,7f,17f,11f) }
            ExpenseSymbol.ARCHIVE -> { box(3f,3f,18f,5f,1f); path(5f to 8f,5f to 21f,19f to 21f,19f to 8f); line(9f,12f,15f,12f) }
            ExpenseSymbol.INFO -> { drawCircle(tint,9f*scale,point(12f,12f),style=Stroke(stroke)); line(12f,11f,12f,17f); drawCircle(tint,scale,point(12f,7f)) }
            ExpenseSymbol.CLOSE -> { line(6f,6f,18f,18f); line(18f,6f,6f,18f) }
            ExpenseSymbol.COFFEE -> { box(3f,7f,13f,12f); path(16f to 8f,21f to 8f,21f to 13f,16f to 13f); line(2f,22f,18f,22f); line(7f,2f,7f,4f); line(12f,2f,12f,4f) }
            ExpenseSymbol.GYM -> { line(7f,12f,17f,12f); line(3f,9f,3f,15f); line(6f,6f,6f,18f); line(18f,6f,18f,18f); line(21f,9f,21f,15f) }
            ExpenseSymbol.HEALTH -> path(2f to 13f,6f to 13f,9f to 5f,14f to 20f,18f to 10f,20f to 13f,23f to 13f)
            ExpenseSymbol.CART -> { path(2f to 3f,5f to 3f,8f to 16f,19f to 16f,22f to 7f,6f to 7f); drawCircle(tint,1.3f*scale,point(9f,21f)); drawCircle(tint,1.3f*scale,point(18f,21f)) }
            ExpenseSymbol.TICKET -> { path(3f to 5f,21f to 5f,21f to 9f,18f to 12f,21f to 15f,21f to 19f,3f to 19f,3f to 15f,6f to 12f,3f to 9f,3f to 5f); line(14f,8f,14f,10f); line(14f,14f,14f,16f) }
        }
    }
}

@Composable
internal fun ExpensePanel(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Surface(modifier.fillMaxWidth(), shape = RoundedCornerShape(18.dp), color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant), shadowElevation = 1.dp) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp), content = content)
    }
}

@Composable
internal fun ExpenseHeader(title: String, subtitle: String, onBack: () -> Unit, action: @Composable () -> Unit = {}) {
    Row(Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 16.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        IconButton(onClick = onBack, modifier = Modifier.semantics { contentDescription = "Back" }) { ExpenseIcon(ExpenseSymbol.BACK, tint = MaterialTheme.colorScheme.onSurface) }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground)
            if (subtitle.isNotBlank()) Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        action()
    }
}

@Composable
internal fun ExpenseSectionTitle(text: String, symbol: ExpenseSymbol) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        ExpenseIcon(symbol, tint = MaterialTheme.colorScheme.primary)
        Text(text, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
    }
}

@Composable
internal fun ExpenseBadge(text: String, color: Color = MaterialTheme.colorScheme.primary) {
    val dark = MaterialTheme.colorScheme.surface.luminance() < 0.5f
    val textColor = if (dark) Color(color.red + (1f - color.red) * 0.45f, color.green + (1f - color.green) * 0.45f, color.blue + (1f - color.blue) * 0.45f)
        else Color(color.red * 0.7f, color.green * 0.7f, color.blue * 0.7f)
    Surface(color = color.copy(alpha = 0.11f), shape = RoundedCornerShape(50)) {
        Text(text, Modifier.padding(horizontal = 9.dp, vertical = 4.dp), style = MaterialTheme.typography.labelSmall,
            color = textColor, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

internal fun expenseMoney(minor: Long): String = expenseMoney(BigDecimal.valueOf(minor, 2))
internal fun expenseMoney(amount: BigDecimal): String = NumberFormat.getNumberInstance(Locale.US).apply {
    minimumFractionDigits = 2; maximumFractionDigits = 2
}.format(amount)

internal fun categoryColor(name: String): Color = when {
    name.contains("rent", true) -> ExpensePurple
    name.contains("food", true) || name.contains("dining", true) || name.contains("coffee", true) -> Color(0xFFE47397)
    name.contains("transport", true) -> Color(0xFF3F96E9)
    name.contains("sub", true) -> Color(0xFF24B0B1)
    name.contains("shop", true) || name.contains("grocer", true) -> ExpenseOrange
    else -> ExpenseTeal
}

internal fun categorySymbol(name: String): ExpenseSymbol = when {
    name.contains("coffee", true) -> ExpenseSymbol.COFFEE
    name.contains("gym", true) || name.contains("fitness", true) || name.contains("wellness", true) -> ExpenseSymbol.GYM
    name.contains("health", true) || name.contains("medical", true) -> ExpenseSymbol.HEALTH
    name.contains("grocer", true) -> ExpenseSymbol.CART
    name.contains("entertain", true) || name.contains("cinema", true) -> ExpenseSymbol.TICKET
    name.contains("utilit", true) || name.contains("electric", true) -> ExpenseSymbol.FLASH
    name.equals("Other", true) -> ExpenseSymbol.GRID
    name.contains("rent", true) -> ExpenseSymbol.HOME
    name.contains("food", true) || name.contains("dining", true) || name.contains("coffee", true) -> ExpenseSymbol.DINING
    name.contains("transport", true) -> ExpenseSymbol.CAR
    name.contains("sub", true) -> ExpenseSymbol.REPEAT
    else -> ExpenseSymbol.BAG
}

@Composable
internal fun CategoryAvatar(name: String, modifier: Modifier = Modifier) {
    val color = categoryColor(name)
    Surface(modifier.size(44.dp), color = color.copy(alpha = 0.13f), shape = RoundedCornerShape(50)) {
        Box(contentAlignment = Alignment.Center) { ExpenseIcon(categorySymbol(name), tint = color) }
    }
}

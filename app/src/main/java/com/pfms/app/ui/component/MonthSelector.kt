package com.pfms.app.ui.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.pfms.app.ui.screen.income.formatMonthLong
import java.time.YearMonth

/**
 * "< October 2026 >" month switcher used by the dashboard and spending analytics (FR-29).
 * Both arrows are 48dp touch targets with content descriptions (NFR-21). The next arrow is
 * disabled on the current month because there is no data in the future.
 */
@Composable
fun MonthSelector(
    month: YearMonth,
    canGoNext: Boolean,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    modifier: Modifier = Modifier,
    contentColor: Color = MaterialTheme.colorScheme.onSurface
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        IconButton(
            onClick = onPrevious,
            modifier = Modifier
                .size(48.dp)
                .semantics { contentDescription = "Previous month" }
        ) {
            Text("\u2039", style = MaterialTheme.typography.headlineSmall, color = contentColor)
        }
        Text(
            text = formatMonthLong(month),
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
            color = contentColor
        )
        IconButton(
            onClick = onNext,
            enabled = canGoNext,
            modifier = Modifier
                .size(48.dp)
                .semantics { contentDescription = "Next month" }
        ) {
            Text(
                "\u203A",
                style = MaterialTheme.typography.headlineSmall,
                color = if (canGoNext) contentColor else contentColor.copy(alpha = 0.3f)
            )
        }
    }
}

package com.pfms.app.ui.component

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun FinancePanel(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Surface(modifier.fillMaxWidth(), shape = RoundedCornerShape(18.dp), color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp), content = content)
    }
}

@Composable
fun FinanceValue(text: String, modifier: Modifier = Modifier, large: Boolean = false,
    color: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.onSurface, compact: Boolean = false,
    align: TextAlign = TextAlign.Start) {
    BasicText(text, modifier.fillMaxWidth(), maxLines = 1,
        style = MaterialTheme.typography.titleMedium.copy(color = color, fontWeight = FontWeight.Bold, textAlign = align, lineHeight = if (large) 32.sp else if (compact) 19.sp else 23.sp),
        autoSize = TextAutoSize.StepBased(minFontSize = 10.sp, maxFontSize = if (large) 26.sp else if (compact) 12.sp else 15.sp, stepSize = .5.sp))
}

@Composable
fun FinanceSaveBar(label: String, saving: Boolean, error: String?, onSave: () -> Unit, onCancel: () -> Unit) {
    Surface(color = MaterialTheme.colorScheme.surface, shadowElevation = 3.dp) {
        Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 16.dp, vertical = 10.dp)) {
            error?.let { Text(it, Modifier.padding(bottom = 8.dp), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                TextButton(onClick = onCancel, enabled = !saving, modifier = Modifier.heightIn(min = 48.dp)) { Text("Cancel") }
                Button(onClick = onSave, enabled = !saving, modifier = Modifier.weight(1f).heightIn(min = 48.dp), shape = RoundedCornerShape(15.dp)) {
                    if (saving) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
                    else Text(label)
                }
            }
        }
    }
}

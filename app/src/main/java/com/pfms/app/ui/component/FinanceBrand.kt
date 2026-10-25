package com.pfms.app.ui.component

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun FinanceBrand(modifier: Modifier = Modifier) {
    Row(modifier.widthIn(max = 340.dp), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
        Surface(Modifier.size(38.dp), shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.primary) {
            Box(contentAlignment = Alignment.Center) { PfmsLogo(Modifier.size(23.dp), barColor = Color.White) }
        }
        BasicText(buildAnnotatedString {
            append("TrackMy")
            withStyle(SpanStyle(color = MaterialTheme.colorScheme.primary)) { append("Finances") }
        }, maxLines = 1, style = MaterialTheme.typography.titleLarge.copy(color = MaterialTheme.colorScheme.onSurface),
            autoSize = TextAutoSize.StepBased(minFontSize = 12.sp, maxFontSize = 21.sp, stepSize = .5.sp))
    }
}

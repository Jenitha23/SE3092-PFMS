package com.pfms.app.ui.component

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pfms.app.domain.validation.PasswordStrength
import java.util.Locale

/** Soft lighting is drawn at device resolution and never stretches screenshot artwork. */
@Composable
fun AuthBackdrop(modifier: Modifier = Modifier, content: @Composable BoxScope.() -> Unit) {
    val colors = MaterialTheme.colorScheme
    Box(modifier.background(colors.background).drawBehind {
        drawRect(Brush.radialGradient(
            colors = listOf(colors.primaryContainer.copy(alpha = 0.55f), Color.Transparent),
            center = Offset(size.width * 0.94f, size.height * 0.12f), radius = size.width * 0.65f
        ))
        drawRect(Brush.radialGradient(
            colors = listOf(colors.primaryContainer.copy(alpha = 0.5f), Color.Transparent),
            center = Offset(0f, size.height * 0.55f), radius = size.width * 0.6f
        ))
    }, content = content)
}

@Composable
fun AuthHeader(isRegistration: Boolean, compact: Boolean = false) {
    val colors = MaterialTheme.colorScheme
    Row(verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Surface(shape = RoundedCornerShape(13.dp), color = colors.primary,
            shadowElevation = 4.dp, modifier = Modifier.size(if (compact) 34.dp else 38.dp)) {
            Canvas(Modifier.padding(11.dp)) {
                listOf(0.55f, 0.05f, 0.33f).forEachIndexed { index, top ->
                    val x = size.width * (0.15f + index * 0.35f)
                    drawLine(Color.White, Offset(x, size.height * top), Offset(x, size.height),
                        strokeWidth = 2.dp.toPx(), cap = StrokeCap.Round)
                }
            }
        }
        Text(buildAnnotatedString {
                append("TrackMy")
                pushStyle(SpanStyle(color = colors.primary))
                append("Finances")
                pop()
            }, style = if (compact) MaterialTheme.typography.titleLarge.copy(fontSize = 18.sp)
                else MaterialTheme.typography.titleLarge,
                color = colors.onSurface,
                modifier = Modifier.padding(vertical = 6.dp))
    }
    Spacer(Modifier.height(if (compact) 8.dp else 14.dp))
    Text(if (isRegistration) "Create Account" else "Welcome Back",
        style = MaterialTheme.typography.headlineLarge.copy(fontSize = 24.sp,
            lineHeight = if (compact) 30.sp else 36.sp), color = colors.onBackground,
        textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
    Spacer(Modifier.height(if (compact) 4.dp else 6.dp))
    Text(if (isRegistration) "Start your journey to financial freedom today."
        else "Enter your credentials to access your financial dashboard.",
        style = if (compact) MaterialTheme.typography.bodyLarge.copy(fontSize = 14.sp, lineHeight = 20.sp)
            else MaterialTheme.typography.bodyLarge, color = colors.onSurfaceVariant,
        textAlign = TextAlign.Center, modifier = Modifier.widthIn(max = 320.dp).fillMaxWidth())
}

@Composable
fun AuthTabs(isRegistration: Boolean, enabled: Boolean, onLogin: () -> Unit, onRegister: () -> Unit,
    compact: Boolean = false) {
    Surface(color = MaterialTheme.colorScheme.secondaryContainer,
        shape = RoundedCornerShape(50), modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.selectableGroup().padding(4.dp)) {
            listOf("Log In", "Sign Up").forEachIndexed { index, label ->
                val selected = isRegistration == (index == 1)
                Surface(color = if (selected) MaterialTheme.colorScheme.surface else Color.Transparent,
                    shadowElevation = if (selected) 1.dp else 0.dp,
                    shape = RoundedCornerShape(50),
                    modifier = Modifier.weight(1f).clip(RoundedCornerShape(50))
                        .selectable(selected, enabled = enabled, role = Role.Tab,
                            onClick = if (index == 0) onLogin else onRegister)) {
                    Row(Modifier.heightIn(min = if (compact) 40.dp else 42.dp).padding(horizontal = 8.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically) {
                        if (selected && index == 0) {
                            Text("•", color = MaterialTheme.colorScheme.primary)
                            Spacer(Modifier.width(6.dp))
                        }
                        Text(label, style = MaterialTheme.typography.labelLarge,
                            color = if (selected) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurfaceVariant)
                        if (selected && index == 1) {
                            Spacer(Modifier.width(6.dp))
                            Text("•", color = MaterialTheme.colorScheme.primary)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun AuthInput(
    value: String, onValueChange: (String) -> Unit, label: String, placeholder: String,
    enabled: Boolean, error: String?, modifier: Modifier = Modifier,
    keyboardType: KeyboardType = KeyboardType.Text, imeAction: ImeAction = ImeAction.Next,
    compact: Boolean = false,
    isPassword: Boolean = false, passwordVisible: Boolean = false,
    onToggleVisibility: () -> Unit = {}, onDone: () -> Unit = {},
    leadingIcon: @Composable () -> Unit
) {
    Column(modifier.fillMaxWidth()) {
        Text(label.uppercase(Locale.ROOT), style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 4.dp, bottom = if (compact) 4.dp else 6.dp))
        OutlinedTextField(value = value, onValueChange = onValueChange,
            modifier = Modifier.fillMaxWidth().semantics { contentDescription = label },
            enabled = enabled, singleLine = true, isError = error != null,
            textStyle = MaterialTheme.typography.bodyLarge,
            placeholder = { Text(placeholder, style = MaterialTheme.typography.bodyLarge) },
            leadingIcon = leadingIcon,
            trailingIcon = if (isPassword) {{
                IconButton(onClick = onToggleVisibility, enabled = enabled,
                    modifier = Modifier.semantics {
                        contentDescription = if (passwordVisible) "Hide password" else "Show password"
                    }) {
                    Icon(if (passwordVisible) AuthEyeIcon else AuthEyeOffIcon, contentDescription = null,
                        modifier = Modifier.size(22.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }} else null,
            visualTransformation = if (isPassword && !passwordVisible) PasswordVisualTransformation()
                else VisualTransformation.None,
            keyboardOptions = KeyboardOptions(keyboardType = keyboardType, imeAction = imeAction),
            keyboardActions = KeyboardActions(onDone = { onDone() }),
            shape = RoundedCornerShape(16.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                errorContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                unfocusedBorderColor = Color.Transparent,
                disabledBorderColor = Color.Transparent,
                focusedBorderColor = MaterialTheme.colorScheme.primary,
                focusedPlaceholderColor = MaterialTheme.colorScheme.outline,
                unfocusedPlaceholderColor = MaterialTheme.colorScheme.outline,
                unfocusedLeadingIconColor = MaterialTheme.colorScheme.onSurfaceVariant
            ))
        if (error != null) Text(error, color = MaterialTheme.colorScheme.error,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(start = 4.dp, top = 4.dp))
    }
}

@Composable
fun AuthPrimaryButton(text: String, isLoading: Boolean, onClick: () -> Unit, compact: Boolean = false) {
    Button(onClick = onClick, enabled = !isLoading, shape = RoundedCornerShape(50),
        elevation = ButtonDefaults.buttonElevation(defaultElevation = 3.dp),
        modifier = Modifier.fillMaxWidth().heightIn(min = if (compact) 48.dp else 54.dp)) {
        if (isLoading) CircularProgressIndicator(Modifier.size(22.dp).semantics {
            contentDescription = "$text in progress"
        }, strokeWidth = 2.dp)
        else {
            Text(text, style = MaterialTheme.typography.labelLarge)
            Spacer(Modifier.width(10.dp))
            Canvas(Modifier.size(18.dp)) {
                val stroke = 1.8.dp.toPx()
                drawLine(Color.White, Offset(0f, size.height / 2), Offset(size.width, size.height / 2), stroke)
                drawLine(Color.White, Offset(size.width * 0.6f, size.height * 0.15f),
                    Offset(size.width * 0.95f, size.height / 2), stroke)
                drawLine(Color.White, Offset(size.width * 0.6f, size.height * 0.85f),
                    Offset(size.width * 0.95f, size.height / 2), stroke)
            }
        }
    }
}

@Composable
fun AuthDivider(text: String) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        HorizontalDivider(Modifier.weight(1f), color = MaterialTheme.colorScheme.outlineVariant)
        Text(text, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        HorizontalDivider(Modifier.weight(1f), color = MaterialTheme.colorScheme.outlineVariant)
    }
}

@Composable
fun AuthGoogleButton(isRegistration: Boolean, enabled: Boolean, onClick: () -> Unit, compact: Boolean = false) {
    Surface(onClick = onClick, enabled = enabled,
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        shadowElevation = 1.dp, modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.heightIn(min = if (compact) 48.dp else 54.dp).padding(horizontal = 12.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
            GoogleLogo(Modifier.size(22.dp))
            Spacer(Modifier.width(14.dp))
            Text(if (isRegistration) "Sign up with Google" else "Continue with Google",
                style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurface)
        }
    }
}

@Composable
fun AuthCheckRow(checked: Boolean, enabled: Boolean, onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier, content: @Composable RowScope.() -> Unit) {
    Row(modifier.heightIn(min = 48.dp).clip(RoundedCornerShape(12.dp))
        .toggleable(checked, enabled = enabled, role = Role.Checkbox, onValueChange = onCheckedChange),
        verticalAlignment = Alignment.CenterVertically) {
        val tint = MaterialTheme.colorScheme.primary.copy(alpha = if (enabled) 1f else 0.4f)
        Surface(shape = RoundedCornerShape(7.dp), border = BorderStroke(1.dp, tint),
            color = if (checked) tint else Color.Transparent,
            modifier = Modifier.size(20.dp)) {
            if (checked) Canvas(Modifier.padding(4.dp)) {
                drawLine(Color.White, Offset(0f, size.height * 0.5f),
                    Offset(size.width * 0.35f, size.height * 0.85f), 1.8.dp.toPx(), StrokeCap.Round)
                drawLine(Color.White, Offset(size.width * 0.35f, size.height * 0.85f),
                    Offset(size.width, size.height * 0.15f), 1.8.dp.toPx(), StrokeCap.Round)
            }
        }
        Spacer(Modifier.width(8.dp))
        content()
    }
}

@Composable
fun AuthPasswordStrength(strength: PasswordStrength) {
    val colors = MaterialTheme.colorScheme
    val label = when (strength) {
        PasswordStrength.EMPTY -> "Use at least 8 characters"
        PasswordStrength.WEAK -> "Too short: use at least 8 characters"
        PasswordStrength.FAIR -> "Add numbers & symbols for strength"
        PasswordStrength.GOOD -> "Good: add letters, numbers & symbols"
        PasswordStrength.STRONG -> "Strong: includes numbers & symbols"
    }
    Column(Modifier.fillMaxWidth().testTag("auth_password_strength").semantics {
        progressBarRangeInfo = ProgressBarRangeInfo(strength.segments.toFloat(), 0f..4f, 3)
    }) {
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            repeat(4) { index ->
                Box(Modifier.weight(1f).height(6.dp).clip(RoundedCornerShape(50))
                    .background(if (index < strength.segments) colors.primary else colors.outlineVariant))
            }
        }
        Spacer(Modifier.height(5.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(label, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodySmall,
                color = if (strength.segments >= 3) colors.primary else colors.onSurfaceVariant)
            Text("8+ chars", style = MaterialTheme.typography.bodySmall, color = colors.outline)
        }
    }
}

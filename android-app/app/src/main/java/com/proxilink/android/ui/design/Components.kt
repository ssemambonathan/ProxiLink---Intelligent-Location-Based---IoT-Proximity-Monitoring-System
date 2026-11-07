package com.proxilink.android.ui.design

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.proxilink.android.domain.proximity.ProximityLevel
import com.proxilink.android.ui.theme.Palette

val ScreenHorizontalPadding = 20.dp
val MainBottomBarInset = 108.dp

@Composable
fun ProxiBackground(
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    0f to Palette.BackgroundTop,
                    0.45f to Palette.Background,
                    1f to Palette.BackgroundDeep,
                ),
            ),
    ) {
        Canvas(Modifier.fillMaxSize()) {
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(Palette.Cyan.copy(alpha = 0.11f), Color.Transparent),
                    center = Offset(size.width * 0.85f, size.height * 0.06f),
                    radius = size.width * 0.75f,
                ),
                radius = size.width * 0.75f,
                center = Offset(size.width * 0.85f, size.height * 0.06f),
            )
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(Palette.Violet.copy(alpha = 0.10f), Color.Transparent),
                    center = Offset(size.width * 0.05f, size.height * 0.55f),
                    radius = size.width * 0.8f,
                ),
                radius = size.width * 0.8f,
                center = Offset(size.width * 0.05f, size.height * 0.55f),
            )
        }
        content()
    }
}

@Composable
fun ScreenScaffold(
    title: String,
    subtitle: String? = null,
    onBack: (() -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {},
    content: @Composable ColumnScope.() -> Unit,
) {
    ProxiBackground {
        Column(
            Modifier
                .fillMaxSize()
                .statusBarsPadding(),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = ScreenHorizontalPadding, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (onBack != null) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(Palette.SurfaceHigh)
                            .clickable(onClick = onBack),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = Icons.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = Palette.TextPrimary,
                            modifier = Modifier.size(19.dp),
                        )
                    }
                    Spacer(Modifier.width(14.dp))
                }
                Column(Modifier.weight(1f)) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.headlineSmall,
                        color = MaterialTheme.colorScheme.onBackground,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (subtitle != null) {
                        Text(
                            text = subtitle,
                            style = MaterialTheme.typography.bodySmall,
                            color = Palette.TextSecondary,
                            maxLines = 2,
                        )
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically, content = actions)
            }
            Column(Modifier.weight(1f).fillMaxWidth(), content = content)
        }
    }
}

@Composable
fun ProxiCard(
    modifier: Modifier = Modifier,
    accent: Color? = null,
    onClick: (() -> Unit)? = null,
    contentPadding: PaddingValues = PaddingValues(18.dp),
    content: @Composable ColumnScope.() -> Unit,
) {
    val shape = RoundedCornerShape(22.dp)
    val base = modifier
        .fillMaxWidth()
        .clip(shape)
        .background(Brush.verticalGradient(listOf(Palette.SurfaceHigh, Palette.Surface)))
        .border(1.dp, Color.White.copy(alpha = 0.06f), shape)
        .let { if (onClick != null) it.clickable(onClick = onClick) else it }

    Column(base) {
        if (accent != null) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(2.dp)
                    .background(
                        Brush.horizontalGradient(
                            listOf(accent.copy(alpha = 0.9f), accent.copy(alpha = 0.05f)),
                        ),
                    ),
            )
        }
        Column(Modifier.padding(contentPadding), content = content)
    }
}

@Composable
fun StatTile(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    hint: String? = null,
    accent: Color = Palette.Cyan,
    icon: ImageVector? = null,
) {
    ProxiCard(modifier = modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    text = label.uppercase(),
                    style = MaterialTheme.typography.labelSmall,
                    color = Palette.TextTertiary,
                )
                Spacer(Modifier.height(6.dp))
                Text(text = value, style = MaterialTheme.typography.headlineMedium, color = Palette.TextPrimary)
                if (hint != null) {
                    Spacer(Modifier.height(2.dp))
                    Text(text = hint, style = MaterialTheme.typography.bodySmall, color = accent)
                }
            }
            if (icon != null) {
                Box(
                    Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(accent.copy(alpha = 0.12f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(icon, contentDescription = null, tint = accent, modifier = Modifier.size(20.dp))
                }
            }
        }
    }
}

@Composable
fun SectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text = title, style = MaterialTheme.typography.titleLarge, color = Palette.TextPrimary)
        Spacer(Modifier.weight(1f))
        if (actionLabel != null && onAction != null) {
            Text(
                text = actionLabel,
                style = MaterialTheme.typography.labelMedium,
                color = Palette.Cyan,
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .clickable(onClick = onAction)
                    .padding(horizontal = 6.dp, vertical = 4.dp),
            )
        }
    }
}

@Composable
fun StatusChip(
    text: String,
    color: Color,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(50))
            .background(color.copy(alpha = 0.14f))
            .border(1.dp, color.copy(alpha = 0.35f), RoundedCornerShape(50))
            .padding(horizontal = 10.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(12.dp))
        }
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            color = color,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
fun ProximityBadge(
    level: ProximityLevel,
    modifier: Modifier = Modifier,
    simulated: Boolean = false,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = modifier) {
        StatusChip(text = level.label.uppercase(), color = level.accent())
        if (simulated) {
            StatusChip(text = "SIMULATED", color = Palette.Violet)
        }
    }
}

@Composable
fun SignalBars(
    rssi: Int?,
    modifier: Modifier = Modifier,
    color: Color = Palette.Cyan,
) {
    val level = signalBars(rssi)
    Row(
        modifier = modifier.height(18.dp),
        verticalAlignment = Alignment.Bottom,
        horizontalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        repeat(4) { index ->
            Box(
                Modifier
                    .width(4.dp)
                    .height((5 + index * 4).dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(if (index < level) color else Palette.Outline.copy(alpha = 0.7f)),
            )
        }
    }
}

@Composable
fun ProximityGauge(
    level: ProximityLevel,
    smoothedRssi: Double?,
    distanceM: Double?,
    modifier: Modifier = Modifier,
) {
    val bands = listOf(
        ProximityLevel.OUT_OF_RANGE,
        ProximityLevel.FAR,
        ProximityLevel.NEARBY,
        ProximityLevel.VERY_CLOSE,
        ProximityLevel.IMMEDIATE,
    )
    ProxiCard(modifier = modifier) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
            Column(Modifier.weight(1f)) {
                Text("PROXIMITY BAND", style = MaterialTheme.typography.labelSmall, color = Palette.TextTertiary)
                Spacer(Modifier.height(6.dp))
                Text(level.label, style = MaterialTheme.typography.headlineMedium, color = level.accent())
                Text(level.description, style = MaterialTheme.typography.bodySmall, color = Palette.TextSecondary)
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(smoothedLabel(smoothedRssi), style = MaterialTheme.typography.titleMedium, color = Palette.TextPrimary)
                Text(distanceLabel(distanceM), style = MaterialTheme.typography.bodySmall, color = Palette.TextSecondary)
            }
        }
        Spacer(Modifier.height(16.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            bands.forEach { band ->
                val index = bands.indexOf(band)
                val selectedIndex = bands.indexOf(level)
                val active = band == level
                val reached = level != ProximityLevel.OUT_OF_RANGE && index <= selectedIndex
                Box(
                    Modifier
                        .weight(1f)
                        .height(10.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(
                            when {
                                active -> band.accent()
                                reached -> band.accent().copy(alpha = 0.4f)
                                else -> Palette.Outline.copy(alpha = 0.55f)
                            },
                        ),
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        Row(Modifier.fillMaxWidth()) {
            Text("out of range", style = MaterialTheme.typography.labelSmall, color = Palette.TextTertiary)
            Spacer(Modifier.weight(1f))
            Text("immediate", style = MaterialTheme.typography.labelSmall, color = Palette.TextTertiary)
        }
        Spacer(Modifier.height(12.dp))
        Text(
            "Bands come from the smoothed RSSI. The metre value is a log-distance estimate, not a measurement.",
            style = MaterialTheme.typography.bodySmall,
            color = Palette.TextTertiary,
        )
    }
}

@Composable
fun LoadingSkeleton(
    modifier: Modifier = Modifier,
    height: Dp = 16.dp,
    corner: Dp = 8.dp,
) {
    val transition = rememberInfiniteTransition(label = "skeleton")
    val progress by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1_300, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "skeleton-progress",
    )
    Box(
        modifier
            .height(height)
            .clip(RoundedCornerShape(corner))
            .background(
                Brush.linearGradient(
                    colors = listOf(
                        Palette.SurfaceHigh,
                        Palette.SurfaceMuted,
                        Palette.SurfaceHigh,
                    ),
                    start = Offset(progress * 900f, 0f),
                    end = Offset(progress * 900f + 320f, 320f),
                ),
            ),
    )
}

@Composable
fun SkeletonCard() {
    ProxiCard {
        LoadingSkeleton(Modifier.fillMaxWidth(0.4f), height = 12.dp)
        Spacer(Modifier.height(12.dp))
        LoadingSkeleton(Modifier.fillMaxWidth(0.7f), height = 22.dp)
        Spacer(Modifier.height(10.dp))
        LoadingSkeleton(Modifier.fillMaxWidth(), height = 12.dp)
    }
}

@Composable
fun EmptyState(
    icon: ImageVector,
    title: String,
    message: String,
    modifier: Modifier = Modifier,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier
                .size(56.dp)
                .clip(CircleShape)
                .background(Palette.SurfaceHigh),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = null, tint = Palette.Cyan, modifier = Modifier.size(26.dp))
        }
        Spacer(Modifier.height(14.dp))
        Text(title, style = MaterialTheme.typography.titleMedium, color = Palette.TextPrimary)
        Spacer(Modifier.height(4.dp))
        Text(
            message,
            style = MaterialTheme.typography.bodySmall,
            color = Palette.TextSecondary,
            modifier = Modifier.padding(horizontal = 12.dp),
        )
        if (actionLabel != null && onAction != null) {
            Spacer(Modifier.height(14.dp))
            PrimaryButton(text = actionLabel, onClick = onAction, modifier = Modifier.width(200.dp))
        }
    }
}

@Composable
fun ErrorBanner(
    message: String,
    modifier: Modifier = Modifier,
    onRetry: (() -> Unit)? = null,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(Palette.Coral.copy(alpha = 0.12f))
            .border(1.dp, Palette.Coral.copy(alpha = 0.3f), RoundedCornerShape(14.dp))
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Filled.Error, contentDescription = null, tint = Palette.Coral, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(10.dp))
        Text(
            text = message,
            style = MaterialTheme.typography.bodySmall,
            color = Palette.TextPrimary,
            modifier = Modifier.weight(1f),
        )
        if (onRetry != null) {
            TextButton(onClick = onRetry) {
                Text("Retry", style = MaterialTheme.typography.labelMedium, color = Palette.Coral)
            }
        }
    }
}

@Composable
fun InfoBanner(
    text: String,
    modifier: Modifier = Modifier,
    accent: Color = Palette.Cyan,
    icon: ImageVector = Icons.Filled.Info,
    trailing: (@Composable () -> Unit)? = null,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(accent.copy(alpha = 0.10f))
            .border(1.dp, accent.copy(alpha = 0.25f), RoundedCornerShape(14.dp))
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = accent, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(10.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall,
            color = Palette.TextSecondary,
            modifier = Modifier.weight(1f),
        )
        trailing?.let {
            Spacer(Modifier.width(8.dp))
            it()
        }
    }
}

@Composable
fun SimulationBanner(modifier: Modifier = Modifier) {
    InfoBanner(
        text = "Simulation mode: synthetic BLE devices, not real hardware.",
        modifier = modifier,
        accent = Palette.Violet,
        icon = Icons.Filled.Warning,
    )
}

@Composable
fun SuccessBanner(text: String, modifier: Modifier = Modifier) {
    InfoBanner(
        text = text,
        modifier = modifier,
        accent = Palette.Mint,
        icon = Icons.Filled.CheckCircle,
    )
}

@Composable
fun PrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    loading: Boolean = false,
) {
    Button(
        onClick = onClick,
        enabled = enabled && !loading,
        modifier = modifier
            .fillMaxWidth()
            .height(52.dp),
        shape = RoundedCornerShape(16.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = Palette.Cyan,
            contentColor = Palette.Background,
            disabledContainerColor = Palette.SurfaceMuted,
            disabledContentColor = Palette.TextTertiary,
        ),
    ) {
        if (loading) {
            CircularProgressIndicator(
                modifier = Modifier.size(18.dp),
                color = Palette.Background,
                strokeWidth = 2.dp,
            )
            Spacer(Modifier.width(10.dp))
        }
        Text(text, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun SecondaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    icon: ImageVector? = null,
) {
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier
            .fillMaxWidth()
            .height(50.dp),
        shape = RoundedCornerShape(16.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, Palette.Outline),
        colors = ButtonDefaults.outlinedButtonColors(
            contentColor = Palette.TextPrimary,
            disabledContentColor = Palette.TextTertiary,
        ),
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp), tint = Palette.Cyan)
            Spacer(Modifier.width(8.dp))
        }
        Text(text, style = MaterialTheme.typography.labelLarge)
    }
}

@Composable
fun KeyValueRow(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    valueColor: Color = Palette.TextPrimary,
    monospace: Boolean = false,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 5.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = Palette.TextTertiary,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall,
            color = valueColor,
            fontWeight = if (monospace) FontWeight.Medium else FontWeight.Normal,
        )
    }
}

@Composable
fun SelectablePillRow(
    options: List<String>,
    selected: String,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        options.forEach { option ->
            val isSelected = option == selected
            Box(
                Modifier
                    .clip(RoundedCornerShape(50))
                    .background(if (isSelected) Palette.Cyan.copy(alpha = 0.16f) else Palette.SurfaceHigh)
                    .border(
                        1.dp,
                        if (isSelected) Palette.Cyan.copy(alpha = 0.55f) else Palette.Outline.copy(alpha = 0.4f),
                        RoundedCornerShape(50),
                    )
                    .clickable { onSelect(option) }
                    .padding(horizontal = 14.dp, vertical = 8.dp),
            ) {
                Text(
                    text = option,
                    style = MaterialTheme.typography.labelMedium,
                    color = if (isSelected) Palette.Cyan else Palette.TextSecondary,
                )
            }
        }
    }
}

@Composable
fun AppTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    placeholder: String? = null,
    keyboardType: KeyboardType = KeyboardType.Text,
    isPassword: Boolean = false,
    passwordVisible: Boolean = false,
    onTogglePassword: (() -> Unit)? = null,
    error: String? = null,
    singleLine: Boolean = true,
    minLines: Int = 1,
    leadingIcon: ImageVector? = null,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier.fillMaxWidth(),
        label = { Text(label, style = MaterialTheme.typography.bodySmall) },
        placeholder = placeholder?.let { { Text(it, style = MaterialTheme.typography.bodySmall) } },
        singleLine = singleLine,
        minLines = minLines,
        isError = error != null,
        supportingText = error?.let { { Text(it, color = Palette.Coral, style = MaterialTheme.typography.labelSmall) } },
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
        leadingIcon = leadingIcon?.let {
            { Icon(it, contentDescription = null, tint = Palette.TextTertiary, modifier = Modifier.size(18.dp)) }
        },
        trailingIcon = if (isPassword) {
            {
                IconButton(onClick = { onTogglePassword?.invoke() }) {
                    Icon(
                        imageVector = if (passwordVisible) {
                            Icons.Filled.VisibilityOff
                        } else {
                            Icons.Filled.Visibility
                        },
                        contentDescription = if (passwordVisible) "Hide password" else "Show password",
                        tint = Palette.TextSecondary,
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
        } else {
            null
        },
        visualTransformation = if (isPassword && !passwordVisible) {
            PasswordVisualTransformation()
        } else {
            VisualTransformation.None
        },
        shape = RoundedCornerShape(14.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedTextColor = Palette.TextPrimary,
            unfocusedTextColor = Palette.TextPrimary,
            focusedContainerColor = Palette.Surface,
            unfocusedContainerColor = Palette.Surface,
            focusedBorderColor = Palette.Cyan,
            unfocusedBorderColor = Palette.Outline,
            errorBorderColor = Palette.Coral,
            cursorColor = Palette.Cyan,
            focusedLabelColor = Palette.Cyan,
            unfocusedLabelColor = Palette.TextSecondary,
            focusedPlaceholderColor = Palette.TextTertiary,
            unfocusedPlaceholderColor = Palette.TextTertiary,
        ),
    )
}

@Composable
fun ToggleRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = if (enabled) Palette.TextPrimary else Palette.TextTertiary,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = Palette.TextSecondary,
            )
        }
        Spacer(Modifier.width(12.dp))
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            enabled = enabled,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Palette.Background,
                checkedTrackColor = Palette.Cyan,
                uncheckedThumbColor = Palette.TextSecondary,
                uncheckedTrackColor = Palette.SurfaceMuted,
                uncheckedBorderColor = Palette.Outline,
            ),
        )
    }
}

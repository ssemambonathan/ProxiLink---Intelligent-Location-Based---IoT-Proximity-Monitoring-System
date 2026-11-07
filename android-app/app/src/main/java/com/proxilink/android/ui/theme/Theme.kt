package com.proxilink.android.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.proxilink.android.domain.model.ThemeMode

private val DarkScheme = darkColorScheme(
    primary = Palette.Cyan,
    onPrimary = Palette.Background,
    primaryContainer = Palette.CyanDeep,
    onPrimaryContainer = Palette.TextPrimary,
    secondary = Palette.Violet,
    onSecondary = Palette.Background,
    secondaryContainer = Palette.SurfaceMuted,
    onSecondaryContainer = Palette.TextPrimary,
    tertiary = Palette.Amber,
    background = Palette.Background,
    onBackground = Palette.TextPrimary,
    surface = Palette.Surface,
    onSurface = Palette.TextPrimary,
    surfaceVariant = Palette.SurfaceHigh,
    onSurfaceVariant = Palette.TextSecondary,
    surfaceTint = Palette.Cyan,
    error = Palette.Coral,
    onError = Color(0xFF2B0707),
    outline = Palette.Outline,
    outlineVariant = Palette.OutlineSoft,
    scrim = Color(0xCC000000),
)

private val LightScheme = lightColorScheme(
    primary = Color(0xFF0E8F85),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFB8F2EB),
    onPrimaryContainer = Color(0xFF00211E),
    secondary = Color(0xFF5B4BD6),
    onSecondary = Color.White,
    background = Palette.LightBackground,
    onBackground = Palette.LightTextPrimary,
    surface = Palette.LightSurface,
    onSurface = Palette.LightTextPrimary,
    surfaceVariant = Palette.LightSurfaceHigh,
    onSurfaceVariant = Palette.LightTextSecondary,
    error = Color(0xFFB3261E),
    onError = Color.White,
    outline = Color(0xFFB6C2D6),
)

@Composable
fun ProxiLinkTheme(
    themeMode: ThemeMode = ThemeMode.DARK,
    content: @Composable () -> Unit,
) {
    val dark = when (themeMode) {
        ThemeMode.DARK -> true
        ThemeMode.LIGHT -> false
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
    }
    MaterialTheme(
        colorScheme = if (dark) DarkScheme else LightScheme,
        typography = ProxiLinkTypography,
        shapes = ProxiLinkShapes,
        content = content,
    )
}

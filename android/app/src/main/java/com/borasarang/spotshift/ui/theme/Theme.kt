package com.borasarang.spotshift.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkColors = darkColorScheme(
    primary = AccentSky,
    onPrimary = Color(0xFF0F172A),
    secondary = SuccessGreen,
    onSecondary = Color(0xFF0F172A),
    background = DarkBackground,
    onBackground = PrimaryText,
    surface = CardBackground,
    onSurface = PrimaryText,
    surfaceVariant = SurfaceMuted,
    onSurfaceVariant = SecondaryText,
    outline = BorderColor,
    error = DangerRed,
    onError = Color.White
)

private val LightColors = lightColorScheme(
    primary = AccentSky,
    onPrimary = Color(0xFF0F172A),
    secondary = SuccessGreen,
    onSecondary = Color.White,
    background = LightBackground,
    onBackground = LightPrimaryText,
    surface = LightCard,
    onSurface = LightPrimaryText,
    surfaceVariant = Color(0xFFE2E8F0),
    onSurfaceVariant = LightSecondaryText,
    outline = Color(0xFFCBD5E1),
    error = DangerRed,
    onError = Color.White
)

@Composable
fun SpotShiftTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        typography = AppTypography,
        content = content
    )
}

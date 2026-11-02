package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val KormoKariDarkColorScheme = darkColorScheme(
    primary = KormoKariPrimary,
    onPrimary = Color.White,
    primaryContainer = KormoKariPrimaryDark,
    onPrimaryContainer = Color.White,
    secondary = KormoKariBlue,
    onSecondary = Color.White,
    tertiary = KormoKariPalePink,
    background = KormoKariNavy,
    onBackground = Color.White,
    surface = Color(0xFF1F2937),
    onSurface = Color.White,
    surfaceVariant = Color(0xFF374151),
    onSurfaceVariant = Color(0xFFD1D5DB),
    outline = Color(0xFF4B5563)
)

private val KormoKariLightColorScheme = lightColorScheme(
    primary = KormoKariPrimary,
    onPrimary = Color.White,
    primaryContainer = KormoKariPalePink,
    onPrimaryContainer = KormoKariPrimaryDark,
    secondary = KormoKariBlue,
    onSecondary = Color.White,
    tertiary = KormoKariPrimaryDark,
    background = KormoKariBackground,
    onBackground = KormoKariNavy,
    surface = KormoKariSurface,
    onSurface = KormoKariNavy,
    surfaceVariant = KormoKariPalePink,
    onSurfaceVariant = KormoKariNavy,
    outline = KormoKariBorder
)

@Composable
fun KormoKariTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) KormoKariDarkColorScheme else KormoKariLightColorScheme
    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}

// Backward-compatible alias for existing code
@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    KormoKariTheme(darkTheme = darkTheme, content = content)
}

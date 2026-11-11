package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkColorScheme = darkColorScheme(
    primary = AsisEmerald,
    onPrimary = Color(0xFF042F24),
    primaryContainer = AsisEmeraldContainer,
    onPrimaryContainer = AsisEmeraldLight,
    secondary = AsisCyan,
    onSecondary = Color(0xFF002B36),
    secondaryContainer = Color(0xFF164E63),
    onSecondaryContainer = Color(0xFFA5F3FC),
    tertiary = AsisAmber,
    onTertiary = Color(0xFF3B2000),
    tertiaryContainer = AsisAmberContainer,
    onTertiaryContainer = Color(0xFFFDE68A),
    error = AsisRose,
    onError = Color.White,
    errorContainer = AsisRoseContainer,
    onErrorContainer = Color(0xFFFECDD3),
    background = AsisNavyDark,
    onBackground = Color(0xFFF1F5F9),
    surface = AsisSurfaceDark,
    onSurface = Color(0xFFF1F5F9),
    surfaceVariant = AsisCardDark,
    onSurfaceVariant = Color(0xFF94A3B8),
    outline = AsisBorderDark
)

private val LightColorScheme = lightColorScheme(
    primary = AsisEmeraldDark,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD1FAE5),
    onPrimaryContainer = Color(0xFF064E3B),
    secondary = Color(0xFF0284C7),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFE0F2FE),
    onSecondaryContainer = Color(0xFF0C4A6E),
    tertiary = Color(0xFFD97706),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFFEF3C7),
    onTertiaryContainer = Color(0xFF78350F),
    error = Color(0xFFE11D48),
    onError = Color.White,
    errorContainer = Color(0xFFFFE4E6),
    onErrorContainer = Color(0xFF881337),
    background = AsisBackgroundLight,
    onBackground = AsisSlateText,
    surface = AsisSurfaceLight,
    onSurface = AsisSlateText,
    surfaceVariant = AsisCardLight,
    onSurfaceVariant = AsisMutedText,
    outline = Color(0xFFCBD5E1)
)

@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}

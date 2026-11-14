package com.example.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkColorScheme = darkColorScheme(
    primary = AsisEmerald,
    onPrimary = Color(0xFF042213),
    primaryContainer = AsisEmeraldContainer,
    onPrimaryContainer = AsisEmeraldLight,
    secondary = AsisCyan,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFF172E54),
    onSecondaryContainer = Color(0xFFBFDBFE),
    tertiary = AsisAmber,
    onTertiary = Color(0xFF2E1600),
    tertiaryContainer = AsisAmberContainer,
    onTertiaryContainer = Color(0xFFFDE68A),
    error = AsisRose,
    onError = Color.White,
    errorContainer = AsisRoseContainer,
    onErrorContainer = Color(0xFFFECDD3),
    background = AsisNavyDark,
    onBackground = Color(0xFFF8FAFC),
    surface = AsisSurfaceDark,
    onSurface = Color(0xFFF8FAFC),
    surfaceVariant = AsisCardDark,
    onSurfaceVariant = Color(0xFF94A3B8),
    surfaceContainerLowest = Color(0xFF0C0F17),
    surfaceContainerLow = AsisSurfaceDark,
    surfaceContainer = AsisCardDark,
    surfaceContainerHigh = AsisCardElevatedDark,
    outline = AsisBorderDark,
    outlineVariant = AsisBorderDark.copy(alpha = 0.78f)
)

private val LightColorScheme = lightColorScheme(
    primary = AsisEmeraldDark,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD1FAE5),
    onPrimaryContainer = Color(0xFF064E3B),
    secondary = Color(0xFF2563EB),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFDBEAFE),
    onSecondaryContainer = Color(0xFF1E3A8A),
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
    surfaceContainerLow = AsisSurfaceLight,
    surfaceContainer = AsisCardLight,
    surfaceContainerHigh = AsisCardElevatedLight,
    outline = AsisBorderLight,
    outlineVariant = AsisBorderLight.copy(alpha = 0.8f)
)

@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = true,
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        shapes = AppShapes,
        content = content
    )
}

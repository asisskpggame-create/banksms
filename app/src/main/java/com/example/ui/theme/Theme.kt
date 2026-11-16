package com.example.ui.theme

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.Color
import com.example.data.AppThemeMode

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

private val MidnightOledColorScheme = darkColorScheme(
    primary = AsisEmerald,
    onPrimary = Color(0xFF042213),
    primaryContainer = Color(0xFF062E1D),
    onPrimaryContainer = AsisEmeraldLight,
    secondary = AsisCyan,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFF0E1D36),
    onSecondaryContainer = Color(0xFFBFDBFE),
    tertiary = AsisAmber,
    onTertiary = Color(0xFF2E1600),
    tertiaryContainer = Color(0xFF2E1704),
    onTertiaryContainer = Color(0xFFFDE68A),
    error = AsisRose,
    onError = Color.White,
    errorContainer = Color(0xFF330816),
    onErrorContainer = Color(0xFFFECDD3),
    background = AsisOledBlack,
    onBackground = Color(0xFFFFFFFF),
    surface = AsisOledBlack,
    onSurface = Color(0xFFFFFFFF),
    surfaceVariant = AsisOledCard,
    onSurfaceVariant = Color(0xFF9CA3AF),
    surfaceContainerLowest = AsisOledBlack,
    surfaceContainerLow = AsisOledSurface,
    surfaceContainer = AsisOledCard,
    surfaceContainerHigh = AsisOledCardElevated,
    outline = AsisOledBorder,
    outlineVariant = AsisOledBorder.copy(alpha = 0.86f)
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
private fun animatedDarkScheme(target: ColorScheme): ColorScheme {
    val animSpec = tween<Color>(durationMillis = 260)
    val bg by animateColorAsState(target.background, animSpec, label = "themeBg")
    val surface by animateColorAsState(target.surface, animSpec, label = "themeSurface")
    val surfaceVariant by animateColorAsState(target.surfaceVariant, animSpec, label = "themeSurfaceVar")
    val surfaceContainerLowest by animateColorAsState(target.surfaceContainerLowest, animSpec, label = "themeLowest")
    val surfaceContainerLow by animateColorAsState(target.surfaceContainerLow, animSpec, label = "themeLow")
    val surfaceContainer by animateColorAsState(target.surfaceContainer, animSpec, label = "themeContainer")
    val surfaceContainerHigh by animateColorAsState(target.surfaceContainerHigh, animSpec, label = "themeHigh")
    val outline by animateColorAsState(target.outline, animSpec, label = "themeOutline")
    val outlineVariant by animateColorAsState(target.outlineVariant, animSpec, label = "themeOutlineVar")

    return target.copy(
        background = bg,
        surface = surface,
        surfaceVariant = surfaceVariant,
        surfaceContainerLowest = surfaceContainerLowest,
        surfaceContainerLow = surfaceContainerLow,
        surfaceContainer = surfaceContainer,
        surfaceContainerHigh = surfaceContainerHigh,
        outline = outline,
        outlineVariant = outlineVariant
    )
}

@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = true,
    themeMode: AppThemeMode = AppThemeMode.DARK_NAVY,
    content: @Composable () -> Unit
) {
    val targetScheme = when {
        !darkTheme -> LightColorScheme
        themeMode == AppThemeMode.MIDNIGHT_OLED -> MidnightOledColorScheme
        else -> DarkColorScheme
    }
    val colorScheme = if (darkTheme) animatedDarkScheme(targetScheme) else targetScheme

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        shapes = AppShapes,
        content = content
    )
}

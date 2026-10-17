package com.behlul.liftlog.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

private val Ember = Color(0xFFFF6B2C)
private val EmberLight = Color(0xFFFFA476)
private val EmberDeep = Color(0xFFB8400F)
private val Steel = Color(0xFF8E96A6)

private val DarkColors = darkColorScheme(
    primary = Ember,
    onPrimary = Color(0xFF1A0A02),
    primaryContainer = Color(0xFF4A2210),
    onPrimaryContainer = EmberLight,
    secondary = Steel,
    onSecondary = Color(0xFF15171C),
    secondaryContainer = Color(0xFF2C313B),
    onSecondaryContainer = Color(0xFFDDE1E8),
    tertiary = Color(0xFF7FD1AE),
    background = Color(0xFF15171C),
    onBackground = Color(0xFFE8EAEE),
    surface = Color(0xFF15171C),
    onSurface = Color(0xFFE8EAEE),
    surfaceVariant = Color(0xFF242832),
    onSurfaceVariant = Color(0xFFAEB4C0),
    surfaceContainerLowest = Color(0xFF101216),
    surfaceContainerLow = Color(0xFF1B1E24),
    surfaceContainer = Color(0xFF1F2229),
    surfaceContainerHigh = Color(0xFF262A32),
    surfaceContainerHighest = Color(0xFF30343D),
    outline = Color(0xFF4A505C),
    outlineVariant = Color(0xFF353A44),
    error = Color(0xFFFF8A80),
)

private val LightColors = lightColorScheme(
    primary = EmberDeep,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFFFDBCB),
    onPrimaryContainer = Color(0xFF3A1100),
    secondary = Color(0xFF555D6C),
    secondaryContainer = Color(0xFFDDE1E8),
    onSecondaryContainer = Color(0xFF1B1E24),
    tertiary = Color(0xFF1E7A57),
    background = Color(0xFFF7F7F9),
    surface = Color(0xFFF7F7F9),
    surfaceVariant = Color(0xFFE6E8EC),
    onSurfaceVariant = Color(0xFF4A505C),
    surfaceContainerLow = Color(0xFFF0F1F4),
    surfaceContainer = Color(0xFFEAECEF),
    surfaceContainerHigh = Color(0xFFE3E5E9),
    surfaceContainerHighest = Color(0xFFDCDFE4),
    outlineVariant = Color(0xFFCDD1D8),
)

private val AppTypography = Typography().run {
    copy(
        displaySmall = displaySmall.copy(fontWeight = FontWeight.Black, letterSpacing = (-1).sp),
        headlineMedium = headlineMedium.copy(fontWeight = FontWeight.Bold),
        titleLarge = titleLarge.copy(fontWeight = FontWeight.Bold),
        titleMedium = titleMedium.copy(fontWeight = FontWeight.SemiBold),
        labelSmall = labelSmall.copy(fontWeight = FontWeight.SemiBold, letterSpacing = 1.2.sp),
    )
}

/** Big tabular numbers for weights and stats, so digits line up across rows. */
val NumberStyle = TextStyle(fontWeight = FontWeight.Bold, fontFeatureSettings = "tnum")

@Composable
fun LiftLogTheme(darkTheme: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        typography = AppTypography,
        content = content,
    )
}

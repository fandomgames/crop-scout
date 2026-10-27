package com.farrah.sproutvision.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val SproutGreen = Color(0xFF2E7D32)
private val SproutGreenDark = Color(0xFF1B5E20)
private val SproutLight = Color(0xFFE8F5E9)
private val HarvestAmber = Color(0xFFF9A825)

private val LightColors = lightColorScheme(
    primary = SproutGreen,
    onPrimary = Color.White,
    primaryContainer = SproutLight,
    onPrimaryContainer = SproutGreenDark,
    secondary = Color(0xFF558B2F),
    tertiary = HarvestAmber
)

private val NightGreen = Color(0xFF69F0AE)
private val NightGreenSoft = Color(0xFFA5D6A7)
private val NightGreenDark = Color(0xFF1B5E20)

private val DarkColors = darkColorScheme(
    primary = NightGreen,
    onPrimary = Color.Black,
    primaryContainer = NightGreenDark,
    onPrimaryContainer = NightGreenSoft,
    secondary = NightGreen,
    onSecondary = Color.Black,
    tertiary = HarvestAmber,
    background = Color.Black,
    onBackground = NightGreen,
    surface = Color.Black,
    onSurface = NightGreen,
    surfaceVariant = Color(0xFF121212),
    onSurfaceVariant = NightGreenSoft,
    surfaceContainerLowest = Color.Black,
    surfaceContainerLow = Color.Black,
    surfaceContainer = Color.Black,
    surfaceContainerHigh = Color(0xFF121212),
    surfaceContainerHighest = Color(0xFF1A1A1A)
)

@Composable
fun SproutVisionTheme(darkMode: Boolean = false, content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (darkMode) DarkColors else LightColors,
        content = content
    )
}

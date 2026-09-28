package com.sj.audio8d.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.sp

val NavyBackground = Color(0xFF050B14)
val CardBackground = Color(0xFF0E1826)
val CardBackgroundElevated = Color(0xFF122033)
val CyanAccent = Color(0xFF22D3EE)
val CyanAccentDim = Color(0xFF0E7A90)
val TextPrimary = Color(0xFFEAF6FA)
val TextSecondary = Color(0xFF8DA2B5)
val DangerRed = Color(0xFFEF4444)

private val Audio8DColorScheme = darkColorScheme(
    primary = CyanAccent,
    onPrimary = NavyBackground,
    secondary = CyanAccentDim,
    background = NavyBackground,
    onBackground = TextPrimary,
    surface = CardBackground,
    onSurface = TextPrimary,
    surfaceVariant = CardBackgroundElevated,
    error = DangerRed
)

@Composable
fun Audio8DTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = Audio8DColorScheme,
        typography = MaterialTheme.typography.copy(
            titleLarge = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
            titleMedium = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold)
        ),
        content = content
    )
}

val MonoSpaceLabel = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.Medium, letterSpacing = 0.5.sp)

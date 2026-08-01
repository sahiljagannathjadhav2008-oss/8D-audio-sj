package com.builtdifferent.audio8d.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val DarkColors = darkColorScheme(
    primary = PurplePrimary,
    secondary = TealAccent,
    background = BackgroundDark,
    surface = SurfaceDark,
)

private val LightColors = lightColorScheme(
    primary = PurplePrimary,
    secondary = PurpleDark,
    background = BackgroundLight,
    surface = SurfaceLight,
)

@Composable
fun Audio8DTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColors else LightColors
    MaterialTheme(
        colorScheme = colorScheme,
        typography = Audio8DTypography,
        content = content
    )
}

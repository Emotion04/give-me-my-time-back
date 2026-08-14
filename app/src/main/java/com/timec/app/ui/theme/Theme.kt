package com.timec.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightColors = lightColorScheme(
    primary = Color(0xFF1B2A41),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFDDE8F2),
    secondary = Color(0xFF4D6A7A),
    tertiary = Color(0xFF7FD1AE),
    background = Color(0xFFF7FAFC),
    surface = Color.White,
    onSurface = Color(0xFF17212B)
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFBBD0E2),
    onPrimary = Color(0xFF0F1A25),
    secondary = Color(0xFFA5C3D2),
    tertiary = Color(0xFF7FD1AE),
    background = Color(0xFF101820),
    surface = Color(0xFF17212B),
    onSurface = Color(0xFFE8F1F8)
)

@Composable
fun TimeCTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) DarkColors else LightColors,
        content = content
    )
}

package com.timec.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val themeNames = listOf("默认", "海盐", "莫奈紫", "苔绿", "珊瑚")

private val lightSchemes: List<ColorScheme> = listOf(
    lightColorScheme(
        primary = Color(0xFF1B2A41), onPrimary = Color.White,
        primaryContainer = Color(0xFFDDE8F2), secondary = Color(0xFF4D6A7A),
        tertiary = Color(0xFF2E8B74), background = Color(0xFFF7FAFC), surface = Color.White
    ),
    lightColorScheme(
        primary = Color(0xFF00696D), onPrimary = Color.White,
        primaryContainer = Color(0xFF9CF1F0), secondary = Color(0xFF4A6363),
        tertiary = Color(0xFF4A6A68), background = Color(0xFFF4FAFA), surface = Color.White
    ),
    lightColorScheme(
        primary = Color(0xFF6750A4), onPrimary = Color.White,
        primaryContainer = Color(0xFFEADDFF), secondary = Color(0xFF625B71),
        tertiary = Color(0xFF7D5260), background = Color(0xFFFDF7FF), surface = Color.White
    ),
    lightColorScheme(
        primary = Color(0xFF386A20), onPrimary = Color.White,
        primaryContainer = Color(0xFFB8F1A5), secondary = Color(0xFF55624C),
        tertiary = Color(0xFF3F6653), background = Color(0xFFF5FBF1), surface = Color.White
    ),
    lightColorScheme(
        primary = Color(0xFF9A4523), onPrimary = Color.White,
        primaryContainer = Color(0xFFFFDBD0), secondary = Color(0xFF77564E),
        tertiary = Color(0xFF8A5A3D), background = Color(0xFFFDF5F3), surface = Color.White
    )
)

private val darkSchemes: List<ColorScheme> = listOf(
    darkColorScheme(
        primary = Color(0xFFBBD0E2), onPrimary = Color(0xFF0F1A25),
        secondary = Color(0xFFA5C3D2), tertiary = Color(0xFF7FD1AE),
        background = Color(0xFF101820), surface = Color(0xFF17212B)
    ),
    darkColorScheme(
        primary = Color(0xFF82D4D4), onPrimary = Color(0xFF003737),
        secondary = Color(0xFFA0CFC6), tertiary = Color(0xFF7FD1AE),
        background = Color(0xFF0E1515), surface = Color(0xFF141F1F)
    ),
    darkColorScheme(
        primary = Color(0xFFD0BCFF), onPrimary = Color(0xFF381E72),
        secondary = Color(0xFFCCC2DC), tertiary = Color(0xFFEFB8C8),
        background = Color(0xFF141218), surface = Color(0xFF1D1A24)
    ),
    darkColorScheme(
        primary = Color(0xFFA9D89B), onPrimary = Color(0xFF123200),
        secondary = Color(0xFFC0C9B8), tertiary = Color(0xFF7FD1AE),
        background = Color(0xFF0F140C), surface = Color(0xFF171E14)
    ),
    darkColorScheme(
        primary = Color(0xFFFFB59B), onPrimary = Color(0xFF5C1900),
        secondary = Color(0xFFE5BFAE), tertiary = Color(0xFFE7B2A0),
        background = Color(0xFF1A120F), surface = Color(0xFF221713)
    )
)

@Composable
fun TimeCTheme(themeIndex: Int, content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    val idx = themeIndex.coerceIn(0, themeNames.size - 1)
    val scheme = if (dark) darkSchemes[idx] else lightSchemes[idx]
    MaterialTheme(colorScheme = scheme, content = content)
}

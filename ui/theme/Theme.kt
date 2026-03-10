package com.example.modrinthforandroid.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.example.modrinthforandroid.data.AppSettings

// Modrinth's brand green
val ModrinthGreen     = Color(0xFF1BD96A)
val ModrinthDarkGreen = Color(0xFF158A47)
val ModrinthBackground = Color(0xFF1A1A2E)
val ModrinthSurface   = Color(0xFF16213E)
val ModrinthCard      = Color(0xFF0F3460)

private val DarkColorScheme = darkColorScheme(
    primary      = ModrinthGreen,
    secondary    = ModrinthDarkGreen,
    background   = ModrinthBackground,
    surface      = ModrinthSurface,
    onPrimary    = Color.Black,
    onBackground = Color.White,
    onSurface    = Color.White
)

// Pure black AMOLED scheme — great for OLED screens
private val AmoledColorScheme = darkColorScheme(
    primary      = ModrinthGreen,
    secondary    = ModrinthDarkGreen,
    background   = Color.Black,
    surface      = Color(0xFF0D0D0D),
    onPrimary    = Color.Black,
    onBackground = Color.White,
    onSurface    = Color.White
)

private val LightColorScheme = lightColorScheme(
    primary      = ModrinthDarkGreen,
    secondary    = ModrinthGreen,
    background   = Color(0xFFF5F5F5),
    surface      = Color.White,
    onPrimary    = Color.White,
    onBackground = Color.Black,
    onSurface    = Color.Black
)

@Composable
fun ModrinthTheme(
    theme: String = AppSettings.THEME_DARK,
    content: @Composable () -> Unit
) {
    val colorScheme = when (theme) {
        AppSettings.THEME_LIGHT -> LightColorScheme
        AppSettings.THEME_AMOLED -> AmoledColorScheme
        else -> DarkColorScheme   // THEME_DARK is the default
    }

    MaterialTheme(
        colorScheme = colorScheme,
        content     = content
    )
}
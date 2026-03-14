package com.example.modrinthforandroid.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.example.modrinthforandroid.data.AppSettings

// ─── Modrinth Green (original) ────────────────────────────────────────────────
val ModrinthGreen      = Color(0xFF1BD96A)
val ModrinthDarkGreen  = Color(0xFF158A47)

private val DarkColorScheme = darkColorScheme(
    primary          = ModrinthGreen,
    secondary        = Color(0xFF0FA854),
    tertiary         = Color(0xFF00D4AA),
    background       = Color(0xFF111827),
    surface          = Color(0xFF1C2A3A),
    surfaceVariant   = Color(0xFF1E3145),
    onPrimary        = Color.Black,
    onSecondary      = Color.Black,
    onBackground     = Color(0xFFE8F0FE),
    onSurface        = Color(0xFFCDD8E8),
    onSurfaceVariant = Color(0xFF8FA3BC),
    outline          = Color(0xFF2D4A6B),
    outlineVariant   = Color(0xFF1E3145),
    primaryContainer = Color(0xFF0D3B26),
    onPrimaryContainer = Color(0xFF6EFFA8)
)

// ─── AMOLED ───────────────────────────────────────────────────────────────────
private val AmoledColorScheme = darkColorScheme(
    primary          = ModrinthGreen,
    secondary        = Color(0xFF0FA854),
    tertiary         = Color(0xFF00D4AA),
    background       = Color(0xFF000000),
    surface          = Color(0xFF0A0A0A),
    surfaceVariant   = Color(0xFF0F1A0F),
    onPrimary        = Color.Black,
    onSecondary      = Color.Black,
    onBackground     = Color(0xFFE8F0FE),
    onSurface        = Color(0xFFCDD8E8),
    onSurfaceVariant = Color(0xFF6B8068),
    outline          = Color(0xFF1A2E1A),
    outlineVariant   = Color(0xFF0F1A0F),
    primaryContainer = Color(0xFF051A0D),
    onPrimaryContainer = Color(0xFF6EFFA8)
)

// ─── Light ────────────────────────────────────────────────────────────────────
private val LightColorScheme = lightColorScheme(
    primary          = Color(0xFF0E7A3E),
    secondary        = Color(0xFF1BD96A),
    tertiary         = Color(0xFF00A67E),
    background       = Color(0xFFF0F7F4),
    surface          = Color(0xFFFFFFFF),
    surfaceVariant   = Color(0xFFE4F2EB),
    onPrimary        = Color.White,
    onSecondary      = Color.Black,
    onBackground     = Color(0xFF0D1F15),
    onSurface        = Color(0xFF1A2E20),
    onSurfaceVariant = Color(0xFF4A6B55),
    outline          = Color(0xFFB0CCBA),
    outlineVariant   = Color(0xFFD4EAD9),
    primaryContainer = Color(0xFFCCF0DC),
    onPrimaryContainer = Color(0xFF053A1B)
)

// ─── 🌊 Ocean Blue ────────────────────────────────────────────────────────────
private val OceanColorScheme = darkColorScheme(
    primary          = Color(0xFF00D4FF),
    secondary        = Color(0xFF0099CC),
    tertiary         = Color(0xFF00FFD0),
    background       = Color(0xFF060D1A),
    surface          = Color(0xFF0A1628),
    surfaceVariant   = Color(0xFF0E2040),
    onPrimary        = Color(0xFF001F2E),
    onSecondary      = Color.White,
    onBackground     = Color(0xFFD0EEFF),
    onSurface        = Color(0xFFB8DEFF),
    onSurfaceVariant = Color(0xFF5A8FAA),
    outline          = Color(0xFF1A3A5C),
    outlineVariant   = Color(0xFF0E2040),
    primaryContainer = Color(0xFF002A40),
    onPrimaryContainer = Color(0xFF80E8FF)
)

// ─── 🌸 Sakura ────────────────────────────────────────────────────────────────
private val SakuraColorScheme = darkColorScheme(
    primary          = Color(0xFFFF7BAC),
    secondary        = Color(0xFFE05585),
    tertiary         = Color(0xFFFFB3CC),
    background       = Color(0xFF1A0A12),
    surface          = Color(0xFF27101B),
    surfaceVariant   = Color(0xFF381525),
    onPrimary        = Color(0xFF2A0016),
    onSecondary      = Color.White,
    onBackground     = Color(0xFFFFE4EE),
    onSurface        = Color(0xFFFFD6E5),
    onSurfaceVariant = Color(0xFFBB7090),
    outline          = Color(0xFF5C2040),
    outlineVariant   = Color(0xFF381525),
    primaryContainer = Color(0xFF4A0A28),
    onPrimaryContainer = Color(0xFFFFB8D0)
)

// ─── 🌙 Midnight Purple ───────────────────────────────────────────────────────
private val PurpleColorScheme = darkColorScheme(
    primary          = Color(0xFFBB86FC),
    secondary        = Color(0xFF9B59F5),
    tertiary         = Color(0xFFE040FB),
    background       = Color(0xFF0A0612),
    surface          = Color(0xFF12091E),
    surfaceVariant   = Color(0xFF1C1030),
    onPrimary        = Color(0xFF1A0030),
    onSecondary      = Color.White,
    onBackground     = Color(0xFFEDE0FF),
    onSurface        = Color(0xFFDDD0F5),
    onSurfaceVariant = Color(0xFF9070BB),
    outline          = Color(0xFF3A1F6A),
    outlineVariant   = Color(0xFF1C1030),
    primaryContainer = Color(0xFF2D1060),
    onPrimaryContainer = Color(0xFFE8CCFF)
)

// ─── ☀️ Sunrise ───────────────────────────────────────────────────────────────
private val SunriseColorScheme = darkColorScheme(
    primary          = Color(0xFFFF9500),
    secondary        = Color(0xFFFF6B35),
    tertiary         = Color(0xFFFFCC02),
    background       = Color(0xFF140A00),
    surface          = Color(0xFF201200),
    surfaceVariant   = Color(0xFF301A00),
    onPrimary        = Color(0xFF2A1400),
    onSecondary      = Color.White,
    onBackground     = Color(0xFFFFEDD0),
    onSurface        = Color(0xFFFFE0B0),
    onSurfaceVariant = Color(0xFFBB8040),
    outline          = Color(0xFF6A3800),
    outlineVariant   = Color(0xFF301A00),
    primaryContainer = Color(0xFF4A2000),
    onPrimaryContainer = Color(0xFFFFCC80)
)

// ─── 🌿 Mint ──────────────────────────────────────────────────────────────────
private val MintColorScheme = darkColorScheme(
    primary          = Color(0xFF00E5A0),
    secondary        = Color(0xFF00C48C),
    tertiary         = Color(0xFF80FFD4),
    background       = Color(0xFF060F0C),
    surface          = Color(0xFF0C1A15),
    surfaceVariant   = Color(0xFF102820),
    onPrimary        = Color(0xFF001F15),
    onSecondary      = Color.Black,
    onBackground     = Color(0xFFD0FFF0),
    onSurface        = Color(0xFFB8F5E0),
    onSurfaceVariant = Color(0xFF4AAA88),
    outline          = Color(0xFF1A4A38),
    outlineVariant   = Color(0xFF102820),
    primaryContainer = Color(0xFF003D28),
    onPrimaryContainer = Color(0xFF80FFD4)
)

// ─── Theme resolver ───────────────────────────────────────────────────────────

@Composable
fun ModrinthTheme(
    theme: String = AppSettings.THEME_DARK,
    content: @Composable () -> Unit
) {
    val colorScheme = when (theme) {
        AppSettings.THEME_LIGHT   -> LightColorScheme
        AppSettings.THEME_AMOLED  -> AmoledColorScheme
        AppSettings.THEME_OCEAN   -> OceanColorScheme
        AppSettings.THEME_SAKURA  -> SakuraColorScheme
        AppSettings.THEME_PURPLE  -> PurpleColorScheme
        AppSettings.THEME_SUNRISE -> SunriseColorScheme
        AppSettings.THEME_MINT    -> MintColorScheme
        else                      -> DarkColorScheme  // THEME_DARK
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography  = Typography,
        content     = content
    )
}
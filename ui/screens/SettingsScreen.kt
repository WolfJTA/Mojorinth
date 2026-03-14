package com.example.modrinthforandroid.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.modrinthforandroid.data.AppSettings
import com.example.modrinthforandroid.ui.components.TutorialOverlay

// ─── Theme metadata ───────────────────────────────────────────────────────────

private data class ThemeOption(
    val key: String,
    val name: String,
    val emoji: String,
    val swatchA: Color,   // gradient start
    val swatchB: Color,   // gradient end
    val isDark: Boolean = true
)

private val THEME_OPTIONS = listOf(
    ThemeOption(
        key     = AppSettings.THEME_DARK,
        name    = "Modrinth",
        emoji   = "",
        swatchA = Color(0xFF111827),
        swatchB = Color(0xFF1BD96A)
    ),
    ThemeOption(
        key     = AppSettings.THEME_AMOLED,
        name    = "AMOLED",
        emoji   = "",
        swatchA = Color(0xFF000000),
        swatchB = Color(0xFF000000)
    ),
    ThemeOption(
        key     = AppSettings.THEME_LIGHT,
        name    = "Light (ew)",
        emoji   = "\uD83D\uDD06",
        swatchA = Color(0xFFF0F7F4),
        swatchB = Color(0xFF0E7A3E),
        isDark  = false
    ),
    ThemeOption(
        key     = AppSettings.THEME_OCEAN,
        name    = "Ocean",
        emoji   = "🌊",
        swatchA = Color(0xFF060D1A),
        swatchB = Color(0xFF00D4FF)
    ),
    ThemeOption(
        key     = AppSettings.THEME_SAKURA,
        name    = "Sakura",
        emoji   = "❀",
        swatchA = Color(0xFF1A0A12),
        swatchB = Color(0xFFFF7BAC)
    ),
    ThemeOption(
        key     = AppSettings.THEME_PURPLE,
        name    = "Midnight",
        emoji   = "☾",
        swatchA = Color(0xFF0A0612),
        swatchB = Color(0xFFBB86FC)
    ),
    ThemeOption(
        key     = AppSettings.THEME_SUNRISE,
        name    = "Sunrise",
        emoji   = "\uD81A\uDD13️",
        swatchA = Color(0xFF140A00),
        swatchB = Color(0xFFFF9500)
    ),
    ThemeOption(
        key     = AppSettings.THEME_MINT,
        name    = "Mint",
        emoji   = "🌿",
        swatchA = Color(0xFF060F0C),
        swatchB = Color(0xFF00E5A0)
    ),
)

// ─── Screen ───────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onThemeChange: (String) -> Unit = {}
) {
    val context  = LocalContext.current
    val settings = remember { AppSettings.get(context) }

    var theme        by remember { mutableStateOf(settings.theme) }
    var dupWarning   by remember { mutableStateOf(settings.showDuplicateWarning) }
    var showTutorial by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "Settings",
                        fontWeight = FontWeight.Bold,
                        color      = MaterialTheme.colorScheme.primary
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack, "Back",
                            tint = MaterialTheme.colorScheme.onBackground
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {

            // ── Appearance ────────────────────────────────────────────────
            SettingsSection("Appearance") {
                Text(
                    "Theme",
                    style      = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    color      = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    "Pick a vibe ✨",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                )
                Spacer(Modifier.height(14.dp))

                ThemeSwatchGrid(
                    currentTheme = theme,
                    onThemePicked = { key ->
                        theme          = key
                        settings.theme = key
                        onThemeChange(key)
                    }
                )
            }

            // ── Downloads ─────────────────────────────────────────────────
            SettingsSection("Downloads") {
                Row(
                    modifier              = Modifier.fillMaxWidth(),
                    verticalAlignment     = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            "Warn on duplicate download",
                            style      = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium,
                            color      = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            "Show a warning before re-downloading a version",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                        )
                    }
                    Spacer(Modifier.width(12.dp))
                    Switch(
                        checked         = dupWarning,
                        onCheckedChange = {
                            dupWarning                    = it
                            settings.showDuplicateWarning = it
                        }
                    )
                }
            }

            // ── Help ──────────────────────────────────────────────────────
            SettingsSection("Help") {
                Text(
                    "App Tutorial",
                    style      = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    color      = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    "Replay the first-launch walkthrough",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                )
                Spacer(Modifier.height(10.dp))
                OutlinedButton(
                    onClick  = {
                        settings.hasSeenTutorial = false
                        showTutorial = true
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("👋  Replay Tutorial")
                }
            }
        }
    }

    if (showTutorial) {
        TutorialOverlay(
            onDismiss = {
                settings.hasSeenTutorial = true
                showTutorial = false
            }
        )
    }
}

// ─── Swatch grid ──────────────────────────────────────────────────────────────

@Composable
private fun ThemeSwatchGrid(
    currentTheme: String,
    onThemePicked: (String) -> Unit
) {
    val rows = THEME_OPTIONS.chunked(4)
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        rows.forEach { row ->
            Row(
                modifier              = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                row.forEach { option ->
                    ThemeSwatch(
                        option    = option,
                        selected  = currentTheme == option.key,
                        onClick   = { onThemePicked(option.key) },
                        modifier  = Modifier.weight(1f)
                    )
                }
                // pad short rows
                repeat(4 - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

@Composable
private fun ThemeSwatch(
    option: ThemeOption,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val borderColor = if (selected)
        MaterialTheme.colorScheme.primary
    else
        MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)

    val borderWidth = if (selected) 2.dp else 1.dp

    Column(
        modifier            = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Box(
            modifier        = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .clip(RoundedCornerShape(14.dp))
                .background(
                    Brush.linearGradient(
                        colors = listOf(option.swatchA, option.swatchB)
                    )
                )
                .border(borderWidth, borderColor, RoundedCornerShape(14.dp))
                .clickable(onClick = onClick),
            contentAlignment = Alignment.Center
        ) {
            if (selected) {
                Box(
                    modifier = Modifier
                        .size(26.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.25f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Default.Check,
                        contentDescription = "Selected",
                        tint     = Color.White,
                        modifier = Modifier.size(16.dp)
                    )
                }
            } else {
                Text(option.emoji, fontSize = 22.sp)
            }
        }

        Text(
            option.name,
            style      = MaterialTheme.typography.labelSmall,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
            color      = if (selected)
                MaterialTheme.colorScheme.primary
            else
                MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
            maxLines   = 1
        )
    }
}

// ─── Section wrapper ──────────────────────────────────────────────────────────

@Composable
private fun SettingsSection(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column {
        Text(
            title,
            style      = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color      = MaterialTheme.colorScheme.primary,
            modifier   = Modifier.padding(bottom = 10.dp)
        )
        Surface(
            shape          = RoundedCornerShape(12.dp),
            color          = MaterialTheme.colorScheme.surface,
            tonalElevation = 2.dp,
            modifier       = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                content  = content
            )
        }
    }
}
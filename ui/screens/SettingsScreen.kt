package com.example.modrinthforandroid.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.modrinthforandroid.data.AppSettings
import com.example.modrinthforandroid.ui.components.TutorialOverlay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onThemeChange: (String) -> Unit = {}
) {
    val context  = LocalContext.current
    val settings = remember { AppSettings.get(context) }

    var theme      by remember { mutableStateOf(settings.theme) }
    var dupWarning by remember { mutableStateOf(settings.showDuplicateWarning) }
    var showTutorial by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings", fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back",
                            tint = MaterialTheme.colorScheme.onBackground)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background)
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
                Text("Theme", style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f))
                Spacer(modifier = Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(
                        AppSettings.THEME_DARK   to "Dark",
                        AppSettings.THEME_LIGHT  to "Light",
                        AppSettings.THEME_AMOLED to "AMOLED"
                    ).forEach { (key, label) ->
                        FilterChip(
                            selected = theme == key,
                            onClick  = {
                                theme          = key
                                settings.theme = key
                                onThemeChange(key)
                            },
                            label = { Text(label) }
                        )
                    }
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text("Theme changes apply instantly.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f))
            }

            // ── Downloads ─────────────────────────────────────────────────
            SettingsSection("Downloads") {
                Row(modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Warn on duplicate download",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurface)
                        Text("Show a warning before re-downloading a version",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Switch(
                        checked = dupWarning,
                        onCheckedChange = {
                            dupWarning                    = it
                            settings.showDuplicateWarning = it
                        }
                    )
                }
            }

            // ── Help ──────────────────────────────────────────────────────
            SettingsSection("Help") {
                Text("App Tutorial",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface)
                Text("Replay the first-launch walkthrough",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
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

@Composable
private fun SettingsSection(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column {
        Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(bottom = 10.dp))
        Surface(shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.surface,
            tonalElevation = 2.dp, modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp), content = content)
        }
    }
}
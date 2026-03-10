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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onThemeChange: (String) -> Unit = {}
) {
    val context  = LocalContext.current
    val settings = remember { AppSettings.get(context) }

    var theme           by remember { mutableStateOf(settings.theme) }
    var dupWarning      by remember { mutableStateOf(settings.showDuplicateWarning) }
    var mojoPath        by remember { mutableStateOf(settings.mojoInstancesPath) }
    var editingMojoPath by remember { mutableStateOf(false) }

    val projectTypes = AppSettings.TYPE_FOLDER_DEFAULTS.keys.toList()
    val folderState  = remember {
        mutableStateMapOf<String, String>().also { map ->
            projectTypes.forEach { map[it] = settings.getDownloadFolder(it) }
        }
    }
    var editingType by remember { mutableStateOf<String?>(null) }
    var editingPath by remember { mutableStateOf("") }

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

            // ── MojoLauncher ──────────────────────────────────────────────
            SettingsSection("MojoLauncher") {
                Text("Instances path",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface)
                Text("Where MojoLauncher stores its game instances.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
                Spacer(modifier = Modifier.height(10.dp))
                Row(modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = mojoPath.split("/").takeLast(3).joinToString("/"),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    OutlinedButton(
                        onClick = { editingMojoPath = true },
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                    ) { Text("Edit", style = MaterialTheme.typography.labelSmall) }
                }
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

            // ── Download Folders ──────────────────────────────────────────
            SettingsSection("Download Folders") {
                Text("Fallback folders used when no instance is active.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                Spacer(modifier = Modifier.height(12.dp))
                projectTypes.forEach { type ->
                    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(AppSettings.TYPE_FOLDER_DEFAULTS[type] ?: type,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onSurface)
                            Text(folderState[type]?.split("/")?.takeLast(2)?.joinToString("/") ?: "",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
                        }
                        OutlinedButton(
                            onClick = { editingType = type; editingPath = folderState[type] ?: "" },
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                        ) { Text("Edit", style = MaterialTheme.typography.labelSmall) }
                    }
                    if (type != projectTypes.last())
                        HorizontalDivider(
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f))
                }
            }
        }
    }

    // ── MojoLauncher path dialog ──────────────────────────────────────────
    if (editingMojoPath) {
        AlertDialog(
            onDismissRequest = { editingMojoPath = false },
            title = { Text("MojoLauncher Instances Path") },
            text = {
                Column {
                    Text("Enter the full path to your MojoLauncher instances folder.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                    Spacer(modifier = Modifier.height(4.dp))
                    Text("Default: ${AppSettings.DEFAULT_MOJO_INSTANCES_PATH}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f))
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedTextField(
                        value = mojoPath,
                        onValueChange = { mojoPath = it },
                        singleLine = true,
                        label = { Text("Path") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(onClick = {
                    settings.mojoInstancesPath = mojoPath
                    editingMojoPath = false
                }) { Text("Save") }
            },
            dismissButton = {
                TextButton(onClick = { editingMojoPath = false }) { Text("Cancel") }
            }
        )
    }

    // ── Download folder edit dialog ───────────────────────────────────────
    if (editingType != null) {
        AlertDialog(
            onDismissRequest = { editingType = null },
            title = { Text("Edit Folder — ${AppSettings.TYPE_FOLDER_DEFAULTS[editingType] ?: editingType}") },
            text = {
                Column {
                    Text("Enter the full path where ${AppSettings.TYPE_FOLDER_DEFAULTS[editingType]} should be saved.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedTextField(
                        value = editingPath,
                        onValueChange = { editingPath = it },
                        singleLine = true,
                        label = { Text("Path") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(onClick = {
                    editingType?.let { type ->
                        settings.setDownloadFolder(type, editingPath)
                        folderState[type] = editingPath
                    }
                    editingType = null
                }) { Text("Save") }
            },
            dismissButton = {
                TextButton(onClick = { editingType = null }) { Text("Cancel") }
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
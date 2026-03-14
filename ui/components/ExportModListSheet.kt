package com.example.modrinthforandroid.ui.components

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.modrinthforandroid.data.ExportResult
import com.example.modrinthforandroid.data.InstanceManager
import com.example.modrinthforandroid.data.MrpackExporter
import kotlinx.coroutines.launch

private enum class ExportState { IDLE, WORKING, DONE, ERROR }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExportModListSheet(onDismiss: () -> Unit) {
    val context        = LocalContext.current
    val scope          = rememberCoroutineScope()
    val instanceConfig = InstanceManager.activeInstanceConfig
    val instanceName   = InstanceManager.activeInstanceName
    val rootUri        = InstanceManager.rootUri

    var exportState  by remember { mutableStateOf(ExportState.IDLE) }
    var exportResult by remember { mutableStateOf<ExportResult?>(null) }
    var errorMsg     by remember { mutableStateOf<String?>(null) }

    // SAF file creator — prompts user to pick save location + filename
    val saveLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/zip")
    ) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        if (instanceConfig == null || instanceName == null || rootUri == null) {
            errorMsg    = "No active instance selected."
            exportState = ExportState.ERROR
            return@rememberLauncherForActivityResult
        }
        exportState = ExportState.WORKING
        scope.launch {
            try {
                val result = MrpackExporter.export(
                    context      = context,
                    rootUri      = rootUri,
                    instanceName = instanceName,
                    config       = instanceConfig,
                    outputUri    = uri
                )
                exportResult = result
                exportState  = ExportState.DONE
            } catch (e: Exception) {
                errorMsg    = e.message ?: "Export failed"
                exportState = ExportState.ERROR
            }
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState       = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // ── Header ────────────────────────────────────────────────────
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "Export Mod List",
                    style      = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.weight(1f))
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                ) {
                    Text(
                        ".mrpack",
                        modifier   = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                        style      = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color      = MaterialTheme.colorScheme.primary
                    )
                }
            }

            // ── Instance info ─────────────────────────────────────────────
            instanceConfig?.let { config ->
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
                ) {
                    Row(
                        modifier          = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text("📦", fontSize = 22.sp)
                        Column {
                            Text(
                                config.name,
                                style      = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                config.summary,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                            )
                        }
                    }
                }
            } ?: run {
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.errorContainer
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(Icons.Default.Warning, null,
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(18.dp))
                        Text(
                            "No active instance selected",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                    }
                }
            }

            // ── What gets included note ───────────────────────────────────
            AnimatedVisibility(exportState == ExportState.IDLE) {
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.07f)
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            "What gets exported",
                            style      = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                            color      = MaterialTheme.colorScheme.primary
                        )
                        listOf(
                            "✅  All enabled .jar mods in your instance",
                            "✅  Modrinth download URLs (resolved via file hash)",
                            "✅  Minecraft version + loader metadata",
                            "⚠️  Non-Modrinth mods will be skipped"
                        ).forEach {
                            Text(it,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f))
                        }
                    }
                }
            }

            // ── Working state ─────────────────────────────────────────────
            AnimatedVisibility(exportState == ExportState.WORKING) {
                Column(
                    modifier            = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                    Text(
                        "Hashing mods and resolving with Modrinth…",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                    Text(
                        "This may take a moment for large mod lists.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                    )
                }
            }

            // ── Done state ────────────────────────────────────────────────
            AnimatedVisibility(exportState == ExportState.DONE) {
                exportResult?.let { result ->
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)
                        ) {
                            Column(
                                modifier = Modifier.fillMaxWidth().padding(14.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Icon(Icons.Default.CheckCircle, null,
                                        tint     = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(20.dp))
                                    Text(
                                        "Export complete!",
                                        style      = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Bold,
                                        color      = MaterialTheme.colorScheme.primary
                                    )
                                }
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceEvenly
                                ) {
                                    StatBox("Total mods",    "${result.totalMods}")
                                    StatBox("Resolved",      "${result.resolvedMods}")
                                    StatBox("Skipped",       "${result.skippedMods.size}")
                                }
                            }
                        }

                        // Skipped mods list
                        if (result.skippedMods.isNotEmpty()) {
                            Text(
                                "⚠️ ${result.skippedMods.size} mod${if (result.skippedMods.size != 1) "s" else ""} couldn't be resolved (not on Modrinth or hash mismatch):",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                            )
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                            ) {
                                LazyColumn(
                                    modifier       = Modifier
                                        .fillMaxWidth()
                                        .heightIn(max = 140.dp)
                                        .padding(8.dp),
                                    verticalArrangement = Arrangement.spacedBy(2.dp)
                                ) {
                                    items(result.skippedMods) { name ->
                                        Text(
                                            "• $name",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                                        )
                                    }
                                }
                            }
                        }

                        Button(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) {
                            Text("Done")
                        }
                    }
                }
            }

            // ── Error state ───────────────────────────────────────────────
            AnimatedVisibility(exportState == ExportState.ERROR) {
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.errorContainer
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(Icons.Default.Error, null,
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(18.dp))
                        Text(
                            errorMsg ?: "Unknown error",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                    }
                }
            }

            // ── Export button ─────────────────────────────────────────────
            AnimatedVisibility(exportState == ExportState.IDLE || exportState == ExportState.ERROR) {
                Button(
                    onClick  = {
                        val packName = instanceConfig?.name
                            ?.replace(" ", "_")
                            ?.replace(Regex("[^a-zA-Z0-9_.-]"), "")
                            ?: "modpack"
                        saveLauncher.launch("$packName.mrpack")
                    },
                    enabled  = instanceConfig != null && rootUri != null,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.FileDownload, null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Choose save location & export")
                }
            }
        }
    }
}

@Composable
private fun StatBox(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value,
            style      = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color      = MaterialTheme.colorScheme.primary)
        Text(label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f))
    }
}

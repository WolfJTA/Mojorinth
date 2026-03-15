package com.example.modrinthforandroid.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.modrinthforandroid.data.AppSettings
import com.example.modrinthforandroid.data.InstanceManager
import com.example.modrinthforandroid.ui.components.MojorinthLoadingSpinner
import com.example.modrinthforandroid.viewmodel.StatsViewModel
import com.example.modrinthforandroid.viewmodel.StatsViewModelFactory

// ─── Data model ───────────────────────────────────────────────────────────────

data class InstanceStats(
    val folderName: String,
    val displayName: String,
    val modsEnabled: Int,
    val modsDisabled: Int,
    val shaders: Int,
    val resourcePacks: Int,
    val totalSizeBytes: Long
)

// ─── Screen ───────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StatsScreen(
    onBack: () -> Unit,
    viewModel: StatsViewModel = viewModel(
        factory = StatsViewModelFactory(LocalContext.current.applicationContext)
    )
) {
    val context    = LocalContext.current
    val settings   = remember { AppSettings.get(context) }

    val isScanning by viewModel.isScanning.collectAsState()
    val statsList  by viewModel.statsList.collectAsState()
    val totalBytes by viewModel.totalBytes.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "Stats",
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
                actions = {
                    if (!isScanning) {
                        IconButton(onClick = { viewModel.refresh() }) {
                            Icon(
                                Icons.Default.Refresh, "Refresh",
                                tint = MaterialTheme.colorScheme.onBackground
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        }
    ) { innerPadding ->

        if (InstanceManager.rootUri == null) {
            Box(Modifier.fillMaxSize().padding(innerPadding), contentAlignment = Alignment.Center) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.padding(32.dp)
                ) {
                    Text("📊", fontSize = 48.sp)
                    Text(
                        "No folder linked",
                        style      = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        "Link your MojoLauncher instances folder from the home screen to see stats.",
                        style     = MaterialTheme.typography.bodySmall,
                        color     = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                        textAlign = TextAlign.Center
                    )
                }
            }
            return@Scaffold
        }

        LazyColumn(
            modifier            = Modifier.fillMaxSize().padding(innerPadding),
            contentPadding      = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {

            // ── Global stats ─────────────────────────────────────────────
            item {
                Text(
                    "Overall",
                    style      = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color      = MaterialTheme.colorScheme.primary,
                    modifier   = Modifier.padding(bottom = 8.dp)
                )
                Surface(
                    shape          = RoundedCornerShape(16.dp),
                    color          = MaterialTheme.colorScheme.surface,
                    tonalElevation = 2.dp,
                    modifier       = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier            = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        GlobalStatRow(
                            icon  = Icons.Default.Download,
                            label = "Total mods downloaded",
                            value = settings.totalDownloads.toString()
                        )
                        GlobalStatRow(
                            icon  = Icons.Default.Storage,
                            label = "Total size",
                            value = formatBytes(totalBytes)
                        )
                        GlobalStatRow(
                            icon  = Icons.Default.GridView,
                            label = "Instances",
                            value = statsList.size.toString()
                        )

                        // ── Milestone progress ────────────────────────────
                        val nextMilestone = AppSettings.MILESTONES
                            .sorted()
                            .firstOrNull { it > settings.totalDownloads }
                        if (nextMilestone != null) {
                            val progress = settings.totalDownloads.toFloat() / nextMilestone.toFloat()
                            HorizontalDivider(
                                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)
                            )
                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Row(
                                    modifier          = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        "🏆 Next milestone",
                                        style      = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.SemiBold,
                                        color      = MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        "${settings.totalDownloads} / $nextMilestone",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f)
                                    )
                                }
                                LinearProgressIndicator(
                                    progress       = { progress.coerceIn(0f, 1f) },
                                    modifier       = Modifier
                                        .fillMaxWidth()
                                        .height(6.dp)
                                        .clip(RoundedCornerShape(3.dp)),
                                    color          = MaterialTheme.colorScheme.primary,
                                    trackColor     = MaterialTheme.colorScheme.surfaceVariant
                                )
                                Text(
                                    AppSettings.milestoneMessage(nextMilestone)?.drop(3)
                                        ?: "$nextMilestone mods",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f)
                                )
                            }
                        }
                    }
                }
            }

            // ── Per-instance stats ────────────────────────────────────────
            if (statsList.isNotEmpty()) {
                item {
                    Text(
                        "Per Instance",
                        style      = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color      = MaterialTheme.colorScheme.primary,
                        modifier   = Modifier.padding(top = 8.dp, bottom = 4.dp)
                    )
                }

                items(statsList, key = { it.folderName }) { stats ->
                    InstanceStatsCard(stats)
                }
            } else if (!isScanning) {
                item {
                    Box(
                        modifier         = Modifier.fillMaxWidth().padding(32.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            "No instances found.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f)
                        )
                    }
                }
            }

            item { Spacer(Modifier.height(32.dp)) }

            // Small inline indicator while still scanning instances
            if (isScanning) {
                item {
                    Row(
                        modifier              = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 16.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment     = Alignment.CenterVertically
                    ) {
                        MojorinthLoadingSpinner(size = 24.dp)
                        Spacer(Modifier.width(8.dp))
                        Text(
                            "Scanning instances…",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f)
                        )
                    }
                }
            }
        }
    }
}

// ─── Instance stats card ──────────────────────────────────────────────────────

@Composable
private fun InstanceStatsCard(stats: InstanceStats) {
    val isActive = stats.folderName == InstanceManager.activeInstanceName
    val green    = MaterialTheme.colorScheme.primary

    Surface(
        shape          = RoundedCornerShape(16.dp),
        color          = MaterialTheme.colorScheme.surface,
        tonalElevation = if (isActive) 4.dp else 2.dp,
        modifier       = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier            = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {

            // Header
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(if (isActive) "🎮" else "📦", fontSize = 20.sp)
                Spacer(Modifier.width(8.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        stats.displayName,
                        style      = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        maxLines   = 1,
                        overflow   = TextOverflow.Ellipsis,
                        color      = if (isActive) green else MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        formatBytes(stats.totalSizeBytes),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                    )
                }
                if (isActive) {
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = green.copy(alpha = 0.12f)
                    ) {
                        Text(
                            "ACTIVE",
                            modifier   = Modifier.padding(horizontal = 7.dp, vertical = 3.dp),
                            style      = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color      = green,
                            fontSize   = 9.sp
                        )
                    }
                }
            }

            HorizontalDivider(
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)
            )

            // Stat chips grid
            Row(
                modifier              = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                StatChip(
                    emoji    = "✅",
                    label    = "Enabled",
                    value    = stats.modsEnabled.toString(),
                    color    = green,
                    modifier = Modifier.weight(1f)
                )
                StatChip(
                    emoji    = "⏸",
                    label    = "Disabled",
                    value    = stats.modsDisabled.toString(),
                    color    = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
                    modifier = Modifier.weight(1f)
                )
            }
            Row(
                modifier              = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                StatChip(
                    emoji    = "✨",
                    label    = "Shaders",
                    value    = stats.shaders.toString(),
                    color    = Color(0xFF42A5F5),
                    modifier = Modifier.weight(1f)
                )
                StatChip(
                    emoji    = "🎨",
                    label    = "Res. Packs",
                    value    = stats.resourcePacks.toString(),
                    color    = Color(0xFFAB47BC),
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

// ─── Small composables ────────────────────────────────────────────────────────

@Composable
private fun GlobalStatRow(icon: ImageVector, label: String, value: String) {
    Row(
        modifier          = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            icon,
            contentDescription = null,
            modifier = Modifier.size(18.dp),
            tint     = MaterialTheme.colorScheme.primary
        )
        Spacer(Modifier.width(10.dp))
        Text(
            label,
            style    = MaterialTheme.typography.bodySmall,
            modifier = Modifier.weight(1f),
            color    = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
        )
        Text(
            value,
            style      = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Bold,
            color      = MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
private fun StatChip(
    emoji: String,
    label: String,
    value: String,
    color: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        shape          = RoundedCornerShape(10.dp),
        color          = color.copy(alpha = 0.08f),
        modifier       = modifier
    ) {
        Column(
            modifier            = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(emoji, fontSize = 16.sp)
            Text(
                value,
                style      = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold,
                color      = color
            )
            Text(
                label,
                style    = MaterialTheme.typography.labelSmall,
                color    = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

// ─── Util ─────────────────────────────────────────────────────────────────────

private fun formatBytes(bytes: Long): String = when {
    bytes >= 1_073_741_824L -> "%.1f GB".format(bytes / 1_073_741_824.0)
    bytes >= 1_048_576L     -> "%.1f MB".format(bytes / 1_048_576.0)
    bytes >= 1_024L         -> "%.1f KB".format(bytes / 1_024.0)
    else                    -> "$bytes B"
}
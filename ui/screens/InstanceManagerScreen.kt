package com.example.modrinthforandroid.ui.screens

import android.content.Context
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.modrinthforandroid.data.InstanceManager
import java.io.File

// ─── Data ─────────────────────────────────────────────────────────────────────

data class ManagedFile(
    val file: File,
    val name: String,
    val isEnabled: Boolean,
    val sizeMb: String,
    val extension: String
)

// ─── Tab definitions ──────────────────────────────────────────────────────────

private data class ContentTab(
    val label: String,
    val emoji: String,
    val subfolder: String,
    val extensions: List<String>
)

private val CONTENT_TABS = listOf(
    ContentTab("Mods",           "⚙️",  "mods",         listOf("jar")),
    ContentTab("Shaders",        "✨",  "shaderpacks",   listOf("zip", "jar")),
    ContentTab("Resource Packs", "🎨",  "resourcepacks", listOf("zip")),
    ContentTab("Data Packs",     "📋",  "datapacks",     listOf("zip")),
    ContentTab("Plugins",        "🔌",  "plugins",       listOf("jar")),
)

// ─── Screen ───────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InstanceManagerScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val instanceName = InstanceManager.activeInstanceName

    var selectedTab by remember { mutableIntStateOf(0) }
    var searchQuery by remember { mutableStateOf("") }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            "Manage Instance",
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        if (instanceName != null) {
                            Text(
                                instanceName,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
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

        if (instanceName == null) {
            Box(
                modifier = Modifier.fillMaxSize().padding(innerPadding),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("🎮", fontSize = 48.sp)
                    Spacer(Modifier.height(16.dp))
                    Text(
                        "No instance selected",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "Pick an instance on the home screen first.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f)
                    )
                }
            }
            return@Scaffold
        }

        Column(modifier = Modifier.fillMaxSize().padding(innerPadding)) {

            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                placeholder = { Text("Search files…") },
                leadingIcon = { Icon(Icons.Default.Search, null) },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { searchQuery = "" }) {
                            Icon(Icons.Default.Close, "Clear")
                        }
                    }
                },
                singleLine = true,
                shape = MaterialTheme.shapes.large,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.2f)
                )
            )

            ScrollableTabRow(
                selectedTabIndex = selectedTab,
                containerColor = MaterialTheme.colorScheme.background,
                contentColor = MaterialTheme.colorScheme.primary,
                edgePadding = 8.dp,
                indicator = { tabPositions ->
                    if (selectedTab < tabPositions.size)
                        TabRowDefaults.SecondaryIndicator(
                            modifier = Modifier.tabIndicatorOffset(tabPositions[selectedTab]),
                            color = MaterialTheme.colorScheme.primary
                        )
                }
            ) {
                CONTENT_TABS.forEachIndexed { index, tab ->
                    Tab(
                        selected = selectedTab == index,
                        onClick = { selectedTab = index; searchQuery = "" },
                        text = {
                            Text(
                                "${tab.emoji} ${tab.label}",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = if (selectedTab == index) FontWeight.Bold else FontWeight.Normal
                            )
                        }
                    )
                }
            }

            val tab = CONTENT_TABS[selectedTab]
            ContentTabPane(
                context = context,
                instanceName = instanceName,
                tab = tab,
                searchQuery = searchQuery
            )
        }
    }
}

// ─── Tab content pane ─────────────────────────────────────────────────────────

@Composable
private fun ContentTabPane(
    context: Context,
    instanceName: String,
    tab: ContentTab,
    searchQuery: String
) {
    // Use the same URI→path conversion that resolveDownloadFolder uses,
    // but strip /files/ since the real path is .../instances/ not .../files/instances/
    val uri = InstanceManager.rootUri
    val instancesRoot = if (uri != null)
        InstanceManager.uriToFilePath(uri).replace("/files/instances", "/instances")
    else ""
    val folderPath = "$instancesRoot/$instanceName/${tab.subfolder}"

    var reloadKey by remember { mutableIntStateOf(0) }

    val allFiles by remember(folderPath, reloadKey) {
        derivedStateOf { loadManagedFiles(folderPath, tab.extensions) }
    }

    val filtered = remember(allFiles, searchQuery) {
        if (searchQuery.isBlank()) allFiles
        else allFiles.filter { it.name.contains(searchQuery, ignoreCase = true) }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        if (allFiles.isEmpty()) {
            Column(
                modifier = Modifier.align(Alignment.Center).padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(tab.emoji, fontSize = 40.sp)
                Spacer(Modifier.height(12.dp))
                Text(
                    "No ${tab.label.lowercase()} found",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    folderPath,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.4f)
                )
            }
        } else {
            LazyColumn(
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                item {
                    Text(
                        "${filtered.size} of ${allFiles.size} file${if (allFiles.size != 1) "s" else ""}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.45f),
                        modifier = Modifier.padding(bottom = 4.dp)
                    )
                }
                items(filtered, key = { it.file.absolutePath }) { managed ->
                    ManagedFileCard(
                        managed = managed,
                        onToggle = {
                            toggleFile(managed)
                            reloadKey++
                        },
                        onDelete = {
                            managed.file.delete()
                            reloadKey++
                        }
                    )
                }
            }
        }
    }
}

// ─── File card ────────────────────────────────────────────────────────────────

@Composable
private fun ManagedFileCard(
    managed: ManagedFile,
    onToggle: () -> Unit,
    onDelete: () -> Unit
) {
    var showDeleteConfirm by remember { mutableStateOf(false) }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("Delete file?") },
            text = { Text("Permanently delete ${managed.name}?") },
            confirmButton = {
                TextButton(onClick = { showDeleteConfirm = false; onDelete() }) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) { Text("Cancel") }
            }
        )
    }

    val enabledColor = MaterialTheme.colorScheme.primary
    val disabledColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.35f)

    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = if (managed.isEnabled) 2.dp else 1.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .padding(horizontal = 14.dp, vertical = 10.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                shape = androidx.compose.foundation.shape.CircleShape,
                color = if (managed.isEnabled) enabledColor.copy(alpha = 0.15f)
                else disabledColor.copy(alpha = 0.1f),
                modifier = Modifier.size(36.dp)
            ) {
                Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                    Text(
                        if (managed.extension == "jar") "⚙️"
                        else if (managed.extension == "zip") "📦"
                        else "📄",
                        fontSize = 16.sp
                    )
                }
            }

            Spacer(Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    managed.name,
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.SemiBold,
                    color = if (managed.isEnabled) MaterialTheme.colorScheme.onSurface
                    else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    lineHeight = 16.sp
                )
                Spacer(Modifier.height(2.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        managed.sizeMb,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                    )
                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = if (managed.isEnabled)
                            enabledColor.copy(alpha = 0.12f)
                        else
                            disabledColor.copy(alpha = 0.08f)
                    ) {
                        Text(
                            if (managed.isEnabled) "Enabled" else "Disabled",
                            modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp),
                            style = MaterialTheme.typography.labelSmall,
                            color = if (managed.isEnabled) enabledColor else disabledColor,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }

            Spacer(Modifier.width(8.dp))

            Switch(
                checked = managed.isEnabled,
                onCheckedChange = { onToggle() },
                modifier = Modifier.height(24.dp),
                colors = SwitchDefaults.colors(
                    checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
                    checkedTrackColor = MaterialTheme.colorScheme.primary
                )
            )

            Spacer(Modifier.width(4.dp))

            IconButton(
                onClick = { showDeleteConfirm = true },
                modifier = Modifier.size(36.dp)
            ) {
                Icon(
                    Icons.Default.Delete,
                    contentDescription = "Delete",
                    tint = MaterialTheme.colorScheme.error.copy(alpha = 0.7f),
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

// ─── Helpers ──────────────────────────────────────────────────────────────────

private fun loadManagedFiles(folderPath: String, extensions: List<String>): List<ManagedFile> {
    val dir = File(folderPath)
    if (!dir.exists() || !dir.isDirectory) return emptyList()

    return (dir.listFiles() ?: emptyArray())
        .filter { file ->
            val baseExt = if (file.name.endsWith(".disabled"))
                file.name.removeSuffix(".disabled").substringAfterLast(".")
            else
                file.name.substringAfterLast(".")
            baseExt in extensions
        }
        .sortedWith(compareBy(
            { it.name.endsWith(".disabled") }, // enabled first
            { it.name.lowercase() }
        ))
        .map { file ->
            val isEnabled = !file.name.endsWith(".disabled")
            val displayName = if (isEnabled) file.name
            else file.name.removeSuffix(".disabled")
            val ext = displayName.substringAfterLast(".")
            val sizeMb = "%.2f MB".format(file.length() / 1_048_576.0)
            ManagedFile(
                file = file,
                name = displayName,
                isEnabled = isEnabled,
                sizeMb = sizeMb,
                extension = ext
            )
        }
}

private fun toggleFile(managed: ManagedFile) {
    val file = managed.file
    val newFile = if (managed.isEnabled)
        File(file.parent, file.name + ".disabled")
    else
        File(file.parent, file.name.removeSuffix(".disabled"))
    file.renameTo(newFile)
}
package com.example.modrinthforandroid.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.modrinthforandroid.data.InstanceManager
import com.example.modrinthforandroid.viewmodel.InstanceFileEntry
import com.example.modrinthforandroid.viewmodel.InstanceManagerViewModel
import com.example.modrinthforandroid.viewmodel.InstanceManagerViewModelFactory

// ─── Tab definitions ──────────────────────────────────────────────────────────

private data class ManagerTab(
    val label: String,
    val emoji: String,
    val subfolder: String
)

private val TABS = listOf(
    ManagerTab("Mods",          "⚙️",  "mods"),
    ManagerTab("Shaders",       "✨",  "shaderpacks"),
    ManagerTab("Resource Packs","🎨",  "resourcepacks")
)

// ─── Screen ───────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InstanceManagerScreen(onBack: () -> Unit) {
    val context       = LocalContext.current
    val instanceName  = InstanceManager.activeInstanceName
    val instanceConfig = InstanceManager.activeInstanceConfig

    // Guard: no active instance
    if (instanceName == null) {
        NoInstancePlaceholder(onBack = onBack)
        return
    }

    var selectedTab  by remember { mutableIntStateOf(0) }
    val currentTab   = TABS[selectedTab]

    val viewModel: InstanceManagerViewModel = viewModel(
        factory = InstanceManagerViewModelFactory(context.applicationContext)
    )

    val files        by viewModel.files.collectAsState()
    val isLoading    by viewModel.isLoading.collectAsState()
    val searchQuery  by viewModel.searchQuery.collectAsState()
    val errorMessage by viewModel.errorMessage.collectAsState()

    // Load files whenever the tab changes
    LaunchedEffect(selectedTab) {
        viewModel.loadFiles(currentTab.subfolder)
    }

    // Confirmation dialog state
    var pendingDelete by remember { mutableStateOf<InstanceFileEntry?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            "Manage Instance",
                            fontWeight = FontWeight.Bold,
                            color      = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            instanceConfig?.name?.takeIf { it.isNotBlank() } ?: instanceName,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f)
                        )
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
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {

            // ── Tab Row ───────────────────────────────────────────────────
            TabRow(
                selectedTabIndex = selectedTab,
                containerColor   = MaterialTheme.colorScheme.background,
                contentColor     = MaterialTheme.colorScheme.primary
            ) {
                TABS.forEachIndexed { index, tab ->
                    Tab(
                        selected = selectedTab == index,
                        onClick  = { selectedTab = index },
                        text     = {
                            Text(
                                "${tab.emoji} ${tab.label}",
                                style      = MaterialTheme.typography.labelMedium,
                                fontWeight = if (selectedTab == index) FontWeight.Bold else FontWeight.Normal
                            )
                        }
                    )
                }
            }

            // ── Search bar ────────────────────────────────────────────────
            OutlinedTextField(
                value         = searchQuery,
                onValueChange = { viewModel.setSearch(it) },
                placeholder   = { Text("Search ${currentTab.label.lowercase()}…") },
                leadingIcon   = {
                    Icon(Icons.Default.Search, null,
                        tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
                },
                trailingIcon  = {
                    AnimatedVisibility(visible = searchQuery.isNotEmpty()) {
                        IconButton(onClick = { viewModel.setSearch("") }) {
                            Icon(Icons.Default.Close, "Clear search",
                                tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
                        }
                    }
                },
                singleLine    = true,
                shape         = RoundedCornerShape(12.dp),
                colors        = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor   = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.2f)
                ),
                modifier      = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 10.dp)
            )

            // ── Error banner ──────────────────────────────────────────────
            AnimatedVisibility(visible = errorMessage != null) {
                errorMessage?.let { msg ->
                    Surface(
                        color    = MaterialTheme.colorScheme.errorContainer,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 4.dp),
                        shape    = RoundedCornerShape(8.dp)
                    ) {
                        Row(
                            modifier          = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Default.Warning, null,
                                tint     = MaterialTheme.colorScheme.onErrorContainer,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                msg,
                                color = MaterialTheme.colorScheme.onErrorContainer,
                                style = MaterialTheme.typography.labelMedium
                            )
                        }
                    }
                }
            }

            // ── File list ─────────────────────────────────────────────────
            Box(modifier = Modifier.fillMaxSize()) {
                when {
                    isLoading -> {
                        CircularProgressIndicator(
                            color    = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.align(Alignment.Center)
                        )
                    }

                    files.isEmpty() -> {
                        EmptyFolderPlaceholder(
                            tab      = currentTab,
                            modifier = Modifier.align(Alignment.Center)
                        )
                    }

                    else -> {
                        LazyColumn(
                            contentPadding      = PaddingValues(
                                start  = 16.dp,
                                end    = 16.dp,
                                top    = 4.dp,
                                bottom = 80.dp
                            ),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            // Count badge
                            item {
                                Text(
                                    "${files.size} file${if (files.size != 1) "s" else ""}",
                                    style    = MaterialTheme.typography.labelSmall,
                                    color    = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f),
                                    modifier = Modifier.padding(bottom = 4.dp)
                                )
                            }

                            items(files, key = { it.uri.toString() }) { entry ->
                                FileRow(
                                    entry      = entry,
                                    isMods     = currentTab.subfolder == "mods",
                                    onToggle   = { viewModel.toggleDisabled(entry) },
                                    onDelete   = { pendingDelete = entry }
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    // ── Delete confirmation dialog ────────────────────────────────────────────
    pendingDelete?.let { entry ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            icon             = {
                Icon(
                    Icons.Default.Delete,
                    null,
                    tint     = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(28.dp)
                )
            },
            title            = { Text("Delete file?", fontWeight = FontWeight.Bold) },
            text             = {
                Text(
                    "\"${entry.displayName}\" will be permanently removed from your instance. This cannot be undone.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f)
                )
            },
            confirmButton    = {
                Button(
                    onClick = {
                        viewModel.deleteFile(entry)
                        pendingDelete = null
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error
                    )
                ) { Text("Delete") }
            },
            dismissButton    = {
                OutlinedButton(onClick = { pendingDelete = null }) { Text("Cancel") }
            }
        )
    }
}

// ─── File row ─────────────────────────────────────────────────────────────────

@Composable
private fun FileRow(
    entry: InstanceFileEntry,
    isMods: Boolean,
    onToggle: () -> Unit,
    onDelete: () -> Unit
) {
    val green   = MaterialTheme.colorScheme.primary
    val dimText = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f)

    Surface(
        shape          = RoundedCornerShape(12.dp),
        color          = MaterialTheme.colorScheme.surface,
        tonalElevation = 2.dp,
        modifier       = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier          = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Disabled indicator stripe
            Box(
                modifier = Modifier
                    .width(3.dp)
                    .height(36.dp)
                    .background(
                        color = if (entry.isDisabled)
                            MaterialTheme.colorScheme.onSurface.copy(alpha = 0.15f)
                        else green,
                        shape = RoundedCornerShape(2.dp)
                    )
            )

            Spacer(Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text       = entry.displayName,
                    style      = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines   = 1,
                    overflow   = TextOverflow.Ellipsis,
                    color      = if (entry.isDisabled)
                        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                    else MaterialTheme.colorScheme.onSurface
                )
                Spacer(Modifier.height(2.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment     = Alignment.CenterVertically
                ) {
                    // File extension chip
                    FileExtChip(entry.extension)

                    if (entry.isDisabled) {
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)
                        ) {
                            Text(
                                "disabled",
                                modifier   = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                style      = MaterialTheme.typography.labelSmall,
                                color      = dimText,
                                fontSize   = 9.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }
            }

            // ── Actions ───────────────────────────────────────────────────
            Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {

                // Toggle disable (only for mods — .jar files)
                if (isMods) {
                    IconButton(
                        onClick  = onToggle,
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            imageVector = if (entry.isDisabled)
                                Icons.Default.PlayArrow else Icons.Default.Pause,
                            contentDescription = if (entry.isDisabled) "Enable mod" else "Disable mod",
                            tint     = if (entry.isDisabled) green else dimText,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                // Delete
                IconButton(
                    onClick  = onDelete,
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        Icons.Default.Delete, "Delete",
                        tint     = MaterialTheme.colorScheme.error.copy(alpha = 0.75f),
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}

// ─── File extension chip ──────────────────────────────────────────────────────

@Composable
private fun FileExtChip(ext: String) {
    val (bg, fg) = when (ext.lowercase()) {
        "jar"      -> Pair(Color(0xFF1BD96A).copy(alpha = 0.12f), Color(0xFF1BD96A))
        "disabled" -> Pair(Color(0xFF9E9E9E).copy(alpha = 0.12f), Color(0xFF9E9E9E))
        "zip"      -> Pair(Color(0xFF42A5F5).copy(alpha = 0.12f), Color(0xFF42A5F5))
        "png"      -> Pair(Color(0xFFAB47BC).copy(alpha = 0.12f), Color(0xFFAB47BC))
        else       -> Pair(
            MaterialTheme.colorScheme.primary.copy(alpha = 0.10f),
            MaterialTheme.colorScheme.primary
        )
    }
    Surface(shape = RoundedCornerShape(4.dp), color = bg) {
        Text(
            ".${ext.take(10)}",
            modifier   = Modifier.padding(horizontal = 5.dp, vertical = 2.dp),
            style      = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color      = fg,
            fontSize   = 9.sp
        )
    }
}

// ─── Placeholder: no active instance ─────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun NoInstancePlaceholder(onBack: () -> Unit) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "Manage Instance",
                        fontWeight = FontWeight.Bold,
                        color      = MaterialTheme.colorScheme.primary
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back",
                            tint = MaterialTheme.colorScheme.onBackground)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        }
    ) { innerPadding ->
        Box(
            modifier         = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier            = Modifier.padding(32.dp)
            ) {
                Text("📦", fontSize = 48.sp)
                Text(
                    "No Active Instance",
                    style      = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    "Select an instance from the home screen first, then come back here to manage its files.",
                    style   = MaterialTheme.typography.bodyMedium,
                    color   = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
                OutlinedButton(onClick = onBack) {
                    Text("Go Back")
                }
            }
        }
    }
}

// ─── Placeholder: empty folder ────────────────────────────────────────────────

@Composable
private fun EmptyFolderPlaceholder(tab: ManagerTab, modifier: Modifier = Modifier) {
    Column(
        modifier            = modifier.padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(tab.emoji, fontSize = 40.sp)
        Text(
            "No ${tab.label} Found",
            style      = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold
        )
        Text(
            "The ${tab.subfolder}/ folder is empty or doesn't exist yet.",
            style   = MaterialTheme.typography.bodySmall,
            color   = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )
    }
}
package com.example.modrinthforandroid.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.modrinthforandroid.data.InstanceManager
import com.example.modrinthforandroid.ui.components.ExportModListSheet
import com.example.modrinthforandroid.ui.components.MojorinthLoadingSpinner
import com.example.modrinthforandroid.viewmodel.DisabledFilter
import com.example.modrinthforandroid.viewmodel.InstanceFileEntry
import com.example.modrinthforandroid.viewmodel.InstanceManagerViewModel
import com.example.modrinthforandroid.viewmodel.InstanceManagerViewModelFactory
import com.example.modrinthforandroid.viewmodel.ManageFilters

// ─── Tab definitions ──────────────────────────────────────────────────────────

private data class ManagerTab(val label: String, val emoji: String, val subfolder: String)

private val TABS = listOf(
    ManagerTab("Mods",          "⚙️",  "mods"),
    ManagerTab("Shaders",       "✨",  "shaderpacks"),
    ManagerTab("Resource Packs","🎨",  "resourcepacks")
)

// ─── Screen ───────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InstanceManagerScreen(onBack: () -> Unit) {
    val context        = LocalContext.current
    val instanceName   = InstanceManager.activeInstanceName
    val instanceConfig = InstanceManager.activeInstanceConfig

    if (instanceName == null) { NoInstancePlaceholder(onBack = onBack); return }

    var selectedTab by remember { mutableIntStateOf(0) }
    val currentTab  = TABS[selectedTab]

    val viewModel: InstanceManagerViewModel = viewModel(
        factory = InstanceManagerViewModelFactory(context.applicationContext)
    )

    val files            by viewModel.files.collectAsState()
    val isLoading        by viewModel.isLoading.collectAsState()
    val searchQuery      by viewModel.searchQuery.collectAsState()
    val filters          by viewModel.filters.collectAsState()
    val errorMessage     by viewModel.errorMessage.collectAsState()
    val availableExts    by viewModel.availableExtensions.collectAsState()

    LaunchedEffect(selectedTab) { viewModel.loadFiles(currentTab.subfolder) }

    var pendingDelete   by remember { mutableStateOf<InstanceFileEntry?>(null) }
    var pendingToggle   by remember { mutableStateOf<InstanceFileEntry?>(null) }
    var showFilterSheet by remember { mutableStateOf(false) }
    var showExportSheet by remember { mutableStateOf(false) }

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
                actions = {
                    // ── Export button ─────────────────────────────────────
                    IconButton(onClick = { showExportSheet = true }) {
                        Icon(
                            Icons.Default.FileDownload, "Export mod list",
                            tint = MaterialTheme.colorScheme.onBackground
                        )
                    }

                    // ── Filter icon with active-count badge ───────────────
                    BadgedBox(
                        badge = {
                            if (filters.activeCount > 0) {
                                Badge { Text("${filters.activeCount}") }
                            }
                        }
                    ) {
                        IconButton(onClick = { showFilterSheet = true }) {
                            Icon(
                                Icons.Default.Settings, "Filters",
                                tint = if (filters.isActive) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.onBackground
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
        Column(Modifier.fillMaxSize().padding(innerPadding)) {

            // ── Tab Row ───────────────────────────────────────────────────
            TabRow(
                selectedTabIndex = selectedTab,
                containerColor   = MaterialTheme.colorScheme.background,
                contentColor     = MaterialTheme.colorScheme.primary
            ) {
                TABS.forEachIndexed { i, tab ->
                    Tab(
                        selected = selectedTab == i,
                        onClick  = { selectedTab = i },
                        text     = {
                            Text(
                                "${tab.emoji} ${tab.label}",
                                style      = MaterialTheme.typography.labelMedium,
                                fontWeight = if (selectedTab == i) FontWeight.Bold else FontWeight.Normal
                            )
                        }
                    )
                }
            }

            // ── Search bar + active filter chips ─────────────────────────
            Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                OutlinedTextField(
                    value         = searchQuery,
                    onValueChange = { viewModel.setSearch(it) },
                    placeholder   = { Text("Search ${currentTab.label.lowercase()}…") },
                    leadingIcon   = {
                        Icon(
                            Icons.Default.Search, null,
                            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                        )
                    },
                    trailingIcon  = {
                        AnimatedVisibility(visible = searchQuery.isNotEmpty()) {
                            IconButton(onClick = { viewModel.setSearch("") }) {
                                Icon(
                                    Icons.Default.Close, "Clear",
                                    tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                                )
                            }
                        }
                    },
                    singleLine    = true,
                    shape         = RoundedCornerShape(12.dp),
                    colors        = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor   = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.2f)
                    ),
                    modifier = Modifier.fillMaxWidth()
                )

                // Active filter chips (quick-remove)
                if (filters.isActive) {
                    Spacer(Modifier.height(8.dp))
                    Row(
                        modifier              = Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        if (filters.disabledFilter != DisabledFilter.ALL) {
                            ActiveFilterChip(
                                label    = when (filters.disabledFilter) {
                                    DisabledFilter.ENABLED_ONLY  -> "Enabled only"
                                    DisabledFilter.DISABLED_ONLY -> "Disabled only"
                                    else                         -> ""
                                },
                                onRemove = { viewModel.setFilters(filters.copy(disabledFilter = DisabledFilter.ALL)) }
                            )
                        }
                        filters.extensionFilter?.let { ext ->
                            ActiveFilterChip(
                                label    = ".$ext",
                                onRemove = { viewModel.setFilters(filters.copy(extensionFilter = null)) }
                            )
                        }
                        if (!filters.sortAZ) {
                            ActiveFilterChip(
                                label    = "Z → A",
                                onRemove = { viewModel.setFilters(filters.copy(sortAZ = true)) }
                            )
                        }
                        AssistChip(
                            onClick = { viewModel.resetFilters() },
                            label   = { Text("Clear all", style = MaterialTheme.typography.labelSmall) }
                        )
                    }
                }
            }

            // ── Error banner ──────────────────────────────────────────────
            AnimatedVisibility(visible = errorMessage != null, enter = fadeIn(), exit = fadeOut()) {
                errorMessage?.let { msg ->
                    Surface(
                        color    = MaterialTheme.colorScheme.errorContainer,
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                        shape    = RoundedCornerShape(8.dp)
                    ) {
                        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
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
            Box(Modifier.fillMaxSize()) {
                when {
                    isLoading -> MojorinthLoadingSpinner(Modifier.align(Alignment.Center))

                    files.isEmpty() -> EmptyPlaceholder(
                        tab      = currentTab,
                        filtered = filters.isActive || searchQuery.isNotEmpty(),
                        modifier = Modifier.align(Alignment.Center)
                    )

                    else -> LazyColumn(
                        contentPadding      = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 80.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        item {
                            Text(
                                "${files.size} file${if (files.size != 1) "s" else ""}",
                                style    = MaterialTheme.typography.labelSmall,
                                color    = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f),
                                modifier = Modifier.padding(bottom = 4.dp)
                            )
                        }
                        items(files, key = { it.uri.toString() }) { entry ->
                            SwipeableFileRow(
                                entry    = entry,
                                isMods   = currentTab.subfolder == "mods",
                                onToggle = { pendingToggle = entry },
                                onDelete = { pendingDelete = entry }
                            )
                        }
                    }
                }
            }
        }
    }

    // ── Toggle confirmation ───────────────────────────────────────────────────
    pendingToggle?.let { entry ->
        AlertDialog(
            onDismissRequest = { pendingToggle = null },
            icon = {
                Icon(
                    if (entry.isDisabled) Icons.Default.PlayArrow else Icons.Default.Pause,
                    null,
                    tint     = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(28.dp)
                )
            },
            title = {
                Text(
                    if (entry.isDisabled) "Enable file?" else "Disable file?",
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Text(
                    if (entry.isDisabled)
                        "\"${entry.displayName}\" will be re-enabled."
                    else
                        "\"${entry.displayName}\" will be disabled and won't load in-game.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f)
                )
            },
            confirmButton = {
                Button(onClick = { viewModel.toggleDisabled(entry); pendingToggle = null }) {
                    Text(if (entry.isDisabled) "Enable" else "Disable")
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { pendingToggle = null }) { Text("Cancel") }
            }
        )
    }

    // ── Delete confirmation ───────────────────────────────────────────────────
    pendingDelete?.let { entry ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            icon  = {
                Icon(
                    Icons.Default.Delete, null,
                    tint     = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(28.dp)
                )
            },
            title = { Text("Delete file?", fontWeight = FontWeight.Bold) },
            text  = {
                Text(
                    "\"${entry.displayName}\" will be permanently removed. This cannot be undone.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f)
                )
            },
            confirmButton = {
                Button(
                    onClick = { viewModel.deleteFile(entry); pendingDelete = null },
                    colors  = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) { Text("Delete") }
            },
            dismissButton = {
                OutlinedButton(onClick = { pendingDelete = null }) { Text("Cancel") }
            }
        )
    }

    // ── Filter bottom sheet ───────────────────────────────────────────────────
    if (showFilterSheet) {
        ManageFilterSheet(
            currentFilters   = filters,
            availableExts    = availableExts,
            isMods           = currentTab.subfolder == "mods",
            onFiltersChanged = { viewModel.setFilters(it) },
            onDismiss        = { showFilterSheet = false }
        )
    }

    // ── Export mod list sheet ─────────────────────────────────────────────────
    if (showExportSheet) {
        ExportModListSheet(onDismiss = { showExportSheet = false })
    }
}

// ─── Filter bottom sheet ──────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ManageFilterSheet(
    currentFilters: ManageFilters,
    availableExts: List<String>,
    isMods: Boolean,
    onFiltersChanged: (ManageFilters) -> Unit,
    onDismiss: () -> Unit
) {
    var disabledFilter  by remember { mutableStateOf(currentFilters.disabledFilter) }
    var extensionFilter by remember { mutableStateOf(currentFilters.extensionFilter) }
    var sortAZ          by remember { mutableStateOf(currentFilters.sortAZ) }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 32.dp)
        ) {
            Text("Filter & Sort",
                style      = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold)

            Spacer(Modifier.height(20.dp))

            if (isMods) {
                SheetSection("Status") {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        DisabledFilter.entries.forEach { option ->
                            FilterChip(
                                selected = disabledFilter == option,
                                onClick  = { disabledFilter = option },
                                label    = {
                                    Text(
                                        when (option) {
                                            DisabledFilter.ALL           -> "All"
                                            DisabledFilter.ENABLED_ONLY  -> "Enabled"
                                            DisabledFilter.DISABLED_ONLY -> "Disabled"
                                        },
                                        style = MaterialTheme.typography.labelSmall
                                    )
                                }
                            )
                        }
                    }
                }
                Spacer(Modifier.height(16.dp))
            }

            if (availableExts.isNotEmpty()) {
                SheetSection("File Type") {
                    Row(
                        modifier              = Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        FilterChip(
                            selected = extensionFilter == null,
                            onClick  = { extensionFilter = null },
                            label    = { Text("Any", style = MaterialTheme.typography.labelSmall) }
                        )
                        availableExts.forEach { ext ->
                            FilterChip(
                                selected = extensionFilter == ext,
                                onClick  = { extensionFilter = if (extensionFilter == ext) null else ext },
                                label    = { Text(".$ext", style = MaterialTheme.typography.labelSmall) }
                            )
                        }
                    }
                }
                Spacer(Modifier.height(16.dp))
            }

            SheetSection("Sort") {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = sortAZ,
                        onClick  = { sortAZ = true },
                        label    = { Text("A → Z", style = MaterialTheme.typography.labelSmall) }
                    )
                    FilterChip(
                        selected = !sortAZ,
                        onClick  = { sortAZ = false },
                        label    = { Text("Z → A", style = MaterialTheme.typography.labelSmall) }
                    )
                }
            }

            Spacer(Modifier.height(24.dp))

            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier              = Modifier.fillMaxWidth()
            ) {
                OutlinedButton(
                    onClick  = { onFiltersChanged(ManageFilters()); onDismiss() },
                    modifier = Modifier.weight(1f)
                ) { Text("Reset") }

                Button(
                    onClick  = {
                        onFiltersChanged(
                            ManageFilters(
                                disabledFilter  = disabledFilter,
                                extensionFilter = extensionFilter,
                                sortAZ          = sortAZ
                            )
                        )
                        onDismiss()
                    },
                    modifier = Modifier.weight(1f)
                ) { Text("Apply") }
            }
        }
    }
}

@Composable
private fun SheetSection(title: String, content: @Composable () -> Unit) {
    Text(
        title,
        style      = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.SemiBold,
        color      = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
    )
    Spacer(Modifier.height(8.dp))
    content()
}

// ─── Active filter chip (dismissible) ────────────────────────────────────────

@Composable
private fun ActiveFilterChip(label: String, onRemove: () -> Unit) {
    InputChip(
        selected     = true,
        onClick      = onRemove,
        label        = { Text(label, style = MaterialTheme.typography.labelSmall) },
        trailingIcon = {
            Icon(Icons.Default.Close, "Remove filter", modifier = Modifier.size(14.dp))
        }
    )
}

// ─── Swipeable file row ───────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SwipeableFileRow(
    entry: InstanceFileEntry,
    isMods: Boolean,
    onToggle: () -> Unit,
    onDelete: () -> Unit
) {
    val dismissState = rememberSwipeToDismissBoxState(
        confirmValueChange = { value ->
            when (value) {
                SwipeToDismissBoxValue.EndToStart -> { onDelete(); false }  // left swipe → delete
                SwipeToDismissBoxValue.StartToEnd -> {                      // right swipe → toggle
                    if (isMods) { onToggle(); false } else false
                }
                else -> false
            }
        },
        positionalThreshold = { it * 0.35f }  // 35% of width to trigger
    )

    SwipeToDismissBox(
        state            = dismissState,
        enableDismissFromStartToEnd = isMods,   // only enable right-swipe for mods
        enableDismissFromEndToStart = true,
        backgroundContent = {
            val direction = dismissState.dismissDirection
            val isDelete  = direction == SwipeToDismissBoxValue.EndToStart
            val isToggle  = direction == SwipeToDismissBoxValue.StartToEnd

            val bgColor = when {
                isDelete -> MaterialTheme.colorScheme.error.copy(alpha = 0.85f)
                isToggle -> if (entry.isDisabled) MaterialTheme.colorScheme.primary.copy(alpha = 0.85f)
                else Color(0xFFFF9800).copy(alpha = 0.85f)
                else     -> Color.Transparent
            }

            val icon = when {
                isDelete -> Icons.Default.Delete
                isToggle -> if (entry.isDisabled) Icons.Default.PlayArrow else Icons.Default.Pause
                else     -> null
            }

            val label = when {
                isDelete -> "Delete"
                isToggle -> if (entry.isDisabled) "Enable" else "Disable"
                else     -> ""
            }

            Box(
                modifier          = Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(12.dp))
                    .background(bgColor)
                    .padding(horizontal = 20.dp),
                contentAlignment  = if (isDelete) Alignment.CenterEnd else Alignment.CenterStart
            ) {
                if (icon != null) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            icon, null,
                            tint     = Color.White,
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(Modifier.height(3.dp))
                        Text(
                            label,
                            color      = Color.White,
                            fontSize   = 10.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    ) {
        FileRow(
            entry    = entry,
            isMods   = isMods,
            onToggle = onToggle,
            onDelete = onDelete
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
                    entry.displayName,
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

            Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                if (isMods) {
                    IconButton(onClick = onToggle, modifier = Modifier.size(36.dp)) {
                        Icon(
                            imageVector        = if (entry.isDisabled) Icons.Default.PlayArrow else Icons.Default.Pause,
                            contentDescription = if (entry.isDisabled) "Enable" else "Disable",
                            tint               = if (entry.isDisabled) green else dimText,
                            modifier           = Modifier.size(18.dp)
                        )
                    }
                }
                IconButton(onClick = onDelete, modifier = Modifier.size(36.dp)) {
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
        "jar"      -> Color(0xFF1BD96A).copy(alpha = 0.12f) to Color(0xFF1BD96A)
        "disabled" -> Color(0xFF9E9E9E).copy(alpha = 0.12f) to Color(0xFF9E9E9E)
        "zip"      -> Color(0xFF42A5F5).copy(alpha = 0.12f) to Color(0xFF42A5F5)
        "png"      -> Color(0xFFAB47BC).copy(alpha = 0.12f) to Color(0xFFAB47BC)
        else       -> MaterialTheme.colorScheme.primary.copy(alpha = 0.10f) to MaterialTheme.colorScheme.primary
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

// ─── Placeholders ─────────────────────────────────────────────────────────────

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
        Box(
            Modifier.fillMaxSize().padding(innerPadding),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier            = Modifier.padding(32.dp)
            ) {
                Text("📦", fontSize = 48.sp)
                Text("No Active Instance",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold)
                Text(
                    "Select an instance from the home screen first.",
                    style     = MaterialTheme.typography.bodyMedium,
                    color     = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                    textAlign = TextAlign.Center
                )
                OutlinedButton(onClick = onBack) { Text("Go Back") }
            }
        }
    }
}

@Composable
private fun EmptyPlaceholder(tab: ManagerTab, filtered: Boolean, modifier: Modifier = Modifier) {
    Column(
        modifier            = modifier.padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(if (filtered) "🔍" else tab.emoji, fontSize = 40.sp)
        Text(
            if (filtered) "No matches" else "No ${tab.label} Found",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold
        )
        Text(
            if (filtered) "Try adjusting your search or filters."
            else "The ${tab.subfolder}/ folder is empty or doesn't exist yet.",
            style     = MaterialTheme.typography.bodySmall,
            color     = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
            textAlign = TextAlign.Center
        )
    }
}
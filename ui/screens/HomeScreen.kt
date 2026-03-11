package com.example.modrinthforandroid.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.example.modrinthforandroid.data.AppSettings
import com.example.modrinthforandroid.data.InstanceManager
import com.example.modrinthforandroid.data.model.SearchResult
import com.example.modrinthforandroid.ui.components.formatNumber
import com.example.modrinthforandroid.viewmodel.HomeUiState
import com.example.modrinthforandroid.viewmodel.HomeViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onModClick: (String) -> Unit,
    onSearchClick: () -> Unit,
    onDownloadsClick: () -> Unit,
    onSettingsClick: () -> Unit,
    onBrowseType: (String) -> Unit = {},
    viewModel: HomeViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(28.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primary),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("M", fontWeight = FontWeight.Black,
                                color = MaterialTheme.colorScheme.onPrimary, fontSize = 16.sp)
                        }
                        Spacer(Modifier.width(8.dp))
                        Text("Mojorinth", fontWeight = FontWeight.Black,
                            color = MaterialTheme.colorScheme.onBackground, fontSize = 20.sp)
                    }
                },
                actions = {
                    IconButton(onClick = onSearchClick) {
                        Icon(Icons.Default.Search, "Browse",
                            tint = MaterialTheme.colorScheme.onBackground)
                    }
                    IconButton(onClick = onDownloadsClick) {
                        Icon(Icons.Default.Build, "Downloads",
                            tint = MaterialTheme.colorScheme.onBackground)
                    }
                    IconButton(onClick = onSettingsClick) {
                        Icon(Icons.Default.Settings, "Settings",
                            tint = MaterialTheme.colorScheme.onBackground)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background)
            )
        }
    ) { innerPadding ->
        when {
            uiState.isLoading -> Box(
                Modifier.fillMaxSize().padding(innerPadding),
                contentAlignment = Alignment.Center
            ) { CircularProgressIndicator(color = MaterialTheme.colorScheme.primary) }

            uiState.error != null -> Box(
                Modifier.fillMaxSize().padding(innerPadding),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("😕 ${uiState.error}", style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f))
                    Spacer(Modifier.height(16.dp))
                    Button(onClick = { viewModel.refresh() }) { Text("Retry") }
                }
            }

            else -> HomeContent(
                uiState      = uiState,
                onModClick   = onModClick,
                onBrowseType = onBrowseType,
                innerPadding = innerPadding
            )
        }
    }
}

// ─── Main content ─────────────────────────────────────────────────────────────

@Composable
private fun HomeContent(
    uiState: HomeUiState,
    onModClick: (String) -> Unit,
    onBrowseType: (String) -> Unit,
    innerPadding: PaddingValues
) {
    LazyColumn(
        contentPadding = PaddingValues(
            top    = innerPadding.calculateTopPadding(),
            bottom = innerPadding.calculateBottomPadding() + 16.dp
        )
    ) {
        item { MojoLauncherCard() }

        item {
            Spacer(Modifier.height(20.dp))
            SectionHeader("Browse", "What are you looking for?")
            Spacer(Modifier.height(12.dp))
            BrowseGrid(onBrowseType = onBrowseType)
        }

        if (uiState.trendingMods.isNotEmpty()) {
            item {
                Spacer(Modifier.height(24.dp))
                SectionHeader("Trending Mods", "Most downloaded right now",
                    onSeeAll = { onBrowseType("mod") })
                Spacer(Modifier.height(12.dp))
                ModRow(mods = uiState.trendingMods, onModClick = onModClick)
            }
        }

        if (uiState.trendingModpacks.isNotEmpty()) {
            item {
                Spacer(Modifier.height(24.dp))
                SectionHeader("Popular Modpacks", "Ready-to-play curated packs",
                    onSeeAll = { onBrowseType("modpack") })
                Spacer(Modifier.height(12.dp))
                ModRow(mods = uiState.trendingModpacks, onModClick = onModClick)
            }
        }

        if (uiState.trendingShaders.isNotEmpty()) {
            item {
                Spacer(Modifier.height(24.dp))
                SectionHeader("Top Shaders", "Make your game beautiful",
                    onSeeAll = { onBrowseType("shader") })
                Spacer(Modifier.height(12.dp))
                ModRow(mods = uiState.trendingShaders, onModClick = onModClick)
            }
        }

        if (uiState.newlyUpdated.isNotEmpty()) {
            item {
                Spacer(Modifier.height(24.dp))
                SectionHeader("Recently Updated", "Fresh patches & new features",
                    onSeeAll = { onBrowseType("mod") })
                Spacer(Modifier.height(12.dp))
                ModRow(mods = uiState.newlyUpdated, onModClick = onModClick)
                Spacer(Modifier.height(8.dp))
            }
        }
    }
}

// ─── MojoLauncher Card ────────────────────────────────────────────────────────
// One card that handles both:
//  Step 1 — pick the instances root folder (system Files app)
//  Step 2 — search/select an instance subfolder

@Composable
private fun MojoLauncherCard() {
    val context = LocalContext.current
    val green   = MaterialTheme.colorScheme.primary

    var rootSet      by cremember { mutableStateOf(InstanceManager.isRootSet) }
    var displayPath  by remember { mutableStateOf(InstanceManager.rootDisplayPath) }
    var activeInst   by remember { mutableStateOf(InstanceManager.activeInstance) }

    // Instances list + search state
    var instances    by remember { mutableStateOf(listOf<String>()) }
    var searchQuery  by remember { mutableStateOf("") }
    var showPicker   by remember { mutableStateOf(false) }

    // Load instance list whenever the root is set
    LaunchedEffect(rootSet) {
        if (rootSet) instances = InstanceManager.listInstances(context)
    }

    val filteredInstances = remember(instances, searchQuery) {
        if (searchQuery.isBlank()) instances
        else instances.filter { it.contains(searchQuery, ignoreCase = true) }
    }

    // System folder picker
    val folderPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        if (uri != null) {
            InstanceManager.setRootFromUri(context, uri)
            rootSet     = InstanceManager.isRootSet
            displayPath = InstanceManager.rootDisplayPath
            activeInst  = null
            instances   = InstanceManager.listInstances(context)
        }
    }

    Surface(
        modifier       = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        shape          = RoundedCornerShape(16.dp),
        color          = MaterialTheme.colorScheme.surface,
        tonalElevation = 3.dp
    ) {
        Column(modifier = Modifier.padding(16.dp)) {

            // ── Header ────────────────────────────────────────────────────
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(green.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) { Text("🎮", fontSize = 18.sp) }
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text("MojoLauncher", style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                    Text(
                        when {
                            activeInst != null -> "Active: $activeInst"
                            rootSet            -> "Select an instance below"
                            else               -> "No folder set — tap to get started"
                        },
                        style    = MaterialTheme.typography.labelSmall,
                        color    = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f),
                        maxLines = 1, overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Spacer(Modifier.height(14.dp))

            // ── Step 1: Root folder chip ──────────────────────────────────
            if (rootSet && displayPath != null) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f))
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.List, null,
                        modifier = Modifier.size(14.dp),
                        tint     = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f))
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text     = displayPath!!,
                        style    = MaterialTheme.typography.labelSmall,
                        color    = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                        modifier = Modifier.weight(1f),
                        maxLines = 1, overflow = TextOverflow.Ellipsis
                    )
                    Spacer(Modifier.width(6.dp))
                    TextButton(
                        onClick        = { folderPicker.launch(null) },
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                    ) {
                        Text("Change", style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary)
                    }
                }

                Spacer(Modifier.height(12.dp))

                // ── Step 2: Active instance chip ──────────────────────────
                if (activeInst != null) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(green.copy(alpha = 0.12f))
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("📁", fontSize = 16.sp)
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text       = activeInst!!,
                            style      = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                            color      = green,
                            modifier   = Modifier.weight(1f),
                            maxLines   = 1, overflow = TextOverflow.Ellipsis
                        )
                        IconButton(
                            onClick  = {
                                InstanceManager.clearActiveInstance(context)
                                activeInst   = null
                                showPicker   = false
                                searchQuery  = ""
                            },
                            modifier = Modifier.size(28.dp)
                        ) {
                            Icon(Icons.Default.Close, "Clear instance",
                                tint     = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                                modifier = Modifier.size(16.dp))
                        }
                    }

                    Spacer(Modifier.height(8.dp))

                    OutlinedButton(
                        onClick  = { showPicker = !showPicker; searchQuery = "" },
                        modifier = Modifier.fillMaxWidth(),
                        shape    = RoundedCornerShape(10.dp)
                    ) {
                        Text("Switch instance", style = MaterialTheme.typography.labelMedium)
                    }
                } else {
                    // No instance selected yet — show picker inline
                    showPicker = true
                }

                // ── Instance search + list ────────────────────────────────
                AnimatedVisibility(visible = showPicker) {
                    Column {
                        Spacer(Modifier.height(8.dp))

                        // Search bar
                        OutlinedTextField(
                            value         = searchQuery,
                            onValueChange = { searchQuery = it },
                            modifier      = Modifier.fillMaxWidth(),
                            placeholder   = { Text("Search instances…") },
                            leadingIcon   = { Icon(Icons.Default.Search, null,
                                modifier = Modifier.size(18.dp)) },
                            trailingIcon  = {
                                if (searchQuery.isNotEmpty()) {
                                    IconButton(onClick = { searchQuery = "" }) {
                                        Icon(Icons.Default.Close, "Clear",
                                            modifier = Modifier.size(16.dp))
                                    }
                                }
                            },
                            singleLine    = true,
                            shape         = RoundedCornerShape(10.dp),
                            colors        = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor   = green,
                                unfocusedBorderColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.2f)
                            )
                        )

                        Spacer(Modifier.height(6.dp))

                        if (filteredInstances.isEmpty()) {
                            Box(
                                modifier         = Modifier.fillMaxWidth().padding(vertical = 16.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    if (instances.isEmpty()) "No instances found in this folder"
                                    else                     "No instances match \"$searchQuery\"",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                                )
                            }
                        } else {
                            // Instance list (capped at 6 visible, scrolls internally)
                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                filteredInstances.take(6).forEach { name ->
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(
                                                if (name == activeInst) green.copy(alpha = 0.1f)
                                                else Color.Transparent
                                            )
                                            .clickable {
                                                InstanceManager.setActiveInstance(context, name)
                                                activeInst  = name
                                                showPicker  = false
                                                searchQuery = ""
                                            }
                                            .padding(horizontal = 12.dp, vertical = 10.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text("📁", fontSize = 16.sp)
                                        Spacer(Modifier.width(10.dp))
                                        Text(
                                            text       = name,
                                            style      = MaterialTheme.typography.bodySmall,
                                            fontWeight = FontWeight.Medium,
                                            color      = if (name == activeInst) green
                                            else MaterialTheme.colorScheme.onSurface,
                                            modifier   = Modifier.weight(1f)
                                        )
                                        if (name == activeInst) {
                                            Icon(Icons.Default.Check, null,
                                                tint     = green,
                                                modifier = Modifier.size(16.dp))
                                        }
                                    }
                                    if (name != filteredInstances.take(6).last()) {
                                        HorizontalDivider(
                                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f))
                                    }
                                }
                                if (filteredInstances.size > 6) {
                                    Text(
                                        "+ ${filteredInstances.size - 6} more — refine your search",
                                        style    = MaterialTheme.typography.labelSmall,
                                        color    = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
                                        modifier = Modifier.padding(top = 4.dp, start = 4.dp)
                                    )
                                }
                            }
                        }
                    }
                }

            } else {
                // ── No root set yet ───────────────────────────────────────
                Text(
                    "Select your MojoLauncher instances folder. You'll then be able to pick which instance to download mods into.",
                    style      = MaterialTheme.typography.bodySmall,
                    color      = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                    lineHeight = 18.sp
                )
                Spacer(Modifier.height(12.dp))
                Button(
                    onClick  = { folderPicker.launch(null) },
                    modifier = Modifier.fillMaxWidth(),
                    shape    = RoundedCornerShape(10.dp)
                ) {
                    Icon(Icons.Default.List, null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Choose instances folder", fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

// ─── Section Header ───────────────────────────────────────────────────────────

@Composable
private fun SectionHeader(
    title: String,
    subtitle: String? = null,
    onSeeAll: (() -> Unit)? = null
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onBackground)
            if (subtitle != null)
                Text(subtitle, style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f))
        }
        if (onSeeAll != null)
            TextButton(onClick = onSeeAll) {
                Text("See all", style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary)
            }
    }
}

// ─── Browse Grid ──────────────────────────────────────────────────────────────

private data class BrowseItem(val label: String, val emoji: String, val type: String, val tint: Color)

@Composable
private fun BrowseGrid(onBrowseType: (String) -> Unit) {
    val green = MaterialTheme.colorScheme.primary
    val items = listOf(
        BrowseItem("Mods",           "⚙️",  "mod",          green),
        BrowseItem("Modpacks",       "📦",  "modpack",      Color(0xFF42A5F5)),
        BrowseItem("Shaders",        "✨",  "shader",       Color(0xFFFFA726)),
        BrowseItem("Resource\nPacks","🎨",  "resourcepack", Color(0xFFAB47BC)),
        BrowseItem("Data\nPacks",    "📋",  "datapack",     Color(0xFF26A69A)),
        BrowseItem("Plugins",        "🔌",  "plugin",       Color(0xFFEF5350))
    )
    LazyRow(
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        items(items) { item ->
            Surface(
                onClick  = { onBrowseType(item.type) },
                shape    = RoundedCornerShape(14.dp),
                color    = item.tint.copy(alpha = 0.1f),
                modifier = Modifier
                    .width(88.dp).height(80.dp)
                    .border(1.dp, item.tint.copy(alpha = 0.2f), RoundedCornerShape(14.dp))
            ) {
                Column(
                    modifier = Modifier.fillMaxSize().padding(8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(item.emoji, fontSize = 24.sp)
                    Spacer(Modifier.height(4.dp))
                    Text(item.label,
                        style      = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold,
                        color      = item.tint,
                        maxLines   = 2, overflow = TextOverflow.Ellipsis,
                        lineHeight = 13.sp)
                }
            }
        }
    }
}

// ─── Horizontal Mod Row ───────────────────────────────────────────────────────

@Composable
private fun ModRow(mods: List<SearchResult>, onModClick: (String) -> Unit) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        items(mods, key = { it.projectId }) { mod ->
            Surface(
                onClick        = { onModClick(mod.projectId) },
                shape          = RoundedCornerShape(14.dp),
                color          = MaterialTheme.colorScheme.surface,
                tonalElevation = 2.dp,
                modifier       = Modifier.width(150.dp)
            ) {
                Column(modifier = Modifier.padding(10.dp)) {
                    AsyncImage(
                        model              = mod.iconUrl,
                        contentDescription = "${mod.title} icon",
                        modifier           = Modifier.size(48.dp).clip(RoundedCornerShape(10.dp)),
                        contentScale       = ContentScale.Crop
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(mod.title, style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.SemiBold, maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        color = MaterialTheme.colorScheme.onSurface, lineHeight = 16.sp)
                    Spacer(Modifier.height(4.dp))
                    Text("⬇ ${formatNumber(mod.downloads)}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary)
                    mod.categories.firstOrNull()?.let { cat ->
                        Spacer(Modifier.height(4.dp))
                        Surface(shape = RoundedCornerShape(4.dp),
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)) {
                            Text(cat,
                                modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp),
                                style    = MaterialTheme.typography.labelSmall,
                                color    = MaterialTheme.colorScheme.primary)
                        }
                    }
                }
            }
        }
    }
}
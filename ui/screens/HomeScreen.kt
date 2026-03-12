package com.example.modrinthforandroid.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
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
    onManageInstance: () -> Unit = {},
    viewModel: HomeViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    // Track active instance reactively so the MANAGE button state stays fresh
    var activeInstance by remember { mutableStateOf(InstanceManager.activeInstanceName) }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                activeInstance = InstanceManager.activeInstanceName
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // Show this when MANAGE is tapped but no instance is active
    var showNoInstanceDialog by remember { mutableStateOf(false) }

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
                            Text(
                                "M", fontWeight = FontWeight.Black,
                                color    = MaterialTheme.colorScheme.onPrimary,
                                fontSize = 16.sp
                            )
                        }
                        Spacer(Modifier.width(8.dp))
                        Text(
                            "Mojorinth", fontWeight = FontWeight.Black,
                            color    = MaterialTheme.colorScheme.onBackground,
                            fontSize = 20.sp
                        )
                    }
                },
                actions = {
                    // MANAGE INSTANCE — always visible; guards itself with a dialog
                    TextButton(
                        onClick = {
                            if (activeInstance != null) onManageInstance()
                            else showNoInstanceDialog = true
                        }
                    ) {
                        Text(
                            "MANAGE",
                            style      = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            // Dim the label when no instance is active so it looks inactive
                            color      = if (activeInstance != null)
                                MaterialTheme.colorScheme.primary
                            else
                                MaterialTheme.colorScheme.onSurface.copy(alpha = 0.35f)
                        )
                    }
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
                    Text(
                        "😕 ${uiState.error}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f)
                    )
                    Spacer(Modifier.height(16.dp))
                    Button(onClick = { viewModel.refresh() }) { Text("Retry") }
                }
            }

            else -> HomeContent(
                uiState          = uiState,
                onModClick       = onModClick,
                onBrowseType     = onBrowseType,
                onManageInstance = onManageInstance,
                onInstancePicked = { activeInstance = InstanceManager.activeInstanceName },
                innerPadding     = innerPadding
            )
        }
    }

    // ── No-instance dialog ────────────────────────────────────────────────────
    if (showNoInstanceDialog) {
        AlertDialog(
            onDismissRequest = { showNoInstanceDialog = false },
            icon             = {
                Text("📦", fontSize = 32.sp)
            },
            title            = { Text("No Instance Selected", fontWeight = FontWeight.Bold) },
            text             = {
                Text(
                    "Select an instance from the card below first, then tap MANAGE to view and edit its mods, shaders, and resource packs.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f)
                )
            },
            confirmButton    = {
                Button(onClick = { showNoInstanceDialog = false }) { Text("Got it") }
            }
        )
    }
}

// ─── Main content ─────────────────────────────────────────────────────────────

@Composable
private fun HomeContent(
    uiState: HomeUiState,
    onModClick: (String) -> Unit,
    onBrowseType: (String) -> Unit,
    onManageInstance: () -> Unit,
    onInstancePicked: () -> Unit,
    innerPadding: PaddingValues
) {
    LazyColumn(
        contentPadding = PaddingValues(
            top    = innerPadding.calculateTopPadding(),
            bottom = innerPadding.calculateBottomPadding() + 16.dp
        )
    ) {
        item { InstancePickerCard(onInstanceChanged = onInstancePicked) }

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

// ─── Instance badge chip ──────────────────────────────────────────────────────

@Composable
private fun InstanceBadge(
    icon: String,
    label: String,
    value: String,
    color: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        shape    = RoundedCornerShape(8.dp),
        color    = color.copy(alpha = 0.10f),
        modifier = modifier
    ) {
        Row(
            modifier          = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(icon, fontSize = 13.sp)
            Spacer(Modifier.width(6.dp))
            Column {
                Text(
                    label,
                    style    = MaterialTheme.typography.labelSmall,
                    color    = color.copy(alpha = 0.75f),
                    fontSize = 9.sp
                )
                Text(
                    value,
                    style      = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.SemiBold,
                    color      = color,
                    maxLines   = 1,
                    overflow   = TextOverflow.Ellipsis
                )
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
        modifier              = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        verticalAlignment     = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color      = MaterialTheme.colorScheme.onBackground)
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
        BrowseItem("Mods",           "⚙️", "mod",          green),
        BrowseItem("Modpacks",       "📦", "modpack",      Color(0xFF42A5F5)),
        BrowseItem("Shaders",        "✨", "shader",       Color(0xFFFFA726)),
        BrowseItem("Resource\nPacks","🎨", "resourcepack", Color(0xFFAB47BC)),
        BrowseItem("Data\nPacks",    "📋", "datapack",     Color(0xFF26A69A)),
        BrowseItem("Plugins",        "🔌", "plugin",       Color(0xFFEF5350))
    )
    LazyRow(
        contentPadding        = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        items(items) { item ->
            Surface(
                onClick  = { onBrowseType(item.type) },
                shape    = RoundedCornerShape(14.dp),
                color    = item.tint.copy(alpha = 0.1f),
                modifier = Modifier
                    .width(88.dp)
                    .height(80.dp)
                    .border(1.dp, item.tint.copy(alpha = 0.2f), RoundedCornerShape(14.dp))
            ) {
                Column(
                    modifier            = Modifier.fillMaxSize().padding(8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(item.emoji, fontSize = 24.sp)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        item.label,
                        style      = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold,
                        color      = item.tint,
                        maxLines   = 2,
                        overflow   = TextOverflow.Ellipsis,
                        lineHeight = 13.sp
                    )
                }
            }
        }
    }
}

// ─── Horizontal Mod Row ───────────────────────────────────────────────────────

@Composable
private fun ModRow(mods: List<SearchResult>, onModClick: (String) -> Unit) {
    LazyRow(
        contentPadding        = PaddingValues(horizontal = 16.dp),
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
                        modifier           = Modifier
                            .size(48.dp)
                            .clip(RoundedCornerShape(10.dp)),
                        contentScale       = ContentScale.Crop
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        mod.title,
                        style      = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.SemiBold,
                        maxLines   = 2,
                        overflow   = TextOverflow.Ellipsis,
                        color      = MaterialTheme.colorScheme.onSurface,
                        lineHeight = 16.sp
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "⬇ ${formatNumber(mod.downloads)}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                    mod.categories.firstOrNull()?.let { cat ->
                        Spacer(Modifier.height(4.dp))
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                        ) {
                            Text(
                                cat,
                                modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp),
                                style    = MaterialTheme.typography.labelSmall,
                                color    = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            }
        }
    }
}
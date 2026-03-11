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
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
                                color = MaterialTheme.colorScheme.onPrimary,
                                fontSize = 16.sp)
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
                    Text("😕 ${uiState.error}",
                        style = MaterialTheme.typography.bodyMedium,
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
        item { InstancePickerCard() }

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

// ─── Instance Picker Card ─────────────────────────────────────────────────────

// ─── Instance Picker Card ─────────────────────────────────────────────────────
// Replace the existing InstancePickerCard composable in HomeScreen.kt with this.

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun InstancePickerCard() {
    val context      = LocalContext.current
    val focusManager = LocalFocusManager.current

    var activeName     by remember { mutableStateOf(InstanceManager.activeInstanceName) }
    var rootDisplay    by remember { mutableStateOf(InstanceManager.rootDisplayPath) }
    var instances      by remember { mutableStateOf(InstanceManager.listInstances(context)) }
    var instanceConfig by remember { mutableStateOf(InstanceManager.activeInstanceConfig) }

    val folderPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        if (uri != null) {
            InstanceManager.setRootFromUri(context, uri)
            rootDisplay    = InstanceManager.rootDisplayPath
            instances      = InstanceManager.listInstances(context)
            activeName     = null
            instanceConfig = null
        }
    }

    val green = MaterialTheme.colorScheme.primary

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
                ) {
                    Text("🎮", fontSize = 18.sp)
                }
                Spacer(Modifier.width(10.dp))
                Column {
                    Text(
                        "Active Instance",
                        style      = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color      = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = when {
                            activeName != null  -> "Downloads → …/$activeName"
                            rootDisplay != null -> "Root set — pick an instance below"
                            else                -> "No instance selected"
                        },
                        style    = MaterialTheme.typography.labelSmall,
                        color    = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Spacer(Modifier.height(14.dp))

            // ── Active instance chip + config badges ──────────────────────
            AnimatedVisibility(visible = activeName != null) {
                Column {
                    // Instance name row
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
                            text       = activeName ?: "",
                            style      = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                            color      = green,
                            modifier   = Modifier.weight(1f)
                        )
                        IconButton(
                            onClick  = {
                                InstanceManager.clearActiveInstance(context)
                                activeName     = null
                                instanceConfig = null
                            },
                            modifier = Modifier.size(28.dp)
                        ) {
                            Icon(
                                Icons.Default.Close, "Clear instance",
                                tint     = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }

                    // ── Parsed config badges ──────────────────────────────
                    AnimatedVisibility(visible = instanceConfig != null) {
                        val config = instanceConfig
                        if (config != null) {
                            Column(modifier = Modifier.padding(top = 10.dp)) {
                                // Row 1: Loader + MC Version
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    InstanceBadge(
                                        icon  = "⚙️",
                                        label = "Loader",
                                        value = if (config.loaderVersion.isNotEmpty())
                                            "${config.loader} ${config.loaderVersion}"
                                        else config.loader,
                                        color = when (config.loader) {
                                            "Fabric"   -> Color(0xFFDBB155)
                                            "Forge"    -> Color(0xFF8B5E3C)
                                            "NeoForge" -> Color(0xFFE87B2B)
                                            "Quilt"    -> Color(0xFF9B59B6)
                                            else       -> MaterialTheme.colorScheme.primary
                                        }
                                    )
                                    InstanceBadge(
                                        icon  = "🟩",
                                        label = "MC Version",
                                        value = config.mcVersion,
                                        color = Color(0xFF5DA85D)
                                    )
                                }

                                Spacer(Modifier.height(8.dp))

                                // Row 2: Renderer
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    InstanceBadge(
                                        icon  = "🖥️",
                                        label = "Renderer",
                                        value = config.rendererDisplay,
                                        color = Color(0xFF4A90D9),
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                }
                            }
                        }
                    }

                    // Config read failed notice
                    AnimatedVisibility(
                        visible = activeName != null && instanceConfig == null
                    ) {
                        Row(
                            modifier = Modifier
                                .padding(top = 8.dp)
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(
                                    MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f)
                                )
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("⚠️", fontSize = 14.sp)
                            Spacer(Modifier.width(8.dp))
                            Text(
                                "Could not read mojo_instance.json",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                            )
                        }
                    }

                    Spacer(Modifier.height(10.dp))
                }
            }

            // ── Root folder row ───────────────────────────────────────────
            Row(
                modifier          = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    if (rootDisplay != null) {
                        Text(
                            rootDisplay!!.split("/").takeLast(3).joinToString("/"),
                            style    = MaterialTheme.typography.labelSmall,
                            color    = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    } else {
                        Text(
                            "No instances folder set",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                        )
                    }
                }
                Spacer(Modifier.width(8.dp))
                OutlinedButton(
                    onClick        = { folderPicker.launch(null) },
                    shape          = RoundedCornerShape(10.dp),
                    colors         = ButtonDefaults.outlinedButtonColors(contentColor = green),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Icon(Icons.Default.List, null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(
                        if (rootDisplay != null) "Change" else "Pick folder",
                        style      = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            // ── Scrollable instance list ──────────────────────────────────
            AnimatedVisibility(visible = instances.isNotEmpty()) {
                Column(modifier = Modifier.padding(top = 12.dp)) {
                    Text(
                        "${instances.size} instance${if (instances.size != 1) "s" else ""} found:",
                        style    = MaterialTheme.typography.labelSmall,
                        color    = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                        modifier = Modifier.padding(bottom = 6.dp)
                    )
                    Surface(
                        shape          = RoundedCornerShape(10.dp),
                        color          = MaterialTheme.colorScheme.background.copy(alpha = 0.5f),
                        tonalElevation = 0.dp,
                        modifier       = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 220.dp)
                    ) {
                        LazyColumn(modifier = Modifier.fillMaxWidth()) {
                            items(instances) { name ->
                                val isActive = name == activeName
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(
                                            if (isActive) green.copy(alpha = 0.08f)
                                            else Color.Transparent
                                        )
                                        .clickable {
                                            InstanceManager.setActiveInstance(context, name)
                                            activeName     = InstanceManager.activeInstanceName
                                            instanceConfig = InstanceManager.activeInstanceConfig
                                            focusManager.clearFocus()
                                        }
                                        .padding(horizontal = 12.dp, vertical = 11.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(if (isActive) "🎮" else "📁", fontSize = 14.sp)
                                    Spacer(Modifier.width(10.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            name,
                                            style      = MaterialTheme.typography.bodySmall,
                                            fontWeight = if (isActive) FontWeight.Bold else FontWeight.Normal,
                                            color      = if (isActive) green
                                            else MaterialTheme.colorScheme.onSurface
                                        )
                                        // Show summary line if this is the active instance
                                        // and config is loaded
                                        if (isActive && instanceConfig != null) {
                                            Text(
                                                instanceConfig!!.summary,
                                                style = MaterialTheme.typography.labelSmall,
                                                color = green.copy(alpha = 0.75f)
                                            )
                                        }
                                    }
                                    if (isActive) {
                                        Icon(
                                            Icons.Default.Check, null,
                                            modifier = Modifier.size(14.dp),
                                            tint     = green
                                        )
                                    }
                                }
                                if (name != instances.last()) {
                                    HorizontalDivider(
                                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f)
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // ── Tips ──────────────────────────────────────────────────────
            if (rootDisplay != null && instances.isEmpty()) {
                Spacer(Modifier.height(10.dp))
                Text(
                    "No instance subfolders found. Make sure you selected the right folder.",
                    style      = MaterialTheme.typography.labelSmall,
                    color      = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f),
                    lineHeight = 16.sp
                )
            }

            if (rootDisplay == null) {
                Spacer(Modifier.height(10.dp))
                Text(
                    "Tap \"Pick folder\" to select your MojoLauncher instances directory.",
                    style      = MaterialTheme.typography.labelSmall,
                    color      = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f),
                    lineHeight = 16.sp
                )
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
                    style  = MaterialTheme.typography.labelSmall,
                    color  = color.copy(alpha = 0.75f),
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
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground)
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
                    Text(item.label,
                        style      = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold,
                        color      = item.tint,
                        maxLines   = 2,
                        overflow   = TextOverflow.Ellipsis,
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
                    Text(mod.title,
                        style      = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.SemiBold,
                        maxLines   = 2,
                        overflow   = TextOverflow.Ellipsis,
                        color      = MaterialTheme.colorScheme.onSurface,
                        lineHeight = 16.sp)
                    Spacer(Modifier.height(4.dp))
                    Text("⬇ ${formatNumber(mod.downloads)}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary)
                    mod.categories.firstOrNull()?.let { cat ->
                        Spacer(Modifier.height(4.dp))
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                        ) {
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
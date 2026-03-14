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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.example.modrinthforandroid.data.AppSettings
import com.example.modrinthforandroid.data.InstanceManager
import com.example.modrinthforandroid.data.model.SearchResult
import com.example.modrinthforandroid.ui.components.ExportModListSheet
import com.example.modrinthforandroid.ui.components.TutorialOverlay
import com.example.modrinthforandroid.ui.components.formatNumber
import com.example.modrinthforandroid.viewmodel.HomeUiState
import com.example.modrinthforandroid.viewmodel.HomeViewModel
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onModClick: (String) -> Unit,
    onSearchClick: () -> Unit,
    onDownloadsClick: () -> Unit,
    onSettingsClick: () -> Unit,
    onBrowseType: (String) -> Unit = {},
    onManageInstance: () -> Unit = {},
    onLogsClick: () -> Unit = {},
    viewModel: HomeViewModel = viewModel()
) {
    val context   = LocalContext.current
    val settings  = remember { AppSettings.get(context) }
    val uiState by viewModel.uiState.collectAsState()

    // Show tutorial on first launch
    var showTutorial by remember { mutableStateOf(!settings.hasSeenTutorial) }

    // Warn if Mojo Launcher isn't installed (only on first launch)
    val mojoInstalled = remember {
        context.packageManager.getLaunchIntentForPackage("git.artdeell.mojo") != null
    }
    var showMojoWarning by remember { mutableStateOf(!mojoInstalled && !settings.hasSeenTutorial) }

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

    var showNoInstanceDialog by remember { mutableStateOf(false) }
    var showExportSheet      by remember { mutableStateOf(false) }

    // ── Drawer state ──────────────────────────────────────────────────────────
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope       = rememberCoroutineScope()
    val density     = LocalDensity.current

    // How wide the touch zone is from the left edge (in px)
    val edgeZonePx  = with(density) { 32.dp.toPx() }
    // How far you need to drag right to trigger open (in px)
    val swipeThresholdPx = with(density) { 40.dp.toPx() }

    ModalNavigationDrawer(
        drawerState    = drawerState,
        gesturesEnabled = false,  // disable built-in (too strict), we handle it ourselves
        drawerContent = {
            AppDrawer(
                activeInstance   = activeInstance,
                onLogsClick      = {
                    scope.launch { drawerState.close() }
                    onLogsClick()
                },
                onManageInstance = {
                    scope.launch { drawerState.close() }
                    if (activeInstance != null) onManageInstance()
                    else showNoInstanceDialog = true
                },
                onSettingsClick  = {
                    scope.launch { drawerState.close() }
                    onSettingsClick()
                },
                onLaunchMojo     = {
                    scope.launch { drawerState.close() }
                    val pm = context.packageManager
                    val intent = pm.getLaunchIntentForPackage("git.artdeell.mojo")
                    if (intent != null) {
                        context.startActivity(intent)
                    }
                },
                onExport         = {
                    scope.launch { drawerState.close() }
                    if (activeInstance != null) showExportSheet = true
                    else showNoInstanceDialog = true
                },
                onClose          = { scope.launch { drawerState.close() } }
            )
        }
    ) {
        Scaffold(
            modifier = Modifier.pointerInput(drawerState) {
                var startX = 0f
                var startY = 0f
                awaitPointerEventScope {
                    while (true) {
                        val down = awaitPointerEvent()
                        val pos  = down.changes.firstOrNull()?.position ?: continue
                        // Only care about touches that start near the left edge
                        if (pos.x > edgeZonePx) continue
                        startX = pos.x
                        startY = pos.y

                        // Track until finger lifts
                        var deltaX = 0f
                        var deltaY = 0f
                        while (true) {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull() ?: break
                            deltaX = change.position.x - startX
                            deltaY = change.position.y - startY
                            if (!change.pressed) break
                        }

                        // Open if swiped right far enough and not mostly vertical
                        if (deltaX > swipeThresholdPx && kotlin.math.abs(deltaX) > kotlin.math.abs(deltaY)) {
                            scope.launch { drawerState.open() }
                        }
                    }
                }
            },
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
                    navigationIcon = {
                        IconButton(onClick = { scope.launch { drawerState.open() } }) {
                            Icon(
                                Icons.Default.Menu, "Menu",
                                tint = MaterialTheme.colorScheme.onBackground
                            )
                        }
                    },
                    actions = {
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
    }

    // ── No-instance dialog ────────────────────────────────────────────────────
    if (showNoInstanceDialog) {
        AlertDialog(
            onDismissRequest = { showNoInstanceDialog = false },
            icon             = { Text("📦", fontSize = 32.sp) },
            title            = { Text("No Instance Selected", fontWeight = FontWeight.Bold) },
            text             = {
                Text(
                    "Select an instance from the card below first, then tap MANAGE to view and edit its mods, shaders, and resource packs.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f)
                )
            },
            confirmButton = {
                Button(onClick = { showNoInstanceDialog = false }) { Text("Got it") }
            }
        )
    }

    // ── Mojo Launcher not installed warning ───────────────────────────────────
    if (showMojoWarning) {
        AlertDialog(
            onDismissRequest = { showMojoWarning = false },
            icon             = { Text("⚠️", fontSize = 32.sp) },
            title            = { Text("Mojo Launcher Not Found", fontWeight = FontWeight.Bold) },
            text             = {
                Text(
                    "Mojorinth is designed to work alongside Mojo Launcher — without it, you won't be able to manage instances or download mods directly to your game.\n\nYou can still browse Modrinth content, but most of the app's features won't do much until Mojo Launcher is installed.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f)
                )
            },
            confirmButton = {
                Button(onClick = { showMojoWarning = false }) { Text("Got it") }
            }
        )
    }

    // ── Tutorial overlay ──────────────────────────────────────────────────────
    if (showTutorial) {
        TutorialOverlay(
            onDismiss = {
                settings.hasSeenTutorial = true
                showTutorial = false
            }
        )
    }

    // ── Export mod list sheet ─────────────────────────────────────────────────
    if (showExportSheet) {
        ExportModListSheet(onDismiss = { showExportSheet = false })
    }
}

// ─── Side Drawer ──────────────────────────────────────────────────────────────

@Composable
private fun AppDrawer(
    activeInstance: String?,
    onLogsClick: () -> Unit,
    onManageInstance: () -> Unit,
    onSettingsClick: () -> Unit,
    onLaunchMojo: () -> Unit,
    onExport: () -> Unit,
    onClose: () -> Unit
) {
    ModalDrawerSheet(
        drawerContainerColor = MaterialTheme.colorScheme.background
    ) {
        // Header
        Column(
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 24.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        "M", fontWeight = FontWeight.Black,
                        color    = MaterialTheme.colorScheme.onPrimary,
                        fontSize = 20.sp
                    )
                }
                Spacer(Modifier.width(10.dp))
                Text(
                    "Mojorinth",
                    fontWeight = FontWeight.Black,
                    fontSize   = 20.sp,
                    color      = MaterialTheme.colorScheme.onBackground
                )
                Spacer(Modifier.weight(1f))
                IconButton(onClick = onClose, modifier = Modifier.size(32.dp)) {
                    Icon(
                        Icons.Default.Close, "Close menu",
                        tint     = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            // Active instance badge
            activeInstance?.let {
                Spacer(Modifier.height(12.dp))
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.10f)
                ) {
                    Row(
                        modifier          = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("📦", fontSize = 14.sp)
                        Spacer(Modifier.width(6.dp))
                        Text(
                            it,
                            style      = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                            color      = MaterialTheme.colorScheme.primary,
                            maxLines   = 1,
                            overflow   = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }

        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
        Spacer(Modifier.height(8.dp))

        // ── Nav items ─────────────────────────────────────────────────────────
        DrawerNavItem(
            icon    = Icons.Default.PlayArrow,
            label   = "Open Mojo Launcher",
            onClick = onLaunchMojo
        )

        DrawerNavItem(
            icon    = Icons.Default.FileDownload,
            label   = "Export Mod List",
            enabled = activeInstance != null,
            hint    = if (activeInstance == null) "Select an instance first" else null,
            onClick = onExport
        )

        DrawerNavItem(
            icon    = Icons.Default.BugReport,
            label   = "Log Analyzer",
            badge   = "NEW",
            onClick = onLogsClick
        )

        DrawerNavItem(
            icon    = Icons.Default.Folder,
            label   = "Instance Manager",
            enabled = activeInstance != null,
            hint    = if (activeInstance == null) "Select an instance first" else null,
            onClick = onManageInstance
        )

        Spacer(Modifier.weight(1f))
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
        Spacer(Modifier.height(4.dp))

        DrawerNavItem(
            icon    = Icons.Default.Settings,
            label   = "Settings",
            onClick = onSettingsClick
        )

        Spacer(Modifier.height(16.dp))
    }
}

@Composable
private fun DrawerNavItem(
    icon: ImageVector,
    label: String,
    badge: String? = null,
    enabled: Boolean = true,
    hint: String? = null,
    onClick: () -> Unit
) {
    NavigationDrawerItem(
        icon  = {
            Icon(
                icon, null,
                tint = if (enabled)
                    MaterialTheme.colorScheme.onSurface
                else
                    MaterialTheme.colorScheme.onSurface.copy(alpha = 0.35f)
            )
        },
        label = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    label,
                    color = if (enabled)
                        MaterialTheme.colorScheme.onSurface
                    else
                        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.35f)
                )
                badge?.let {
                    Spacer(Modifier.width(6.dp))
                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = MaterialTheme.colorScheme.primary
                    ) {
                        Text(
                            it,
                            modifier   = Modifier.padding(horizontal = 5.dp, vertical = 1.dp),
                            style      = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color      = MaterialTheme.colorScheme.onPrimary,
                            fontSize   = 8.sp
                        )
                    }
                }
            }
        },
        badge = hint?.let {
            {
                Text(
                    it,
                    style    = MaterialTheme.typography.labelSmall,
                    color    = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.35f),
                    fontSize = 9.sp
                )
            }
        },
        selected = false,
        onClick  = { if (enabled) onClick() },
        modifier = Modifier.padding(horizontal = 12.dp)
    )
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
        }

        item {
            BrowseTypeGrid(onBrowseType = onBrowseType)
            Spacer(Modifier.height(20.dp))
        }

        if (uiState.trendingMods.isNotEmpty()) {
            item { SectionHeader("Trending Mods", "Popular right now") }
            item {
                FeaturedRow(mods = uiState.trendingMods, onModClick = onModClick)
                Spacer(Modifier.height(20.dp))
            }
        }

        if (uiState.trendingModpacks.isNotEmpty()) {
            item { SectionHeader("Trending Modpacks", "Top modpacks") }
            item {
                FeaturedRow(mods = uiState.trendingModpacks, onModClick = onModClick)
                Spacer(Modifier.height(20.dp))
            }
        }

        if (uiState.trendingShaders.isNotEmpty()) {
            item { SectionHeader("Trending Shaders", "Make it beautiful") }
            item {
                FeaturedRow(mods = uiState.trendingShaders, onModClick = onModClick)
                Spacer(Modifier.height(20.dp))
            }
        }

        if (uiState.newlyUpdated.isNotEmpty()) {
            item { SectionHeader("Recently Updated", "Fresh content") }
            item {
                FeaturedRow(mods = uiState.newlyUpdated, onModClick = onModClick)
                Spacer(Modifier.height(20.dp))
            }
        }
    }
}

// ─── Section Header ───────────────────────────────────────────────────────────

@Composable
private fun SectionHeader(
    title: String,
    subtitle: String? = null,
    onSeeAll: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    Row(
        modifier          = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onBackground)
            subtitle?.let {
                Text(it, style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f))
            }
        }
        onSeeAll?.let {
            TextButton(onClick = it) {
                Text("See all", style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary)
            }
        }
    }
}

// ─── Browse type grid ─────────────────────────────────────────────────────────

private val BROWSE_TYPES = listOf(
    Triple("🧩", "Mods",            "mod"),
    Triple("🎨", "Resource Packs",  "resourcepack"),
    Triple("🌍", "Data Packs",      "datapack"),
    Triple("✨", "Shaders",         "shader"),
    Triple("🗺", "Modpacks",        "modpack"),
    Triple("🔌", "Plugins",         "plugin")
)

@Composable
private fun BrowseTypeGrid(onBrowseType: (String) -> Unit) {
    val rows = BROWSE_TYPES.chunked(3)
    Column(
        modifier            = Modifier.padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        rows.forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { (emoji, label, type) ->
                    BrowseTypeChip(
                        emoji    = emoji,
                        label    = label,
                        onClick  = { onBrowseType(type) },
                        modifier = Modifier.weight(1f)
                    )
                }
                repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

@Composable
private fun BrowseTypeChip(
    emoji: String,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        onClick = onClick,
        shape   = RoundedCornerShape(12.dp),
        color   = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
        border  = androidx.compose.foundation.BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
        ),
        modifier = modifier
    ) {
        Column(
            modifier              = Modifier.padding(vertical = 14.dp),
            horizontalAlignment   = Alignment.CenterHorizontally,
            verticalArrangement   = Arrangement.spacedBy(4.dp)
        ) {
            Text(emoji, fontSize = 22.sp)
            Text(
                label,
                style      = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.SemiBold,
                maxLines   = 1,
                overflow   = TextOverflow.Ellipsis,
                color      = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

// ─── Featured row ─────────────────────────────────────────────────────────────

@Composable
private fun FeaturedRow(mods: List<SearchResult>, onModClick: (String) -> Unit) {
    LazyRow(
        contentPadding        = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        items(mods, key = { it.projectId }) { mod ->
            ModCard(mod = mod, onClick = { onModClick(mod.projectId) })
        }
    }
}

@Composable
private fun ModCard(mod: SearchResult, onClick: () -> Unit) {
    Surface(
        onClick  = onClick,
        shape    = RoundedCornerShape(12.dp),
        color    = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        modifier = Modifier
            .width(120.dp)
            .border(
                1.dp,
                MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f),
                RoundedCornerShape(12.dp)
            )
    ) {
        Column(
            modifier            = Modifier.padding(10.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
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
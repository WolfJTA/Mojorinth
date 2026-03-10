package com.example.modrinthforandroid.ui.screens

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
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.example.modrinthforandroid.data.AppSettings
import com.example.modrinthforandroid.data.InstanceManager
import com.example.modrinthforandroid.data.model.SearchResult
import com.example.modrinthforandroid.ui.components.formatNumber
import com.example.modrinthforandroid.viewmodel.HomeUiState
import com.example.modrinthforandroid.viewmodel.HomeViewModel
import java.io.File

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
    val context = LocalContext.current

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
        // ── Instance picker ───────────────────────────────────────────────
        item { InstancePickerCard() }

        // ── Browse shortcuts ──────────────────────────────────────────────
        item {
            Spacer(Modifier.height(20.dp))
            SectionHeader("Browse", "What are you looking for?")
            Spacer(Modifier.height(12.dp))
            BrowseGrid(onBrowseType = onBrowseType)
        }

        // ── Trending Mods ─────────────────────────────────────────────────
        if (uiState.trendingMods.isNotEmpty()) {
            item {
                Spacer(Modifier.height(24.dp))
                SectionHeader("Trending Mods", "Most downloaded right now",
                    onSeeAll = { onBrowseType("mod") })
                Spacer(Modifier.height(12.dp))
                ModRow(mods = uiState.trendingMods, onModClick = onModClick)
            }
        }

        // ── Popular Modpacks ──────────────────────────────────────────────
        if (uiState.trendingModpacks.isNotEmpty()) {
            item {
                Spacer(Modifier.height(24.dp))
                SectionHeader("Popular Modpacks", "Ready-to-play curated packs",
                    onSeeAll = { onBrowseType("modpack") })
                Spacer(Modifier.height(12.dp))
                ModRow(mods = uiState.trendingModpacks, onModClick = onModClick)
            }
        }

        // ── Top Shaders ───────────────────────────────────────────────────
        if (uiState.trendingShaders.isNotEmpty()) {
            item {
                Spacer(Modifier.height(24.dp))
                SectionHeader("Top Shaders", "Make your game beautiful",
                    onSeeAll = { onBrowseType("shader") })
                Spacer(Modifier.height(12.dp))
                ModRow(mods = uiState.trendingShaders, onModClick = onModClick)
            }
        }

        // ── Recently Updated ──────────────────────────────────────────────
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun InstancePickerCard() {
    val context      = LocalContext.current
    val focusManager = LocalFocusManager.current

    // Mirror InstanceManager state into local compose state so the card recomposes
    var activeName      by remember { mutableStateOf(InstanceManager.activeInstanceName) }
    var typedName       by remember { mutableStateOf("") }
    var showSuggestions by remember { mutableStateOf(false) }
    var showBrowser     by remember { mutableStateOf(false) }

    // Instances found on disk
    val instances = remember { InstanceManager.listInstances(context) }

    // Folder browser dialog
    if (showBrowser) {
        FolderBrowserDialog(
            startPath = AppSettings.get(context).mojoInstancesPath,
            onFolderSelected = { folderName ->
                InstanceManager.setInstance(folderName)
                activeName  = InstanceManager.activeInstanceName
                typedName   = ""
                showBrowser = false
            },
            onDismiss = { showBrowser = false }
        )
    }

    val filteredInstances = remember(typedName, instances) {
        if (typedName.isBlank()) instances
        else instances.filter { it.contains(typedName, ignoreCase = true) }
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
                    Text("Active Instance",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface)
                    Text(
                        text = if (activeName != null)
                            "Downloads → MojoLauncher/instances/$activeName"
                        else
                            "No instance selected — downloads go to fallback folders",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Spacer(Modifier.height(14.dp))

            // ── Active instance chip or picker input ──────────────────────
            if (activeName != null) {
                // Show the active instance with a clear button
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
                        text  = activeName!!,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = green,
                        modifier = Modifier.weight(1f)
                    )
                    // Instance exists indicator
                    val exists = InstanceManager.instanceExists(context, activeName!!)
                    if (!exists) {
                        Icon(Icons.Default.Warning, "Folder not found",
                            tint = Color(0xFFFFA726),
                            modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                    }
                    IconButton(
                        onClick  = {
                            InstanceManager.clearInstance()
                            activeName = null
                            typedName  = ""
                        },
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(Icons.Default.Close, "Clear instance",
                            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                            modifier = Modifier.size(16.dp))
                    }
                }

                if (!InstanceManager.instanceExists(context, activeName!!)) {
                    Spacer(Modifier.height(6.dp))
                    Text("⚠ Folder not found — check your MojoLauncher instances path in Settings",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color(0xFFFFA726))
                }

            } else {
                // ── Text input to type or search an instance name ──────────
                OutlinedTextField(
                    value          = typedName,
                    onValueChange  = {
                        typedName       = it
                        showSuggestions = it.isNotEmpty() || instances.isNotEmpty()
                    },
                    modifier       = Modifier.fillMaxWidth(),
                    placeholder    = { Text("Type or pick an instance name…") },
                    leadingIcon    = { Icon(Icons.Default.Search, null) },
                    trailingIcon   = {
                        if (typedName.isNotEmpty()) {
                            IconButton(onClick = { typedName = ""; showSuggestions = false }) {
                                Icon(Icons.Default.Close, "Clear")
                            }
                        }
                    },
                    singleLine     = true,
                    shape          = RoundedCornerShape(10.dp),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = {
                        if (typedName.isNotBlank()) {
                            InstanceManager.setInstance(typedName)
                            activeName      = InstanceManager.activeInstanceName
                            showSuggestions = false
                            focusManager.clearFocus()
                        }
                    }),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor   = green,
                        unfocusedBorderColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.2f)
                    )
                )

                // ── Browse filesystem button ───────────────────────────────
                Spacer(Modifier.height(6.dp))
                OutlinedButton(
                    onClick  = { showBrowser = true },
                    modifier = Modifier.fillMaxWidth(),
                    shape    = RoundedCornerShape(10.dp),
                    colors   = ButtonDefaults.outlinedButtonColors(
                        contentColor = green
                    )
                ) {
                    Icon(Icons.Default.List, null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Browse instance folders", style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold)
                }

                // ── Suggestions dropdown from scanned instances ────────────
                AnimatedVisibility(visible = showSuggestions && filteredInstances.isNotEmpty()) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 4.dp)
                    ) {
                        Text("Found on device:",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                            modifier = Modifier.padding(bottom = 4.dp))
                        filteredInstances.take(5).forEach { name ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable {
                                        InstanceManager.setInstance(name)
                                        activeName      = InstanceManager.activeInstanceName
                                        typedName       = ""
                                        showSuggestions = false
                                        focusManager.clearFocus()
                                    }
                                    .padding(horizontal = 12.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("📁", fontSize = 14.sp)
                                Spacer(Modifier.width(8.dp))
                                Text(name,
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.Medium,
                                    color = MaterialTheme.colorScheme.onSurface)
                            }
                            HorizontalDivider(
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f))
                        }
                    }
                }

                // ── Confirm button when text is typed ─────────────────────
                AnimatedVisibility(visible = typedName.isNotBlank()) {
                    Column {
                        Spacer(Modifier.height(8.dp))
                        Button(
                            onClick = {
                                InstanceManager.setInstance(typedName)
                                activeName      = InstanceManager.activeInstanceName
                                showSuggestions = false
                                focusManager.clearFocus()
                            },
                            modifier = Modifier.fillMaxWidth(),
                            shape    = RoundedCornerShape(10.dp)
                        ) {
                            Icon(Icons.Default.Check, null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Use \"$typedName\"", fontWeight = FontWeight.SemiBold)
                        }
                    }
                }

                // ── Tip when no instances found on disk ───────────────────
                if (instances.isEmpty()) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "No instances found at ${AppSettings.get(context).mojoInstancesPath} — " +
                                "check the path in Settings, or type a name manually.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f),
                        lineHeight = 16.sp
                    )
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
        BrowseItem("Mods",          "⚙️",  "mod",          green),
        BrowseItem("Modpacks",      "📦",  "modpack",      Color(0xFF42A5F5)),
        BrowseItem("Shaders",       "✨",  "shader",       Color(0xFFFFA726)),
        BrowseItem("Resource\nPacks","🎨", "resourcepack", Color(0xFFAB47BC)),
        BrowseItem("Data\nPacks",   "📋",  "datapack",     Color(0xFF26A69A)),
        BrowseItem("Plugins",       "🔌",  "plugin",       Color(0xFFEF5350))
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
// ─── Folder Browser Dialog ────────────────────────────────────────────────────

/**
 * A simple filesystem browser dialog.
 * Starts at [startPath] (the MojoLauncher instances root) and lets the user
 * tap folders. At the instances-root level, tapping a folder (or "Select") picks
 * it as the active instance. Deeper levels let the user navigate around.
 */
@Composable
fun FolderBrowserDialog(
    startPath: String,
    onFolderSelected: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var currentDir by remember { mutableStateOf(File(startPath)) }
    val entries by remember(currentDir) {
        derivedStateOf {
            currentDir.listFiles()
                ?.filter { it.isDirectory }
                ?.sortedBy { it.name }
                ?: emptyList()
        }
    }

    val breadcrumb = currentDir.absolutePath
        .removePrefix(startPath)
        .trim('/')
        .let { if (it.isEmpty()) "instances" else "instances/$it" }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 4.dp,
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.75f)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {

                Text("Browse Instance Folders",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface)

                Spacer(Modifier.height(4.dp))

                Text("📂 $breadcrumb",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis)

                Spacer(Modifier.height(12.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))
                Spacer(Modifier.height(8.dp))

                // Back row when navigated into a subfolder
                if (currentDir.absolutePath != startPath) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { currentDir = currentDir.parentFile ?: currentDir }
                            .padding(horizontal = 10.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.ArrowBack, "Go up",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(10.dp))
                        Text(".. (go up)",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Medium)
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f))
                }

                // Folder list
                if (entries.isEmpty()) {
                    Box(
                        modifier = Modifier.weight(1f).fillMaxWidth(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            if (!currentDir.exists()) "Path not found — check Settings"
                            else "No subfolders here",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                        )
                    }
                } else {
                    LazyColumn(modifier = Modifier.weight(1f)) {
                        items(entries, key = { it.absolutePath }) { folder ->
                            val isInstanceRoot = currentDir.absolutePath == startPath
                            val looksLikeInstance = File(folder, "mods").exists()
                                    || File(folder, "shaderpacks").exists()
                                    || File(folder, "resourcepacks").exists()

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable {
                                        if (isInstanceRoot) {
                                            // At instances root: tap row = select instance
                                            onFolderSelected(folder.name)
                                        } else {
                                            currentDir = folder
                                        }
                                    }
                                    .padding(horizontal = 10.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text(
                                        text = if (isInstanceRoot && looksLikeInstance) "🎮" else "📁",
                                        fontSize = 18.sp
                                    )
                                    Spacer(Modifier.width(10.dp))
                                    Column {
                                        Text(folder.name,
                                            style = MaterialTheme.typography.bodySmall,
                                            fontWeight = FontWeight.Medium,
                                            color = MaterialTheme.colorScheme.onSurface,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis)
                                        if (isInstanceRoot && looksLikeInstance) {
                                            Text("Minecraft instance",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f))
                                        }
                                    }
                                }
                                if (isInstanceRoot) {
                                    TextButton(
                                        onClick = { onFolderSelected(folder.name) },
                                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                                    ) {
                                        Text("Select",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.primary,
                                            fontWeight = FontWeight.Bold)
                                    }
                                } else {
                                    Icon(Icons.Default.KeyboardArrowRight, "Open",
                                        tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f),
                                        modifier = Modifier.size(18.dp))
                                }
                            }
                            HorizontalDivider(
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f))
                        }
                    }
                }

                Spacer(Modifier.height(12.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))
                Spacer(Modifier.height(8.dp))

                TextButton(
                    onClick = onDismiss,
                    modifier = Modifier.align(Alignment.End)
                ) { Text("Cancel") }
            }
        }
    }
}
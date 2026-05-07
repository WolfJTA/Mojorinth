package com.example.modrinthforandroid.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.modrinthforandroid.data.InstanceManager
import com.example.modrinthforandroid.data.SearchHistory
import com.example.modrinthforandroid.ui.components.FilterBottomSheet
import com.example.modrinthforandroid.ui.components.ModCard
import com.example.modrinthforandroid.viewmodel.BrowseFilters
import com.example.modrinthforandroid.viewmodel.BrowseViewModel
import com.example.modrinthforandroid.viewmodel.BrowseViewModelFactory1

fun projectTypeDisplayName(type: String) = when (type) {
    "mod"          -> "Mods"
    "modpack"      -> "Modpacks"
    "shader"       -> "Shaders"
    "resourcepack" -> "Resource Packs"
    "datapack"     -> "Data Packs"
    "plugin"       -> "Plugins"
    else           -> type.replaceFirstChar { it.uppercase() }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BrowseScreen(
    projectType: String,
    onModClick: (String) -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val history = remember { SearchHistory.get(context) }

    val instanceConfig = remember { InstanceManager.activeInstanceConfig }
    val initialFilters = remember(instanceConfig) {
        BrowseFilters(
            gameVersion = instanceConfig?.mcVersion,
            loader      = instanceConfig?.loaderSlug
                ?.takeIf { projectType == "mod" || projectType == "plugin" }
        )
    }

    val viewModel: BrowseViewModel = viewModel(
        key     = projectType,
        factory = BrowseViewModelFactory1(projectType, initialFilters, context.applicationContext)
    )

    val uiState      by viewModel.uiState.collectAsState()
    val query        by viewModel.query.collectAsState()
    val filters      by viewModel.filters.collectAsState()
    val installedIds by viewModel.installedIds.collectAsState()

    var showFilters    by remember { mutableStateOf(false) }
    var searchFocused  by remember { mutableStateOf(false) }
    var historyEntries by remember { mutableStateOf(history.get(projectType)) }
    val listState = rememberLazyListState()

    // Infinite scroll
    val shouldLoadMore by remember {
        derivedStateOf {
            val lastVisible = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            val total       = listState.layoutInfo.totalItemsCount
            lastVisible >= total - 5 && total > 0
        }
    }
    LaunchedEffect(shouldLoadMore) {
        if (shouldLoadMore) viewModel.loadMore()
    }

    val activeFilterCount = listOf(filters.gameVersion, filters.loader, filters.category)
        .count { it != null } + if (filters.sortIndex != "relevance") 1 else 0

    val isInstanceFiltered = instanceConfig != null &&
            filters.gameVersion == instanceConfig.mcVersion &&
            (filters.loader == instanceConfig.loaderSlug || projectType !in listOf("mod", "plugin"))

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            projectTypeDisplayName(projectType),
                            fontWeight = FontWeight.Bold,
                            color      = MaterialTheme.colorScheme.primary
                        )
                        if (isInstanceFiltered && instanceConfig != null) {
                            Text(
                                "🎮 ${instanceConfig.summary}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.65f)
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    BadgedBox(
                        badge = {
                            if (activeFilterCount > 0) Badge { Text("$activeFilterCount") }
                        }
                    ) {
                        IconButton(onClick = { showFilters = true }) {
                            Icon(Icons.Default.Settings, contentDescription = "Filters")
                        }
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
            SearchBarWithHistory(
                query          = query,
                onQueryChange  = {
                    viewModel.onQueryChange(it)
                    if (it.isBlank()) historyEntries = history.get(projectType)
                },
                onFocusChanged = { searchFocused = it },
                onSubmit       = {
                    if (query.isNotBlank()) {
                        history.add(projectType, query)
                        historyEntries = history.get(projectType)
                    }
                }
            )

            ActiveFilterChips(
                filters          = filters,
                isInstanceFilter = isInstanceFiltered,
                instanceSummary  = instanceConfig?.summary,
                onClearFilter    = { viewModel.onFiltersChange(it) }
            )

            Box(modifier = Modifier.fillMaxSize()) {
                when {
                    // Search history overlay
                    searchFocused && query.isBlank() && historyEntries.isNotEmpty() -> {
                        SearchHistoryOverlay(
                            entries   = historyEntries,
                            onSelect  = { viewModel.onQueryChange(it) },
                            onRemove  = {
                                history.remove(projectType, it)
                                historyEntries = history.get(projectType)
                            }
                        )
                    }

                    uiState.isLoading -> Box(
                        Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator()
                    }

                    uiState.error != null -> Column(
                        modifier            = Modifier.align(Alignment.Center),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text("❌ ${uiState.error}")
                        Spacer(modifier = Modifier.height(8.dp))
                        Button(onClick = { viewModel.onQueryChange(query) }) { Text("Retry") }
                    }

                    uiState.results.isEmpty() -> Column(
                        modifier            = Modifier.align(Alignment.Center),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text("🔍 No results found", style = MaterialTheme.typography.titleMedium)
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            "Try different search terms or filters",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
                        )
                        if (isInstanceFiltered) {
                            Spacer(modifier = Modifier.height(12.dp))
                            OutlinedButton(onClick = { viewModel.onFiltersChange(BrowseFilters()) }) {
                                Text("Clear instance filters")
                            }
                        }
                    }

                    else -> LazyColumn(
                        state               = listState,
                        contentPadding      = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        items(uiState.results, key = { it.projectId }) { mod ->
                            ModCard(
                                mod         = mod,
                                onClick     = { onModClick(mod.projectId) },
                                isInstalled = mod.projectId in installedIds
                            )
                        }
                        if (uiState.isLoadingMore) {
                            item {
                                Box(
                                    modifier         = Modifier.fillMaxWidth().padding(16.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    CircularProgressIndicator(
                                        modifier    = Modifier.size(24.dp),
                                        strokeWidth = 2.dp
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showFilters) {
        FilterBottomSheet(
            currentFilters   = filters,
            onFiltersChanged = { viewModel.onFiltersChange(it) },
            onDismiss        = { showFilters = false }
        )
    }
}

// ─── Search bar with focus tracking ──────────────────────────────────────────

@Composable
fun SearchBarWithHistory(
    query: String,
    onQueryChange: (String) -> Unit,
    onFocusChanged: (Boolean) -> Unit,
    onSubmit: () -> Unit
) {
    OutlinedTextField(
        value         = query,
        onValueChange = onQueryChange,
        modifier      = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .onFocusChanged { onFocusChanged(it.isFocused) },
        placeholder   = { Text("Search...") },
        leadingIcon   = { Icon(Icons.Default.Search, contentDescription = null) },
        trailingIcon  = {
            if (query.isNotBlank()) {
                IconButton(onClick = { onQueryChange("") }) {
                    Icon(
                        Icons.Default.Close, "Clear",
                        tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                    )
                }
            }
        },
        singleLine    = true,
        shape         = MaterialTheme.shapes.large,
        colors        = OutlinedTextFieldDefaults.colors(
            focusedBorderColor   = MaterialTheme.colorScheme.primary,
            unfocusedBorderColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.2f)
        )
    )
}

// Keep the old SearchBar for any other screens that import it
@Composable
fun SearchBar(query: String, onQueryChange: (String) -> Unit) {
    SearchBarWithHistory(
        query          = query,
        onQueryChange  = onQueryChange,
        onFocusChanged = {},
        onSubmit       = {}
    )
}

@Composable
fun ActiveFilterChips(
    filters: BrowseFilters,
    isInstanceFilter: Boolean = false,
    instanceSummary: String? = null,
    onClearFilter: (BrowseFilters) -> Unit
) {
    val chips = buildList {
        if (isInstanceFilter && instanceSummary != null) {
            add("🎮 $instanceSummary" to filters.copy(gameVersion = null, loader = null))
        } else {
            filters.gameVersion?.let { add("MC $it" to filters.copy(gameVersion = null)) }
            filters.loader?.let { add(it.replaceFirstChar { c -> c.uppercase() } to filters.copy(loader = null)) }
        }
        filters.category?.let { add(it to filters.copy(category = null)) }
        if (filters.sortIndex != "relevance") add("Sort: ${filters.sortIndex}" to filters.copy(sortIndex = "relevance"))
    }

    if (chips.isNotEmpty()) {
        androidx.compose.foundation.lazy.LazyRow(
            contentPadding        = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(chips) { (label, cleared) ->
                FilterChip(
                    selected     = true,
                    onClick      = { onClearFilter(cleared) },
                    label        = { Text(label, style = MaterialTheme.typography.labelSmall) },
                    trailingIcon = {
                        Icon(Icons.Default.Close, "Remove", modifier = Modifier.size(14.dp))
                    }
                )
            }
        }
    }
}

@Composable
fun SearchHistoryOverlay(
    entries: List<String>,
    onSelect: (String) -> Unit,
    onRemove: (String) -> Unit
) {
    LazyColumn(
        modifier       = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp)
    ) {
        items(entries) { entry ->
            ListItem(
                headlineContent = { Text(entry, style = MaterialTheme.typography.bodyMedium) },
                leadingContent  = {
                    Icon(Icons.Default.History, null,
                        tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f))
                },
                trailingContent = {
                    IconButton(onClick = { onRemove(entry) }) {
                        Icon(Icons.Default.Close, "Remove",
                            modifier = Modifier.size(16.dp),
                            tint     = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f))
                    }
                },
                modifier = Modifier.fillMaxWidth()
            )
            HorizontalDivider(thickness = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
        }
    }
}
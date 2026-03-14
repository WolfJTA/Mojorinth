package com.example.modrinthforandroid.ui.screens

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
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
import com.example.modrinthforandroid.viewmodel.BrowseViewModelFactory

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
        factory = BrowseViewModelFactory(projectType, initialFilters)
    )

    val uiState by viewModel.uiState.collectAsState()
    val query   by viewModel.query.collectAsState()
    val filters by viewModel.filters.collectAsState()

    var showFilters     by remember { mutableStateOf(false) }
    var searchFocused   by remember { mutableStateOf(false) }
    var historyEntries  by remember { mutableStateOf(history.get(projectType)) }
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
            // ── Search bar ────────────────────────────────────────────────
            SearchBarWithHistory(
                query       = query,
                onQueryChange = { viewModel.onQueryChange(it) },
                onFocusChanged = { searchFocused = it },
                onSubmit    = {
                    if (query.isNotBlank()) {
                        history.add(projectType, query)
                        historyEntries = history.get(projectType)
                    }
                }
            )

            // ── History chips (shown when search bar is focused + query is empty) ──
            if (searchFocused && query.isBlank() && historyEntries.isNotEmpty()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment     = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.History, null,
                        modifier = Modifier.size(14.dp),
                        tint     = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                    )
                    historyEntries.forEach { entry ->
                        InputChip(
                            selected     = false,
                            onClick      = {
                                viewModel.onQueryChange(entry)
                                searchFocused = false
                            },
                            label        = {
                                Text(entry, style = MaterialTheme.typography.labelSmall)
                            },
                            trailingIcon = {
                                IconButton(
                                    onClick  = {
                                        history.remove(projectType, entry)
                                        historyEntries = history.get(projectType)
                                    },
                                    modifier = Modifier.size(16.dp)
                                ) {
                                    Icon(
                                        Icons.Default.Close, "Remove",
                                        modifier = Modifier.size(12.dp),
                                        tint     = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                                    )
                                }
                            }
                        )
                    }
                }
            }

            // ── Active filter chips ───────────────────────────────────────
            if (activeFilterCount > 0) {
                ActiveFilterChips(
                    filters          = filters,
                    isInstanceFilter = isInstanceFiltered,
                    instanceSummary  = instanceConfig?.summary,
                    onClearFilters   = { viewModel.onFiltersChange(BrowseFilters()) }
                )
            }

            Box(modifier = Modifier.fillMaxSize()) {
                when {
                    uiState.isLoading -> CircularProgressIndicator(
                        modifier = Modifier.align(Alignment.Center),
                        color    = MaterialTheme.colorScheme.primary
                    )

                    uiState.error != null -> Column(
                        modifier            = Modifier.align(Alignment.Center),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text("😕 ${uiState.error}")
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
                        state           = listState,
                        contentPadding  = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        items(uiState.results, key = { it.projectId }) { mod ->
                            ModCard(mod = mod, onClick = { onModClick(mod.projectId) })
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
    onClearFilters: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        if (isInstanceFilter && instanceSummary != null) {
            AssistChip(
                onClick = {},
                label   = {
                    Text(
                        "🎮 $instanceSummary",
                        style = MaterialTheme.typography.labelSmall
                    )
                }
            )
        } else {
            filters.gameVersion?.let {
                AssistChip(onClick = {}, label = { Text(it, style = MaterialTheme.typography.labelSmall) })
            }
            filters.loader?.let {
                AssistChip(onClick = {}, label = { Text(it, style = MaterialTheme.typography.labelSmall) })
            }
        }
        filters.category?.let {
            AssistChip(onClick = {}, label = { Text(it, style = MaterialTheme.typography.labelSmall) })
        }
        if (filters.sortIndex != "relevance") {
            AssistChip(onClick = {}, label = { Text(filters.sortIndex, style = MaterialTheme.typography.labelSmall) })
        }
        TextButton(onClick = onClearFilters) {
            Text("Clear", style = MaterialTheme.typography.labelSmall)
        }
    }
}